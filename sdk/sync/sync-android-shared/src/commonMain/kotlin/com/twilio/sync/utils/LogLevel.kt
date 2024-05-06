//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import com.twilio.util.TwilioLogger

/**
 * Log level constants.
 */
enum class LogLevel(val value: Int) {

    /** Show low-level tracing messages as well as all Debug log messages.  */
    Verbose(TwilioLogger.VERBOSE),

    /** Show low-level debugging messages as well as all Info log messages.  */
    Debug(TwilioLogger.DEBUG),

    /** Show informational messages as well as all Warning log messages.  */
    Info(TwilioLogger.INFO),

    /** Show warnings as well as all Critical log messages.  */
    Warn(TwilioLogger.WARN),

    /** Show critical log messages as well as all Fatal log messages.  */
    Error(TwilioLogger.ERROR),

    /** Show fatal errors only.  */
    Assert(TwilioLogger.ASSERT),

    /** Show no log messages.  */
    Silent(TwilioLogger.SILENT);
}
