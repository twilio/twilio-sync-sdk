//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.twilio.sync.client.java

import com.twilio.sync.client.Streams
import com.twilio.sync.client.java.utils.ListenerNotifier
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.toSyncStreamJavaMessage
import com.twilio.util.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class StreamsJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val streams: Streams
) : StreamsJava {

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    override fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncStreamJava.Listener?,
        output: SuccessListener<SyncStreamJava>
    ) = notifyListener(output) {
        val syncStream = streams.create(uniqueName, ttlSeconds.seconds)
        val syncStreamJava = SyncStreamJavaImpl(coroutineScope, listenersDispatcher, syncStream)
        listener?.let { syncStreamJava.addListener(it) }
        return@notifyListener syncStreamJava
    }

    override fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncStreamJava.Listener?,
        output: SuccessListener<SyncStreamJava>
    ) = notifyListener(output) {
        val syncStream = streams.openOrCreate(uniqueName, ttlSeconds.seconds)
        val syncStreamJava = SyncStreamJavaImpl(coroutineScope, listenersDispatcher, syncStream)
        listener?.let { syncStreamJava.addListener(it) }
        return@notifyListener syncStreamJava
    }

    override fun openExisting(
        sidOrUniqueName: String,
        listener: SyncStreamJava.Listener?,
        output: SuccessListener<SyncStreamJava>
    ) = notifyListener(output) {
        val syncStream = streams.openExisting(sidOrUniqueName)
        val syncStreamJava = SyncStreamJavaImpl(coroutineScope, listenersDispatcher, syncStream)
        listener?.let { syncStreamJava.addListener(it) }
        return@notifyListener syncStreamJava
    }

    override fun setTtl(
        sidOrUniqueName: String,
        ttlSeconds: Long,
        callback: SuccessListener<Unit>
    ) = notifyListener(callback) {
        streams.setTtl(sidOrUniqueName, ttlSeconds.seconds)
    }

    override fun publishMessage(
        sidOrUniqueName: String,
        jsonData: String,
        callback: SuccessListener<SyncStreamJava.Message>
    ) = notifyListener(callback) {
        streams.publishMessage(sidOrUniqueName, json.decodeFromString(jsonData))
            .toSyncStreamJavaMessage()
    }

    override fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>) = notifyListener(callback) {
        streams.remove(sidOrUniqueName)
    }
}
