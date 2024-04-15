//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.conversations.test.integration

import com.twilio.twilsock.client.AuthData
import com.twilio.twilsock.client.ClientMetadata
import com.twilio.twilsock.client.TwilsockImpl
import com.twilio.test.util.kTestTwilsockServiceUrl
import androidx.test.platform.app.InstrumentationRegistry
import com.twilio.twilsock.client.ContinuationTokenStorage
import com.twilio.twilsock.client.ContinuationTokenStorageImpl
import com.twilio.twilsock.client.TwilsockState.Connected
import com.twilio.twilsock.client.test.integration.TwilsockTest
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCerts
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.wait
import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.twilsock.util.ConnectivityMonitorImpl
import com.twilio.twilsock.util.HttpRequest
import com.twilio.twilsock.util.HttpResponse
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.TwilioLogger
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Ignore
import org.junit.Test
import com.twilio.util.logger
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlin.test.fail
import kotlin.test.assertEquals
import com.twilio.util.LogWriter

class TwilsockAndroidTest : TwilsockTest() {

    override val continuationTokenStorage: ContinuationTokenStorage by lazy { ContinuationTokenStorageImpl() }

    override val connectivityMonitor: ConnectivityMonitor by lazy { ConnectivityMonitorImpl(testCoroutineScope) }

    init {
        ApplicationContextHolder.applicationContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    @Ignore("Manual test to measure twilsock performance")
    fun sendPings1000() = runTest {
        TwilioLogger.setLogLevel(TwilioLogger.SILENT) // disable logging - it affects performance a lot.

        twilsock.connect()
        wait { twilsock.state is Connected }

        val responses: MutableList<Deferred<HttpResponse>> = mutableListOf()
        val httpRequest = HttpRequest("https://aim.us1.twilio.com/ping/200")

        val start = getTimeMillis()

        repeat(1000) {
            responses += async { twilsock.sendRequest(httpRequest) }
        }

        responses.awaitAll()

        val duration = getTimeMillis() - start
        println("sendPings1000 duration: ${duration}ms")
    }

    @Test
    fun multipleTwilsockInstances_shouldNotCrashLogger() = runTest {

        val twilsockInstance1 = TwilsockImpl(
            testCoroutineScope,
            kTestTwilsockServiceUrl,
            useProxy = false,
            authData,
            ClientMetadata(),
            continuationTokenStorage,
            connectivityMonitor,
        )

        val twilsockInstance2 = TwilsockImpl(
            testCoroutineScope,
            kTestTwilsockServiceUrl,
            useProxy = false,
            authData,
            ClientMetadata(),
            continuationTokenStorage,
            connectivityMonitor,
        )

        val twilsockInstance3 = TwilsockImpl(
            testCoroutineScope,
            kTestTwilsockServiceUrl,
            useProxy = false,
            authData,
            ClientMetadata(),
            continuationTokenStorage,
            connectivityMonitor,
        )

        try {
            twilsockInstance1.connect()
            twilsockInstance2.connect()
            twilsockInstance3.connect()
            logger.i("Test log")

        } catch (e: Exception) {
            fail("Logger crashed")
        }
    }
}
