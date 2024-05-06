//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncList
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.kDefaultPageSize
import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject

interface Lists {

    /**
     * Create new [SyncList] object.
     *
     * @param uniqueName        Unique name to assign to new [SyncList] upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Created [SyncList].
     * @throws TwilioException  When error occurred while list creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun create(
        uniqueName: String? = null,
        ttl: Duration = Duration.INFINITE,
    ): SyncList

    /**
     * Open existing [SyncList] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName        Unique name to find existing list or to assign to new list upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Opened or created [SyncList].
     * @throws TwilioException  When error occurred while list opening or creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openOrCreate(
        uniqueName: String,
        ttl: Duration = Duration.INFINITE,
    ): SyncList

    /**
     * Open existing [SyncList] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncList].
     * @return                  Opened [SyncList].
     * @throws TwilioException  When error occurred while list opening.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openExisting(
        sidOrUniqueName: String,
    ): SyncList

    /**
     * Set time to live for [SyncList] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncList].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating ttl.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setTtl(sidOrUniqueName: String, ttl: Duration)

    /**
     * Remove [SyncList] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncList].
     * @throws TwilioException  When error occurred while removing the list.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun remove(sidOrUniqueName: String)

    /**
     * Retrieve Item from the [SyncList] without opening it.
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemIndex             Index of the item to retrieve.
     * @param useCache              When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                              and [Events.onItemRemoved] to receive notifications about the item changes.
     *                              When `false` - performs network request to get latest data from backend.
     *
     * @return                      [SyncList.Item] for this itemIndex or null if no item associated with the key.
     * @throws TwilioException      When error occurred while retrieving the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun getListItem(listSidOrUniqueName: String, itemIndex: Long, useCache: Boolean = true): SyncList.Item?

    /**
     * Add Item to the [SyncList] without opening it.
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemData              Item data to add as a [JsonObject].
     * @return                      [SyncList.Item] which has been added.
     * @throws TwilioException      When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun addListItem(listSidOrUniqueName: String, itemData: JsonObject): SyncList.Item

    /**
     * Add Item to the [SyncList] without opening it.
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemData              Item data to add as a [JsonObject].
     * @param ttl                   Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                      [SyncList.Item] which has been added.
     * @throws TwilioException      When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun addListItemWithTtl(
        listSidOrUniqueName: String,
        itemData: JsonObject,
        ttl: Duration
    ): SyncList.Item

    /**
     * Set Item in the [SyncList] without opening it.
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemIndex             Index of the item to set.
     * @param itemData              Item data to set as a [JsonObject].
     * @return                      [SyncList.Item] which has been set.
     * @throws TwilioException      When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setListItem(listSidOrUniqueName: String, itemIndex: Long, itemData: JsonObject): SyncList.Item

    /**
     * Set Item in the [SyncList] without opening it.
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemIndex             Index of the item to set.
     * @param itemData              Item data to set as a [JsonObject].
     * @param ttl                   Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                      [SyncList.Item] which has been set.
     * @throws TwilioException      When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        itemData: JsonObject,
        ttl: Duration
    ): SyncList.Item

    /**
     * Mutate value of the existing [SyncList.Item] without opening it using provided Mutator function.
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
     * syncClient.lists.mutateItem<Counter>("listName", itemIndex = 0) { counter -> counter + 1 }
     * ```
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemIndex             Index of the item to mutate.
     * @param mutator               Mutator which will be applied to the list item.
     *
     *                              This function will be provided with the
     *                              previous data contents and should return new desired contents or null to abort
     *                              mutate operation. This function is invoked on a background thread.
     *
     * @return                      [SyncList.Item] which has been set during mutation.
     * @throws TwilioException      When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): SyncList.Item

    /**
     * Mutate value of the existing [SyncList.Item] without opening it using provided Mutator function.
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
     * syncClient.lists.mutateItem<Counter>("listName", itemIndex = 0, ttl = 1.days) { counter -> counter + 1 }
     * ```
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemIndex             Index of the item to mutate.
     * @param ttl                   Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @param mutator               Mutator which will be applied to the list item.
     *
     *                              This function will be provided with the
     *                              previous data contents and should return new desired contents or null to abort
     *                              mutate operation. This function is invoked on a background thread.
     *
     * @return                      [SyncList.Item] which has been set during mutation.
     * @throws TwilioException      When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject) -> JsonObject?
    ): SyncList.Item

    /**
     * Remove [SyncList.Item] without opening the [SyncList].
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param itemIndex             Index of the item to remove.
     * @throws TwilioException      When error occurred while removing the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeListItem(listSidOrUniqueName: String, itemIndex: Long)

    /**
     * Retrieve items without opening the [SyncList].
     *
     * @param listSidOrUniqueName   SID or unique name of existing [SyncList].
     * @param startIndex            Index of the first item to retrieve, `null` means start from the first
     *                              item in the SyncList
     *
     * @param includeStartIndex     When `true` - result includes the item with the startIndex.
     *                              When `false` - the item with the startIndex is skipped.
     *                              Ignored when startIndex = null
     *
     * @param queryOrder            [QueryOrder] for sorting results.
     * @param pageSize              Page size for querying items from backend.
     * @param useCache              When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                              and [Events.onItemRemoved] to receive notifications about the item changes.
     *                              When `false` - performs network request to get latest data from backend.
     *
     * @return                      New [SyncIterator] to sequentially access the elements from this [SyncList].
     */
    fun queryItems(
        listSidOrUniqueName: String,
        startIndex: Long? = null,
        includeStartIndex: Boolean = true,
        queryOrder: QueryOrder = QueryOrder.Ascending,
        pageSize: Int = kDefaultPageSize,
        useCache: Boolean = true,
    ): SyncIterator<SyncList.Item>
}
