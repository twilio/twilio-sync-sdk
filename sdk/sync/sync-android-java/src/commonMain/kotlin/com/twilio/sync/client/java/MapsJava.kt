//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.kDefaultPageSize
import kotlin.time.Duration

interface MapsJava {

    /**
     * Create new [SyncMapJava] object.
     *
     * @param output Listener that will receive created [SyncMapJava] in its onSuccess() callback or
     *               any error in onFailure() callback.
     * @return       [CancellationToken] which allows to cancel network request.
     */
    fun create(output: SuccessListener<SyncMapJava>): CancellationToken =
        create(
            uniqueName = null,
            ttlSeconds = Duration.INFINITE.inWholeSeconds,
            listener = null,
            output
        )

    /**
     * Create new [SyncMapJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncMapJava] upon creation.
     * @param output        Listener that will receive created [SyncMapJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, output: SuccessListener<SyncMapJava>): CancellationToken =
        create(uniqueName, ttlSeconds = Duration.INFINITE.inWholeSeconds, listener = null, output)

    /**
     * Create new [SyncMapJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncMapJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive created [SyncMapJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, ttlSeconds: Long, output: SuccessListener<SyncMapJava>): CancellationToken =
        create(uniqueName, ttlSeconds, listener = null, output)

    /**
     * Create new [SyncMapJava] object.
     *
     * @param uniqueName    Unique name to assign to new [SyncMapJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncMapJava.Listener] that will receive notifications regarding this map.
     * @param output        Listener that will receive created [SyncMapJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncMapJava.Listener?,
        output: SuccessListener<SyncMapJava>
    ): CancellationToken

    /**
     * Open existing [SyncMapJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing map or to assign to new map upon creation.
     * @param output        Listener that will receive opened or created [SyncMapJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(uniqueName: String, output: SuccessListener<SyncMapJava>): CancellationToken =
        openOrCreate(
            uniqueName = uniqueName,
            ttlSeconds = Duration.INFINITE.inWholeSeconds,
            listener = null,
            output = output
        )

    /**
     * Open existing [SyncMapJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing map or to assign to new map upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive opened or created [SyncMapJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        output: SuccessListener<SyncMapJava>
    ): CancellationToken =
        openOrCreate(
            uniqueName = uniqueName,
            ttlSeconds = ttlSeconds,
            listener = null,
            output = output
        )

    /**
     * Open existing [SyncMapJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing map or to assign to new map upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncMapJava.Listener] that will receive notifications regarding this map.
     * @param output        Listener that will receive opened or created [SyncMapJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncMapJava.Listener?,
        output: SuccessListener<SyncMapJava>
    ): CancellationToken

    /**
     * Open existing [SyncMapJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncMapJava].
     * @param output            Listener that will receive opened [SyncMapJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(sidOrUniqueName: String, output: SuccessListener<SyncMapJava>): CancellationToken =
        openExisting(sidOrUniqueName = sidOrUniqueName, listener = null, output = output)

    /**
     * Open existing [SyncMapJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncMapJava].
     * @param listener          [SyncMapJava.Listener] that will receive notifications regarding this map.
     * @param output            Listener that will receive opened [SyncMapJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(
        sidOrUniqueName: String,
        listener: SyncMapJava.Listener?,
        output: SuccessListener<SyncMapJava>,
    ): CancellationToken

    /**
     * Set time to live for [SyncMapJava] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncMapJava].
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setTtl(sidOrUniqueName: String, ttlSeconds: Long, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Retrieve Item from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param itemKey               Key of the item to retrieve.
     * @param callback              Async result listener that will receive a value of the [SyncMapJava.Item] in
     *                              its onSuccess() callback (null if item doesn't exist) or any error in
     *                              onFailure() callback.
     *
     * @return                      [CancellationToken] which allows to cancel network request.
     */
    fun getMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        callback: SuccessListener<SyncMapJava.Item?>
    ): CancellationToken

    /**
     * Set Item in the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param itemKey               Key of the item to set.
     * @param jsonData              Item data to set as a serialized JSON object.
     * @param callback              Async result listener that will receive new value of the [SyncMapJava.Item] in
     *                              its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                      [CancellationToken] which allows to cancel network request.
     */
    fun setMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        jsonData: String,
        callback: SuccessListener<SyncMapJava.Item>
    ): CancellationToken

    /**
     * Set Item in the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param itemKey               Key of the item to set.
     * @param jsonData              Item data to set as a serialized JSON object.
     * @param ttlSeconds            Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback              Async result listener that will receive new value of the [SyncMapJava.Item] in
     *                              its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                      [CancellationToken] which allows to cancel network request.
     */
    fun setMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncMapJava.Item>
    ): CancellationToken

    /**
     * Mutate value of the [SyncMapJava.Item] using provided Mutator function.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param itemKey               Key of the item to mutate.
     * @param mutator               [SyncMutator] which will be applied to the map item.
     * @param callback              Async result listener that will receive new value of the [SyncMapJava.Item] in
     *                              its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                      [CancellationToken] which allows to cancel network request.
     */
    fun mutateMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        mutator: SyncMutator,
        callback: SuccessListener<SyncMapJava.Item>
    ): CancellationToken

    /**
     * Mutate value of the [SyncMapJava.Item] using provided Mutator function.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param itemKey               Key of the item to mutate.
     * @param mutator               [SyncMutator] which will be applied to the map item.
     * @param ttlSeconds            Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback              Async result listener that will receive new value of the [SyncMapJava.Item] in
     *                              its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun mutateMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncMapJava.Item>,
    ): CancellationToken

    /**
     * Remove Item from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param itemKey               Key of the item to set.
     * @param callback              Async result listener. See [SuccessListener].
     * @return                      [CancellationToken] which allows to cancel network request.
     */
    fun removeMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        callback: SuccessListener<Unit>
    ): CancellationToken

    /**
     * Remove [SyncMapJava] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncMapJava].
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Retrieve all items from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @return                      New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryMapItems(mapSidOrUniqueName: String): SyncIteratorJava<SyncMapJava.Item> =
        queryMapItems(mapSidOrUniqueName, startKey = null, includeStartKey = true, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param startKey              Key of the first item to retrieve.
     * @return                      New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryMapItems(
        mapSidOrUniqueName: String,
        startKey: String,
    ): SyncIteratorJava<SyncMapJava.Item> =
        queryMapItems(mapSidOrUniqueName, startKey, includeStartKey = true)

    /**
     * Retrieve items from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param startKey              Key of the first item to retrieve.
     * @param includeStartKey       When `true` - result includes the item with the startKey
     *                              When `false` - the item with the startKey is skipped.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryMapItems(
        mapSidOrUniqueName: String,
        startKey: String,
        includeStartKey: Boolean,
    ): SyncIteratorJava<SyncMapJava.Item> =
        queryMapItems(mapSidOrUniqueName, startKey, includeStartKey, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param startKey              Key of the first item to retrieve, `null` means start from the
     *                              first item in the [SyncMapJava].
     * @param includeStartKey       When `true` - result includes the item with the startKey.
     *                              When `false` - the item with the startKey is skipped.
     *                              Ignored when startKey = null
     *
     * @param queryOrder            [QueryOrder] for sorting results.
     * @return                      New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryMapItems(
        mapSidOrUniqueName: String,
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
    ): SyncIteratorJava<SyncMapJava.Item> =
        queryMapItems(mapSidOrUniqueName, startKey, includeStartKey, queryOrder, pageSize = kDefaultPageSize)

    /**
     * Retrieve items from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param startKey              Key of the first item to retrieve, `null` means start from the
     *                              first item in the [SyncMapJava].
     * @param includeStartKey       When `true` - result includes the item with the startKey.
     *                              When `false` - the item with the startKey is skipped.
     *                              Ignored when startKey = null
     *
     * @param queryOrder            [QueryOrder] for sorting results.
     * @param pageSize              Page size for querying items from backend.
     * @return                      New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryMapItems(
        mapSidOrUniqueName: String,
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
    ): SyncIteratorJava<SyncMapJava.Item> =
        queryMapItems(mapSidOrUniqueName, startKey, includeStartKey, queryOrder, pageSize, useCache = true)

    /**
     * Retrieve items from the [SyncMapJava] without opening it.
     *
     * @param mapSidOrUniqueName    SID or unique name of existing [SyncMapJava].
     * @param startKey              Key of the first item to retrieve, `null` means start from the
     *                              first item in the [SyncMapJava].
     * @param includeStartKey       When `true` - result includes the item with the startKey.
     *                              When `false` - the item with the startKey is skipped.
     *                              Ignored when startKey = null
     *
     * @param queryOrder            [QueryOrder] for sorting results.
     * @param pageSize              Page size for querying items from backend.
     * @param useCache              When `true` returns cached value if found in cache.
     *                              When `false` - performs network request to get latest data from backend.
     *
     * @return                  New [SyncIteratorJava] to sequentially access the elements from this [SyncMapJava].
     */
    fun queryMapItems(
        mapSidOrUniqueName: String,
        startKey: String?,
        includeStartKey: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIteratorJava<SyncMapJava.Item>
}
