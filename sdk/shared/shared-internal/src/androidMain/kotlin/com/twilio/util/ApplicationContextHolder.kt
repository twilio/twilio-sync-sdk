//
//  Twilio Utils
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import android.content.Context
import kotlin.properties.Delegates

object ApplicationContextHolder {
    @JvmStatic
    var applicationContext by Delegates.atomicNotNull<Context>()
}
