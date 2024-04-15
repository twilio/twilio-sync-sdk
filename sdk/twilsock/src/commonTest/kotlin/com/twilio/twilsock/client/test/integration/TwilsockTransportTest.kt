//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.integration

import com.twilio.test.util.IgnoreIos
import com.twilio.test.util.joinLines
import com.twilio.test.util.kTestTwilsockServiceUrl
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCerts
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.client.TwilsockTransport
import com.twilio.twilsock.client.test.util.TwilsockTransportListener
import com.twilio.twilsock.client.test.util.buildFakeMessage
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.HostnameUnverified
import com.twilio.util.ErrorReason.TransportDisconnected
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TwilsockTransportTest {

    private val kConnectTimeout = 60.seconds

    @BeforeTest
    fun setUp() {
        setupTestLogging()
    }

    @Test
    fun connectAndDisconnect() = runTest {
        val onConnected = CompletableDeferred<Unit>()
        val onDisconnected = CompletableDeferred<ErrorInfo>()

        val transport = TwilsockTransport(testCoroutineScope, kConnectTimeout, testCerts, TwilsockTransportListener(
            onTransportConnected = { onConnected.complete(Unit) },
            onTransportDisconnected = { onDisconnected.complete(it) },
        ))

        transport.connect(kTestTwilsockServiceUrl)
        onConnected.await()

        val reason = "test reason"
        transport.disconnect(reason)
        val disconnectedParams = onDisconnected.await()

        val expectedError = ErrorInfo(TransportDisconnected, message = "Disconnect called: $reason")
        assertEquals(expectedError, disconnectedParams)
    }

    @Test
    fun connectAndSendInvalidInitMessage() = runTest {
        val onConnected = CompletableDeferred<Unit>()
        val onDisconnected = CompletableDeferred<ErrorInfo>()
        val onMessageReceived = CompletableDeferred<ByteArray>()

        val transport = TwilsockTransport(testCoroutineScope, kConnectTimeout, testCerts, TwilsockTransportListener(
            onTransportConnected = { onConnected.complete(Unit) },
            onTransportDisconnected = { onDisconnected.complete(it) },
            onMessageReceived = { onMessageReceived.complete(it) },
        ))

        transport.connect(kTestTwilsockServiceUrl)
        onConnected.await()

        transport.sendMessage("invalid message".toByteArray())

        val receivedMessage = onMessageReceived.await().decodeToString()
        val disconnectedParams = onDisconnected.await()

        val expectedMessage = """
            "status":{
                "code":406,
                "status":"NOT_ACCEPTABLE_MESSAGE",
                "description":"Not acceptable message",
                "errorCode":51214
            }
        """.joinLines()

        assertTrue { receivedMessage.contains(expectedMessage) }
        assertEquals(TransportDisconnected, disconnectedParams.reason)
    }

    @Test
    fun connectAndSendInvalidToken() = runTest {
        val onConnected = CompletableDeferred<Unit>()
        val onDisconnected = CompletableDeferred<ErrorInfo>()
        val onMessageReceived = CompletableDeferred<ByteArray>()

        val transport = TwilsockTransport(testCoroutineScope, kConnectTimeout, testCerts, TwilsockTransportListener(
            onTransportConnected = { onConnected.complete(Unit) },
            onTransportDisconnected = { onDisconnected.complete(it) },
            onMessageReceived = { onMessageReceived.complete(it) },
        ))

        transport.connect(kTestTwilsockServiceUrl)
        onConnected.await()

        val initMessage = buildFakeMessage(
            headers = """
                {
                   "method":"init",
                   "id":"RQ16b9d21d63194a5098aee182318e71fb",
                   "token":"invalid_test_token"
                }
            """.joinLines()
        )

        transport.sendMessage(initMessage.toByteArray())

        val receivedMessage = onMessageReceived.await().decodeToString()
        val disconnectedParams = onDisconnected.await()

        val expectedMessage = """
            "status":{
                "code":401,
                "status":"UNAUTHORIZED",
                "description":"Invalid Access Token",
                "errorCode":20101
            }
        """.joinLines()

        assertTrue { receivedMessage.contains(expectedMessage) }
        assertEquals(TransportDisconnected, disconnectedParams.reason)
    }

    @Test
    @IgnoreIos // On iOS we ignore certificates parameter and always use system certificates
    fun errorInvalidCertificate() = runTest {
        val onDisconnected = CompletableDeferred<ErrorInfo>()

        val transport = TwilsockTransport(testCoroutineScope, kConnectTimeout, certificates = emptyList(),
            TwilsockTransportListener(
                onTransportDisconnected = { onDisconnected.complete(it) },
            )
        )

        transport.connect(kTestTwilsockServiceUrl)
        val disconnectedParams = onDisconnected.await()

        val expectedParams = ErrorInfo(HostnameUnverified)
        assertEquals(expectedParams, disconnectedParams)
    }
}
