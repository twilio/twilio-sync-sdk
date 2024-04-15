//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.ListenableImpl
import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SubscriptionStateJava
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.client.java.utils.toSubscriptionStateJava
import com.twilio.sync.client.java.utils.toSyncMapJavaItem
import com.twilio.sync.client.java.utils.wrap
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.utils.QueryOrder
import com.twilio.util.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class SyncMapJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val syncMap: SyncMap,
) : SyncMapJava, ListenableImpl<SyncMapJava.Listener>(coroutineScope, listenersDispatcher) {

    override val sid: String
        get() = syncMap.sid
    override val uniqueName: String?
        get() = syncMap.uniqueName
    override val subscriptionState: SubscriptionStateJava
        get() = syncMap.subscriptionState.toSubscriptionStateJava()
    override val dateCreated: Long
        get() = syncMap.dateCreated.toEpochMilliseconds()
    override val dateUpdated: Long
        get() = syncMap.dateUpdated.toEpochMilliseconds()
    override val dateExpires: Long?
        get() = syncMap.dateExpires?.toEpochMilliseconds()
    override val isRemoved: Boolean
        get() = syncMap.isRemoved
    override val isFromCache: Boolean
        get() = syncMap.isFromCache

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    init {
        with(syncMap.events) {
            onSubscriptionStateChanged.connectListeners {
                onSubscriptionStateChanged(this@SyncMapJavaImpl, it.toSubscriptionStateJava())
            }
            onRemoved.connectListeners { onRemoved(this@SyncMapJavaImpl) }
        }
    }

    override fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncMapJava>) = notifyListener(callback) {
        syncMap.setTtl(ttlSeconds.seconds)
        return@notifyListener this
    }

    override fun getItem(itemKey: String, callback: SuccessListener<SyncMapJava.Item?>) = notifyListener(callback) {
        syncMap.getItem(itemKey)?.toSyncMapJavaItem()
    }

    override fun setItem(
        itemKey: String,
        jsonData: String,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        syncMap.setItem(itemKey, json.decodeFromString(jsonData)).toSyncMapJavaItem()
    }

    override fun setItemWithTtl(
        itemKey: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        syncMap.setItemWithTtl(itemKey, json.decodeFromString(jsonData), ttlSeconds.seconds).toSyncMapJavaItem()
    }

    override fun mutateItem(
        itemKey: String,
        mutator: SyncMutator,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        syncMap.mutateItem(itemKey, mutator.wrap()).toSyncMapJavaItem()
    }

    override fun mutateItemWithTtl(
        itemKey: String,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncMapJava.Item>
    ) = notifyListener(callback) {
        syncMap.mutateItemWithTtl(itemKey, ttlSeconds.seconds, mutator.wrap()).toSyncMapJavaItem()
    }

    override fun removeItem(itemKey: String, callback: SuccessListener<SyncMapJava>) = notifyListener(callback) {
        syncMap.removeItem(itemKey)
        return@notifyListener this
    }

    override fun removeMap(callback: SuccessListener<SyncMapJava>) = notifyListener(callback) {
        syncMap.removeMap()
        return@notifyListener this
    }

    override fun queryItems(
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIteratorJava<SyncMapJava.Item> {
        val syncIterator = syncMap.queryItems(startKey, includeStartKey, queryOrder, pageSize, useCache)
        return SyncMapIteratorJavaImpl(notifyListener, syncIterator)
    }

    override fun close() = syncMap.close()
}
