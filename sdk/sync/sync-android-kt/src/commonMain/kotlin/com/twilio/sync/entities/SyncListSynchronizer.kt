//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.Synchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class SyncListSynchronizer(
    scope: CoroutineScope,
    list: SyncList,
) : Synchronizer<SyncList>(scope, list), SyncList {

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

    override suspend fun getItem(itemIndex: Long, useCache: Boolean): SyncList.Item? =
        doSynchronizeSuspend { getItem(itemIndex, useCache) }

    override suspend fun addItem(itemData: JsonObject): SyncList.Item =
        doSynchronizeSuspend { addItem(itemData) }

    override suspend fun addItemWithTtl(itemData: JsonObject, ttl: Duration): SyncList.Item =
        doSynchronizeSuspend { addItemWithTtl(itemData, ttl) }

    override suspend fun setItem(itemIndex: Long, itemData: JsonObject) =
        doSynchronizeSuspend { setItem(itemIndex, itemData) }

    override suspend fun setItemWithTtl(itemIndex: Long, itemData: JsonObject, ttl: Duration) =
        doSynchronizeSuspend { setItemWithTtl(itemIndex, itemData, ttl) }

    override suspend fun mutateItem(
        itemIndex: Long,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ) =
        doSynchronizeSuspend { mutateItem(itemIndex, mutator) }

    override suspend fun mutateItemWithTtl(
        itemIndex: Long,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ) =
        doSynchronizeSuspend { mutateItemWithTtl(itemIndex, ttl, mutator) }

    override suspend fun removeItem(itemIndex: Long) =
        doSynchronizeSuspend { removeItem(itemIndex) }

    override fun queryItems(
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncList.Item> =
        // Implemented with Channel. Could be called without synchronisation at all.
        delegate.queryItems(startIndex, includeStartIndex, queryOrder, pageSize, useCache)

    override suspend fun removeList() =
        doSynchronizeSuspend { removeList() }

    override fun close() =
        doSynchronizeAsync { close() }
}
