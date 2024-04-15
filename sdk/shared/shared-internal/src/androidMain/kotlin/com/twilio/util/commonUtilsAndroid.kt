//
//  Twilio Utils
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import java.util.*

actual fun generateUUID(): String = UUID.randomUUID().toString()

actual fun getCurrentThreadId() = Thread.currentThread().id.toString()
