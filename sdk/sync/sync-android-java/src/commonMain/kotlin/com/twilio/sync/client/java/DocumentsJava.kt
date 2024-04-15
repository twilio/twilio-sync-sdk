//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.entities.SyncDocument
import kotlin.time.Duration

interface DocumentsJava {

    /**
     * Create new [SyncDocumentJava] object.
     *
     * @param output Listener that will receive created [SyncDocumentJava] in its onSuccess() callback or
     *               any error in onFailure() callback.
     * @return       [CancellationToken] which allows to cancel network request.
     */
    fun create(output: SuccessListener<SyncDocumentJava>): CancellationToken =
        create(
            uniqueName = null,
            ttlSeconds = Duration.INFINITE.inWholeSeconds,
            listener = null,
            output
        )

    /**
     * Create new [SyncDocumentJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncDocumentJava] upon creation.
     * @param output        Listener that will receive created [SyncDocumentJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, output: SuccessListener<SyncDocumentJava>): CancellationToken =
        create(uniqueName, ttlSeconds = Duration.INFINITE.inWholeSeconds, listener = null, output)

    /**
     * Create new [SyncDocumentJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncDocumentJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive created [SyncDocumentJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, ttlSeconds: Long, output: SuccessListener<SyncDocumentJava>): CancellationToken =
        create(uniqueName, ttlSeconds, listener = null, output)

    /**
     * Create new [SyncDocumentJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncDocumentJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncDocumentJava.Listener] that will receive notifications regarding this document.
     * @param output        Listener that will receive created [SyncDocumentJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncDocumentJava.Listener?,
        output: SuccessListener<SyncDocumentJava>
    ): CancellationToken

    /**
     * Open existing [SyncDocumentJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing document or to assign to new document upon creation.
     * @param output        Listener that will receive opened or created [SyncDocumentJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(uniqueName: String, output: SuccessListener<SyncDocumentJava>): CancellationToken =
        openOrCreate(
            uniqueName = uniqueName,
            ttlSeconds = Duration.INFINITE.inWholeSeconds,
            listener = null,
            output = output
        )

    /**
     * Open existing [SyncDocumentJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing document or to assign to new document upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive opened or created [SyncDocumentJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        output: SuccessListener<SyncDocumentJava>
    ): CancellationToken =
        openOrCreate(
            uniqueName = uniqueName,
            ttlSeconds = ttlSeconds,
            listener = null,
            output = output
        )

    /**
     * Open existing [SyncDocumentJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing document or to assign to new document upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncDocumentJava.Listener] that will receive notifications regarding this document.
     * @param output        Listener that will receive opened or created [SyncDocumentJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncDocumentJava.Listener?,
        output: SuccessListener<SyncDocumentJava>
    ): CancellationToken

    /**
     * Open existing [SyncDocumentJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncDocumentJava].
     * @param output            Listener that will receive opened [SyncDocumentJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(sidOrUniqueName: String, output: SuccessListener<SyncDocumentJava>): CancellationToken =
        openExisting(sidOrUniqueName = sidOrUniqueName, listener = null, output = output)

    /**
     * Open existing [SyncDocumentJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncDocumentJava].
     * @param listener          [SyncDocumentJava.Listener] that will receive notifications regarding this document.
     * @param output            Listener that will receive opened [SyncDocumentJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(
        sidOrUniqueName: String,
        listener: SyncDocumentJava.Listener?,
        output: SuccessListener<SyncDocumentJava>,
    ): CancellationToken

    /**
     * Set time to live for [SyncDocumentJava] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocumentJava].
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setTtl(sidOrUniqueName: String, ttlSeconds: Long, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Set value of the [SyncDocumentJava] as a JSON object without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocument].
     * @param jsonData          New document data as a serialised JSON object.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun updateDocument(sidOrUniqueName: String, jsonData: String, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Set value of the [SyncDocumentJava] as a JSON object without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocumentJava].
     * @param jsonData          New document data as a serialised JSON object.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun updateDocumentWithTtl(
        sidOrUniqueName: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<Unit>
    ): CancellationToken

    /**
     * Mutate value of the [SyncDocumentJava] without opening it using provided Mutator function.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocumentJava].
     * @param mutator           [SyncMutator] which will be applied to document data.
     * @param callback          Async result listener that will receive new value of the [SyncDocumentJava] as a
     *                          serialised JSON object in its onSuccess() callback or any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateDocument(
        sidOrUniqueName: String,
        mutator: SyncMutator,
        callback: SuccessListener<String>
    ): CancellationToken

    /**
     * Mutate value of the [SyncDocumentJava] without opening it using provided Mutator function.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocumentJava].
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param mutator           [SyncMutator] which will be applied to document data.
     * @param callback          Async result listener that will receive new value of the [SyncDocumentJava] as a
     *                          serialised JSON object in its onSuccess() callback or any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateDocumentWithTtl(
        sidOrUniqueName: String,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<String>
    ): CancellationToken

    /**
     * Remove [SyncDocumentJava] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncDocumentJava].
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>): CancellationToken
}
