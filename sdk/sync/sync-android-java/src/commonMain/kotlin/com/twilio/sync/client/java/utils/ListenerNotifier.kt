//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java.utils

import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.toTwilioException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ListenerNotifier(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
) {

    inline operator fun <T : Any?> invoke(
        listener: SuccessListener<T>,
        defaultReason: ErrorReason = Unknown,
        crossinline block: suspend () -> T
    ): CancellationToken {
        val job = coroutineScope.launch {
            runCatching { block() }
                .onSuccess {
                    withContext(listenersDispatcher) { listener.onSuccess(it) }
                }
                .onFailure { t ->
                    val errorInfo = t.toTwilioException(defaultReason).errorInfo
                    withContext(listenersDispatcher) { listener.onFailure(errorInfo) }
                }
        }
        return CancellationToken { job.cancel() }
    }
}
