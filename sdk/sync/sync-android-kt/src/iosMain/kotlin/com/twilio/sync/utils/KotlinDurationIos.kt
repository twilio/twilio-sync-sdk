//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object KotlinDuration {

        val ZERO = Duration.ZERO

        val INFINITE = Duration.INFINITE

        fun fromMills(@ObjCName("_") mills: Long): Duration = mills.milliseconds
}
