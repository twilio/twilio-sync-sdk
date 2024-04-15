//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncStream
import com.twilio.sync.entities.SyncStreamImpl
import com.twilio.sync.entities.SyncStreamSynchronizer
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class StreamsImpl(
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
) : Streams {

    override suspend fun create(uniqueName: String?, ttl: Duration) =
        open(OpenOptions.CreateNew(uniqueName, ttl))

    override suspend fun openOrCreate(uniqueName: String, ttl: Duration) =
        open(OpenOptions.OpenOrCreate(uniqueName, ttl))

    override suspend fun openExisting(sidOrUniqueName: String) =
        open(OpenOptions.OpenExisting(sidOrUniqueName))

    override suspend fun setTtl(sidOrUniqueName: String, ttl: Duration) {
        repository.setStreamTtl(sidOrUniqueName, ttl)
    }

    override suspend fun publishMessage(sidOrUniqueName: String, data: JsonObject): SyncStream.Message {
        val sid = repository.publishStreamMessage(sidOrUniqueName, data)
        return SyncStream.Message(sid, data)
    }

    override suspend fun remove(sidOrUniqueName: String) {
        repository.removeStream(sidOrUniqueName)
    }

    private suspend fun open(options: OpenOptions): SyncStream {
        val stream = SyncStreamImpl(
            coroutineScope,
            subscriptionManager,
            repository,
        )

        stream.open(options)
        return SyncStreamSynchronizer(coroutineScope, stream)
    }
}
