//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
package com.twilio.sync.client.java

import com.twilio.sync.client.Lists
import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.client.java.utils.toSyncListJavaItem
import com.twilio.sync.client.java.utils.wrap
import com.twilio.sync.utils.QueryOrder
import com.twilio.util.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.seconds

internal class ListsJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val lists: Lists
) : ListsJava {

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    override fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncListJava.Listener?,
        output: SuccessListener<SyncListJava>
    ) = notifyListener(output) {
        val syncList = lists.create(uniqueName, ttlSeconds.seconds)
        val syncListJava = SyncListJavaImpl(coroutineScope, listenersDispatcher, syncList)
        listener?.let { syncListJava.addListener(it) }
        return@notifyListener syncListJava
    }

    override fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncListJava.Listener?,
        output: SuccessListener<SyncListJava>
    ) = notifyListener(output) {
        val syncList = lists.openOrCreate(uniqueName, ttlSeconds.seconds)
        val syncListJava = SyncListJavaImpl(coroutineScope, listenersDispatcher, syncList)
        listener?.let { syncListJava.addListener(it) }
        return@notifyListener syncListJava
    }

    override fun openExisting(
        sidOrUniqueName: String,
        listener: SyncListJava.Listener?,
        output: SuccessListener<SyncListJava>
    ) = notifyListener(output) {
        val syncList = lists.openExisting(sidOrUniqueName)
        val syncListJava = SyncListJavaImpl(coroutineScope, listenersDispatcher, syncList)
        listener?.let { syncListJava.addListener(it) }
        return@notifyListener syncListJava
    }

    override fun setTtl(
        sidOrUniqueName: String,
        ttlSeconds: Long,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        lists.setTtl(sidOrUniqueName, ttlSeconds.seconds)
    }

    override fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>) = notifyListener(callback) {
        lists.remove(sidOrUniqueName)
    }

    override fun getListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        callback: SuccessListener<SyncListJava.Item?>
    ) = notifyListener(callback) {
        lists.getListItem(listSidOrUniqueName, itemIndex)?.toSyncListJavaItem()
    }

    override fun addListItem(
        listSidOrUniqueName: String,
        jsonData: String,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        lists.addListItem(listSidOrUniqueName, json.decodeFromString(jsonData)).toSyncListJavaItem()
    }

    override fun addListItemWithTtl(
        listSidOrUniqueName: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        lists.addListItemWithTtl(listSidOrUniqueName, json.decodeFromString(jsonData), ttlSeconds.seconds)
            .toSyncListJavaItem()
    }

    override fun setListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        jsonData: String,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        lists.setListItem(listSidOrUniqueName, itemIndex, json.decodeFromString(jsonData)).toSyncListJavaItem()
    }

    override fun setListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        lists.setListItemWithTtl(listSidOrUniqueName, itemIndex, json.decodeFromString(jsonData), ttlSeconds.seconds)
            .toSyncListJavaItem()
    }

    override fun mutateListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        lists.mutateListItem(listSidOrUniqueName, itemIndex, mutator.wrap()).toSyncListJavaItem()
    }

    override fun mutateListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncListJava.Item>
    ) = notifyListener(callback) {
        lists.mutateListItemWithTtl(listSidOrUniqueName, itemIndex, ttlSeconds.seconds, mutator.wrap()).toSyncListJavaItem()
    }

    override fun removeListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        lists.removeListItem(listSidOrUniqueName, itemIndex)
    }

    override fun queryListItems(
        listSidOrUniqueName: String,
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean
    ): SyncIteratorJava<SyncListJava.Item> {
        val syncIterator = lists.queryItems(listSidOrUniqueName, startIndex, includeStartIndex, queryOrder, pageSize, useCache)
        return SyncListIteratorJavaImpl(notifyListener, syncIterator)
    }
}
