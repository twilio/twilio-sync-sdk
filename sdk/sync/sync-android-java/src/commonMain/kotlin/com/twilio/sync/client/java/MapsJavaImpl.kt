//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.twilio.sync.client.java

import com.twilio.sync.client.Maps
import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.client.java.utils.toSyncMapJavaItem
import com.twilio.sync.client.java.utils.wrap
import com.twilio.sync.utils.QueryOrder
import com.twilio.util.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class MapsJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val maps: Maps
) : MapsJava {

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    override fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncMapJava.Listener?,
        output: SuccessListener<SyncMapJava>
    ) = notifyListener(output) {
        val syncMap = maps.create(uniqueName, ttlSeconds.seconds)
        val syncMapJava = SyncMapJavaImpl(coroutineScope, listenersDispatcher, syncMap)
        listener?.let { syncMapJava.addListener(it) }
        return@notifyListener syncMapJava
    }

    override fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncMapJava.Listener?,
        output: SuccessListener<SyncMapJava>
    ) = notifyListener(output) {
        val syncMap = maps.openOrCreate(uniqueName, ttlSeconds.seconds)
        val syncMapJava = SyncMapJavaImpl(coroutineScope, listenersDispatcher, syncMap)
        listener?.let { syncMapJava.addListener(it) }
        return@notifyListener syncMapJava
    }

    override fun openExisting(
        sidOrUniqueName: String,
        listener: SyncMapJava.Listener?,
        output: SuccessListener<SyncMapJava>
    ) = notifyListener(output) {
        val syncMap = maps.openExisting(sidOrUniqueName)
        val syncMapJava = SyncMapJavaImpl(coroutineScope, listenersDispatcher, syncMap)
        listener?.let { syncMapJava.addListener(it) }
        return@notifyListener syncMapJava
    }

    override fun setTtl(
        sidOrUniqueName: String,
        ttlSeconds: Long,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        maps.setTtl(sidOrUniqueName, ttlSeconds.seconds)
    }

    override fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>) = notifyListener(callback) {
        maps.remove(sidOrUniqueName)
    }

    override fun getMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        callback: SuccessListener<SyncMapJava.Item?>
    ) = notifyListener(callback) {
        maps.getMapItem(mapSidOrUniqueName, itemKey)?.toSyncMapJavaItem()
    }

    override fun setMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        jsonData: String,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        maps.setMapItem(mapSidOrUniqueName, itemKey, json.decodeFromString(jsonData)).toSyncMapJavaItem()
    }

    override fun setMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        maps.setMapItemWithTtl(mapSidOrUniqueName, itemKey, json.decodeFromString(jsonData), ttlSeconds.seconds)
            .toSyncMapJavaItem()
    }

    override fun mutateMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        mutator: SyncMutator,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        maps.mutateMapItem(mapSidOrUniqueName, itemKey, mutator.wrap()).toSyncMapJavaItem()
    }

    override fun mutateMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        maps.mutateMapItemWithTtl(mapSidOrUniqueName, itemKey, ttlSeconds.seconds, mutator.wrap()).toSyncMapJavaItem()
    }

    override fun removeMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        maps.removeMapItem(mapSidOrUniqueName, itemKey)
    }

    override fun queryMapItems(
        mapSidOrUniqueName: String,
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIteratorJava<SyncMapJava.Item> {
        val syncIterator = maps.queryItems(mapSidOrUniqueName, startKey, includeStartKey, queryOrder, pageSize, useCache)
        return SyncMapIteratorJavaImpl(notifyListener, syncIterator)
    }
}
