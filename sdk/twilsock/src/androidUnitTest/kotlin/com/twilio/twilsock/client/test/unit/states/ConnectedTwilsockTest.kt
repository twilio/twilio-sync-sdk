//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.unit.states

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.joinLines
import com.twilio.test.util.runTest
import com.twilio.test.util.twilioException
import com.twilio.test.util.wait
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.Status
import com.twilio.twilsock.client.Status.Companion.Ok
import com.twilio.twilsock.client.TwilsockMessage
import com.twilio.twilsock.client.TwilsockState.Connected
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.client.TwilsockState.Throttling
import com.twilio.twilsock.client.TwilsockState.WaitAndReconnect
import com.twilio.twilsock.client.test.util.buildFakeMessage
import com.twilio.twilsock.client.test.util.captureFatalError
import com.twilio.twilsock.client.test.util.captureNonFatalError
import com.twilio.twilsock.client.test.util.captureSentMessage
import com.twilio.twilsock.client.test.util.captureSentString
import com.twilio.twilsock.client.test.util.fakeInitReply
import com.twilio.twilsock.client.test.util.fakeTooManyRequestsReply
import com.twilio.twilsock.client.test.util.fakeUpdateTokenReply
import com.twilio.twilsock.client.test.util.fakeUpstreamRequestReply
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.CannotParse
import com.twilio.util.ErrorReason.CloseMessageReceived
import com.twilio.util.ErrorReason.NetworkBecameUnreachable
import com.twilio.util.ErrorReason.TokenExpired
import com.twilio.util.ErrorReason.TooManyRequests
import com.twilio.util.ErrorReason.TransportDisconnected
import com.twilio.util.ErrorReason.Unauthorized
import io.mockk.clearMocks
import io.mockk.every
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.serialization.json.jsonPrimitive

@ExcludeFromInstrumentedTests
class ConnectedTwilsockTest : BaseTwilsockTest() {

    internal lateinit var sentInitMessage: TwilsockMessage

    override fun setUp() = runTest {
        super.setUp()
        twilsock.connect()
        twilsock.onTransportConnected()

        val sentInitMessage = twilsockTransport.captureSentMessage()

        val reply = fakeInitReply(sentInitMessage.requestId)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        wait { twilsock.state is Connected }

        clearMocks(twilsockTransport)
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
    fun sendRequest() = runTest {
        val httpRequest = HttpRequest("http://www.example.com")
        val deferredResponse = async { twilsock.sendRequest(httpRequest) }

        val sentMessage = twilsockTransport.captureSentMessage()

        val testPayload = "test payload"
        val reply = fakeUpstreamRequestReply(
            requestId = sentMessage.requestId,
            status = Ok,
            httpStatus = Ok,
            payload = testPayload,
        )
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val httpResponse = deferredResponse.await()

        assertEquals(200, httpResponse.statusCode)
        assertEquals("OK", httpResponse.status.uppercase())
        assertEquals(testPayload, httpResponse.payload)
        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun sendRequestAfterReconnect() = runTest {
        twilsock.onTransportDisconnected(ErrorInfo(TransportDisconnected, message = "test reason"))
        assertIs<WaitAndReconnect>(twilsock.state)
        assertTrue { twilsock.pendingRequests.isEmpty() }

        val httpRequest = HttpRequest("http://www.example.com")
        val deferredResponse = async { twilsock.sendRequest(httpRequest) }

        wait { twilsock.pendingRequests.size == 1 }
        wait { twilsock.state is Connecting }

        twilsock.onTransportDisconnected(ErrorInfo())

        wait { twilsock.state is WaitAndReconnect }
        assertEquals(1, twilsock.pendingRequests.size)
        wait { twilsock.state is Connecting }

        twilsock.onTransportConnected()
        val sentInitMessage = twilsockTransport.captureSentMessage()
        clearMocks(twilsockTransport)

        val initReply = fakeInitReply(sentInitMessage.requestId)
        twilsock.onMessageReceived(initReply.encodeToByteArray())

        wait { twilsock.state is Connected }

        val sentMessage = twilsockTransport.captureSentMessage()

        val testPayload = "test payload"
        val reply = fakeUpstreamRequestReply(
            requestId = sentMessage.requestId,
            status = Ok,
            httpStatus = Ok,
            payload = testPayload,
        )
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val httpResponse = deferredResponse.await()

        assertEquals(200, httpResponse.statusCode)
        assertEquals("OK", httpResponse.status.uppercase())
        assertEquals(testPayload, httpResponse.payload)
        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun sendRequestFailure() = runTest {
        val httpRequest = HttpRequest("http://www.example.com")
        val deferredResult = async {
            runCatching { twilsock.sendRequest(httpRequest) }
        }

        val sentMessage = twilsockTransport.captureSentMessage()

        val testPayload = "test payload"
        val reply = fakeUpstreamRequestReply(
            requestId = sentMessage.requestId,
            status = Status(code = 503, status = "Service unavailable"),
            httpStatus = Ok,
            payload = testPayload,
        )
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val result = deferredResult.await()
        assertTrue { result.isFailure }

        val expectedError = ErrorInfo(status = 503, message = "Service unavailable")
        assertEquals(expectedError, result.twilioException.errorInfo)

        // due to async processing the state could ether be WaitAndReconnect or already switched to the Connecting
        assertTrue { twilsock.state is WaitAndReconnect || twilsock.state is Connecting }
    }

    @Test
    fun sendRequestTimeout() = runTest {
        val httpRequest = HttpRequest("http://www.example.com", timeout = 1.seconds)
        val result = runCatching { twilsock.sendRequest(httpRequest) }

        assertTrue { result.isFailure }
        assertEquals(ErrorReason.Timeout, result.twilioException.errorInfo.reason)
    }

    @Test
    fun tooManyRequests() = runTest {
        val httpRequest = HttpRequest("http://www.example.com")
        val deferredResult = async {
            runCatching { twilsock.sendRequest(httpRequest) }
        }

        val sentMessage = twilsockTransport.captureSentMessage()

        val reply = fakeTooManyRequestsReply(sentMessage.requestId)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val result = deferredResult.await()
        assertTrue { result.isFailure }

        val expectedError = ErrorInfo(reason = TooManyRequests, status = 429, code = 51202, message = "TOO_MANY_REQUESTS", description = "Too many requests")
        assertEquals(expectedError, result.twilioException.errorInfo)

        assertIs<Throttling>(twilsock.state)
    }

    @Test
    fun updateToken() = runTest {
        val newToken = "newToken"
        val future = async {
            runCatching { twilsock.updateToken(newToken) }
        }

        val sentMessage = twilsockTransport.captureSentMessage()
        assertEquals(newToken, sentMessage.headers["token"]?.jsonPrimitive?.content)

        val reply = fakeUpdateTokenReply(sentMessage.requestId, status = Ok)
        twilsock.onMessageReceived(reply.encodeToByteArray())

        val result = future.await()
        assertTrue { result.isSuccess }
        assertEquals(newToken, twilsock.token)
        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun updateToInvalidToken() = runTest {
        val newToken = "newToken"
        val future = async {
            runCatching { twilsock.updateToken(newToken) }
        }

        val sentMessage = twilsockTransport.captureSentMessage()
        assertEquals(newToken, sentMessage.headers["token"]?.jsonPrimitive?.content)

        val status = Status(code = 401, errorCode = 20101, status = "UNAUTHORIZED")
        val reply = fakeUpdateTokenReply(sentMessage.requestId, status)

        twilsock.onMessageReceived(reply.encodeToByteArray())

        val result = future.await()
        assertTrue { result.isFailure }
        assertEquals(newToken, twilsock.token)

        val expectedError = ErrorInfo(reason = Unauthorized, status = 401, code = 20101, message = "UNAUTHORIZED")
        waitAndVerify { twilsockObserver.onFatalError(expectedError) }
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun tokenAboutToExpire() = runTest {
        val clientUpdateMessage = buildFakeMessage(
            headers = """
                {
                   "method":"client_update",
                   "id":"TM6963367e3f96496f94125cdb5144f4b1",
                   "payload_size":0,
                   "client_update_type":"token_about_to_expire",
                   "client_update_message":"Your token is about to expire. Time left: less than 240000 ms"
                }
            """.joinLines()
        )

        val expectedReply = buildFakeMessage(
            headers = """
                {
                   "method":"reply",
                   "id":"TM6963367e3f96496f94125cdb5144f4b1",
                   "status":{
                      "status":"ok",
                      "code":200
                   }
                }
            """.joinLines()
        )

        twilsock.onMessageReceived(clientUpdateMessage.encodeToByteArray())

        val reply = twilsockTransport.captureSentString()

        assertEquals(expectedReply, reply)
        waitAndVerify { twilsockObserver.onTokenAboutToExpire() }
        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun tokenExpired() = runTest {
        val closeMessage = buildFakeMessage(
            headers = """
                {
                   "method":"close",
                   "id":"TM50106942aba74eb3a1f8304cd3d8450e",
                   "payload_size":0,
                   "status":{
                      "code":410,
                      "status":"TOKEN_EXPIRED",
                      "description":"Token expired",
                      "errorCode":51207
                   }
                }
            """.joinLines()
        )

        val expectedReply = buildFakeMessage(
            headers = """
                {
                   "method":"reply",
                   "id":"TM50106942aba74eb3a1f8304cd3d8450e",
                   "status":{
                      "status":"ok",
                      "code":200
                   }
                }
            """.joinLines()
        )

        twilsock.onMessageReceived(closeMessage.encodeToByteArray())

        val reply = twilsockTransport.captureSentString()

        assertEquals(expectedReply, reply)
        waitAndVerify { twilsockObserver.onTokenExpired() }

        val errorInfo = twilsockObserver.captureFatalError()
        val expectedError = ErrorInfo(TokenExpired, 410, 51207, "TOKEN_EXPIRED", "Token expired")

        assertEquals(expectedError, errorInfo)
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun tokenWithDifferentInstanceSid() = runTest { // RTDSDK-3448
        val closeMessage = buildFakeMessage(
            headers = """
                {
                   "method":"close",
                   "id":"TM50106942aba74eb3a1f8304cd3d8450e",
                   "payload_size":0,
                   "status":{
                      "code":308,
                      "status":"UPDATE_IS_FORBIDDEN",
                      "description":"Token from update is unauthorised or different from initial",
                      "errorCode":51232
                   }
                }
            """.joinLines()
        )

        val expectedReply = buildFakeMessage(
            headers = """
                {
                   "method":"reply",
                   "id":"TM50106942aba74eb3a1f8304cd3d8450e",
                   "status":{
                      "status":"ok",
                      "code":200
                   }
                }
            """.joinLines()
        )

        twilsock.onMessageReceived(closeMessage.encodeToByteArray())

        val reply = twilsockTransport.captureSentString()
        assertEquals(expectedReply, reply)

        val errorInfo = twilsockObserver.captureFatalError()
        val expectedError = ErrorInfo(CloseMessageReceived, 308, 51232, "UPDATE_IS_FORBIDDEN", "Token from update is unauthorised or different from initial")

        assertEquals(expectedError, errorInfo)
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun targetNotificationMessageReceived() = runTest {
        val payload = """
            {
               "event_type":"subscription_established",
               "correlation_id":996543,
               "event_protocol_version":4,
               "events":[
                  {
                     "object_sid":"MPc75e557cb18b44b5abf53df36e405ee2",
                     "object_type":"map",
                     "replay_status":"completed",
                     "last_event_id":-1
                  },
                  {
                     "object_sid":"MP2347e932ba4445008b9bf633822480c4",
                     "object_type":"map",
                     "replay_status":"completed",
                     "last_event_id":2
                  }
               ]
            }
        """.joinLines()

        val notificationMessage = buildFakeMessage(
            headers = """
                {
                   "method":"notification",
                   "id":"TM25cdd8b0267b4f038d6073be271fd1fe",
                   "payload_size":${payload.length},
                   "payload_type":"application/json",
                   "message_type":"twilio.sync.event",
                   "notification_ctx_id":""
                }            
            """.joinLines(),

            payload = payload
        )

        val expectedReply = buildFakeMessage(
            headers = """
                {
                   "method":"reply",
                   "id":"TM25cdd8b0267b4f038d6073be271fd1fe",
                   "status":{
                      "status":"ok",
                      "code":200
                   }
                }
            """.joinLines()
        )

        twilsock.onMessageReceived(notificationMessage.encodeToByteArray())

        val reply = twilsockTransport.captureSentString()

        assertEquals(expectedReply, reply)
        waitAndVerify { twilsockObserver.onMessageReceived("twilio.sync.event", payload) }

        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun pingMessageReceived() = runTest {
        val pingMessage = buildFakeMessage(
            headers = """
                {
                   "method":"ping",
                   "id":"TM5f7911701a564ae4ad0f4cf1aae2be60",
                   "payload_size":0
                }
            """.joinLines()
        )

        val expectedReply = buildFakeMessage(
            headers = """
                {
                   "method":"reply",
                   "id":"TM5f7911701a564ae4ad0f4cf1aae2be60",
                   "status":{
                      "status":"ok",
                      "code":200
                   }
                }
            """.joinLines()
        )

        twilsock.onMessageReceived(pingMessage.encodeToByteArray())

        val reply = twilsockTransport.captureSentString()

        assertEquals(expectedReply, reply)
        assertIs<Connected>(twilsock.state)
    }

    @Test
    fun parseError() = runTest {
        val invalidMessageWithoutId = buildFakeMessage(
            headers = """
                {
                   "method":"ping",
                   "payload_size":0
                }
            """.joinLines()
        )

        twilsock.onMessageReceived(invalidMessageWithoutId.encodeToByteArray())

        val errorInfo = twilsockObserver.captureFatalError()
        val expectedError = ErrorInfo(CannotParse)

        assertEquals(expectedError, errorInfo)
        assertIs<Disconnected>(twilsock.state)
    }
}
