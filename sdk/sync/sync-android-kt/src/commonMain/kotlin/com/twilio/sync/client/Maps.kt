//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncMap
import com.twilio.sync.entities.SyncMap.Events
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncIterator
import com.twilio.sync.utils.kDefaultPageSize
import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject

interface Maps {

    /**
     * Create new [SyncMap] object.
     *
     * @param uniqueName        Unique name to assign to new [SyncMap] upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Created [SyncMap].
     * @throws TwilioException  When error occurred while map creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun create(
        uniqueName: String? = null,
        ttl: Duration = Duration.INFINITE,
    ): SyncMap

    /**
     * Open existing [SyncMap] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName        Unique name to find existing map or to assign to new map upon creation.
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                  Opened or created [SyncMap].
     * @throws TwilioException  When error occurred while map opening or creation.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openOrCreate(
        uniqueName: String,
        ttl: Duration = Duration.INFINITE,
    ): SyncMap

    /**
     * Open existing [SyncMap] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncMap].
     * @return                  Opened [SyncMap].
     * @throws TwilioException  When error occurred while map opening.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun openExisting(
        sidOrUniqueName: String,
    ): SyncMap

    /**
     * Set time to live for [SyncMap] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncMap].
     * @param ttl               Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @throws TwilioException  When error occurred while updating ttl.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setTtl(sidOrUniqueName: String, ttl: Duration)

    /**
     * Remove [SyncMap] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncMap].
     * @throws TwilioException  When error occurred while removing the map.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun remove(sidOrUniqueName: String)

    /**
     * Retrieve Item from the [SyncMap] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param itemKey               Key of the item to retrieve.
     * @param useCache              When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                              and [Events.onItemRemoved] to receive notifications about the item changes.
     *                              When `false` - performs network request to get latest data from backend.
     *
     * @return                      [SyncMap.Item] for this itemKey or null if no item associated with the key.
     * @throws TwilioException      When error occurred while retrieving the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun getMapItem(mapSidOrUniqueName: String, itemKey: String, useCache: Boolean = true): SyncMap.Item?

    /**
     * Set Item in the [SyncMap] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param itemKey               Key of the item to set.
     * @param itemData              Item data to set as a [JsonObject].
     * @return                      [SyncMap.Item] which has been set.
     * @throws TwilioException      When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setMapItem(mapSidOrUniqueName: String, itemKey: String, itemData: JsonObject): SyncMap.Item

    /**
     * Set Item in the [SyncMap] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param itemKey               Key of the item to set.
     * @param itemData              Item data to set as a [JsonObject].
     * @param ttl                   Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @return                      [SyncMap.Item] which has been set.
     * @throws TwilioException      When error occurred while setting the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun setMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        itemData: JsonObject,
        ttl: Duration
    ): SyncMap.Item

    /**
     * Mutate value of the [SyncMap.Item] without opening [SyncMap] using provided Mutator function.
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
     * syncClient.maps.mutateItem<Counter>("mapName", "counter") { counter -> counter?.let { it + 1 } ?: Counter(1) }
     * ```
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param itemKey               Key of the item to mutate.
     * @param mutator               Mutator which will be applied to the map item.
     *
     *                              This function will be provided with the
     *                              previous data contents and should return new desired contents or null to abort
     *                              mutate operation. This function is invoked on a background thread.
     *
     * @return                      [SyncMap.Item] which has been set during mutation.
     * @throws TwilioException      When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ): SyncMap.Item

    /**
     * Mutate value of the [SyncMap.Item] without opening [SyncMap] using provided Mutator function.
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
     * syncClient.maps.mutateItem<Counter>("mapName", "counter", ttl = 1.days) { counter -> counter?.let { it + 1 } ?: Counter(1) }
     * ```
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param itemKey               Key of the item to mutate.
     * @param ttl                   Time to live from now or [Duration.INFINITE] to indicate no expiry.
     * @param mutator               Mutator which will be applied to the map item.
     *
     *                              This function will be provided with the
     *                              previous data contents and should return new desired contents or null to abort
     *                              mutate operation. This function is invoked on a background thread.
     *
     * @return                      [SyncMap.Item] which has been set during mutation.
     * @throws TwilioException      When error occurred while mutating the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun mutateMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        ttl: Duration,
        mutator: suspend (currentData: JsonObject?) -> JsonObject?
    ): SyncMap.Item

    /**
     * Remove [SyncMap.Item] without opening the [SyncMap].
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param itemKey               Key of the item to remove.
     * @throws TwilioException      When error occurred while removing the Item.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend fun removeMapItem(mapSidOrUniqueName: String, itemKey: String)

    /**
     * Retrieve items without opening the [SyncMap].
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMap].
     * @param startKey              Key of the first item to retrieve, `null` means start from the first
     *                              item in the SyncMap
     *
     * @param includeStartKey       When `true` - result includes the item with the
     *                              startKey (if exist in the [SyncMap]).
     *                              When `false` - the item with the startKey is skipped.
     *                              Ignored when startKey = null
     *
     * @param queryOrder            [QueryOrder] for sorting results.
     * @param pageSize              Page size for querying items from backend.
     * @param useCache              When `true` returns cached value if found in cache. Collect [Events.onItemUpdated]
     *                              and [Events.onItemRemoved] to receive notifications about the item changes.
     *                              When `false` - performs network request to get latest data from backend.
     *
     * @return                      New [SyncIterator] to sequentially access the elements from this [SyncMap].
     */
    fun queryItems(
        mapSidOrUniqueName: String,
        startKey: String? = null,
        includeStartKey: Boolean = true,
        queryOrder: QueryOrder = QueryOrder.Ascending,
        pageSize: Int = kDefaultPageSize,
        useCache: Boolean = true,
    ): SyncIterator<SyncMap.Item>
}
