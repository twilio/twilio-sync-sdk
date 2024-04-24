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
import com.twilio.sync.client.java.utils.wrap
import com.twilio.sync.entities.SyncDocument
import com.twilio.util.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class SyncDocumentJavaImpl(
    private val coroutineScope: CoroutineScope,
    private val listenersDispatcher: CoroutineDispatcher,
    private val syncDocument: SyncDocument,
) : SyncDocumentJava, ListenableImpl<SyncDocumentJava.Listener>(coroutineScope, listenersDispatcher) {

    override val sid: String
        get() = syncDocument.sid
    override val uniqueName: String?
        get() = syncDocument.uniqueName
    override val subscriptionState: SubscriptionStateJava
        get() = syncDocument.subscriptionState.toSubscriptionStateJava()
    override val jsonData: String
        get() = syncDocument.data.toString()
    override val dateCreated: Long
        get() = syncDocument.dateCreated.toEpochMilliseconds()
    override val dateUpdated: Long
        get() = syncDocument.dateUpdated.toEpochMilliseconds()
    override val dateExpires: Long?
        get() = syncDocument.dateExpires?.toEpochMilliseconds()
    override val isRemoved: Boolean
        get() = syncDocument.isRemoved
    override val isFromCache: Boolean
        get() = syncDocument.isFromCache

    private val notifyListener = ListenerNotifier(coroutineScope, listenersDispatcher)

    init {
        with(syncDocument.events) {
            onSubscriptionStateChanged.connectListeners {
                onSubscriptionStateChanged(this@SyncDocumentJavaImpl, it.toSubscriptionStateJava())
            }
            onUpdated.connectListeners { onUpdated(this@SyncDocumentJavaImpl) }
            onRemoved.connectListeners { onRemoved(this@SyncDocumentJavaImpl) }
        }
    }

    override fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncDocumentJava>) = notifyListener(callback) {
        syncDocument.setTtl(ttlSeconds.seconds)
        return@notifyListener this
    }

    override fun setData(jsonData: String, callback: SuccessListener<SyncDocumentJava>) = notifyListener(callback) {
        syncDocument.setData(json.decodeFromString(jsonData))
        return@notifyListener this

    }

    override fun setDataWithTtl(jsonData: String, ttlSeconds: Long, callback: SuccessListener<SyncDocumentJava>) =
        notifyListener(callback) {
            syncDocument.setDataWithTtl(json.decodeFromString(jsonData), ttlSeconds.seconds)
            return@notifyListener this
        }

    override fun mutateData(mutator: SyncMutator, callback: SuccessListener<SyncDocumentJava>) =
        notifyListener(callback) {
            syncDocument.mutateData(mutator.wrap())
            return@notifyListener this
        }

    override fun mutateDataWithTtl(
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncDocumentJava>
    ) = notifyListener(callback) {
        syncDocument.mutateDataWithTtl(ttlSeconds.seconds, mutator.wrap())
        return@notifyListener this
    }

    override fun removeDocument(callback: SuccessListener<SyncDocumentJava>) = notifyListener(callback) {
        syncDocument.removeDocument()
        return@notifyListener this
    }

    override fun close() = syncDocument.close()
}
