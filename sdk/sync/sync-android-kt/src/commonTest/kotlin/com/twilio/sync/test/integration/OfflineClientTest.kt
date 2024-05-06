//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.client.SyncClient
import com.twilio.sync.client.createInternalWithTwilsock
import com.twilio.sync.client.createTwilsock
import com.twilio.sync.test.util.TestData
import com.twilio.sync.test.util.TestMetadata
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.asFlow
import com.twilio.sync.utils.data
import com.twilio.sync.utils.readCertificates
import com.twilio.sync.utils.setData
import com.twilio.sync.utils.setItem
import com.twilio.sync.utils.twilsockUrl
import com.twilio.sync.utils.use
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.twilsock.client.Twilsock
import com.twilio.util.ErrorReason.ClientShutdown
import com.twilio.util.ErrorReason.MismatchedLastUserAccount
import com.twilio.util.ErrorReason.RetrierReachedMaxTime
import com.twilio.util.InternalTwilioApi
import com.twilio.util.TwilioException
import com.twilio.util.newSerialCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours

@OptIn(InternalTwilioApi::class)
class OfflineClientTest {

    private val identity = "ClientAndroidTest"

    private val tokenProvider = suspend { requestToken(identity) }

    private lateinit var offlineClient: SyncClient

    private lateinit var offlineTwilsock: Twilsock

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()
        SyncCacheCleaner.clearAllCaches()

        TestSyncClient(tokenProvider = tokenProvider).use { onlineClient ->

            val document = onlineClient.documents.openOrCreate("testDocument")
            document.setData(TestData(1))

            val map = onlineClient.maps.openOrCreate("testMap")
            map.setItem("key1", TestData(1))
            map.setItem("key2", TestData(2))

            // Iterate through item. So cache knows that it has all items of the map.
            map.queryItems().asFlow().collect()
        }

        offlineClient = createOfflineClient()
    }

    @AfterTest
    fun tearDown() {
        offlineClient.shutdown()
    }

    @Test
    fun createOfflineClientWithoutCachedAccountDescriptor() = runTest {
        SyncCacheCleaner.clearAllCaches()
        assertFails { createOfflineClient() }
    }

    @Test
    fun createOfflineClientNotUsingLastUser() = runTest {
        assertFails { createOfflineClient(useLastUserCache = false) }
    }

    @Test
    fun createOnlineClientWithDifferentIdentity() = runTest {
        val onlineClient = TestSyncClient { requestToken("differentIdentity") }
        val errorInfo = async { onlineClient.events.onError.first() }

        val documentCreateException = assertFailsWith<TwilioException> { onlineClient.documents.create() }
        assertEquals(ClientShutdown, documentCreateException.errorInfo.reason)

        val cancellationException = documentCreateException.cause as CancellationException
        assertEquals("SyncClient already shutdown", cancellationException.message)

        val clientShutdownException = cancellationException.cause as TwilioException
        assertEquals(ClientShutdown, clientShutdownException.errorInfo.reason)

        val mismatchedAccountException = clientShutdownException.cause as TwilioException
        assertEquals(MismatchedLastUserAccount, mismatchedAccountException.errorInfo.reason)

        assertEquals(MismatchedLastUserAccount, errorInfo.await().reason)

        onlineClient.shutdown()
    }

    @Test
    fun createOnlineClientWithDifferentIdentityNotUsingLastUser() = runTest {
        val onlineClient = TestSyncClient(useLastUserAccount = false) { requestToken("differentIdentity") }
        val document = onlineClient.documents.create(ttl = 1.hours) 
        onlineClient.shutdown()

        assertNotNull(document)
    }

    @Test
    fun createOffineClientWithDifferentIdentity() = runTest {
        val otherOfflineClient = createOfflineClient { requestToken("differentIdentity") }
        val errorInfo = async { otherOfflineClient.events.onError.first() }

        val offlineDocument = otherOfflineClient.documents.openExisting("testDocument")
        // Client is offline. We cannot verify account yet. So taking the document from cache.
        assertEquals(TestData(1), offlineDocument.data()) 

        offlineTwilsock.connect() // Now connect the twilsock. So the client comes online.

        // Now client realised that cache from wrong account has been used.
        assertEquals(MismatchedLastUserAccount, errorInfo.await().reason)
    }

    @Test
    fun offlineOpenExistingDocument() = runTest {
        val offlineDocument = offlineClient.documents.openExisting("testDocument")
        assertEquals(TestData(1), offlineDocument.data())
    }

    @Test
    fun offlineOpenNonExistingDocument() = runTest {
        val exception = assertFailsWith<TwilioException> {
            offlineClient.documents.openExisting("testNonExistingDocument")
        }
        assertEquals(RetrierReachedMaxTime, exception.errorInfo.reason)
    }

    @Test
    fun offlineOpenMap() = runTest {
        val offlineMap = offlineClient.maps.openOrCreate("testMap")

        assertEquals(TestData(1), offlineMap.getItem("key1")!!.data())
        assertEquals(TestData(2), offlineMap.getItem("key2")!!.data())

        offlineMap.queryItems().asFlow().toList().let { items ->
            assertEquals(2, items.size)
            assertEquals(TestData(1), items[0].data())
            assertEquals(TestData(2), items[1].data())
        }

        val exception = assertFailsWith<TwilioException> {
            offlineMap.getItem("nonExistingKey")
        }
        assertEquals(RetrierReachedMaxTime, exception.errorInfo.reason)
    }

    @Test
    fun offlineOpenNonExistingMap() = runTest {
        val exception = assertFailsWith<TwilioException> {
            offlineClient.maps.openOrCreate("testNonExistingMap")
        }
        assertEquals(RetrierReachedMaxTime, exception.errorInfo.reason)
    }

    @Test
    fun offlineOpenStream() = runTest {
        val exception = assertFailsWith<TwilioException> {
            offlineClient.streams.openExisting("testStream")
        }
        assertEquals(RetrierReachedMaxTime, exception.errorInfo.reason)
    }

    private suspend fun createOfflineClient(
        useLastUserCache: Boolean = true,
        tokenProvider: suspend () -> String = this.tokenProvider,
    ): SyncClient {
        val coroutineContext = newSerialCoroutineContext()
        val coroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

        val syncConfig = SyncConfig()
        val clientMetadata = TestMetadata()
        val certificates = readCertificates(syncConfig.syncClientConfig.deferCertificateTrustToPlatform)

        offlineTwilsock = createTwilsock(
            coroutineScope,
            syncConfig.twilsockUrl,
            syncConfig.syncClientConfig.useProxy,
            certificates,
            clientMetadata,
            connectivityMonitor = null, // Use default connectivity monitor implementation
            tokenProvider,
        )

        assertFalse(offlineTwilsock.isConnected) // Don't connect twilsock. So client will be offline.

        return createInternalWithTwilsock(
            offlineTwilsock,
            coroutineScope,
            useLastUserCache,
            syncConfig,
            tokenProvider,
        )
    }
}
