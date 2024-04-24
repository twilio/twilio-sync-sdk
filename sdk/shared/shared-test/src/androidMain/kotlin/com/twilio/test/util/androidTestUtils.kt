//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.test.util

import androidx.test.platform.app.InstrumentationRegistry
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.client.TwilsockObserver
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ApplicationContextHolder
import io.mockk.MockKVerificationScope
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun waitAndVerify(exactly: Int = 1, timeout: Duration = 1.seconds, verifyBlock: suspend MockKVerificationScope.() -> Unit) = runCatching {
    wait(timeout) {
        // Sometimes we have to execute other coroutines in order to pass the verification block.
        // Sometime these coroutines start other coroutines. So we have to call `yield()` (inside `wait`) more than once.
        // That's why we cannot just pass the `timeout` parameter into regular mockk's `verify()`
        runCatching { coVerify(exactly = exactly, verifyBlock = verifyBlock) }.isSuccess
    }
}.onFailure {
    // Print sane stacktrace if verification still fails
    coVerify(exactly = exactly, verifyBlock = verifyBlock)
}

suspend fun Twilsock.captureSentRequest(): HttpRequest {
    val sentRequest = slot<HttpRequest>()
    waitAndVerify { sendRequest(capture(sentRequest)) }
    return sentRequest.captured
}

suspend fun Twilsock.captureAddObserver(): TwilsockObserver {
    val slot = slot<TwilsockObserver.() -> Unit>()
    waitAndVerify { addObserver(capture(slot)) }
    val observerInitializer = slot.captured
    return TwilsockObserver().apply(observerInitializer)
}

actual fun setupTestAndroidContext() {
    ApplicationContextHolder.applicationContext = InstrumentationRegistry.getInstrumentation().targetContext
}

actual fun setDatabaseLastModified(name: String, lastModified: Instant) {
    val context = ApplicationContextHolder.applicationContext
    val file = context.getDatabasePath(name)
    file.setLastModified(lastModified.toEpochMilliseconds())
}

actual fun createNewDatabaseFile(name: String) {
    val context = ApplicationContextHolder.applicationContext
    val file = context.getDatabasePath(name)
    file.createNewFile()
}
