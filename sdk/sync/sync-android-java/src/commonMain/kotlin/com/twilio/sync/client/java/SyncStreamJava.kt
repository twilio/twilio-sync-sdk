//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.SyncStreamJava.Message
import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.Listenable
import com.twilio.sync.client.java.utils.SubscriptionStateJava
import com.twilio.sync.client.java.utils.SuccessListener

/**
 * Interface for Sync Pub-sub messaging primitive.
 *
 * Message Stream is a Sync primitive for real-time pub-sub messaging.
 * Stream Messages are not persisted, they exist only in transit, and will be dropped
 * if (due to congestion or network anomalies) they cannot be delivered promptly.
 *
 * You can publish [Message]s and listen for incoming [Message]s.
 *
 * To obtain an instance of a SyncStreamJava use [SyncClientJava.streams].
 */
interface SyncStreamJava : Listenable<SyncStreamJava.Listener> {

    /** An immutable system-assigned identifier of this [SyncStreamJava]. */
    val sid: String

    /** An optional unique name for this stream, assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Listener.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionStateJava

    /**
     * A date this [SyncStreamJava] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Long?

    /** `true` when this [SyncStreamJava] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncStreamJava] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean

    /**
     * Set time to live for this [SyncStreamJava].
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncStreamJava>): CancellationToken

    /**
     * Publish a new message to this [SyncStreamJava].
     *
     * @param jsonData          Contains the payload of the dispatched message as a serialised JSON object.
     *                          Maximum size: 4KB.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun publishMessage(jsonData: String, callback: SuccessListener<Message>): CancellationToken

    /**
     * Remove this [SyncStreamJava] without opening it.
     *
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun removeStream(callback: SuccessListener<SyncStreamJava>): CancellationToken

    /**
     * Close this [SyncStreamJava].
     *
     * After closing [SyncStreamJava] stops and notifying [Listener]s.
     * Call this method to cleanup resources when finish using this [SyncStreamJava] object.
     */
    fun close()

    /**
     * Method to add listener for this [SyncStreamJava].
     *
     * @param listener the listener to add.
     */
    override fun addListener(listener: Listener)

    /**
     * Method to Method to remove listener from this [SyncStreamJava].
     *
     * @param listener the listener to remove.
     */
    override fun removeListener(listener: Listener)

    /**
     * Method to remove all listeners from this [SyncStreamJava].
     */
    override fun removeAllListeners()

    /** Single message in a [SyncStreamJava]. */
    data class Message(

        /** An immutable system-assigned identifier of this [Message]. */
        val sid: String,

        /** Payload of this [Message] as a serialised JSON object. Maximum size: 4KB. */
        val jsonData: String,
    )

    /** Listener for all operations on a [SyncStreamJava]. */
    interface Listener {

        /**
         * Called when [SyncStreamJava] subscription state has changed.
         *
         * @param stream            The stream which invoked callback.
         * @param subscriptionState New subscription state.
         */
        fun onSubscriptionStateChanged(stream: SyncStreamJava, subscriptionState: SubscriptionStateJava) {}

        /**
         * This callback is invoked when [SyncStreamJava] has successfully published a message.
         *
         * @param stream  The stream which invoked callback.
         * @param message Snapshot of the received [Message].
         */
        fun onMessagePublished(stream: SyncStreamJava, message: Message) {}

        /**
         * This callback is invoked when the [SyncStreamJava] has been removed.
         *
         * @param stream The stream which invoked callback.
         */
        fun onRemoved(stream: SyncStreamJava) {}
    }
}
