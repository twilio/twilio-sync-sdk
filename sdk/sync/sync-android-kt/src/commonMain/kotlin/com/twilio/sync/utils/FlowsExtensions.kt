//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformWhile

internal inline fun <T> Flow<T>.drop(count: Int, crossinline action: suspend (T) -> Unit): Flow<T> {
    require(count >= 0) { "Drop count should be non-negative, but had $count" }

    var skipped = 0
    return transform { value ->
        if (skipped < count) {
            ++skipped
            action(value)
        } else {
            emit(value)
        }
    }
}
