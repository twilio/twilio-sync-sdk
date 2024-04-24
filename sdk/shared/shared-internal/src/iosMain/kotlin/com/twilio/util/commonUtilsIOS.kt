//
//  Twilio Utils
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import platform.Foundation.NSThread
import platform.Foundation.NSUUID

actual fun generateUUID(): String = NSUUID().UUIDString()

actual fun getCurrentThreadId(): String = NSThread.currentThread().toString()
