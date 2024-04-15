//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncStream
import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject

interface Streams {

    /**
     * Create new [SyncStream] object.
     *
     * @param uniqueName        Unique name to assign to new [SyncStream] upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Created [SyncStream].
     * @throws TwilioException  When error occurred while stream creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun create(
        uniqueName: String? = null,
        ttl: Duration = Duration.INFINITE,
    ): SyncStream

    /**
     * Open existing [SyncStream] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName        Unique name to find existing stream or to assign to new stream upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Opened or created [SyncStream].
     * @throws TwilioException  When error occurred while stream opening or creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openOrCreate(
        uniqueName: String,
        ttl: Duration = Duration.INFINITE,
    ): SyncStream

    /**
     * Open existing [SyncStream] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncStream].
     * @return                  Opened [SyncStream].
     * @throws TwilioException  When error occurred while stream opening.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openExisting(
        sidOrUniqueName: String,
    ): SyncStream

    /**
     * Set time to live for [SyncStream] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncStream].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating ttl.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setTtl(sidOrUniqueName: String, ttl: Duration)

    /**
     * Publish a new message to [SyncStream] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncStream].
     * @param data              Contains the payload of the dispatched message. Maximum size in serialized JSON: 4KB.
     * @throws TwilioException  When error occurred while publishing message.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun publishMessage(sidOrUniqueName: String, data: JsonObject): SyncStream.Message

    /**
     * Remove [SyncStream] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncStream].
     * @throws TwilioException  When error occurred while removing the stream.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun remove(sidOrUniqueName: String)
}
