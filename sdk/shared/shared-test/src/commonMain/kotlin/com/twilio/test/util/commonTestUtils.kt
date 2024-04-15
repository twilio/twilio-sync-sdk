//
//  Twilio Conversations Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package com.twilio.test.util

import com.twilio.twilsock.client.ContinuationTokenStorage
import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.twilsock.util.HttpResponse
import com.twilio.twilsock.util.MultiMap
import com.twilio.util.LogWriter
import com.twilio.util.TwilioException
import com.twilio.util.TwilioLogger
import com.twilio.util.getCurrentThreadId
import com.twilio.util.logWriter
import com.twilio.util.newSerialCoroutineContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

val testCoroutineContext = newSerialCoroutineContext()
val testCoroutineScope = CoroutineScope(testCoroutineContext + SupervisorJob())

val testLogger = TwilioLogger.getLogger("TestLogger")

fun runTest(timeout: Duration = 60.seconds, block: suspend CoroutineScope.() -> Unit) =
    runBlocking(testCoroutineContext) {
        withTimeout(timeout, block)
    }

suspend fun requestToken(
    identity: String = "sdkCommonTest",
    ttl: Duration = 1.hours,
): String = HttpClient().use { client ->
    testLogger.d { "Requesting token for identity $identity" }
    val token = client.get("$kTokenGeneratorServiceUrl&identity=$identity&ttl=${ttl.inWholeSeconds}").body<String>()
    testLogger.d { "Token for identity $identity received" }
    return@use token
}

/**
 * Exclude test from instrumentation tests - don't run it on any device or emulator.
 * When running instrumented tests on CI, tests with this annotation wil be filtered
 * using `notAnnotation` option.
 * See: https://developer.android.com/reference/android/support/test/runner/AndroidJUnitRunner
 *
 * This is used to filter tests with mocks, because of mockk have a lot of issues with mocking in release builds.
 */
@Target(CLASS, FUNCTION)
@Retention(RUNTIME)
annotation class ExcludeFromInstrumentedTests

fun String.joinLines() = split('\n').joinToString(separator = "") { it.trim() }

class TestContinuationTokenStorage : ContinuationTokenStorage {
    override var continuationToken = ""
}

class TestConnectivityMonitor : ConnectivityMonitor {
    override val isNetworkAvailable = true
    override var onChanged: () -> Unit = {}
    override fun start() = Unit
    override fun stop() = Unit
}

val <T> Result<T>.twilioException: TwilioException get() = exceptionOrNull() as TwilioException

suspend fun wait(timeout: Duration = INFINITE, predicate: suspend () -> Boolean) = withTimeout(timeout) {
    while (!predicate()) {
        yield()
    }
}

class TestLogWriter : LogWriter {

    override fun v(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    override fun d(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    override fun i(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    override fun w(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    override fun e(tag: String, msg: String, t: Throwable?) = writeLog(tag, msg, t)

    private fun writeLog(tag: String, msg: String, t: Throwable?) {
        val timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val threadId = getCurrentThreadId()
        println("$timestamp [$threadId] $tag $msg ${t?.stackTraceToString() ?: ""}")
    }
}

fun setupTestLogging() {
    logWriter = TestLogWriter()
    TwilioLogger.setLogLevel(TwilioLogger.VERBOSE)
}

expect fun setupTestAndroidContext()

fun generateRandomString(prefix: String = "randomString"): String {
    val number = Random.nextInt(1_000_000)
    return "$prefix$number"
}

fun generateRandomDuration(): Duration {
    val number = Random.nextInt(1_000_000)
    return number.seconds
}

fun testHttpResponse(httpStatus: HttpStatusCode, headers: MultiMap<String, String> = MultiMap(), payload: String = "") =
    HttpResponse(httpStatus.value, httpStatus.description, rawMessageHeaders = "", headers, payload)

suspend fun Flow<*>.captureException(): Throwable {
    var result: Throwable? = null
    catch { result = it }.collect()
    return result ?: error("No exception was thrown")
}

expect fun setDatabaseLastModified(name: String, lastModified: Instant)

expect fun createNewDatabaseFile(name: String)
