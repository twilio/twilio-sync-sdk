//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.unit.states

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.twilioException
import com.twilio.test.util.wait
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.client.TwilsockState.WaitAndReconnect
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.ErrorReason.TransportDisconnected
import com.twilio.util.fibonacci
import com.twilio.util.logger
import io.mockk.every
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

@ExcludeFromInstrumentedTests
class WaitAndReconnectTwilsockTest : BaseTwilsockTest() {

    override fun setUp() = runTest {
        super.setUp()
        twilsock.connect()
        twilsock.onTransportDisconnected(ErrorInfo(TransportDisconnected))

        // Twilsock switches to the Connecting state immediately first time.
        // So in order to perform tests in the WaitAndReconnect state we disconnect transport second time
        wait { twilsock.state is Connecting }
        twilsock.onTransportDisconnected(ErrorInfo(TransportDisconnected))
        assertIs<WaitAndReconnect>(twilsock.state)

        waitAndVerify { twilsockObserver.onDisconnected(any()) }
        clearTwilsockObserverMock()

        logger.i { "Setup complete" }
    }

    @Test
    fun connect() {
        twilsock.connect()
        assertIs<Connecting>(twilsock.state)
    }

    @Test
    fun disconnect() = runTest {
        twilsock.disconnect()

        waitAndVerify { twilsockObserver.onDisconnected(any()) }
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun connectOnTimeout() = runTest {
        repeat(3) { n ->
            assertEquals(n + 2, twilsock.failedReconnectionAttempts)

            val minTimeout = fibonacci(n + 1).toInt().seconds

            delay(minTimeout - 0.5.seconds)
            assertIs<WaitAndReconnect>(twilsock.state)

            // maxTimeout = minTimeout + 1s. So wait 2s just for case
            wait(2.seconds) { twilsock.state is Connecting }

            twilsock.onTransportDisconnected(ErrorInfo(TransportDisconnected))
            assertIs<WaitAndReconnect>(twilsock.state)
        }
    }

    @Test
    fun networkBecameReachable() = runTest {
        every { connectivityMonitor.isNetworkAvailable } returns true
        onConnectivityChanged()

        assertIs<Connecting>(twilsock.state)
    }

    @Test
    fun updateToken() = runTest {
        val newToken = "newToken"
        val result = runCatching { twilsock.updateToken(newToken) }

        assertTrue { result.isSuccess } // token updated locally
        assertEquals(newToken, twilsock.token)
        assertIs<WaitAndReconnect>(twilsock.state)
    }

    @Test
    fun sendRequest() = runTest {
        val future = async {
            val httpRequest = HttpRequest("http://www.example.com", timeout = 1.seconds)
            runCatching { twilsock.sendRequest(httpRequest) }
        }

        wait { twilsock.pendingRequests.size == 1 }
        assertIs<WaitAndReconnect>(twilsock.state)

        val result = future.await()
        assertTrue { result.isFailure }
        assertEquals(Timeout, result.twilioException.errorInfo.reason)
        assertEquals(0, twilsock.pendingRequests.size)
    }
}
