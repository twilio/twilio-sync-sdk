//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.entities

import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.Synchronizer
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject

internal class SyncMapSynchronizer(
    scope: CoroutineScope,
    map: SyncMap,
) : Synchronizer<SyncMap>(scope, map), SyncMap {

    override val sid get() = delegate.sid
    override val uniqueName get() = delegate.uniqueName
    override val subscriptionState get() = delegate.subscriptionState
    override val dateCreated get() = delegate.dateCreated
    override val dateUpdated get() = delegate.dateUpdated
    override val dateExpires get() = delegate.dateExpires
    override val isRemoved get() = delegate.isRemoved
    override val events get() = delegate.events

    override suspend fun setTtl(ttl: Duration) =
        doSynchronizeSuspend { setTtl(ttl) }

    override suspend fun getItem(itemKey: String, useCache: Boolean): SyncMap.Item? =
        doSynchronizeSuspend { getItem(itemKey, useCache) }

    override suspend fun setItem(itemKey: String, itemData: JsonObject)=
        doSynchronizeSuspend { setItem(itemKey, itemData) }

    override suspend fun setItemWithTtl(itemKey: String, itemData: JsonObject, ttl: Duration) =
        doSynchronizeSuspend { setItemWithTtl(itemKey, itemData, ttl) }

    override suspend fun mutateItem(
        itemKey: String,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    )=
        doSynchronizeSuspend { mutateItem(itemKey, mutator) }

    override suspend fun mutateItemWithTtl(
        itemKey: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    )=
        doSynchronizeSuspend { mutateItemWithTtl(itemKey, ttl, mutator) }

    override suspend fun removeItem(itemKey: String) =
        doSynchronizeSuspend { removeItem(itemKey) }

    override fun queryItems(
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncMap.Item> =
        // Implemented with Channel. Could be called without synchronisation at all.
        delegate.queryItems(startKey, includeStartKey, queryOrder, pageSize, useCache)

    override suspend fun removeMap() =
        doSynchronizeSuspend { removeMap() }

    override fun close() =
        doSynchronizeAsync { close() }
}
