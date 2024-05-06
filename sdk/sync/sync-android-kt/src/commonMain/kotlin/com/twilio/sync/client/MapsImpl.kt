//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncMap
import com.twilio.sync.entities.SyncMapImpl
import com.twilio.sync.entities.SyncMapSynchronizer
import com.twilio.sync.entities.toMapItem
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.utils.CollectionItemId.Key
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.SyncIteratorImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class MapsImpl(
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
) : Maps {

    override suspend fun create(uniqueName: String?, ttl: Duration) =
        open(OpenOptions.CreateNew(uniqueName, ttl))

    override suspend fun openOrCreate(uniqueName: String, ttl: Duration) =
        open(OpenOptions.OpenOrCreate(uniqueName, ttl))

    override suspend fun openExisting(sidOrUniqueName: String) =
        open(OpenOptions.OpenExisting(sidOrUniqueName))

    override suspend fun setTtl(sidOrUniqueName: String, ttl: Duration) {
        repository.setCollectionTtl(CollectionType.Map, sidOrUniqueName, ttl)
    }

    override suspend fun remove(sidOrUniqueName: String) {
        repository.removeCollection(CollectionType.Map, sidOrUniqueName)
    }

    override suspend fun getMapItem(mapSidOrUniqueName: String, itemKey: String, useCache: Boolean): SyncMap.Item? {
        return repository.getCollectionItemData(mapSidOrUniqueName, Key(itemKey), useCache)?.toMapItem()
    }

    override suspend fun setMapItem(mapSidOrUniqueName: String, itemKey: String, itemData: JsonObject): SyncMap.Item {
        return repository.setCollectionItemData(mapSidOrUniqueName, Key(itemKey), itemData, ttl = null).toMapItem()
    }

    override suspend fun setMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        itemData: JsonObject,
        ttl: Duration
    ): SyncMap.Item {
        return repository.setCollectionItemData(mapSidOrUniqueName, Key(itemKey), itemData, ttl).toMapItem()
    }

    override suspend fun mutateMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ): SyncMap.Item {
        return repository.mutateOrAddCollectionItemData(mapSidOrUniqueName, Key(itemKey), ttl = null, mutator).toMapItem()
    }

    override suspend fun mutateMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ): SyncMap.Item {
        return repository.mutateOrAddCollectionItemData(mapSidOrUniqueName, Key(itemKey), ttl, mutator).toMapItem()
    }

    override suspend fun removeMapItem(mapSidOrUniqueName: String, itemKey: String) {
        repository.removeCollectionItem(mapSidOrUniqueName, Key(itemKey))
    }

    override fun queryItems(
        mapSidOrUniqueName: String,
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncMap.Item> {
        require(pageSize > 0)

        val startId = startKey?.let { Key(it) }

        val channel =
            repository.getCollectionItemsData(
                CollectionType.Map, mapSidOrUniqueName, startId, includeStartKey, queryOrder, pageSize, useCache)

        return SyncIteratorImpl(channel) { it.toMapItem() }
    }

    private suspend fun open(options: OpenOptions): SyncMap {
        val map = SyncMapImpl(
            coroutineScope = coroutineScope,
            subscriptionManager = subscriptionManager,
            repository = repository,
        )

        map.open(options)
        return SyncMapSynchronizer(coroutineScope, map)
    }
}
