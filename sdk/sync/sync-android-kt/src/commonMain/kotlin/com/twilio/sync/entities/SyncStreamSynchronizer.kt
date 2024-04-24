//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.entities

import com.twilio.sync.utils.Synchronizer
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class SyncStreamSynchronizer(
    scope: CoroutineScope,
    stream: SyncStream,
) : Synchronizer<SyncStream>(scope, stream), SyncStream {

    override val sid get() = delegate.sid
    override val uniqueName get() = delegate.uniqueName
    override val dateExpires get() = delegate.dateExpires
    override val isRemoved get() = delegate.isRemoved
    override val subscriptionState get() = delegate.subscriptionState
    override val events get() = delegate.events

    override suspend fun setTtl(ttl: Duration) =
        doSynchronizeSuspend { setTtl(ttl) }

    override suspend fun publishMessage(data: JsonObject) =
        doSynchronizeSuspend { publishMessage(data) }

    override suspend fun removeStream() =
        doSynchronizeSuspend { removeStream() }

    override fun close() =
        doSynchronizeAsync { close() }
}
