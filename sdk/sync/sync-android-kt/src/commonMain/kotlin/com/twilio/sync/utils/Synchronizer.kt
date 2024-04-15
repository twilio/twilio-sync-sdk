//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import com.twilio.util.ErrorReason
import com.twilio.util.getOrThrowTwilioException
import com.twilio.util.getOrThrowTwilioExceptionSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal open class Synchronizer<T>(
    val coroutineScope: CoroutineScope,
    val delegate: T,
) {

    protected suspend inline fun <R> doSynchronizeSuspend(crossinline block: suspend T.() -> R): R = runCatching {
        withContext(coroutineScope.coroutineContext) {
            delegate.block()
        }
    }.getOrThrowTwilioException(ErrorReason.Unknown)

    protected inline fun doSynchronizeAsync(crossinline block: T.() -> Unit) {
        runCatching {
            coroutineScope.launch {
                delegate.block()
            }
        }.getOrThrowTwilioExceptionSync(ErrorReason.Unknown)
    }

    protected inline fun <R> doSynchronizeBlocking(crossinline block: T.() -> R): R = runCatching {
        runBlocking(coroutineScope.coroutineContext) {
            delegate.block()
        }
    }.getOrThrowTwilioExceptionSync(ErrorReason.Unknown)
}
