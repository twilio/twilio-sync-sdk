//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit

import com.twilio.sync.client.AccountStorage
import com.twilio.sync.client.SyncClientImpl
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.utils.ConnectionState
import com.twilio.sync.utils.ConnectionState.Connected
import com.twilio.sync.utils.ConnectionState.Connecting
import com.twilio.sync.utils.ConnectionState.Denied
import com.twilio.sync.utils.ConnectionState.Disconnected
import com.twilio.sync.utils.ConnectionState.Error
import com.twilio.sync.utils.ConnectionState.FatalError
import com.twilio.sync.utils.SyncConfig
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureAddObserver
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.wait
import com.twilio.twilsock.client.Twilsock
import com.twilio.util.AccountDescriptor
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CannotParse
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.ErrorReason.Unauthorized
import com.twilio.util.newChildCoroutineScope
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verifyOrder
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExcludeFromInstrumentedTests
class ClientImplTest {

    private lateinit var syncClient: SyncClientImpl

    @RelaxedMockK
    private lateinit var testCoroutineContext: CoroutineContext

    @RelaxedMockK
    private lateinit var subscriptionManager: SubscriptionManager

    @RelaxedMockK
    private lateinit var repository: SyncRepository

    @RelaxedMockK
    private lateinit var twilsock: Twilsock

    @RelaxedMockK
    private lateinit var accountStorage: AccountStorage

    @RelaxedMockK
    private lateinit var accountDescriptor: AccountDescriptor

    private fun createTestClient(coroutineScope: CoroutineScope = testCoroutineScope) = SyncClientImpl(
        coroutineScope,
        SyncConfig(),
        twilsock,
        subscriptionManager,
        repository,
        accountStorage,
        accountDescriptor,
    )

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@ClientImplTest, relaxUnitFun = true)
        every { twilsock.accountDescriptor } returns accountDescriptor
    }

    @Test
    fun initialConnectionStateConnected() = runTest {
        every { twilsock.isConnected } returns true

        val syncClient = createTestClient()

        assertEquals(Connected, syncClient.connectionState)
    }

    @Test
    fun connectionState() = runTest {
        every { twilsock.isConnected } returns false

        val syncClient = createTestClient()
        val twilsockObserver = twilsock.captureAddObserver()

        val actualStates = mutableListOf<ConnectionState>()
        val listener = launch { syncClient.events.onConnectionStateChanged.toList(actualStates) }

        assertEquals(Disconnected, syncClient.connectionState)
        wait { actualStates.size == 1 }

        twilsockObserver.onConnecting()
        assertEquals(Connecting, syncClient.connectionState)
        wait { actualStates.size == 2 }

        twilsockObserver.onConnected()
        assertEquals(Connected, syncClient.connectionState)
        wait { actualStates.size == 3 }

        twilsockObserver.onDisconnected("testReason")
        assertEquals(Disconnected, syncClient.connectionState)
        wait { actualStates.size == 4 }

        twilsockObserver.onNonFatalError(ErrorInfo(Timeout))
        assertEquals(Error, syncClient.connectionState)
        wait { actualStates.size == 5 }

        twilsockObserver.onFatalError(ErrorInfo(CannotParse))
        assertEquals(FatalError, syncClient.connectionState)
        wait { actualStates.size == 6 }

        twilsockObserver.onFatalError(ErrorInfo(Unauthorized, status = HttpStatusCode.Unauthorized.value))
        assertEquals(Denied, syncClient.connectionState)
        wait { actualStates.size == 7 }

        val expectedStates = listOf(
            Disconnected,
            Connecting, Connected,
            Disconnected, Error, FatalError, Denied
        )
        assertEquals(expectedStates, actualStates)

        listener.cancel()
    }

    @Test
    fun nonFatalError() = runTest {
        val syncClient = createTestClient()
        val twilsockObserver = twilsock.captureAddObserver()
        val actualError = async { syncClient.events.onError.first() }

        val expectedError = ErrorInfo(Timeout)
        twilsockObserver.onNonFatalError(expectedError)

        assertEquals(expectedError, actualError.await())
    }

    @Test
    fun fatalError() = runTest {
        val syncClient = createTestClient()
        val twilsockObserver = twilsock.captureAddObserver()
        val actualError = async { syncClient.events.onError.first() }

        val expectedError = ErrorInfo(CannotParse)
        twilsockObserver.onFatalError(expectedError)

        assertEquals(expectedError, actualError.await())
    }

    @Test
    fun deniedError() = runTest {
        val syncClient = createTestClient()
        val twilsockObserver = twilsock.captureAddObserver()
        val actualError = async { syncClient.events.onError.first() }

        val expectedError = ErrorInfo(Unauthorized, status = HttpStatusCode.Unauthorized.value)
        twilsockObserver.onFatalError(expectedError)

        assertEquals(expectedError, actualError.await())
    }

    @Test
    fun shutdown() = runTest {
        // Create separate client in order to not cancel testCoroutineScope
        val coroutineScope = testCoroutineScope.newChildCoroutineScope()
        val syncClient = createTestClient(coroutineScope)

        syncClient.shutdown()

        verifyOrder {
            twilsock.disconnect()
            repository.close()
            coroutineScope.cancel()
        }
    }
}
