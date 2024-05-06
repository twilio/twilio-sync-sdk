//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.unit.commands

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.test.util.twilioException
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.Cancelled
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.ErrorReason.RetrierReachedMaxTime
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.RetrierConfig
import com.twilio.util.TwilioException
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ExcludeFromInstrumentedTests
class BaseCommandTest {

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@BaseCommandTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true
    }

    @Test
    fun httpTimeout() = runTest {
        val config = CommandsConfig(
            retrierConfig = RetrierConfig(
                minDelay = 100.milliseconds,
                randomizeFactor = 0.0,
                maxAttemptsTime = 300.milliseconds, // 3 attempts: (0 + 100 + 100) < 300 < (0 + 100 + 100 + 200)
            )
        )
        val command = TestCommand(config)

        coEvery { twilsock.sendRequest(any()) }.throws(TwilioException(ErrorInfo(reason = Timeout)))

        command.execute(twilsock)
        val result = runCatching { command.awaitResult() }

        assertTrue(result.isFailure)
        assertEquals(RetrierReachedMaxTime, result.twilioException.errorInfo.reason)

        coVerify(exactly = 3) { twilsock.sendRequest(any()) }
    }

    @Test
    fun cancel() = runTest {
        val config = CommandsConfig(
            retrierConfig = RetrierConfig(
                minDelay = 100.milliseconds,
                maxAttemptsTime = INFINITE,
            )
        )
        val command = TestCommand(config)

        coEvery { twilsock.sendRequest(any()) }.throws(TwilioException(ErrorInfo(reason = Timeout)))

        command.execute(twilsock)

        launch {
            delay(200.milliseconds) // cancel after 2 attempts: (0 + 100) < 200 < (0 + 100 + 200)
            command.cancel()
        }

        val result = runCatching { command.awaitResult() }

        assertTrue(result.isFailure)
        assertEquals(Cancelled, result.twilioException.errorInfo.reason)

        delay(1.seconds) // to be sure no more calls
        coVerify(exactly = 2) { twilsock.sendRequest(any()) } // cancel after 2 attempts
    }

    @Test
    fun errorResponse() = runTest {
        val config = CommandsConfig(
            retrierConfig = RetrierConfig(
                minDelay = 1.milliseconds,
                maxAttemptsTime = INFINITE,
            )
        )
        val command = TestCommand(config)

        coEvery { twilsock.sendRequest(any()) }.returnsMany(
            testHttpResponse(HttpStatusCode.ServiceUnavailable),    // Recoverable error
            testHttpResponse(HttpStatusCode.GatewayTimeout),        // Recoverable error
            testHttpResponse(HttpStatusCode.BadGateway),            // Recoverable error
            testHttpResponse(HttpStatusCode.TooManyRequests),       // Recoverable error
            testHttpResponse(HttpStatusCode.NotFound),              // Permanent error
        )

        command.execute(twilsock)
        val result = runCatching { command.awaitResult() }

        assertTrue(result.isFailure)
        assertEquals(CommandPermanentError, result.twilioException.errorInfo.reason)

        coVerify(exactly = 5) { twilsock.sendRequest(any()) }
    }

    @Test
    fun errorParse() = runTest {
        val invalidPayload = "<< invalid json object >>"

        coEvery { twilsock.sendRequest(any()) } returns testHttpResponse(HttpStatusCode.OK, payload = invalidPayload)

        val command = TestCommand()
        command.execute(twilsock)
        runCatching { command.awaitResult() }
            .onSuccess { assertSame(Unit, it) } // this command doesn't parse payload and returns Unit when succeeded
            .onFailure { t ->
                assertEquals(ErrorReason.CannotParse, (t as TwilioException).errorInfo.reason)
            }
    }

    class TestCommand(config: CommandsConfig = CommandsConfig()) : BaseCommand<Unit>(testCoroutineScope, config) {
        private val url = "fakeUrl"

        override suspend fun makeRequest(twilsock: Twilsock): Unit =
            twilsock.sendRequest(HttpRequest(url)).parseResponse()
    }
}
