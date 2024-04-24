//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.unit.states

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.wait
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.client.TwilsockState.Initializing
import com.twilio.twilsock.client.TwilsockState.WaitAndReconnect
import com.twilio.twilsock.client.test.util.captureNonFatalError
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.NetworkBecameUnreachable
import com.twilio.util.ErrorReason.TransportDisconnected
import io.mockk.every
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async

@ExcludeFromInstrumentedTests
class ConnectingTwilsockTest : BaseTwilsockTest() {

    override fun setUp() = runTest {
        super.setUp()
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
    fun transportConnected() = runTest {
        twilsock.onTransportConnected()
        assertIs<Initializing>(twilsock.state)
    }

    @Test
    fun transportDisconnected() = runTest {
        twilsock.onTransportDisconnected(ErrorInfo(TransportDisconnected, message = "test reason"))

        val errorInfo = twilsockObserver.captureNonFatalError()

        assertEquals(TransportDisconnected, errorInfo.reason)
        assertEquals("test reason", errorInfo.message)

        // due to async processing the state could ether be WaitAndReconnect or already switched to the Connecting
        assertTrue { twilsock.state is WaitAndReconnect || twilsock.state is Connecting }
    }

    @Test
    fun networkBecameUnreachable() = runTest {
        every { connectivityMonitor.isNetworkAvailable } returns false
        onConnectivityChanged()

        val errorInfo = twilsockObserver.captureNonFatalError()

        assertIs<WaitAndReconnect>(twilsock.state)
        assertEquals(NetworkBecameUnreachable, errorInfo.reason)
    }

    @Test
    fun updateToken() = runTest {
        val newToken = "newToken"
        val result = runCatching { twilsock.updateToken(newToken) }

        assertIs<Connecting>(twilsock.state)

        assertTrue { result.isSuccess } // token updated locally
        assertEquals(newToken, twilsock.token)
    }

    @Test
    fun sendRequest() = runTest {
        val future = async {
            val httpRequest = HttpRequest("http://www.example.com")
            twilsock.sendRequest(httpRequest)
        }

        wait { twilsock.pendingRequests.size == 1 }
        assertIs<Connecting>(twilsock.state)

        future.cancel()

        wait { twilsock.pendingRequests.isEmpty() }
        assertIs<Connecting>(twilsock.state)
    }
}
