//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.twilio.util

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.newCoroutineContext

/** @return new child [CoroutineScope] which cancels when current [CoroutineScope] is cancelled */
inline fun CoroutineScope.newChildCoroutineScope(
    context: CoroutineContext = EmptyCoroutineContext,
    jobFactory: (parent: Job) -> Job = ::SupervisorJob,
): CoroutineScope {
    val job = jobFactory(coroutineContext.job)
    return CoroutineScope(newCoroutineContext(context + job))
}
