//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlinx.atomicfu.atomic

object NextLong {
    private val counter = atomic(0L)

    operator fun invoke() = counter.incrementAndGet()
}
