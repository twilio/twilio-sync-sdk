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
import com.twilio.twilsock.client.TwilsockMessage
import com.twilio.twilsock.client.TwilsockMessage.Method.INIT
import com.twilio.twilsock.client.TwilsockState.Connected
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.client.TwilsockState.Initializing
import com.twilio.twilsock.client.TwilsockState.WaitAndReconnect
import com.twilio.twilsock.client.test.util.captureNonFatalError
import com.twilio.twilsock.client.test.util.captureSentMessage
import com.twilio.twilsock.client.test.util.fakeInitReply
import com.twilio.twilsock.client.test.util.fakeTooManyRequestsReply
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.HostnameUnverified
import com.twilio.util.ErrorReason.NetworkBecameUnreachable
import com.twilio.util.ErrorReason.SslHandshakeError
import com.twilio.util.ErrorReason.TooManyRequests
import com.twilio.util.ErrorReason.TransportDisconnected
import io.mockk.every
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async

@ExcludeFromInstrumentedTests
class InitializingTwilsockTest : BaseTwilsockTest() {

    internal lateinit var sentInitMessage: TwilsockMessage

    override fun setUp() = runTest {
        super.setUp()
        twilsock.connect()
        twilsock.onTransportConnected()

        assertIs<Initializing>(twilsock.state)

        sentInitMessage = twilsockTransport.captureSentMessage()
        assertEquals(INIT, sentInitMessage.method)
    }

    @Test
    fun disconnect() = runTest {
        twilsock.disconnect()

        waitAndVerify { twilsockObserver.onDisconnected(any()) }
        assertIs<Disconnected>(twilsock.state)
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
        val future = async {
            twilsock.updateToken(newToken)
        }

        wait { twilsock.pendingRequests.size == 1 }
        assertIs<Initializing>(twilsock.state)
        assertEquals(newToken, twilsock.token)

        future.cancel()

        wait { twilsock.pendingRequests.isEmpty() }
        assertIs<Initializing>(twilsock.state)

        // New token will be used after reconnect. It's a little bit strange, but doesn't violate requirements
        // which say that on cancel we don't rollback anything.
        assertEquals(newToken, twilsock.token)
    }

    @Test
    fun sendRequest() = runTest {
        val future = async {
            val httpRequest = HttpRequest("http://www.example.com")
            twilsock.sendRequest(httpRequest)
        }

        wait { twilsock.pendingRequests.size == 1 }
        assertIs<Initializing>(twilsock.state)

        future.cancel()

        wait { twilsock.pendingRequests.isEmpty() }
        assertIs<Initializing>(twilsock.state)
    }

    @Test
    fun fatalErrorUnauthorised() = runTest {
        val errorInfo = ErrorInfo(SslHandshakeError)
        twilsock.onTransportDisconnected(errorInfo)

        waitAndVerify { twilsockObserver.onFatalError(errorInfo) }
        waitAndVerify { twilsockObserver.onDisconnected(errorInfo.message) }
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun fatalErrorInvalidSslCertificate() = runTest {
        val errorInfo = ErrorInfo(HostnameUnverified)
        twilsock.onTransportDisconnected(errorInfo)

        waitAndVerify { twilsockObserver.onFatalError(errorInfo) }
        waitAndVerify { twilsockObserver.onDisconnected(errorInfo.message) }
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun tooManyRequests() = runTest {
        val reply = fakeTooManyRequestsReply(sentInitMessage.requestId)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val errorInfo = twilsockObserver.captureNonFatalError()

        assertEquals(TooManyRequests, errorInfo.reason)
        assertIs<WaitAndReconnect>(twilsock.state)
    }

    @Test
    fun initMessageReceived() = runTest {
        val reply = fakeInitReply(sentInitMessage.requestId)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        waitAndVerify { twilsockObserver.onConnected() }
        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun timeout() = runTest {
        // timeout should happen after 5 seconds
        wait(6.seconds) { twilsock.state is WaitAndReconnect }
        waitAndVerify { twilsockObserver.onNonFatalError(any()) }
    }
}
