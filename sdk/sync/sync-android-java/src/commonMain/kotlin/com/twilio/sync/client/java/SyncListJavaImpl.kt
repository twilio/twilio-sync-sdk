//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.ListenableImpl
import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SubscriptionStateJava
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.client.java.utils.toSubscriptionStateJava
import com.twilio.sync.client.java.utils.toSyncListJavaItem
import com.twilio.sync.client.java.utils.wrap
import com.twilio.sync.entities.SyncList
import com.twilio.sync.utils.QueryOrder
import com.twilio.util.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.seconds

internal class SyncListJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val syncList: SyncList,
) : SyncListJava, ListenableImpl<SyncListJava.Listener>(coroutineScope, listenersDispatcher) {

    override val sid: String
        get() = syncList.sid
    override val uniqueName: String?
        get() = syncList.uniqueName
    override val subscriptionState: SubscriptionStateJava
        get() = syncList.subscriptionState.toSubscriptionStateJava()
    override val dateCreated: Long
        get() = syncList.dateCreated.toEpochMilliseconds()
    override val dateUpdated: Long
        get() = syncList.dateUpdated.toEpochMilliseconds()
    override val dateExpires: Long?
        get() = syncList.dateExpires?.toEpochMilliseconds()
    override val isRemoved: Boolean
        get() = syncList.isRemoved
    override val isFromCache: Boolean
        get() = syncList.isFromCache

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    init {
        with(syncList.events) {
            onSubscriptionStateChanged.connectListeners {
                onSubscriptionStateChanged(this@SyncListJavaImpl, it.toSubscriptionStateJava())
            }
            onRemoved.connectListeners { onRemoved(this@SyncListJavaImpl) }
        }
    }

    override fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncListJava>) = notifyListener(callback) {
        syncList.setTtl(ttlSeconds.seconds)
        return@notifyListener this
    }

    override fun getItem(itemIndex: Long, callback: SuccessListener<SyncListJava.Item?>) = notifyListener(callback) {
        syncList.getItem(itemIndex)?.toSyncListJavaItem()
    }

    override fun addItem(
        jsonData: String,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        syncList.addItem(json.decodeFromString(jsonData)).toSyncListJavaItem()
    }

    override fun addItemWithTtl(
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        syncList.addItemWithTtl(json.decodeFromString(jsonData), ttlSeconds.seconds).toSyncListJavaItem()
    }

    override fun setItem(
        itemIndex: Long,
        jsonData: String,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        syncList.setItem(itemIndex, json.decodeFromString(jsonData)).toSyncListJavaItem()
    }

    override fun setItemWithTtl(
        itemIndex: Long,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        syncList.setItemWithTtl(itemIndex, json.decodeFromString(jsonData), ttlSeconds.seconds).toSyncListJavaItem()
    }

    override fun mutateItem(
        itemIndex: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        syncList.mutateItem(itemIndex, mutator.wrap()).toSyncListJavaItem()
    }

    override fun mutateItemWithTtl(
        itemIndex: Long,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        syncList.mutateItemWithTtl(itemIndex, ttlSeconds.seconds, mutator.wrap()).toSyncListJavaItem()
    }

    override fun removeItem(itemIndex: Long, callback: SuccessListener<SyncListJava>) = notifyListener(callback) {
        syncList.removeItem(itemIndex)
        return@notifyListener this
    }

    override fun removeList(callback: SuccessListener<SyncListJava>) = notifyListener(callback) {
        syncList.removeList()
        return@notifyListener this
    }

    override fun queryItems(
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIteratorJava<SyncListJava.Item> {
        val syncIterator = syncList.queryItems(startIndex, includeStartIndex, queryOrder, pageSize, useCache)
        return SyncListIteratorJavaImpl(notifyListener, syncIterator)
    }

    override fun close() = syncList.close()
}
