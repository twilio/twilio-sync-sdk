//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.Listenable
import com.twilio.sync.client.java.utils.SubscriptionStateJava
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator

/**
 * SyncDocument is an arbitrary JSON value.
 *
 * You can set, get and modify this value.
 *
 * To obtain an instance of a SyncDocumentJava use [SyncClientJava.documents]
 */
interface SyncDocumentJava: Listenable<SyncDocumentJava.Listener> {

    /** An immutable system-assigned identifier of this [SyncDocumentJava]. */
    val sid: String

    /** An optional unique name for this document, assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Listener.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionStateJava

    /** Value of the document as a serialized JSON object. */
    val jsonData: String

    /** A date when this [SyncDocumentJava] was created. */
    val dateCreated: Long

    /** A date when this [SyncDocumentJava] was last updated. */
    val dateUpdated: Long

    /**
     * A date this [SyncDocumentJava] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Long?

    /** `true` when this [SyncDocumentJava] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncDocumentJava] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean

    /**
     * Set time to live for this [SyncDocumentJava].
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
    fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncDocumentJava>): CancellationToken

    /**
     * Set value of the [SyncDocumentJava] as a serialized JSON object.
     *
     * @param jsonData          New document data as a serialized JSON object.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setData(jsonData: String, callback: SuccessListener<SyncDocumentJava>): CancellationToken

    /**
     * Set value of the [SyncDocumentJava] as a serialized JSON object.
     *
     * @param jsonData          New document data as a serialized JSON object.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setDataWithTtl(
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncDocumentJava>
    ): CancellationToken

    /**
     * Mutate value of the [SyncDocumentJava] using provided Mutator function.
     * Once this method finished the [jsonData] property contains updated document data.
     *
     * @param mutator           [SyncMutator] which will be applied to document data.
     * @param callback          Async result listener that will receive new value of the [SyncDocumentJava] as
     *                          a serialised JSON object in its onSuccess() callback or any error
     *                          in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateData(mutator: SyncMutator, callback: SuccessListener<SyncDocumentJava>): CancellationToken

    /**
     * Mutate value of the [SyncDocumentJava] using provided Mutator function.
     * Once this method finished the [jsonData] property contains updated document data.
     *
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param mutator           [SyncMutator] which will be applied to document data.
     * @param callback          Async result listener that will receive new value of the [SyncDocumentJava] as
     *                          a serialised JSON object in its onSuccess() callback or any error
     *                          in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateDataWithTtl(
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncDocumentJava>
    ): CancellationToken

    /**
     * Remove this [SyncDocumentJava].
     *
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun removeDocument(callback: SuccessListener<SyncDocumentJava>): CancellationToken

    /**
     * Close this [SyncDocumentJava].
     *
     * After closing [SyncDocumentJava] stops notifying [Listener]s.
     * Call this method to cleanup resources when finish using this [SyncDocumentJava] object.
     */
    fun close()

    /**
     * Method to add listener for this [SyncDocumentJava].
     *
     * @param listener the listener to add.
     */
    override fun addListener(listener: Listener)

    /**
     * Method to Method to remove listener from this [SyncDocumentJava].
     *
     * @param listener the listener to remove.
     */
    override fun removeListener(listener: Listener)

    /**
     * Method to remove all listeners from this [SyncDocumentJava].
     */
    override fun removeAllListeners()

    /** Listener for all operations on a [SyncDocumentJava]. */
    interface Listener {

        /**
         * Called when [SyncDocumentJava] has been updated.
         * Use document's properties to get updated values.
         *
         * @param document The document which invoked callback.
         */
        fun onUpdated(document: SyncDocumentJava) {}

        /**
         * This callback is invoked when the [SyncDocumentJava] has been removed.
         *
         * @param document The document which invoked callback.
         */
        fun onRemoved(document: SyncDocumentJava) {}

        /**
         * Called when [SyncDocumentJava] subscription state has changed.
         *
         * @param document          The document which invoked callback.
         * @param subscriptionState New subscription state.
         */
        fun onSubscriptionStateChanged(document: SyncDocumentJava, subscriptionState: SubscriptionStateJava) {}
    }
}
