//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client

import com.twilio.sync.utils.Synchronizer
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class StreamsSynchronizer(
    scope: CoroutineScope,
    streams: Streams,
) : Synchronizer<Streams>(scope, streams), Streams {

    override suspend fun create(uniqueName: String?, ttl: Duration) =
        doSynchronizeSuspend { create(uniqueName, ttl) }

    override suspend fun openOrCreate(uniqueName: String, ttl: Duration) =
        doSynchronizeSuspend { openOrCreate(uniqueName, ttl) }

    override suspend fun openExisting(sidOrUniqueName: String) =
        doSynchronizeSuspend { openExisting(sidOrUniqueName) }

    override suspend fun setTtl(sidOrUniqueName: String, ttl: Duration) =
        doSynchronizeSuspend { setTtl(sidOrUniqueName, ttl) }

    override suspend fun remove(sidOrUniqueName: String) =
        doSynchronizeSuspend { remove(sidOrUniqueName) }

    override suspend fun publishMessage(sidOrUniqueName: String, data: JsonObject) =
        doSynchronizeSuspend { publishMessage(sidOrUniqueName, data) }
}
