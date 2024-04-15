//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.CancellationToken
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.SyncMutator
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.kDefaultPageSize
import kotlin.time.Duration

interface ListsJava {

    /**
     * Creates a new [SyncListJava] object with no unique name and infinite time to live.
     *
     * @param output Listener that will receive the created [SyncListJava] in its onSuccess() callback or
     *               any error in onFailure() callback.
     * @return       [CancellationToken] which allows to cancel network request.
     */
    fun create(output: SuccessListener<SyncListJava>): CancellationToken =
        create(
            uniqueName = null,
            ttlSeconds = Duration.INFINITE.inWholeSeconds,
            listener = null,
            output
        )

    /**
     * Creates a new [SyncListJava] object with a unique name and infinite time to live.
     *
     * @param uniqueName    Unique name to assign to the new [SyncListJava] upon creation.
     * @param output        Listener that will receive the created [SyncListJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, output: SuccessListener<SyncListJava>): CancellationToken =
        create(uniqueName, ttlSeconds = Duration.INFINITE.inWholeSeconds, listener = null, output)

    /**
     * Creates a new [SyncListJava] object with a unique name and specified time to live.
     *
     * @param uniqueName    Unique name to assign to the new [SyncListJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive the created [SyncListJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(uniqueName: String?, ttlSeconds: Long, output: SuccessListener<SyncListJava>): CancellationToken =
        create(uniqueName, ttlSeconds, listener = null, output)

    /**
     * Creates a new [SyncListJava] object with a unique name, specified time to live, and a listener for notifications.
     *
     * @param uniqueName    Unique name to assign to the new [SyncListJava] upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncListJava.Listener] that will receive notifications regarding this list.
     * @param output        Listener that will receive the created [SyncListJava] in its onSuccess() callback or
     *                      any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun create(
        uniqueName: String?,
        ttlSeconds: Long,
        listener: SyncListJava.Listener?,
        output: SuccessListener<SyncListJava>
    ): CancellationToken

    /**
     * Open existing [SyncListJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing list or to assign to new list upon creation.
     * @param output        Listener that will receive opened or created [SyncListJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(uniqueName: String, output: SuccessListener<SyncListJava>): CancellationToken =
        openOrCreate(
            uniqueName = uniqueName,
            ttlSeconds = Duration.INFINITE.inWholeSeconds,
            listener = null,
            output = output
        )

    /**
     * Open existing [SyncListJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing list or to assign to new list upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param output        Listener that will receive opened or created [SyncListJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        output: SuccessListener<SyncListJava>
    ): CancellationToken =
        openOrCreate(
            uniqueName = uniqueName,
            ttlSeconds = ttlSeconds,
            listener = null,
            output = output
        )

    /**
     * Open existing [SyncListJava] by unique name or create a new one if specified name does not exist.
     *
     * @param uniqueName    Unique name to find existing list or to assign to new list upon creation.
     * @param ttlSeconds    Time to live in seconds from now.
     * @param listener      [SyncListJava.Listener] that will receive notifications regarding this list.
     * @param output        Listener that will receive opened or created [SyncListJava] in its onSuccess()
     *                      callback or any error in onFailure() callback.
     * @return              [CancellationToken] which allows to cancel network request.
     */
    fun openOrCreate(
        uniqueName: String,
        ttlSeconds: Long,
        listener: SyncListJava.Listener?,
        output: SuccessListener<SyncListJava>
    ): CancellationToken

    /**
     * Open existing [SyncListJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncListJava].
     * @param output            Listener that will receive opened [SyncListJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(sidOrUniqueName: String, output: SuccessListener<SyncListJava>): CancellationToken =
        openExisting(sidOrUniqueName = sidOrUniqueName, listener = null, output = output)

    /**
     * Open existing [SyncListJava] by SID or unique name.
     *
     * @param sidOrUniqueName   SID or unique name to find existing [SyncListJava].
     * @param listener          [SyncListJava.Listener] that will receive notifications regarding this list.
     * @param output            Listener that will receive opened [SyncListJava] in its onSuccess() callback or
     *                          any error in onFailure() callback.
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun openExisting(
        sidOrUniqueName: String,
        listener: SyncListJava.Listener?,
        output: SuccessListener<SyncListJava>,
    ): CancellationToken

    /**
     * Set time to live for [SyncListJava] without opening it.
     *
     * This TTL specifies the minimum time the object will live,
     * sometime soon after this time the object will be deleted.
     *
     * If time to live is not specified, object lives infinitely long.
     *
     * TTL could be used in order to auto-recycle old unused objects,
     * but building app logic, like timers, using TTL is not recommended.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncListJava].
     * @param ttlSeconds        Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun setTtl(sidOrUniqueName: String, ttlSeconds: Long, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Retrieve Item from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param itemIndex              Index of the item to retrieve.
     * @param callback               Async result listener that will receive a value of the [SyncListJava.Item] in
     *                               its onSuccess() callback (null if item doesn't exist) or any error in
     *                               onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun getListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        callback: SuccessListener<SyncListJava.Item?>
    ): CancellationToken

    /**
     * Ad Item in the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param jsonData               Item data to set as a serialized JSON object.
     * @param callback               Async result listener that will receive new value of the [SyncListJava.Item] in
     *                               its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun addListItem(
        listSidOrUniqueName: String,
        jsonData: String,
        callback: SuccessListener<SyncListJava.Item>
    ): CancellationToken

    /**
     * Add Item in the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param jsonData               Item data to set as a serialized JSON object.
     * @param ttlSeconds             Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback               Async result listener that will receive new value of the [SyncListJava.Item] in
     *                               its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun addListItemWithTtl(
        listSidOrUniqueName: String,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncListJava.Item>
    ): CancellationToken

    /**
     * Set Item in the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param itemIndex              Index of the item to set.
     * @param jsonData               Item data to set as a serialized JSON object.
     * @param callback               Async result listener that will receive new value of the [SyncListJava.Item] in
     *                               its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun setListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        jsonData: String,
        callback: SuccessListener<SyncListJava.Item>
    ): CancellationToken

    /**
     * Set Item in the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param itemIndex              Index of the item to set.
     * @param jsonData               Item data to set as a serialized JSON object.
     * @param ttlSeconds             Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback               Async result listener that will receive new value of the [SyncListJava.Item] in
     *                               its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun setListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        jsonData: String,
        ttlSeconds: Long,
        callback: SuccessListener<SyncListJava.Item>
    ): CancellationToken

    /**
     * Mutate value of the [SyncListJava.Item] using provided Mutator function.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param itemIndex              Index of the item to mutate.
     * @param mutator                [SyncMutator] which will be applied to the list item.
     * @param callback               Async result listener that will receive new value of the [SyncListJava.Item] in
     *                               its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun mutateListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncListJava.Item>
    ): CancellationToken

    /**
     * Mutate value of the [SyncListJava.Item] using provided Mutator function.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param itemIndex              Index of the item to mutate.
     * @param mutator                [SyncMutator] which will be applied to the list item.
     * @param ttlSeconds             Time to live in seconds from now or 0 to indicate no expiry.
     * @param callback               Async result listener that will receive new value of the [SyncListJava.Item] in
     *                               its onSuccess() callback or any error in onFailure() callback.
     *
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun mutateListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Long,
        ttlSeconds: Long,
        mutator: SyncMutator,
        callback: SuccessListener<SyncListJava.Item>,
    ): CancellationToken

    /**
     * Remove Item from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param itemIndex              Index of the item to remove.
     * @param callback               Async result listener. See [SuccessListener].
     * @return                       [CancellationToken] which allows to cancel network request.
     */
    fun removeListItem(
        listSidOrUniqueName: String,
        itemIndex: Long,
        callback: SuccessListener<Unit>
    ): CancellationToken

    /**
     * Remove [SyncListJava] without opening it.
     *
     * @param sidOrUniqueName   SID or unique name of existing [SyncListJava].
     * @param callback          Async result listener. See [SuccessListener].
     * @return                  [CancellationToken] which allows to cancel network request.
     */
    fun remove(sidOrUniqueName: String, callback: SuccessListener<Unit>): CancellationToken

    /**
     * Retrieve all items from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @return                      New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryListItems(listSidOrUniqueName: String): SyncIteratorJava<SyncListJava.Item> =
        queryListItems(listSidOrUniqueName, startIndex = null, includeStartIndex = true, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param startIndex             Index of the first item to retrieve.
     * @return                       New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryListItems(
        listSidOrUniqueName: String,
        startIndex: Long,
    ): SyncIteratorJava<SyncListJava.Item> =
        queryListItems(listSidOrUniqueName, startIndex, includeStartIndex = true)

    /**
     * Retrieve items from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param startIndex             Index of the first item to retrieve.
     * @param includeStartIndex      When `true` - result includes the item with the startIndex.
     *                               When `false` - the item with the startIndex is skipped.
     *
     * @return                       New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryListItems(
        listSidOrUniqueName: String,
        startIndex: Long,
        includeStartIndex: Boolean,
    ): SyncIteratorJava<SyncListJava.Item> =
        queryListItems(listSidOrUniqueName, startIndex, includeStartIndex, QueryOrder.Ascending)

    /**
     * Retrieve items from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param startIndex             Index of the first item to retrieve, `null` means start from the
     *                               first item in the [SyncListJava].
     * @param includeStartIndex      When `true` - result includes the item with the startIndex.
     *                               When `false` - the item with the startIndex is skipped.
     *                               Ignored when startIndex = null
     *
     * @param queryOrder             [QueryOrder] for sorting results.
     * @return                       New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryListItems(
        listSidOrUniqueName: String,
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
    ): SyncIteratorJava<SyncListJava.Item> =
        queryListItems(listSidOrUniqueName, startIndex, includeStartIndex, queryOrder, pageSize = kDefaultPageSize)

    /**
     * Retrieve items from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param startIndex             Index of the first item to retrieve, `null` means start from the
     *                               first item in the [SyncListJava].
     * @param includeStartIndex      When `true` - result includes the item with the startIndex.
     *                               When `false` - the item with the startIndex is skipped.
     *                               Ignored when startIndex = null
     *
     * @param queryOrder             [QueryOrder] for sorting results.
     * @param pageSize               Page size for querying items from backend.
     * @return                       New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryListItems(
        listSidOrUniqueName: String,
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
    ): SyncIteratorJava<SyncListJava.Item> =
        queryListItems(listSidOrUniqueName, startIndex, includeStartIndex, queryOrder, pageSize, useCache = true)

    /**
     * Retrieve items from the [SyncListJava] without opening it.
     *
     * @param listSidOrUniqueName    SID or unique name of existing [SyncListJava].
     * @param startIndex             Index of the first item to retrieve, `null` means start from the
     *                               first item in the [SyncListJava].
     * @param includeStartIndex      When `true` - result includes the item with the startIndex.
     *                               When `false` - the item with the startIndex is skipped.
     *                               Ignored when startIndex = null
     *
     * @param queryOrder             [QueryOrder] for sorting results.
     * @param pageSize               Page size for querying items from backend.
     * @param useCache               When `true` returns cached value if found in cache.
     *                               When `false` - performs network request to get latest data from backend.
     *
     * @return                       New [SyncIteratorJava] to sequentially access the elements from this [SyncListJava].
     */
    fun queryListItems(
        listSidOrUniqueName: String,
        startIndex: Long?,
        includeStartIndex: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): SyncIteratorJava<SyncListJava.Item>
}
