//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

import com.twilio.util.ErrorReason
import com.twilio.util.getOrThrowTwilioException
import com.twilio.util.getOrThrowTwilioExceptionSync
import kotlinx.coroutines.channels.ReceiveChannel

internal class SyncIteratorImpl<T, R>(
    private val channel: ReceiveChannel<T>,
    private val transform: (T) -> R,
) : SyncIterator<R> {

    private val channelIterator = channel.iterator()

    override fun next() =
        runCatching { transform(channelIterator.next()) }.getOrThrowTwilioExceptionSync(ErrorReason.IteratorError)

    override suspend fun hasNext() =
        runCatching { channelIterator.hasNext() }.getOrThrowTwilioException(ErrorReason.IteratorError)

    override fun close() = channel.cancel()
}
