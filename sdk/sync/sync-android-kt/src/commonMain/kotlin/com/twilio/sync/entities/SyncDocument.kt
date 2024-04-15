//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.client.SyncClient
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
 * SyncDocument is an arbitrary JSON value.
 *
 * You can set, get and modify this value.
 *
 * To obtain an instance of a SyncDocument use [SyncClient.documents]
 */
interface SyncDocument {

    /** An immutable system-assigned identifier of this [SyncDocument]. */
    val sid: EntitySid

    /** An optional unique name for this document, assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Events.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionState

    /** Value of the document as a JSON object. */
    val data: JsonObject

    /** A date when this [SyncDocument] was created. */
    val dateCreated: Instant

    /** A date when this [SyncDocument] was last updated. */
    val dateUpdated: Instant

    /**
     * A date this [SyncDocument] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Instant?

    /** `true` when this [SyncDocument] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncDocument] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean get() = (subscriptionState != SubscriptionState.Established)

    /** Provides scope of [Flow]s objects to get notified about events. */
    val events: Events

    /**
     * Set time to live for this [SyncDocument].
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
     * Set value of the [SyncDocument] as a JSON object.
     *
     * @param data              New document data.
     * @throws TwilioException  When error occurred while updating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setData(data: JsonObject)

    /**
     * Set value of the [SyncDocument] as a JSON object.
     *
     * @param data              New document data.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setDataWithTtl(data: JsonObject, ttl: Duration)

    /**
     * Mutate value of the [SyncDocument] using provided Mutator function.
     *
     * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
     * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
     * on latest document data.
     *
     * Possible use case is to implement distributed counter:
     *
     * ```
     * @Serializable
     * data class Counter(val value: Long = 0) {
     *     operator fun plus(x: Long) = Counter(value + x)
     * }
     *
     * document.mutateData<Counter> { counter -> counter + 1 }
     * ```
     *
     * @param mutator           Mutator which will be applied to document data.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     *                          Once this method finished the [data] property contains updated document data.
     *
     * @throws TwilioException  When error occurred while mutating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateData(mutator: suspend (currentData: JsonObject) -> JsonObject?)

    /**
     * Mutate value of the [SyncDocument] using provided Mutator function.
     *
     * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
     * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
     * on latest document data.
     *
     * Possible use case is to implement distributed counter:
     *
     * ```
     * @Serializable
     * data class Counter(val value: Long = 0) {
     *     operator fun plus(x: Long) = Counter(value + x)
     * }
     *
     * document.mutateDataWithTtl<Counter>(1.hours) { counter -> counter + 1 }
     * ```
     *
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @param mutator           Mutator which will be applied to document data.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     *                          Once this method finished the [data] property contains updated document data.
     *
     * @return                  New value of the [SyncDocument] as a JSON object.
     * @throws TwilioException  When error occurred while mutating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateDataWithTtl(ttl: Duration, mutator: suspend (currentData: JsonObject) -> JsonObject?)

    /**
     * Remove this [SyncDocument].
     *
     * @throws TwilioException  When error occurred while removing the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeDocument()

    /**
     * Close this [SyncDocument].
     *
     * After closing [SyncDocument] stops emitting [events].
     * Call this method to cleanup resources when finish using this [SyncDocument] object.
     */
    fun close()

    interface Events {

        /** Emits when [SyncDocument] has been updated. */
        val onUpdated: SharedFlow<SyncDocument>

        /** Emits when [SyncDocument] has been removed. */
        val onRemoved: SharedFlow<SyncDocument>

        /** Emits when [SyncDocument] subscription state has changed. */
        val onSubscriptionStateChanged: StateFlow<SubscriptionState>
    }
}
