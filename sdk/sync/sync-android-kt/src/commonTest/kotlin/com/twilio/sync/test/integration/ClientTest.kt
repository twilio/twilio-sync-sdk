//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.test.util.TestData
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.ConnectionState
import com.twilio.sync.utils.ConnectionState.Connected
import com.twilio.sync.utils.ConnectionState.Denied
import com.twilio.sync.utils.data
import com.twilio.sync.utils.setData
import com.twilio.sync.utils.updateDocument
import com.twilio.sync.utils.use
import com.twilio.test.util.createNewDatabaseFile
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.wait
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.InternalTwilioApi
import com.twilio.util.TwilioException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalTwilioApi::class)
class ClientTest {

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()
        SyncCacheCleaner.clearAllCaches()
    }

    @Test
    fun shutdown() = runTest {
        val syncClient = TestSyncClient { requestToken() }
        syncClient.shutdown()
    }

    @Test
    fun shutdownTwice() = runTest {
        val syncClient = TestSyncClient { requestToken() }
        syncClient.shutdown()
        syncClient.shutdown()
    }

    @Test
    fun createWithInvalidToken() = runTest {
        val exception = assertFailsWith<TwilioException> {
            TestSyncClient { "INVALID_TOKEN" }
        }

        assertEquals(ErrorReason.Unauthorized, exception.errorInfo.reason)
    }

    @Test
    fun createDocumentAfterShutdown() = runTest {
        val syncClient = TestSyncClient { requestToken() }
        syncClient.shutdown()

        val exception = assertFailsWith<TwilioException> { syncClient.documents.create() }
        assertEquals(ErrorReason.ClientShutdown, exception.errorInfo.reason)
    }

    @Test
    fun updateDocumentAfterShutdown() = runTest {
        val syncClient = TestSyncClient { requestToken() }
        syncClient.shutdown()

        val exception = assertFailsWith<TwilioException> {
            syncClient.documents.updateDocument("nonExistingDocument", TestData(1))
        }
        assertEquals(ErrorReason.ClientShutdown, exception.errorInfo.reason)
    }

    @Test
    fun createWhenOtherDatabaseExists() = runTest { // RTDSDK-4299
        createNewDatabaseFile("RTDSDK-4299")

        val syncClient = TestSyncClient { requestToken() }
        syncClient.shutdown()
    }

    @Test
    fun shutdownWhileReceivingUpdates() = runTest {
        val syncClient = TestSyncClient { requestToken() }

        val document = syncClient.documents.create()
        document.setData(TestData(0))

        val observerJob = document.events.onUpdated.launchIn(this)

        val updateJob = launch {
            TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
                var counter = 1

                coroutineScope {
                    List(10) {
                        launch {
                            while (isActive) {
                                runCatching { client.documents.updateDocument(document.sid, TestData(counter++)) }
                            }
                        }
                    }
                }
            }
        }

        document.events.onUpdated.first { it.data<TestData>().value >= 50 }

        syncClient.shutdown()

        observerJob.cancel()
        updateJob.cancel()
    }

    @Test
    @Ignore // This is manual test (too long)
    fun shutdownWhileReceivingUpdatesInfinite() = runBlocking {
        // The RTDSDK-4307 issue is reproduced by this test after 2-3 minutes of execution (in average).
        withTimeout(5.minutes) {
            var counter = 0
            while (true) {
                ensureActive()
                println("!!! Iteration: ${counter++}")
                shutdownWhileReceivingUpdates()
                SyncCacheCleaner.clearAllCaches()
            }
        }
    }

    @Test
    @Ignore // This is manual test (too long)
    fun expiredToken() = runTest(timeout = 350.seconds) {
        val token = requestToken(ttl = 250.seconds)
        val syncClient = TestSyncClient { token }

        val actualStates = mutableListOf<ConnectionState>()
        val listener1 = launch { syncClient.events.onConnectionStateChanged.toList(actualStates) }

        val actualErrors = mutableListOf<ErrorInfo>()
        val listener2 = launch { syncClient.events.onError.toList(actualErrors) }

        val expectedStates = listOf(Connected, Denied)

        val expectedErrors = listOf(
            ErrorInfo(
                ErrorReason.TokenExpired,
                status = 410,
                code = 51207,
                message = "TOKEN_EXPIRED",
                description = "Token expired"
            )
        )

        wait {
            delay(100.milliseconds) // to avoid crash with thread suspension
            actualStates.size == expectedStates.size && actualErrors.size == expectedErrors.size
        }

        delay(5.seconds) // to be sure no more events will be emitted

        assertEquals(expectedStates, actualStates)
        assertEquals(expectedErrors, actualErrors)

        listener1.cancel()
        listener2.cancel()
    }
}
