//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.entities

import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.subscriptions.isFailedWithNotFound
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.CollectionType.List
import com.twilio.sync.utils.CollectionType.Map
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.SyncIteratorImpl
import com.twilio.sync.utils.drop
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.OpenCollectionError
import com.twilio.util.TwilioException
import com.twilio.util.atomicNotNull
import com.twilio.util.logger
import com.twilio.util.newChildCoroutineScope
import com.twilio.util.toTwilioException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlin.properties.Delegates
import kotlin.time.Duration

internal class CollectionDelegate<CollectionImpl, ItemImpl>(
    private val owner: CollectionImpl,
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
    private val collectionType: CollectionType,
    private val transformItem: (CollectionItemData) -> ItemImpl,
) {

    private val collectionScope = coroutineScope.newChildCoroutineScope()

    var metadata by Delegates.atomicNotNull<CollectionMetadata>()
        private set

    private var isSubscribed = false

    var isRemoved by atomic(false)
        private set

    val events = Events()

    val subscriptionState: SubscriptionState get() = events.onSubscriptionStateChanged.value

    suspend fun removeCollection() {
        repository.removeCollection(collectionType, metadata.sid)
        isRemoved = true
        /** onRemoved event will be emitted from [handleMetadataChanged] when come event from cache with data == null */
    }

    suspend fun setTtl(ttl: Duration) {
        metadata = repository.setCollectionTtl(collectionType, metadata.sid, ttl)
    }

    suspend fun getItem(itemId: CollectionItemId, useCache: Boolean): ItemImpl? {
        val collectionItem = repository.getCollectionItemData(metadata, itemId, useCache)
        return collectionItem?.let(transformItem)
    }

    suspend fun addItem(itemData: JsonObject, ttl: Duration?): ItemImpl {
        val collectionItem = repository.addCollectionItemData(collectionType, metadata.sid, itemId = null, itemData, ttl)
        return transformItem(collectionItem)
    }

    suspend fun updateItem(itemId: CollectionItemId, itemData: JsonObject, ttl: Duration?): ItemImpl {
        val collectionItem =  repository.updateCollectionItemData(metadata.sid, itemId, itemData, ttl)
        return transformItem(collectionItem)
    }

    suspend fun setItem(itemId: CollectionItemId, itemData: JsonObject, ttl: Duration?): ItemImpl {
        val collectionItem = repository.setCollectionItemData(metadata.sid, itemId, itemData, ttl)
        return transformItem(collectionItem)
    }

    suspend fun mutateOrAddItem(
        itemId: CollectionItemId,
        ttl: Duration?,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ): ItemImpl {
        val collectionItem = repository.mutateOrAddCollectionItemData(metadata.sid, itemId, ttl, mutator)
        return transformItem(collectionItem)
    }

    suspend fun mutateItem(
        itemId: CollectionItemId,
        ttl: Duration?,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): ItemImpl {
        val collectionItem = repository.mutateCollectionItemData(metadata.sid, itemId, ttl, mutator)
        return transformItem(collectionItem)
    }

    suspend fun removeItem(itemId: CollectionItemId) {
        repository.removeCollectionItem(metadata, itemId)
    }

    fun queryItems(
        startId: CollectionItemId?,
        includeStartId: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIterator<ItemImpl> {
        require(pageSize > 0)

        val channel =
            repository.getCollectionItemsData(
                collectionType, metadata.sid, startId, includeStartId, queryOrder, pageSize, useCache)

        return SyncIteratorImpl(channel, transformItem)
    }

    fun close() {
        collectionScope.cancel()
        unsubscribe()
    }

    suspend fun open(openOptions: OpenOptions) {
        val metadataFlow = repository.getCollectionMetadata(collectionType, openOptions)
        val deferredFirstMetadata = CompletableDeferred<CollectionMetadata?>()

        metadataFlow
            .drop(count = 1) { deferredFirstMetadata.complete(it) }
            // Exception expected to be thrown only on first flow element during fetching data from backend
            .catch { deferredFirstMetadata.completeExceptionally(it) }
            .onEach { handleMetadataChanged(it) }
            .launchIn(collectionScope)

        logger.d { "open: await first metadata... ${collectionScope.isActive}" }

        val firstMetadata = runCatching {
            deferredFirstMetadata.await() ?: throw TwilioException(ErrorInfo(OpenCollectionError))
        }

        metadata = firstMetadata.getOrElse { t ->
            close()
            throw t.toTwilioException(OpenCollectionError)
        }

        events.subscriptionCount
            .onEach { logger.d { "events.subscriptionCount: $it; sid: ${metadata.sid}" } }
            .onEach { if (it > 0) subscribe() else unsubscribe() }
            .launchIn(collectionScope)

        logger.d { "opened" }
    }

    private fun handleMetadataChanged(data: CollectionMetadata?) {
        logger.d { "handleMetadataChanged: $data" }

        if (data != null) {
            metadata = data
        } else {
            isRemoved = true
            val isEmitted = events.onRemoved.tryEmit(owner)
            check(isEmitted) { "tryEmit must always return true when overflow strategy is BufferOverflow.DROP_OLDEST" }
        }
    }

    private var subscriptionScope = collectionScope.newChildCoroutineScope()

    private fun subscribe() {
        logger.d { "subscribe: ${metadata.sid}; isSubscribed: $isSubscribed" }

        if (isSubscribed) return
        isSubscribed = true

        subscriptionScope.cancel()
        subscriptionScope = collectionScope.newChildCoroutineScope()

        repository.onCollectionItemAdded(metadata.sid)
            .onEach { events.onItemAdded.emit(transformItem(it)) }
            .launchIn(subscriptionScope)

        repository.onCollectionItemUpdated(metadata.sid)
            .onEach { events.onItemUpdated.emit(transformItem(it)) }
            .launchIn(subscriptionScope)

        repository.onCollectionItemRemoved(metadata.sid)
            .onEach { events.onItemRemoved.emit(transformItem(it)) }
            .launchIn(subscriptionScope)

        val entityType = when (collectionType) {
            List -> "list"
            Map -> "map"
        }

        subscriptionManager.subscribe(metadata.sid, entityType, metadata.lastEventId)
            .onEach { checkIfCollectionRemoved(it) }
            .onEach { events.onSubscriptionStateChanged.value = it }
            .launchIn(subscriptionScope)
    }

    private fun unsubscribe() {
        logger.d { "unsubscribe: isSubscribed: $isSubscribed" }

        if (!isSubscribed) return
        isSubscribed = false

        logger.d { "unsubscribe: ${metadata.sid}" }
        subscriptionManager.unsubscribe(metadata.sid)
    }

    private suspend fun checkIfCollectionRemoved(subscriptionState: SubscriptionState) {
        if (!subscriptionState.isFailedWithNotFound) return

        logger.d("checkIfCollectionRemoved: Subscription failed, because of collection $collectionType has been removed")

        isRemoved = true
        repository.removeCollectionFromCache(collectionType, metadata.sid)
        /** onRemoved event will be emitted from [handleMetadataChanged] when come event from cache with data == null */
    }

    inner class Events {
        val onRemoved = MutableSharedFlow<CollectionImpl>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        val onSubscriptionStateChanged = MutableStateFlow<SubscriptionState>(SubscriptionState.Unsubscribed)

        val onItemAdded = MutableSharedFlow<ItemImpl>()

        val onItemUpdated = MutableSharedFlow<ItemImpl>()

        val onItemRemoved = MutableSharedFlow<ItemImpl>()

        val subscriptionCount = combine(
            onRemoved.subscriptionCount,
            onItemAdded.subscriptionCount,
            onItemUpdated.subscriptionCount,
            onItemRemoved.subscriptionCount,
            // onSubscriptionsStateChanged.subscriptionCount should not be here - monitoring subscription state
            // doesn't required to be subscribed
        ) { array -> array.sum() }
    }
}
