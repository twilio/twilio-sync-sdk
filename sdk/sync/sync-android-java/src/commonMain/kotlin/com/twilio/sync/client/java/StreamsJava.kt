//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.entities.SyncStream
import kotlin.time.Duration

interface StreamsJava {

    /**
     * Create new [SyncStreamJava] object.
     *
     * @param output Listener that will receive created [SyncStreamJava] in its onSuccess() callback or
     *               any error in onFailure() callback.
     * @return       [CancellationToken] which allows to cancel network request.
     */
    fun create(output: SuccessListener<SyncStreamJava>): CancellationToken =
        create(uniqueName = null, ttlSeconds = Duration.INFINITE.inWholeSeconds, listener = null, output)

    /**
     * Create new [SyncStreamJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncStreamJava] upon creation.
     * @param output        Listener that will receive created [SyncStreamJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, output: SuccessListener<SyncStreamJava>): CancellationToken =
        create(uniqueName, ttlSeconds = Duration.INFINITE.inWholeSeconds, listener = null, output)

    /**
     * Create new [SyncStreamJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncStreamJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive created [SyncStreamJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, ttlSeconds: Long, output: SuccessListener<SyncStreamJava>): CancellationToken =
        create(uniqueName, ttlSeconds, listener = null, output)

    /**
     * Create new [SyncStreamJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncStreamJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncStreamJava.Listener] that will receive notifications regarding this stream.
     * @param output        Listener that will receive created [SyncStreamJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncStreamJava.Listener?,
        output: SuccessListener<SyncStreamJava>
    ): CancellationToken

    /**
     * Open existing [SyncStreamJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing stream or to assign to new stream upon creation.
     * @param output        Listener that will receive opened or created [SyncStreamJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(uniqueName: String, output: SuccessListener<SyncStreamJava>): CancellationToken =
        openOrCreate(uniqueName, ttlSeconds = Duration.INFINITE.inWholeSeconds, listener = null, output)

    /**
     * Open existing [SyncStreamJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing stream or to assign to new stream upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive opened or created [SyncStreamJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(uniqueName: String, ttlSeconds: Long, output: SuccessListener<SyncStreamJava>): CancellationToken =
        openOrCreate(uniqueName, ttlSeconds, listener = null, output)

    /**
     * Open existing [SyncStreamJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing stream or to assign to new stream upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncStreamJava.Listener] that will receive notifications regarding this stream.
     * @param output        Listener that will receive opened or created [SyncStreamJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncStreamJava.Listener?,
        output: SuccessListener<SyncStreamJava>
    ): CancellationToken

    /**
     * Open existing [SyncStreamJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncStreamJava].
     * @param output            Listener that will receive opened [SyncStreamJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(sidOrUniqueName: String, output: SuccessListener<SyncStreamJava>): CancellationToken =
        openExisting(sidOrUniqueName, listener = null, output)

    /**
     * Open existing [SyncStreamJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncStreamJava].
     * @param listener          [SyncStreamJava.Listener] that will receive notifications regarding this stream.
     * @param output            Listener that will receive opened [SyncStreamJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(sidOrUniqueName: String,
        listener: SyncStreamJava.Listener? = null,
        output: SuccessListener<SyncStreamJava>,
    ): CancellationToken

    /**
     * Set time to live for [SyncStreamJava] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncStreamJava].
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setTtl(sidOrUniqueName: String, ttlSeconds: Long, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Publish a new message to [SyncStreamJava] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncStreamJava].
     * @param jsonData          Contains the payload of the dispatched message. Maximum size in serialized JSON: 4KB.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun publishMessage(
        sidOrUniqueName: String,
        jsonData: String,
        callback: SuccessListener<SyncStreamJava.Message>
    ): CancellationToken

    /**
     * Remove [SyncStreamJava] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncStream].
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>): CancellationToken
}
