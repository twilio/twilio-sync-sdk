//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncMap
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.Synchronizer
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class MapsSynchronizer(
    scope: CoroutineScope,
    maps: Maps,
) : Synchronizer<Maps>(scope, maps), Maps {

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

    override suspend fun getMapItem(mapSidOrUniqueName: String, itemKey: String, useCache: Boolean): SyncMap.Item? =
        doSynchronizeSuspend { getMapItem(mapSidOrUniqueName, itemKey, useCache) }

    override suspend fun setMapItem(mapSidOrUniqueName: String, itemKey: String, itemData: JsonObject) =
        doSynchronizeSuspend { setMapItem(mapSidOrUniqueName, itemKey, itemData) }

    override suspend fun setMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        itemData: JsonObject,
        ttl: Duration
    ) = doSynchronizeSuspend { setMapItemWithTtl(mapSidOrUniqueName, itemKey, itemData, ttl) }

    override suspend fun mutateMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ) = doSynchronizeSuspend { mutateMapItem(mapSidOrUniqueName, itemKey, mutator) }

    override suspend fun mutateMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ) = doSynchronizeSuspend { mutateMapItemWithTtl(mapSidOrUniqueName, itemKey, ttl, mutator) }

    override suspend fun removeMapItem(mapSidOrUniqueName: String, itemKey: String) =
        doSynchronizeSuspend { removeMapItem(mapSidOrUniqueName, itemKey) }

    override fun queryItems(
        mapSidOrUniqueName: String,
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncMap.Item> =
        // Implemented with Channel. Could be called without synchronisation at all.
        delegate.queryItems(mapSidOrUniqueName, startKey, includeStartKey, queryOrder, pageSize, useCache)
}
