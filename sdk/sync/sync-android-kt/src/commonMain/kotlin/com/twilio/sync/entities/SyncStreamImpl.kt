//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.entities

import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.StreamMessagePublishedNotification
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.subscriptions.isFailedWithNotFound
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.drop
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.OpenStreamError
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

internal class SyncStreamImpl(
    private val coroutineScope: CoroutineScope,
    private val subscriptionManager: SubscriptionManager,
    private val repository: SyncRepository,
) : SyncStream {

    private val streamScope = coroutineScope.newChildCoroutineScope()

    private var metadata by Delegates.atomicNotNull<StreamCacheMetadata>()

    private var isSubscribed = false

    override val sid: EntitySid get() = metadata.sid
    override val uniqueName: String? get() = metadata.uniqueName
    override val dateExpires: Instant? get() = metadata.dateExpires

    override var isRemoved: Boolean by atomic(false)

    override val subscriptionState: SubscriptionState get() = events.onSubscriptionStateChanged.value

    override val events = Events()

    override suspend fun setTtl(ttl: Duration) {
        metadata = repository.setStreamTtl(metadata.sid, ttl)
    }

    override suspend fun publishMessage(data: JsonObject): SyncStream.Message {
        val messageSid = repository.publishStreamMessage(metadata.sid, data)

        val message = SyncStream.Message(messageSid, data)
        events.onMessagePublished.emit(message)

        return message
    }

    override suspend fun removeStream() {
        repository.removeStream(metadata.sid)
        isRemoved = true
        /** onRemoved event will be emitted from [handleMetadataChanged] when come event from cache with data == null */
    }

    override fun close() {
        streamScope.cancel()
        unsubscribe()
    }

    suspend fun open(openOptions: OpenOptions) {
        val streamMetadataFlow = repository.getStreamMetadata(openOptions)
        val deferredFirst = CompletableDeferred<StreamCacheMetadata?>()

        streamMetadataFlow
            .drop(count = 1) { deferredFirst.complete(it) }
            // Exception expected to be thrown only on first flow element during fetching data from backend
            .catch { deferredFirst.completeExceptionally(it) }
            .onEach { handleMetadataChanged(it) }
            .launchIn(streamScope)

        val first = runCatching { deferredFirst.await() ?: throw TwilioException(ErrorInfo(OpenStreamError)) }

        metadata = first.getOrElse { t ->
            close()
            throw t.toTwilioException(OpenStreamError)
        }

        events.subscriptionCount
            .onEach { logger.d { "events.subscriptionCount: $it; sid: ${metadata.sid}" } }
            .onEach { if (it > 0) subscribe() else unsubscribe() }
            .launchIn(streamScope)

        repository.getStreamMessages(metadata.sid)
            .map { it.toSyncMessage() }
            .onEach { events.onMessagePublished.emit(it) }
            .launchIn(streamScope)
    }

    private fun handleMetadataChanged(data: StreamCacheMetadata?) {
        logger.d { "handleMetadataChanged: $data" }

        if (data != null) {
            metadata = data
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
            subscriptionManager.subscribe(metadata.sid, "stream")
                .onEach { checkIfStreamRemoved(it) }
                .onEach { events.onSubscriptionStateChanged.value = it }
                .launchIn(streamScope)
    }

    private fun unsubscribe() {
        logger.d { "unsubscribe: isSubscribed: $isSubscribed" }

        if (!isSubscribed) return
        isSubscribed = false

        logger.d { "unsubscribe: ${metadata.sid}" }
        subscriptionManager.unsubscribe(sid)
    }

    private suspend fun checkIfStreamRemoved(subscriptionState: SubscriptionState) {
        if (!subscriptionState.isFailedWithNotFound) return

        logger.d("checkIfDocumentRemoved: Subscription failed, because of stream has been removed")

        isRemoved = true
        repository.removeStreamFromCache(metadata.sid)
        /** onRemoved event will be emitted from [handleMetadataChanged] when come event from cache with data == null */
    }

    private fun StreamMessagePublishedNotification.toSyncMessage() = SyncStream.Message(messageSid, messageData)

    inner class Events : SyncStream.Events {
        override val onMessagePublished = MutableSharedFlow<SyncStream.Message>()

        override val onRemoved =
            MutableSharedFlow<SyncStream>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val onSubscriptionStateChanged = MutableStateFlow<SubscriptionState>(SubscriptionState.Unsubscribed)

        val subscriptionCount = combine(
            onMessagePublished.subscriptionCount,
            onRemoved.subscriptionCount,
            // onSubscriptionsStateChanged.subscriptionCount should not be here - monitoring subscription state
            // doesn't required to be subscribed
        ) { array -> array.sum() }
    }
}
