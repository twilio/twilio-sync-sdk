//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.subscriptions.isFailedWithNotFound
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.drop
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.OpenDocumentError
import com.twilio.util.TwilioException
import com.twilio.util.atomicNotNull
import com.twilio.util.logger
import com.twilio.util.newChildCoroutineScope
import com.twilio.util.toTwilioException
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

internal class SyncDocumentImpl(
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
) : SyncDocument {

    private val documentScope = coroutineScope.newChildCoroutineScope()

    private var metadata by Delegates.atomicNotNull<DocumentCacheMetadata>()

    private var isSubscribed = false

    override val sid: EntitySid get() = metadata.sid
    override val uniqueName: String? get() = metadata.uniqueName
    override val data: JsonObject get() = metadata.documentData
    override val dateCreated: Instant get() = metadata.dateCreated
    override val dateUpdated: Instant get() = metadata.dateUpdated
    override val dateExpires: Instant? get() = metadata.dateExpires

    override var isRemoved by atomic(false)

    override val events = Events()

    override val subscriptionState: SubscriptionState get() = events.onSubscriptionStateChanged.value

    private suspend fun setData(data: JsonObject, ttl: Duration?) {
        metadata = repository.setDocumentData(metadata.sid, data, ttl)
    }

    override suspend fun setData(data: JsonObject) = setData(data, ttl = null)

    override suspend fun setDataWithTtl(data: JsonObject, ttl: Duration) = setData(data, ttl)

    private suspend fun mutateData(ttl: Duration?, mutator: suspend (currentData: JsonObject) -> JsonObject?) {
        metadata = repository.mutateDocumentData(metadata, ttl, mutator)
    }

    override suspend fun mutateData(mutator: suspend (currentData: JsonObject) -> JsonObject?) =
        mutateData(ttl = null, mutator)

    override suspend fun mutateDataWithTtl(ttl: Duration, mutator: suspend (currentData: JsonObject) -> JsonObject?) =
        mutateData(ttl, mutator)

    override suspend fun removeDocument() {
        repository.removeDocument(metadata.sid)
        isRemoved = true
        /** onRemoved event will be emitted from [handleMetadataChanged] when come event from cache with data == null */
    }

    override suspend fun setTtl(ttl: Duration) {
        metadata = repository.setDocumentTtl(metadata.sid, ttl)
            ?: error("Cannot find document in cache to update. This should never happen. " +
                    "Please report this error to https://support.twilio.com/")
    }

    override fun close() {
        documentScope.cancel()
        unsubscribe()
    }

    suspend fun open(openOptions: OpenOptions) {
        val documentMetadataFlow = repository.getDocumentMetadata(openOptions)
        val deferredFirstMetadata = CompletableDeferred<DocumentCacheMetadata?>()

        documentMetadataFlow
            .drop(count = 1) { deferredFirstMetadata.complete(it) }
            // Exception expected to be thrown only on first flow element during fetching data from backend
            .catch { deferredFirstMetadata.completeExceptionally(it) }
            .onEach { handleMetadataChanged(it) }
            .launchIn(documentScope)

        logger.d { "open: await first metadata... ${documentScope.isActive}" }

        val firstMetadata = runCatching {
            deferredFirstMetadata.await() ?: throw TwilioException(ErrorInfo(OpenDocumentError))
        }

        metadata = firstMetadata.getOrElse { t ->
            close()
            throw t.toTwilioException(OpenDocumentError)
        }

        events.subscriptionCount
            .onEach { logger.d { "events.subscriptionCount: $it; sid: ${metadata.sid}" } }
            .onEach { if (it > 0) subscribe() else unsubscribe() }
            .launchIn(documentScope)

        // Emit first onUpdated event in order to any new listener receive it immediately
        events.onUpdated.tryEmit(this)
        logger.d { "opened" }
    }

    private fun handleMetadataChanged(data: DocumentCacheMetadata?) {
        logger.d { "handleMetadataChanged: $data" }

        if (data != null) {
            metadata = data
            val isEmitted = events.onUpdated.tryEmit(this)
            check(isEmitted) { "tryEmit must always return true when overflow strategy is BufferOverflow.DROP_OLDEST" }
        } else {
            isRemoved = true
            val isEmitted = events.onRemoved.tryEmit(this)
            check(isEmitted) { "tryEmit must always return true when overflow strategy is BufferOverflow.DROP_OLDEST" }
        }
    }

    private var subscriptionStateListener: Job? = null

    private fun subscribe() {
        logger.d { "subscribe: ${metadata.sid}; isSubscribed: $isSubscribed" }

        if (isSubscribed) return
        isSubscribed = true

        subscriptionStateListener?.cancel()
        subscriptionStateListener =
            subscriptionManager.subscribe(metadata.sid, "document", metadata.lastEventId)
                .onEach { checkIfDocumentRemoved(it) }
                .onEach { events.onSubscriptionStateChanged.value = it }
                .launchIn(documentScope)
    }

    private fun unsubscribe() {
        logger.d { "unsubscribe: isSubscribed: $isSubscribed" }

        if (!isSubscribed) return
        isSubscribed = false

        logger.d { "unsubscribe: ${metadata.sid}" }
        subscriptionManager.unsubscribe(sid)
    }

    private suspend fun checkIfDocumentRemoved(subscriptionState: SubscriptionState) {
        if (!subscriptionState.isFailedWithNotFound) return

        logger.d("checkIfDocumentRemoved: Subscription failed, because of document has been removed")

        isRemoved = true
        repository.removeDocumentFromCache(metadata.sid)
        /** onRemoved event will be emitted from [handleMetadataChanged] when come event from cache with data == null */
    }

    inner class Events : SyncDocument.Events {
        override val onUpdated =
            MutableSharedFlow<SyncDocument>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val onRemoved =
            MutableSharedFlow<SyncDocument>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val onSubscriptionStateChanged = MutableStateFlow<SubscriptionState>(SubscriptionState.Unsubscribed)

        val subscriptionCount = combine(
            onUpdated.subscriptionCount,
            onRemoved.subscriptionCount,
            // onSubscriptionsStateChanged.subscriptionCount should not be here - monitoring subscription state
            // doesn't required to be subscribed
        ) { array -> array.sum() }
    }
}
