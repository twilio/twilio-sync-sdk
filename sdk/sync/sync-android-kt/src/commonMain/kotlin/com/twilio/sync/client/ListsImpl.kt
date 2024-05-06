//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncList
import com.twilio.sync.entities.SyncListImpl
import com.twilio.sync.entities.SyncListSynchronizer
import com.twilio.sync.entities.toListItem
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.utils.CollectionItemId.Index
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.SyncIteratorImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class ListsImpl(
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
) : Lists {

    override suspend fun create(uniqueName: String?, ttl: Duration) =
        open(OpenOptions.CreateNew(uniqueName, ttl))

    override suspend fun openOrCreate(uniqueName: String, ttl: Duration) =
        open(OpenOptions.OpenOrCreate(uniqueName, ttl))

    override suspend fun openExisting(sidOrUniqueName: String) =
        open(OpenOptions.OpenExisting(sidOrUniqueName))

    override suspend fun setTtl(sidOrUniqueName: String, ttl: Duration) {
        repository.setCollectionTtl(CollectionType.List, sidOrUniqueName, ttl)
    }

    override suspend fun remove(sidOrUniqueName: String) {
        repository.removeCollection(CollectionType.List, sidOrUniqueName)
    }

    override suspend fun getListItem(listSidOrUniqueName: String, itemIndex: Long, useCache: Boolean): SyncList.Item? {
        return repository.getCollectionItemData(listSidOrUniqueName, Index(itemIndex), useCache)?.toListItem()
    }

    override suspend fun addListItem(listSidOrUniqueName: String, itemData: JsonObject): SyncList.Item =
        repository.addCollectionItemData(CollectionType.List, listSidOrUniqueName, itemId = null, itemData, ttl = null)
            .toListItem()

    override suspend fun addListItemWithTtl(
        listSidOrUniqueName: String,
        itemData: JsonObject,
        ttl: Duration
    ): SyncList.Item =
        repository.addCollectionItemData(CollectionType.List, listSidOrUniqueName, itemId = null, itemData, ttl)
            .toListItem()

    override suspend fun setListItem(listSidOrUniqueName: String, itemIndex: Long, itemData: JsonObject): SyncList.Item =
        repository.updateCollectionItemData(listSidOrUniqueName, Index(itemIndex), itemData, ttl = null).toListItem()

    override suspend fun setListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        itemData: JsonObject,
        ttl: Duration
    ): SyncList.Item {
        return repository.updateCollectionItemData(listSidOrUniqueName, Index(itemIndex), itemData, ttl).toListItem()
    }

    override suspend fun mutateListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): SyncList.Item {
        return repository.mutateCollectionItemData(listSidOrUniqueName, Index(itemIndex), ttl = null, mutator).toListItem()
    }

    override suspend fun mutateListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): SyncList.Item {
        return repository.mutateCollectionItemData(listSidOrUniqueName, Index(itemIndex), ttl, mutator).toListItem()
    }

    override suspend fun removeListItem(listSidOrUniqueName: String, itemIndex: Long) {
        repository.removeCollectionItem(listSidOrUniqueName, Index(itemIndex))
    }

    override fun queryItems(
        listSidOrUniqueName: String,
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncList.Item> {
        require(pageSize > 0)

        val startId = startIndex?.let { Index(it) }

        val channel =
            repository.getCollectionItemsData(
                CollectionType.List, listSidOrUniqueName, startId, includeStartIndex, queryOrder, pageSize, useCache)

        return SyncIteratorImpl(channel) { it.toListItem() }
    }

    private suspend fun open(options: OpenOptions): SyncList {
        val list = SyncListImpl(
            coroutineScope = coroutineScope,
            subscriptionManager = subscriptionManager,
            repository = repository,
        )

        list.open(options)
        return SyncListSynchronizer(coroutineScope, list)
    }
}
