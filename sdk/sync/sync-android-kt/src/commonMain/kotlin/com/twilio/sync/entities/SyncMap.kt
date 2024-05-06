//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.entities

import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncMap.Item
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.kDefaultPageSize
import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

/**
 * SyncMap is a key-value store with Strings as keys and [Item] objects as values.
 *
 * You can add, remove and modify values associated with the keys.
 *
 * To obtain an instance of a SyncMap use [SyncClient.maps]
 */
interface SyncMap {

    /** An immutable system-assigned identifier of this [SyncMap]. */
    val sid: EntitySid

    /** An optional unique name for this [SyncMap], assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Events.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionState

    /** A date when this [SyncMap] was created. */
    val dateCreated: Instant

    /** A date when this [SyncMap] was last updated. */
    val dateUpdated: Instant

    /**
     * A date this [SyncMap] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Instant?

    /** `true` when this [SyncMap] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncMap] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean get() = (subscriptionState != SubscriptionState.Established)

    /** Provides scope of [Flow]s objects to get notified about events. */
    val events: Events

    /**
     * Set time to live for this [SyncMap].
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
     * Retrieve Item from the SyncMap.
     *
     * @param itemKey           Key of the item to retrieve.
     * @param useCache          When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                          and [Events.onItemRemoved] to receive notifications about the item changes.
     *                          When `false` - performs network request to get latest data from backend.
     *
     * @return                  [Item] for this itemKey or null if no item associated with the key.
     * @throws TwilioException  When error occurred while retrieving the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun getItem(itemKey: String, useCache: Boolean = true): Item?

    /**
     * Set Item in the SyncMap.
     *
     * @param itemKey           Key of the item to set.
     * @param itemData          Item data to set as a [JsonObject].
     * @return                  [Item] which has been set.
     * @throws TwilioException  When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setItem(itemKey: String, itemData: JsonObject): Item

    /**
     * Set Item in the SyncMap.
     *
     * @param itemKey           Key of the item to set.
     * @param itemData          Item data to set as a [JsonObject].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  [Item] which has been set.
     * @throws TwilioException  When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setItemWithTtl(itemKey: String, itemData: JsonObject, ttl: Duration): Item

    /**
     * Mutate value of the [Item] using provided Mutator function.
     *
     * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
     * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
     * on latest item data.
     *
     * Possible use case is to implement distributed counter:
     *
     * ```
     * @Serializable
     * data class Counter(val value: Long = 0) {
     *     operator fun plus(x: Long) = Counter(value + x)
     * }
     *
     * map.mutateItem<Counter>("counter") { counter -> counter?.let { it + 1 } ?: Counter(1) }
     * ```
     *
     * @param itemKey           Key of the item to mutate.
     * @param mutator           Mutator which will be applied to the map item.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     * @return                  [Item] which has been set during mutation.
     * @throws TwilioException  When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateItem(itemKey: String, mutator: suspend (currentData: JsonObject?) -> JsonObject?): Item

    /**
     * Mutate value of the [Item] using provided Mutator function and set time to live for the [Item].
     *
     * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
     * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
     * on latest item data.
     *
     * Possible use case is to implement distributed counter:
     *
     * ```
     * @Serializable
     * data class Counter(val value: Long = 0) {
     *     operator fun plus(x: Long) = Counter(value + x)
     * }
     *
     * map.mutateItemWithTtl<Counter>("counter", ttl = 1.days) { counter -> counter?.let { it + 1 } ?: Counter(1) }
     * ```
     *
     * @param itemKey           Key of the item to mutate.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @param mutator           Mutator which will be applied to the map item.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     * @return                  [Item] which has been set during mutation.
     * @throws TwilioException  When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateItemWithTtl(
        itemKey: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ): Item

    /**
     * Remove Item from the SyncMap.
     *
     * @param itemKey           Key of the item to remove.
     * @throws TwilioException  When error occurred while removing the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeItem(itemKey: String)

    /**
     * @return a new [SyncIterator] to receive all elements from this [SyncMap] using a `for` loop.
     *
     * Example:
     *
     * ```
     *  val map = syncClient.maps.openExisting("MyMap")
     *
     *  for (item in map) {
     *    println("${item.key}: ${item.data}")
     *  }
     * ```
     */
    operator fun iterator(): SyncIterator<Item> = queryItems()

    /**
     * Retrieve items from the SyncMap.
     *
     * @param startKey          Key of the first item to retrieve, `null` means start from the first item in the SyncMap.
     * @param includeStartKey   When `true` - result includes the item with the startKey (if exists in the [SyncMap]).
     *                          When `false` - the item with the startKey is skipped.
     *                          Ignored when startKey = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @param pageSize          Page size for querying items from backend.
     * @param useCache          When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                          and [Events.onItemRemoved] to receive notifications about the item changes.
     *                          When `false` - performs network request to get latest data from backend.
     *
     * @return                  New [SyncIterator] to sequentially access the elements from this [SyncMap].
     */
    fun queryItems(
        startKey: String? = null,
        includeStartKey: Boolean = true,
        queryOrder: QueryOrder = QueryOrder.Ascending,
        pageSize: Int = kDefaultPageSize,
        useCache: Boolean = true,
    ): SyncIterator<Item>

    /**
     * Remove this [SyncMap].
     *
     * @throws TwilioException  When error occurred while removing the map.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeMap()

    /**
     * Close this [SyncMap].
     *
     * After closing [SyncMap] stops emitting [events].
     * Call this method to cleanup resources when finish using this [SyncMap] object.
     */
    fun close()

    interface Events {

        /** Emits when [SyncMap] has been removed. */
        val onRemoved: SharedFlow<SyncMap>

        /** Emits when [SyncMap] subscription state has changed. */
        val onSubscriptionStateChanged: StateFlow<SubscriptionState>

        /** Emits when [Item] has been added. */
        val onItemAdded: SharedFlow<Item>

        /** Emits when [Item] has been updated. */
        val onItemUpdated: SharedFlow<Item>

        /** Emits when [Item] has been removed. */
        val onItemRemoved: SharedFlow<Item>
    }

    /** Represents a value associated with each key in [SyncMap]. */
    data class Item(

        /** Key of the [Item]. */
        val key: String,

        /** Value of the [Item] as a JSON object. */
        val data: JsonObject,

        /** A date when this [Item] was created. */
        val dateCreated: Instant,

        /** A date when this [Item] was last updated. */
        val dateUpdated: Instant,

        /**
         * A date this [Item] will expire, `null` means will not expire.
         *
         * @see setItemWithTtl
         */
        val dateExpires: Instant?,
    )
}
