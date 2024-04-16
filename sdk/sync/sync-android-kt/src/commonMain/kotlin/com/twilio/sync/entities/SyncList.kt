//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.entities

import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncList.Item
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
 * SyncList is an ordered sequence of [Item] objects as values.
 *
 * You can add, remove and modify values associated with each index in the list.
 *
 * To obtain an instance of a SyncList use [SyncClient.lists]
 */
interface SyncList {

    /** An immutable system-assigned identifier of this [SyncList]. */
    val sid: EntitySid

    /** An optional unique name for this [SyncList], assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Events.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionState

    /** A date when this [SyncList] was created. */
    val dateCreated: Instant

    /** A date when this [SyncList] was last updated. */
    val dateUpdated: Instant

    /**
     * A date this [SyncList] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Instant?

    /** `true` when this [SyncList] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncList] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean get() = (subscriptionState != SubscriptionState.Established)

    /** Provides scope of [Flow]s objects to get notified about events. */
    val events: Events

    /**
     * Set time to live for this [SyncList].
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
     * Retrieve Item from the SyncList.
     *
     * @param itemIndex         Index of the item to retrieve.
     * @param useCache          When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                          and [Events.onItemRemoved] to receive notifications about the item changes.
     *                          When `false` - performs network request to get latest data from backend.
     *
     * @return                  [Item] for this itemIndex or null if no item associated with the index.
     * @throws TwilioException  When error occurred while retrieving the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun getItem(itemIndex: Long, useCache: Boolean = true): Item?

    /**
     * Add Item to the SyncList.
     *
     * @param itemData          Item data to add as a [JsonObject].
     * @return                  [Item] which has been added.
     * @throws TwilioException  When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun addItem(itemData: JsonObject): Item

    /**
     * Add Item to the SyncList.
     *
     * @param itemData          Item data to add as a [JsonObject].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  [Item] which has been added.
     * @throws TwilioException  When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun addItemWithTtl(itemData: JsonObject, ttl: Duration): Item

    /**
     * Set Item in the SyncList.
     *
     * @param itemIndex         Index of the item to set.
     * @param itemData          Item data to set as a [JsonObject].
     * @return                  [Item] which has been set.
     * @throws TwilioException  When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setItem(itemIndex: Long, itemData: JsonObject): Item

    /**
     * Set Item in the SyncList.
     *
     * @param itemIndex         Index of the item to set.
     * @param itemData          Item data to set as a [JsonObject].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  [Item] which has been set.
     * @throws TwilioException  When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setItemWithTtl(itemIndex: Long, itemData: JsonObject, ttl: Duration): Item

    /**
     * Mutate value of the existing [Item] using provided Mutator function.
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
     * list.mutateItem<Counter>(itemIndex = 0) { counter -> counter + 1 }
     * ```
     *
     * @param itemIndex         Index of the item to mutate.
     * @param mutator           Mutator which will be applied to the list item.
     *
     *                          This function will be provided with the
     *                          previous data contents and should return new desired contents or null to abort
     *                          mutate operation. This function is invoked on a background thread.
     *
     * @return                  [Item] which has been set during mutation.
     * @throws TwilioException  When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateItem(itemIndex: Long, mutator: suspend (currentData: JsonObject) -> JsonObject?): Item

    /**
     * Mutate value of the existing [Item] using provided Mutator function.
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
     * list.mutateItemWithTtl<Counter>(itemIndex = 0, ttl = 1.days) { counter -> counter + 1 }
     * ```
     *
     * @param itemIndex         Index of the item to mutate.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @param mutator           Mutator which will be applied to the list item.
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
        itemIndex: Long,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): Item

    /**
     * Remove Item from the SyncList.
     *
     * @param itemIndex         Index of the item to remove.
     * @throws TwilioException  When error occurred while removing the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeItem(itemIndex: Long)

    /**
     * @return a new [SyncIterator] to receive all elements from this [SyncList] using a `for` loop.
     *
     * Example:
     *
     * ```
     *  val list = syncClient.lists.openExisting("MyList")
     *
     *  for (item in list) {
     *    println("${item.index}: ${item.data}")
     *  }
     * ```
     */
    operator fun iterator(): SyncIterator<Item> = queryItems()

    /**
     * Retrieve items from the SyncList.
     *
     * @param startIndex        Index of the first item to retrieve, `null` means start from the first
     *                          item in the SyncList
     *
     * @param includeStartIndex When `true` - result includes the item with the startIndex.
     *                          When `false` - the item with the startIndex is skipped.
     *                          Ignored when startIndex = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @param pageSize          Page size for querying items from backend.
     * @param useCache          When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                          and [Events.onItemRemoved] to receive notifications about the item changes.
     *                          When `false` - performs network request to get latest data from backend.
     *
     * @return                  New [SyncIterator] to sequentially access the elements from this [SyncList].
     */
    fun queryItems(
        startIndex: Long? = null,
        includeStartIndex: Boolean = true,
        queryOrder: QueryOrder = QueryOrder.Ascending,
        pageSize: Int = kDefaultPageSize,
        useCache: Boolean = true,
    ): SyncIterator<Item>

    /**
     * Remove this [SyncList].
     *
     * @throws TwilioException  When error occurred while removing the list.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeList()

    /**
     * Close this [SyncList].
     *
     * After closing [SyncList] stops emitting [events].
     * Call this method to cleanup resources when finish using this [SyncList] object.
     */
    fun close()

    interface Events {

        /** Emits when [SyncList] has been removed. */
        val onRemoved: SharedFlow<SyncList>

        /** Emits when [SyncList] subscription state has changed. */
        val onSubscriptionStateChanged: StateFlow<SubscriptionState>

        /** Emits when [Item] has been added. */
        val onItemAdded: SharedFlow<Item>

        /** Emits when [Item] has been updated. */
        val onItemUpdated: SharedFlow<Item>

        /** Emits when [Item] has been removed. */
        val onItemRemoved: SharedFlow<Item>
    }

    /** Represents a value associated with each index in [SyncList]. */
    data class Item(

        /** Index of the [Item]. */
        val index: Long,

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