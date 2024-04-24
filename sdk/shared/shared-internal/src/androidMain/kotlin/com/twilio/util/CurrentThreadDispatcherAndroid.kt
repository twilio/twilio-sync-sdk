//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher

actual fun currentThreadDispatcher(): CoroutineDispatcher {
    val looper = Looper.myLooper() ?: return Dispatchers.Main
    return Handler(looper).asCoroutineDispatcher()
}
