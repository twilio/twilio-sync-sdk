//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.entities

import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.utils.CollectionItemId.Index
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class SyncListImpl(
    coroutineScope: CoroutineScope,
    subscriptionManager: SubscriptionManager,
    repository: SyncRepository,
) : SyncList {

    private val delegate = CollectionDelegate(
        this,
        coroutineScope,
        subscriptionManager,
        repository,
        CollectionType.List,
        transformItem = { it.toListItem() }
    )

    override val sid: EntitySid get() = delegate.metadata.sid
    override val uniqueName: String? get() = delegate.metadata.uniqueName
    override val dateCreated: Instant get() = delegate.metadata.dateCreated
    override val dateUpdated: Instant get() = delegate.metadata.dateUpdated
    override val dateExpires: Instant? get() = delegate.metadata.dateExpires

    override val isRemoved: Boolean get() = delegate.isRemoved

    override val events = Events()

    override val subscriptionState: SubscriptionState get() = delegate.subscriptionState

    override suspend fun setTtl(ttl: Duration) = delegate.setTtl(ttl)

    override suspend fun getItem(itemIndex: Long, useCache: Boolean): SyncList.Item? =
        delegate.getItem(Index(itemIndex), useCache)

    override suspend fun addItem(itemData: JsonObject): SyncList.Item =
        delegate.addItem(itemData, ttl = null)

    override suspend fun addItemWithTtl(itemData: JsonObject, ttl: Duration): SyncList.Item =
        delegate.addItem(itemData, ttl)

    override suspend fun setItem(itemIndex: Long, itemData: JsonObject): SyncList.Item =
        delegate.updateItem(Index(itemIndex), itemData, ttl = null)

    override suspend fun setItemWithTtl(itemIndex: Long, itemData: JsonObject, ttl: Duration): SyncList.Item =
        delegate.updateItem(Index(itemIndex), itemData, ttl)

    override suspend fun mutateItem(
        itemIndex: Long,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): SyncList.Item =
        delegate.mutateItem(Index(itemIndex), ttl = null, mutator)

    override suspend fun mutateItemWithTtl(
        itemIndex: Long,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): SyncList.Item =
        delegate.mutateItem(Index(itemIndex), ttl, mutator)

    override suspend fun removeItem(itemIndex: Long) = delegate.removeItem(Index(itemIndex))

    override fun queryItems(
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncList.Item> {
        val startItemId = startIndex?.let { Index(it) }
        return delegate.queryItems(startItemId, includeStartIndex, queryOrder, pageSize, useCache)
    }

    override suspend fun removeList() = delegate.removeCollection()

    override fun close() = delegate.close()

    suspend fun open(openOptions: OpenOptions) = delegate.open(openOptions)

    inner class Events : SyncList.Events {
        override val onRemoved = delegate.events.onRemoved
        override val onSubscriptionStateChanged = delegate.events.onSubscriptionStateChanged
        override val onItemAdded = delegate.events.onItemAdded
        override val onItemUpdated = delegate.events.onItemUpdated
        override val onItemRemoved = delegate.events.onItemRemoved
    }
}
