//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncDocument
import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject

interface Documents {

    /**
     * Create new [SyncDocument] object.
     *
     * @param uniqueName        Unique name to assign to new [SyncDocument] upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Created [SyncDocument].
     * @throws TwilioException  When error occurred while document creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun create(
        uniqueName: String? = null,
        ttl: Duration = Duration.INFINITE,
    ): SyncDocument

    /**
     * Open existing [SyncDocument] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName        Unique name to find existing document or to assign to new document upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Opened or created [SyncDocument].
     * @throws TwilioException  When error occurred while document opening or creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openOrCreate(
        uniqueName: String,
        ttl: Duration = Duration.INFINITE,
    ): SyncDocument

    /**
     * Open existing [SyncDocument] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncDocument].
     * @return                  Opened [SyncDocument].
     * @throws TwilioException  When error occurred while document opening.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openExisting(
        sidOrUniqueName: String,
    ): SyncDocument

    /**
     * Set time to live for [SyncDocument] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating ttl.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setTtl(sidOrUniqueName: String, ttl: Duration)

    /**
     * Set value of the [SyncDocument] as a JSON object without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @param data              New document data.
     * @throws TwilioException  When error occurred while updating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun updateDocument(sidOrUniqueName: String, data: JsonObject)

    /**
     * Set value of the [SyncDocument] as a JSON object without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @param data              New document data.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun updateDocumentWithTtl(sidOrUniqueName: String, data: JsonObject, ttl: Duration)

    /**
     * Mutate value of the [SyncDocument] without opening it using provided Mutator function.
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
     * syncClient.documents.mutateDocument<Counter>("mydocument") { counter -> counter + 1 }
     * ```
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @param mutator           Mutator which will be applied to document data.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     * @return                  New value of the [SyncDocument] as a JSON object.
     * @throws TwilioException  When error occurred while mutating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateDocument(
        sidOrUniqueName: String,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): JsonObject

    /**
     * Mutate value of the [SyncDocument] without opening it using provided Mutator function.
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
     * syncClient.documents.mutateDocumentWithTtl<Counter>("mydocument", ttl = 1.hours) { counter -> counter + 1 }
     * ```
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @param mutator           Mutator which will be applied to document data.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     * @return                  New value of the [SyncDocument] as a JSON object.
     * @throws TwilioException  When error occurred while mutating the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateDocumentWithTtl(
        sidOrUniqueName: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): JsonObject

    /**
     * Remove [SyncDocument] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @throws TwilioException  When error occurred while removing the document.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun remove(sidOrUniqueName: String)
}
