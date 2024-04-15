//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import com.twilio.util.TwilioLogger

class KotlinLogger(@ObjCName("_") name: String) {

    private val logger = TwilioLogger.getLogger(name)

    fun v(buildMsg: () -> String) = logger.v(buildMsg = buildMsg)

    fun d(buildMsg: () -> String) = logger.d(buildMsg = buildMsg)

    fun i(buildMsg: () -> String) = logger.i(buildMsg = buildMsg)

    fun w(buildMsg: () -> String) = logger.w(buildMsg = buildMsg)

    fun e(buildMsg: () -> String) = logger.e(buildMsg = buildMsg)

    companion object {
        fun setLogLevel(level: LogLevel) = TwilioLogger.setLogLevel(level.value)
    }
}
