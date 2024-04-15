//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import android.content.Context
import kotlin.properties.Delegates

object ApplicationContextHolder {
    @JvmStatic
    var applicationContext by Delegates.atomicNotNull<Context>()
}
