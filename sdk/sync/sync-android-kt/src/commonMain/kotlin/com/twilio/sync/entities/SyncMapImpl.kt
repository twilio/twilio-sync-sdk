//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.utils.CollectionItemId.Key
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class SyncMapImpl(
    coroutineScope: CoroutineScope,
    subscriptionManager: SubscriptionManager,
    repository: SyncRepository,
) : SyncMap {

    private val delegate = CollectionDelegate(
        this,
        coroutineScope,
        subscriptionManager,
        repository,
        CollectionType.Map,
        transformItem = { it.toMapItem() }
    )

    override val sid: EntitySid get() = delegate.metadata.sid
    override val uniqueName: String? get() = delegate.metadata.uniqueName
    override val dateCreated: Instant get() = delegate.metadata.dateCreated
    override val dateUpdated: Instant get() = delegate.metadata.dateUpdated
    override val dateExpires: Instant? get() = delegate.metadata.dateExpires

    override val isRemoved: Boolean get() = delegate.isRemoved

    override val events = Events()

    override val subscriptionState: SubscriptionState get() = delegate.subscriptionState

    override suspend fun removeMap() = delegate.removeCollection()

    override suspend fun setTtl(ttl: Duration) = delegate.setTtl(ttl)

    override suspend fun getItem(itemKey: String, useCache: Boolean): SyncMap.Item? =
        delegate.getItem(Key(itemKey), useCache)

    override suspend fun setItem(itemKey: String, itemData: JsonObject) =
        delegate.setItem(Key(itemKey), itemData, ttl = null)

    override suspend fun setItemWithTtl(itemKey: String, itemData: JsonObject, ttl: Duration) =
        delegate.setItem(Key(itemKey), itemData, ttl)

    override suspend fun mutateItem(
        itemKey: String,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ) =
        delegate.mutateOrAddItem(Key(itemKey), ttl = null, mutator)

    override suspend fun mutateItemWithTtl(
        itemKey: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ) =
        delegate.mutateOrAddItem(Key(itemKey), ttl, mutator)

    override suspend fun removeItem(itemKey: String) = delegate.removeItem(Key(itemKey))

    override fun queryItems(
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncMap.Item> {
        val startItemId = startKey?.let { Key(it) }
        return delegate.queryItems(startItemId, includeStartKey, queryOrder, pageSize, useCache)
    }

    override fun close() = delegate.close()

    suspend fun open(openOptions: OpenOptions) = delegate.open(openOptions)

    inner class Events : SyncMap.Events {
        override val onRemoved = delegate.events.onRemoved
        override val onSubscriptionStateChanged = delegate.events.onSubscriptionStateChanged
        override val onItemAdded = delegate.events.onItemAdded
        override val onItemUpdated = delegate.events.onItemUpdated
        override val onItemRemoved = delegate.events.onItemRemoved
    }
}
