//
//  Twilio Utils
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import kotlinx.atomicfu.atomic

object NextLong {
    private val counter = atomic(0L)

    operator fun invoke() = counter.incrementAndGet()
}
