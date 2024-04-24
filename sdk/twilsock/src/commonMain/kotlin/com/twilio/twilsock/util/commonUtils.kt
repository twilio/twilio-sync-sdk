//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.util

import com.twilio.twilsock.client.Status
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.Unknown

internal fun Status.toErrorInfo(reason: ErrorReason = Unknown) =
    ErrorInfo(reason, code, errorCode ?: 0, status, description ?: "")
