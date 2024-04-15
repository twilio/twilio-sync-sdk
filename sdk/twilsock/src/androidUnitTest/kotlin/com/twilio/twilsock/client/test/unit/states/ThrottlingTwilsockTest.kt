//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.unit.states

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.wait
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.TwilsockState
import com.twilio.twilsock.client.TwilsockState.Connected
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.client.TwilsockState.Throttling
import com.twilio.twilsock.client.TwilsockState.WaitAndReconnect
import com.twilio.twilsock.client.test.util.captureNonFatalError
import com.twilio.twilsock.client.test.util.captureSentMessage
import com.twilio.twilsock.client.test.util.fakeInitReply
import com.twilio.twilsock.client.test.util.fakeTooManyRequestsReply
import com.twilio.twilsock.client.test.util.fakeUpdateTokenReply
import com.twilio.twilsock.client.test.util.fakeUpstreamRequestReply
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.NetworkBecameUnreachable
import com.twilio.util.ErrorReason.TooManyRequests
import com.twilio.util.ErrorReason.TransportDisconnected
import io.mockk.clearMocks
import io.mockk.every
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@ExcludeFromInstrumentedTests
class ThrottlingTwilsockTest : BaseTwilsockTest() {

    override fun setUp() = runTest {
        super.setUp()
        twilsock.connect()
        twilsock.onTransportConnected()

        val sentInitMessage = twilsockTransport.captureSentMessage()

        val initReply = fakeInitReply(sentInitMessage.requestId)
        twilsock.onMessageReceived(initReply.encodeToByteArray())

        wait { twilsock.state is Connected }

        clearMocks(twilsockTransport)
        launch {
            runCatching { twilsock.sendRequest(HttpRequest("http://www.example.com")) }
        }

        val sentMessage = twilsockTransport.captureSentMessage()

        val reply = fakeTooManyRequestsReply(sentMessage.requestId)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        wait { twilsock.state is Throttling }

        // ensure that mock has received the error before cleaning
        assertEquals(TooManyRequests, twilsockObserver.captureNonFatalError().reason)
        clearMocks(twilsockTransport)
        clearTwilsockObserverMock()
    }

    @Test
    fun connect() {
        twilsock.connect()

        // connect() does nothing
        assertIs<Throttling>(twilsock.state)
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
        assertTrue { twilsock.state is WaitAndReconnect || twilsock.state is TwilsockState.Connecting }
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
            runCatching { twilsock.updateToken(newToken) }
        }

        wait { twilsock.pendingRequests.size == 1 }
        assertEquals(newToken, twilsock.token)
        assertIs<Throttling>(twilsock.state)

        wait { twilsock.state is Connected }
        val sentMessage = twilsockTransport.captureSentMessage()
        val sentToken = sentMessage.headers["token"]?.jsonPrimitive?.content
        assertEquals(newToken, sentToken)

        val reply = fakeUpdateTokenReply(sentMessage.requestId)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val result = future.await()
        assertTrue { result.isSuccess }
    }

    @Test
    fun sendRequest() = runTest {
        val future = async {
            val httpRequest = HttpRequest("http://www.example.com")
            twilsock.sendRequest(httpRequest)
        }

        wait { twilsock.pendingRequests.size == 1 }
        assertIs<Throttling>(twilsock.state)

        wait { twilsock.state is Connected }
        val sentMessage = twilsockTransport.captureSentMessage()

        val testPayload = "test payload"
        val reply = fakeUpstreamRequestReply(sentMessage.requestId, payload = testPayload)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val httpResponse = future.await()
        assertEquals(200, httpResponse.statusCode)
        assertEquals("OK", httpResponse.status.uppercase())
        assertEquals(testPayload, httpResponse.payload)
    }

    @Test
    fun timeout() = runTest {
        // timeout should happen after 2 seconds
        wait(3.seconds) { twilsock.state is Connected }
    }
}
