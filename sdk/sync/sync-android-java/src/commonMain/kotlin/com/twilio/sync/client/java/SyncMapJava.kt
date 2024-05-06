//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.Listenable
import com.twilio.sync.client.java.utils.SubscriptionStateJava
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.entities.SyncMap.Item
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.kDefaultPageSize

/**
 * SyncMap is a key-value store with Strings as keys and arbitrary JSON objects as values.
 *
 * You can add, remove and modify values associated with the keys.
 *
 * To obtain an instance of a SyncMapJava use [SyncClientJava.maps]
 */
interface SyncMapJava: Listenable<SyncMapJava.Listener> {

    /** An immutable system-assigned identifier of this [SyncMapJava]. */
    val sid: String

    /** An optional unique name for this map, assigned at creation time. */
    val uniqueName: String?

    /**
     * Current subscription state.
     *
     * @see Listener.onSubscriptionStateChanged
     */
    val subscriptionState: SubscriptionStateJava

    /** A date when this [SyncMapJava] was created. */
    val dateCreated: Long

    /** A date when this [SyncMapJava] was last updated. */
    val dateUpdated: Long

    /**
     * A date this [SyncMapJava] will expire, `null` means will not expire.
     *
     * @see setTtl
     */
    val dateExpires: Long?

    /** `true` when this [SyncMapJava] has been removed on the backend, `false` otherwise. */
    val isRemoved: Boolean

    /** `true` when this [SyncMapJava] is offline and doesn't receive updates from backend, `false` otherwise. */
    val isFromCache: Boolean

    /**
     * Set time to live for this [SyncMapJava].
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
    fun setTtl(ttlSeconds: Long, callback: SuccessListener<SyncMapJava>): CancellationToken

    /**
     * Retrieve Item from the [SyncMapJava].
     *
     * @param itemKey           Key of the item to retrieve.
     * @param callback          Async result listener that will receive a value of the [Item] in
     *                          its onSuccess() callback (null if item doesn't exist) or any error in
     *                          onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun getItem(itemKey: String, callback: SuccessListener<Item?>): CancellationToken

    /**
     * Set Item in the [SyncMapJava].
     *
     * @param itemKey           Key of the item to set.
     * @param jsonData          Item data to set as a serialized JSON object.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setItem(itemKey: String, jsonData: String, callback: SuccessListener<Item>): CancellationToken

    /**
     * Set Item in the [SyncMapJava].
     *
     * @param itemKey           Key of the item to set.
     * @param jsonData          Item data to set as a serialized JSON object.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setItemWithTtl(
        itemKey: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<Item>
    ): CancellationToken

    /**
     * Mutate value of the [Item] using provided Mutator function.
     *
     * @param itemKey           Key of the item to mutate.
     * @param mutator           [SyncMutator] which will be applied to the map item.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateItem(itemKey: String, mutator: SyncMutator, callback: SuccessListener<Item>): CancellationToken

    /**
     * Mutate value of the [Item] using provided Mutator function.
     *
     * @param itemKey           Key of the item to mutate.
     * @param mutator           [SyncMutator] which will be applied to the map item.
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener that will receive new value of the [Item] in
     *                          its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
   fun mutateItemWithTtl(
        itemKey: String,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<Item>,
    ): CancellationToken

    /**
     * Remove Item from the [SyncMapJava].
     *
     * @param itemKey           Key of the item to remove.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun removeItem(itemKey: String, callback: SuccessListener<SyncMapJava>): CancellationToken

    /**
     * Retrieve all items from the SyncMap.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryItems(): SyncIteratorJava<Item> = queryItems(startKey = null, includeStartKey = true, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncMapJava].
     *
     * @param startKey          Key of the first item to retrieve.
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryItems(
        startKey: String,
    ): SyncIteratorJava<Item> = queryItems(startKey, includeStartKey = true)

    /**
     * Retrieve items from the [SyncMapJava].
     *
     * @param startKey          Key of the first item to retrieve.
     * @param includeStartKey   When `true` - result includes the item with the startKey
     *                          When `false` - the item with the startKey is skipped.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryItems(
        startKey: String,
        includeStartKey: Boolean,
    ): SyncIteratorJava<Item> = queryItems(startKey, includeStartKey, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncMapJava].
     *
     * @param startKey          Key of the first item to retrieve, `null` means start from the
     *                          first item in the [SyncMapJava].
     * @param includeStartKey   When `true` - result includes the item with the startKey.
     *                          When `false` - the item with the startKey is skipped.
     *                          Ignored when startKey = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryItems(
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
    ): SyncIteratorJava<Item> = queryItems(startKey, includeStartKey, queryOrder, pageSize = kDefaultPageSize)

    /**
     * Retrieve items from the [SyncMapJava].
     *
     * @param startKey          Key of the first item to retrieve, `null` means start from the
     *                          first item in the [SyncMapJava].
     * @param includeStartKey   When `true` - result includes the item with the startKey.
     *                          When `false` - the item with the startKey is skipped.
     *                          Ignored when startKey = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @param pageSize          Page size for querying items from backend.
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryItems(
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
    ): SyncIteratorJava<Item> = queryItems(startKey, includeStartKey, queryOrder, pageSize, useCache = true)

    /**
     * Retrieve items from the [SyncMapJava].
     *
     * @param startKey          Key of the first item to retrieve, `null` means start from the
     *                          first item in the [SyncMapJava].
     * @param includeStartKey   When `true` - result includes the item with the startKey.
     *                          When `false` - the item with the startKey is skipped.
     *                          Ignored when startKey = null
     *
     * @param queryOrder        [QueryOrder] for sorting results.
     * @param pageSize          Page size for querying items from backend.
     * @param useCache          When `true` returns cached value if found in cache.
     *                          When `false` - performs network request to get latest data from backend.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryItems(
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIteratorJava<Item>

    /**
     * Remove this [SyncMapJava].
     *
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun removeMap(callback: SuccessListener<SyncMapJava>): CancellationToken

    /**
     * Close this [SyncMapJava].
     *
     * After closing [SyncMapJava] stops notifying [Listener]s.
     * Call this method to cleanup resources when finish using this [SyncMapJava] object.
     */
    fun close()

    /**
     * Method to add listener for this [SyncMapJava].
     *
     * @param listener the listener to add.
     */
    override fun addListener(listener: Listener)

    /**
     * Method to Method to remove listener from this [SyncMapJava].
     *
     * @param listener the listener to remove.
     */
    override fun removeListener(listener: Listener)

    /**
     * Method to remove all listeners from this [SyncMapJava].
     */
    override fun removeAllListeners()

    /** Listener for all operations on a [SyncMapJava]. */
    interface Listener {

        /**
         * This callback is invoked when an [Item] has been added into this [SyncMapJava].
         *
         * @param map The map which invoked callback.
         * @param item The item which has been added.
         */
        fun onItemAdded(map: SyncMapJava, item: Item) {}

        /**
         * This callback is invoked when an [Item] has been updated in this [SyncMapJava].
         *
         * @param map The map which invoked callback.
         * @param item The item which has been added.
         */
        fun onItemUpdated(map: SyncMapJava, item: Item) {}

        /**
         * This callback is invoked when an [Item] has been removed from this [SyncMapJava].
         *
         * @param map The map which invoked callback.
         * @param item The item which has been removed.
         */
        fun onItemRemoved(map: SyncMapJava, item: Item) {}

        /**
         * This callback is invoked when the [SyncMapJava] has been removed.
         *
         * @param map The map which invoked callback.
         */
        fun onRemoved(map: SyncMapJava) {}

        /**
         * Called when [SyncMapJava] subscription state has changed.
         *
         * @param map               The map which invoked callback.
         * @param subscriptionState New subscription state.
         */
        fun onSubscriptionStateChanged(map: SyncMapJava, subscriptionState: SubscriptionStateJava) {}
    }

    /** Represents a value associated with each key in [SyncMapJava]. */
    data class Item(
        /** Key of the [Item]. */
        val key: String,

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
