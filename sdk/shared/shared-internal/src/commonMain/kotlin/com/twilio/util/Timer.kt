//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Timer(
    @PublishedApi
    internal val scope: CoroutineScope
) {
    @PublishedApi
    internal var job: Job? = null

    val isScheduled get() = job != null

    inline fun schedule(duration: Duration, crossinline task: () -> Unit) {
        cancel()
        job = scope.launch {
            delay(duration)
            job = null
            task()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
