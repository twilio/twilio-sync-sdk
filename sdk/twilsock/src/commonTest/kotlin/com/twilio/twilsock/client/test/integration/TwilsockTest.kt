//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.integration

import com.twilio.test.util.TestConnectivityMonitor
import com.twilio.test.util.TestContinuationTokenStorage
import com.twilio.test.util.kTestTwilsockServiceUrl
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCerts
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.twilioException
import com.twilio.test.util.wait
import com.twilio.twilsock.client.AuthData
import com.twilio.twilsock.client.ClientMetadata
import com.twilio.twilsock.client.ContinuationTokenStorage
import com.twilio.twilsock.client.TwilsockImpl
import com.twilio.twilsock.client.TwilsockState.Connected
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.TokenExpired
import com.twilio.util.ErrorReason.Unauthorized
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val IDENTITY = "TwilsockTest"
const val ACTIVE_GRANT = "ip_messaging"
const val PRODUCT_ID = "ip_messaging"

open class TwilsockTest {

    internal open val continuationTokenStorage: ContinuationTokenStorage =
        TestContinuationTokenStorage()

    internal open val connectivityMonitor: ConnectivityMonitor = TestConnectivityMonitor()

    internal lateinit var twilsock: TwilsockImpl

    lateinit var authData:AuthData

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()

        authData = AuthData(
            token = requestToken(IDENTITY),
            activeGrant = ACTIVE_GRANT,
            notificationProductId = PRODUCT_ID,
            certificates = testCerts,
        )

        twilsock = TwilsockImpl(
            testCoroutineScope,
            kTestTwilsockServiceUrl,
            useProxy = false,
            authData,
            ClientMetadata(),
            continuationTokenStorage,
            connectivityMonitor,
        )
    }

    @AfterTest
    fun tearDown() {
        twilsock.disconnect()
    }

    @Test
    fun connectAndDisconnect() = runTest {
        val onConnecting = CompletableDeferred<Unit>()
        val onConnected = CompletableDeferred<Unit>()
        val onDisconnected = CompletableDeferred<Unit>()

        twilsock.addObserver {
            this.onConnecting = { onConnecting.complete(Unit) }
            this.onConnected = { onConnected.complete(Unit) }
            this.onDisconnected = { onDisconnected.complete(Unit) }
        }

        twilsock.connect()

        onConnecting.await()
        onConnected.await()

        val account = twilsock.accountDescriptor

        assertNotNull(account)
        assertTrue(account.accountSid.startsWith("AC"))
        assertTrue(account.instanceSids[ACTIVE_GRANT]!!.startsWith("IS"))
        assertEquals(IDENTITY, account.identity)

        twilsock.disconnect()
        onDisconnected.await()
    }

    @Test
    fun sendRequest() = runTest {
        twilsock.connect()
        wait { twilsock.state is Connected }

        val httpRequest = HttpRequest("https://aim.us1.twilio.com/ping/200")
        val response = twilsock.sendRequest(httpRequest)

        assertEquals(200, response.statusCode)
        assertEquals("OK", response.status)
        assertEquals("text/plain", response.headers["content-type"]?.single())
        assertEquals("""{"status":"OK"}""", response.payload)
    }

    @Test
    fun updateTokenLocally() = runTest {
        val updateTokenJob = launch {
            twilsock.updateToken(requestToken())
        }

        wait { twilsock.state is Connecting }
        updateTokenJob.cancel()
    }

    @Test
    fun updateTokenRemotely() = runTest {
        twilsock.connect()
        wait { twilsock.state is Connected }

        val token = requestToken(IDENTITY)
        twilsock.updateToken(token)

        assertEquals(token, twilsock.token)
    }

    @Test
    fun onInvalidTokenUpdateShouldAssertErrorInfoObject() = runTest {
        val onFatalError = CompletableDeferred<ErrorInfo>()
        twilsock.addObserver {
            this.onFatalError = { onFatalError.complete(it) }
        }

        twilsock.connect()
        wait { twilsock.state is Connected }

        val result = runCatching { twilsock.updateToken("invalid token") }

        assertTrue { result.isFailure }

        val expectedError = ErrorInfo(
            reason = Unauthorized,
            status = 401,
            code = 20101,
            message = "UNAUTHORIZED",
            description = "Invalid Access Token"
        )
        assertEquals(expectedError, result.twilioException.errorInfo)
        assertEquals(expectedError, onFatalError.await())
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun onTokenUpdateWithDifferentIdentityShouldAssertErrorInfoObject() = runTest {
        val onFatalError = CompletableDeferred<ErrorInfo>()
        twilsock.addObserver {
            this.onFatalError = { onFatalError.complete(it) }
        }

        twilsock.connect()
        wait { twilsock.state is Connected }

        val token = requestToken("sync")
        val result = runCatching { twilsock.updateToken(token) }

        assertTrue { result.isFailure }

        val expectedError = ErrorInfo(
            reason = Unauthorized,
            status = 401,
            code = 51215,
            message = "UNAUTHORIZED",
            description = "Can't update token, new token was issued for other identity/instance/product."
        )
        assertEquals(expectedError, result.twilioException.errorInfo)
        assertEquals(expectedError, onFatalError.await())
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun onTokenUpdateWithExpiredTokenShouldEmitOnTokedExpired() = runTest {
        val onFatalError = CompletableDeferred<ErrorInfo>()

        var onFatalErrorCounter by atomic(0)
        var onTokenExpiredCounter by atomic(0)

        twilsock.addObserver {
            this.onFatalError = {
                onFatalErrorCounter++
                onFatalError.complete(it)
            }
            this.onTokenExpired = { onTokenExpiredCounter++ }
        }

        twilsock.connect()
        wait { twilsock.state is Connected }

        val token = requestToken(ttl = -1.hours) // generate expired token
        val result = runCatching { twilsock.updateToken(token) }

        assertTrue { result.isFailure }

        val expectedError = ErrorInfo(
            reason = TokenExpired,
            status = 401,
            code = 20104,
            message = "UNAUTHORIZED",
            description = "Access Token expired or expiration date invalid"
        )
        assertEquals(expectedError, result.twilioException.errorInfo)
        assertEquals(expectedError, onFatalError.await())
        wait { onTokenExpiredCounter == 1 }
        assertIs<Disconnected>(twilsock.state)

        delay(1.seconds)

        assertEquals(1, onFatalErrorCounter)
        assertEquals(1, onTokenExpiredCounter)
    }
}
