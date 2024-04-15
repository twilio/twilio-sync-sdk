//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun currentThreadDispatcher(): CoroutineDispatcher {
    // TODO: return dispatcher which dispatches to current iOS thread if possible, otherwise Dispatchers.Main
    return Dispatchers.Main
}
