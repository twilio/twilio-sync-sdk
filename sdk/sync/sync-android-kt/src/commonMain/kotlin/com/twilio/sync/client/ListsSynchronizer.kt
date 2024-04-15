//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncList
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.Synchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class ListsSynchronizer(
    scope: CoroutineScope,
    lists: Lists,
) : Synchronizer<Lists>(scope, lists), Lists {

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

    override suspend fun getListItem(listSidOrUniqueName: String, itemIndex: Long, useCache: Boolean): SyncList.Item? =
        doSynchronizeSuspend { getListItem(listSidOrUniqueName, itemIndex, useCache) }

    override suspend fun addListItem(listSidOrUniqueName: String, itemData: JsonObject): SyncList.Item =
        doSynchronizeSuspend { addListItem(listSidOrUniqueName, itemData) }

    override suspend fun addListItemWithTtl(
        listSidOrUniqueName: String,
        itemData: JsonObject,
        ttl: Duration
    ): SyncList.Item =
        doSynchronizeSuspend { addListItemWithTtl(listSidOrUniqueName, itemData, ttl) }

    override suspend fun setListItem(listSidOrUniqueName: String, itemIndex: Long, itemData: JsonObject) =
        doSynchronizeSuspend { setListItem(listSidOrUniqueName, itemIndex, itemData) }

    override suspend fun setListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        itemData: JsonObject,
        ttl: Duration
    ) = doSynchronizeSuspend { setListItemWithTtl(listSidOrUniqueName, itemIndex, itemData, ttl) }

    override suspend fun mutateListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ) = doSynchronizeSuspend { mutateListItem(listSidOrUniqueName, itemIndex, mutator) }

    override suspend fun mutateListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ) = doSynchronizeSuspend { mutateListItemWithTtl(listSidOrUniqueName, itemIndex, ttl, mutator) }

    override suspend fun removeListItem(listSidOrUniqueName: String, itemIndex: Long) =
        doSynchronizeSuspend { removeListItem(listSidOrUniqueName, itemIndex) }

    override fun queryItems(
        listSidOrUniqueName: String,
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIterator<SyncList.Item> =
        // Implemented with Channel. Could be called without synchronisation at all.
        delegate.queryItems(listSidOrUniqueName, startIndex, includeStartIndex, queryOrder, pageSize, useCache)
}
