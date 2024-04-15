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
import com.twilio.sync.client.java.utils.toSubscriptionStateJava
import com.twilio.sync.client.java.utils.toSyncStreamJavaMessage
import com.twilio.sync.entities.SyncStream
import com.twilio.util.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class SyncStreamJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val syncStream: SyncStream,
) : SyncStreamJava, ListenableImpl<SyncStreamJava.Listener>(coroutineScope, listenersDispatcher) {

    override val sid: String
        get() = syncStream.sid
    override val uniqueName: String?
        get() = syncStream.uniqueName
    override val subscriptionState: SubscriptionStateJava
        get() = syncStream.subscriptionState.toSubscriptionStateJava()
    override val dateExpires: Long?
        get() = syncStream.dateExpires?.toEpochMilliseconds()
    override val isRemoved: Boolean
        get() = syncStream.isRemoved
    override val isFromCache: Boolean
        get() = syncStream.isFromCache

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    init {
        with(syncStream.events) {
            onSubscriptionStateChanged.connectListeners {
                onSubscriptionStateChanged(this@SyncStreamJavaImpl, it.toSubscriptionStateJava())
            }
            onMessagePublished.connectListeners {
                onMessagePublished(this@SyncStreamJavaImpl, it.toSyncStreamJavaMessage())
            }
            onRemoved.connectListeners { onRemoved(this@SyncStreamJavaImpl) }
        }
    }

    override fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncStreamJava>) = notifyListener(callback) {
        syncStream.setTtl(ttlSeconds.seconds)
        return@notifyListener this
    }

    override fun publishMessage(jsonData: String, callback: SuccessListener<SyncStreamJava.Message>) =
        notifyListener(callback) {
            syncStream
                .publishMessage(json.decodeFromString(jsonData))
                .toSyncStreamJavaMessage()
        }

    override fun removeStream(callback: SuccessListener<SyncStreamJava>) = notifyListener(callback) {
        syncStream.removeStream()
        return@notifyListener this
    }

    override fun close() = syncStream.close()
}
