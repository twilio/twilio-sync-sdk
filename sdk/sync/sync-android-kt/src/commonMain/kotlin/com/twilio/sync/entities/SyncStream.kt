//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncStream.Message
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.utils.EntitySid
import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

/**
 * Interface for Sync Pub-sub messaging primitive.
 *
 * Message Stream is a Sync primitive for real-time pub-sub messaging.
 * Stream Messages are not persisted, they exist only in transit, and will be dropped
 * if (due to congestion or network anomalies) they cannot be delivered promptly.
 *
 * You can publish [Message]s and listen for incoming [Message]s.
 *
 * To obtain an instance of a SyncStream use [SyncClient.streams].
 */
interface SyncStream {

    /** An immutable system-assigned identifier of this [SyncStream]. */
    val sid: EntitySid

    /** An optional unique name for this stream, assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Events.onSubscriptionStateChanged
     * @see Listener.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionState

    /**
     * A date this [SyncStream] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Instant?

    /** `true` when this [SyncStream] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncStream] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean get() = (subscriptionState != SubscriptionState.Established)

    /** Provides scope of [Flow]s objects to get notified about events. */
    val events: Events

    /**
     * Set time to live for this [SyncStream].
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating ttl.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setTtl(ttl: Duration)

    /**
     * Publish a new message to this [SyncStream].
     *
     * @param data              Contains the payload of the dispatched message. Maximum size in serialized JSON: 4KB.
     * @throws TwilioException  When error occurred while publishing message.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun publishMessage(data: JsonObject): Message

    /**
     * Remove this [SyncStream].
     *
     * @throws TwilioException  When error occurred while removing the stream.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeStream()

    /**
     * Close this [SyncStream].
     *
     * After closing [SyncStream] stops emitting [events].
     * Call this method to cleanup resources when finish using this [SyncStream] object.
     */
    fun close()

    /** Single message in a [SyncStream]. */
    data class Message(

        /** An immutable system-assigned identifier of this [Message]. */
        val sid: String,

        /** Payload of this [Message]. Maximum size in serialized JSON: 4KB. */
        val data: JsonObject,
    )

    interface Events {

        /** Emits when [SyncStream] subscription state has changed. */
        val onSubscriptionStateChanged: StateFlow<SubscriptionState>

        /** Emits when [SyncStream] has successfully published a message. */
        val onMessagePublished: SharedFlow<Message>

        /** Emits when the [SyncStream] has been removed. */
        val onRemoved: SharedFlow<SyncStream>
    }
}
