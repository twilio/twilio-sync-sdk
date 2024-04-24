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
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.kDefaultPageSize

/**
 * SyncListJava is an ordered sequence of [Item] objects as values.
 *
 * You can add, remove and modify values associated with the keys.
 *
 * To obtain an instance of a SyncListJava use [SyncListJava.lists]
 */
interface SyncListJava: Listenable<SyncListJava.Listener> {

    /** An immutable system-assigned identifier of this [SyncListJava]. */
    val sid: String

    /** An optional unique name for this list, assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Listener.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionStateJava

    /** A date when this [SyncListJava] was created. */
    val dateCreated: Long

    /** A date when this [SyncListJava] was last updated. */
    val dateUpdated: Long

    /**
     * A date this [SyncListJava] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Long?

    /** `true` when this [SyncListJava] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncListJava] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean

    /**
     * Set time to live for this [SyncListJava].
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
    fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncListJava>): CancellationToken

    /**
     * Retrieve Item from the [SyncListJava].
     *
     * @param itemIndex         Index of the item to retrieve.
     * @param callback          Async result listener that will receive a value of the [Item] in
     *                          its onSuccess() callback (null if item doesn't exist) or any error in
     *                          onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun getItem(itemIndex: Long, callback: SuccessListener<Item?>): CancellationToken

    /**
     * Add Item in the [SyncListJava].
     *
     * @param jsonData          Item data to set as a serialized JSON object.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun addItem(jsonData: String, callback: SuccessListener<Item>): CancellationToken

    /**
     * Add Item in the [SyncListJava].
     *
     * @param jsonData          Item data to set as a serialized JSON object.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun addItemWithTtl(
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<Item>
    ): CancellationToken

    /**
     * Set Item in the [SyncListJava].
     *
     * @param itemIndex         Index of the item to set.
     * @param jsonData          Item data to set as a serialized JSON object.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setItem(itemIndex: Long, jsonData: String, callback: SuccessListener<Item>): CancellationToken

    /**
     * Set Item in the [SyncListJava].
     *
     * @param itemIndex         Index of the item to set.
     * @param jsonData          Item data to set as a serialized JSON object.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setItemWithTtl(
        itemIndex: Long,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<Item>
    ): CancellationToken

    /**
     * Mutate value of the [Item] using provided Mutator function.
     *
     * @param itemIndex         Index of the item to mutate.
     * @param mutator           [SyncMutator] which will be applied to the list item.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateItem(itemIndex: Long, mutator: SyncMutator, callback: SuccessListener<Item>): CancellationToken

    /**
     * Mutate value of the [Item] using provided Mutator function.
     *
     * @param itemIndex         Index of the item to mutate.
     * @param mutator           [SyncMutator] which will be applied to the list item.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateItemWithTtl(
        itemIndex: Long,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<Item>,
    ): CancellationToken

    /**
     * Remove Item from the [SyncListJava].
     *
     * @param itemIndex         Index of the item to remove.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun removeItem(itemIndex: Long, callback: SuccessListener<SyncListJava>): CancellationToken

    /**
     * Retrieve all items from the SyncListJava.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryItems(): SyncIteratorJava<Item> =
        queryItems(startIndex = null, includeStartIndex = true, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncListJava].
     *
     * @param startIndex        Index of the first item to retrieve.
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryItems(
        startIndex: Long,
    ): SyncIteratorJava<Item> = queryItems(startIndex, includeStartIndex = true)

    /**
     * Retrieve items from the [SyncListJava].
     *
     * @param startIndex        Index of the first item to retrieve.
     * @param includeStartIndex When `true` - result includes the item with the startIndex
     *                          When `false` - the item with the startIndex is skipped.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryItems(
        startIndex: Long,
        includeStartIndex: Boolean,
    ): SyncIteratorJava<Item> = queryItems(startIndex, includeStartIndex, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncListJava].
     *
     * @param startIndex        Index of the first item to retrieve, `null` means start from the
     *                          first item in the [SyncListJava].
     * @param includeStartIndex When `true` - result includes the item with the startIndex.
     *                          When `false` - the item with the startIndex is skipped.
     *                          Ignored when startIndex = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryItems(
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
    ): SyncIteratorJava<Item> = queryItems(startIndex, includeStartIndex, queryOrder, pageSize = kDefaultPageSize)

    /**
     * Retrieve items from the [SyncListJava].
     *
     * @param startIndex        Index of the first item to retrieve, `null` means start from the
     *                          first item in the [SyncListJava].
     * @param includeStartIndex When `true` - result includes the item with the startIndex.
     *                          When `false` - the item with the startIndex is skipped.
     *                          Ignored when startIndex = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @param pageSize          Page size for querying items from backend.
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryItems(
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
    ): SyncIteratorJava<Item> = queryItems(startIndex, includeStartIndex, queryOrder, pageSize, useCache = true)

    /**
     * Retrieve items from the [SyncListJava].
     *
     * @param startIndex        Index of the first item to retrieve, `null` means start from the
     *                          first item in the [SyncListJava].
     * @param includeStartIndex When `true` - result includes the item with the startIndex.
     *                          When `false` - the item with the startIndex is skipped.
     *                          Ignored when startIndex = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @param pageSize          Page size for querying items from backend.
     * @param useCache          When `true` returns cached value if found in cache.
     *                          When `false` - performs network request to get latest data from backend.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryItems(
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIteratorJava<Item>

    /**
     * Remove this [SyncListJava].
     *
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun removeList(callback: SuccessListener<SyncListJava>): CancellationToken

    /**
     * Close this [SyncListJava].
     *
     * After closing [SyncListJava] stops notifying [Listener]s.
     * Call this method to cleanup resources when finish using this [SyncListJava] object.
     */
    fun close()

    /**
     * Method to add listener for this [SyncListJava].
     *
     * @param listener the listener to add.
     */
    override fun addListener(listener: Listener)

    /**
     * Method to Method to remove listener from this [SyncListJava].
     *
     * @param listener the listener to remove.
     */
    override fun removeListener(listener: Listener)

    /**
     * Method to remove all listeners from this [SyncListJava].
     */
    override fun removeAllListeners()

    /** Listener for all operations on a [SyncListJava]. */
    interface Listener {

        /**
         * This callback is invoked when an [Item] has been added into this [SyncListJava].
         *
         * @param list The list which invoked callback.
         * @param item The item which has been added.
         */
        fun onItemAdded(list: SyncListJava, item: Item) {}

        /**
         * This callback is invoked when an [Item] has been updated in this [SyncListJava].
         *
         * @param list The list which invoked callback.
         * @param item The item which has been updated.
         */
        fun onItemUpdated(list: SyncListJava, item: Item) {}

        /**
         * This callback is invoked when an [Item] has been removed from this [SyncListJava].
         *
         * @param list The list which invoked callback.
         * @param item The item which has been removed.
         */
        fun onItemRemoved(list: SyncListJava, item: Item) {}

        /**
         * This callback is invoked when the [SyncListJava] has been removed.
         *
         * @param list The list which invoked callback.
         */
        fun onRemoved(list: SyncListJava) {}

        /**
         * Called when [SyncListJava] subscription state has changed.
         *
         * @param list              The list which invoked callback.
         * @param subscriptionState New subscription state.
         */
        fun onSubscriptionStateChanged(list: SyncListJava, subscriptionState: SubscriptionStateJava) {}
    }

    /** Represents a value associated with each index in [SyncListJava]. */
    data class Item(
        /** Index of the [Item]. */
        val index: Long,

        /** Value of the [Item] as a serialised JSON object. */
        val jsonData: String,

        /** A date when this [Item] was created. */
        val dateCreated: Long,

        /** A date when this [Item] was last updated. */
        val dateUpdated: Long,

        /**
         * A date this [Item] will expire, `null` means will not expire.
         *
         * @see setItemWithTtl
         */
        val dateExpires: Long?,
    )
}
