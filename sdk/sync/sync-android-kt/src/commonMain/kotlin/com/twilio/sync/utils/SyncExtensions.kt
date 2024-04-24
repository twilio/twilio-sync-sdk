//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

import com.twilio.sync.client.Documents
import com.twilio.sync.client.Lists
import com.twilio.sync.client.Maps
import com.twilio.sync.client.Streams
import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncDocument
import com.twilio.sync.entities.SyncList
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.entities.SyncStream
import com.twilio.util.TwilioException
import com.twilio.util.TwilioLogger
import com.twilio.util.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration

/**
 * Executes the given block function on [SyncClient] and then shuts it down correctly
 * whether an exception is thrown or not.
 *
 * @param block A function to process this [SyncClient].
 * @return      The result of block function invoked on this [SyncClient].
*/
inline fun <R> SyncClient.use(block: (SyncClient) -> R): R {
    try {
        return block(this)
    } catch (e: Exception) {
        TwilioLogger.getLogger("SyncClient").e("use: ", e)
        throw e
    } finally {
        shutdown()
    }
}

/**
 * Executes the given block function on [SyncDocument] and then closes it down correctly
 * whether an exception is thrown or not.
 *
 * @param block A function to process this [SyncDocument].
 * @return      The result of block function invoked on this [SyncDocument].
 */
inline fun <R> SyncDocument.use(block: (SyncDocument) -> R): R {
    try {
        return block(this)
    } catch (e: Exception) {
        TwilioLogger.getLogger("SyncDocument").e("use: ", e)
        throw e
    } finally {
        close()
    }
}

/**
 * Executes the given block function on [SyncMap] and then closes it down correctly
 * whether an exception is thrown or not.
 *
 * @param block A function to process this [SyncMap].
 * @return      The result of block function invoked on this [SyncMap].
 */
inline fun <R> SyncMap.use(block: (SyncMap) -> R): R {
    try {
        return block(this)
    } catch (e: Exception) {
        TwilioLogger.getLogger("SyncMap").e("use: ", e)
        throw e
    } finally {
        close()
    }
}

/**
 * Executes the given block function on [SyncList] and then closes it down correctly
 * whether an exception is thrown or not.
 *
 * @param block A function to process this [SyncList].
 * @return      The result of block function invoked on this [SyncList].
 */
inline fun <R> SyncList.use(block: (SyncList) -> R): R {
    try {
        return block(this)
    } catch (e: Exception) {
        TwilioLogger.getLogger("SyncList").e("use: ", e)
        throw e
    } finally {
        close()
    }
}

/**
 * Executes the given block function on [SyncStream] and then closes it down correctly
 * whether an exception is thrown or not.
 *
 * @param block A function to process this [SyncStream].
 * @return      The result of block function invoked on this [SyncStream].
 */
inline fun <R> SyncStream.use(block: (SyncStream) -> R): R {
    try {
        return block(this)
    } catch (e: Exception) {
        TwilioLogger.getLogger("SyncList").e("use: ", e)
        throw e
    } finally {
        close()
    }
}

/**
 * Deserializes the [SyncDocument.data] into a value of type T using a deserializer retrieved
 * from reified type parameter.
 *
 * Type T must be annotated with @Serializable.
 *
 * @throws SerializationException   If the [SyncDocument.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the decoded input cannot be represented as a valid instance of type T.
 */
inline fun <reified T : Any> SyncDocument.data(): T = json.decodeFromJsonElement(data)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncDocument].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param data                      New document data.
 * @throws SerializationException   If the given value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> SyncDocument.setData(data: T) =
    setData(json.encodeToJsonElement(data).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncDocument].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param data                      New document data.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @throws SerializationException   If the given value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> SyncDocument.setDataWithTtl(data: T, ttl: Duration) =
    setDataWithTtl(json.encodeToJsonElement(data).jsonObject, ttl)

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncDocument]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * document.mutateData<Counter> { counter -> counter + 1 }
 * ```
 *
 * @param mutator                   Mutator which will be applied to document data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 *                                  Once this method finished the [data] property contains updated document data.
 *
 * @throws SerializationException   If the [SyncDocument.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the document.
 */
suspend inline fun <reified T : Any> SyncDocument.mutateData(crossinline mutator: suspend (currentData: T) -> T?) {
    mutateData { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Document data cannot be null" }
            mutator(currentData)
        }
    }
}

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncDocument]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * document.mutateData<Counter> { counter -> counter + 1 }
 * ```
 *
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @param mutator                   Mutator which will be applied to document data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 *                                  Once this method finished the [data] property contains updated document data.
 *
 * @throws SerializationException   If the [SyncDocument.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 * @throws TwilioException          When error occurred while mutating the document.
 */
suspend inline fun <reified T : Any> SyncDocument.mutateDataWithTtl(
    ttl: Duration,
    crossinline mutator: suspend (currentData: T) -> T?
) {
    mutateDataWithTtl(ttl) {  jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Document data cannot be null" }
            mutator(currentData)
        }
    }
}

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncDocument] without opening the document.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param sidOrUniqueName           SID or unique name of existing [SyncDocument].
 * @param data                      New document data.
 * @throws SerializationException   If the given value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> Documents.updateDocument(sidOrUniqueName: String, data: T) =
    updateDocument(sidOrUniqueName, json.encodeToJsonElement(data).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncDocument] without opening the document.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param sidOrUniqueName           SID or unique name of existing [SyncDocument].
 * @param data                      New document data.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @throws SerializationException   If the given value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> Documents.updateDocumentWithTtl(sidOrUniqueName: String, data: T, ttl: Duration) =
    updateDocumentWithTtl(sidOrUniqueName, json.encodeToJsonElement(data).jsonObject, ttl)

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncDocument] without opening the document.
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * document.mutateData<Counter> { counter -> counter + 1 }
 * ```
 *
 * @param sidOrUniqueName           SID or unique name of existing [SyncDocument].
 * @param mutator                   Mutator which will be applied to document data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 *                                  Once this method finished the [data] property contains updated document data.
 *
 * @return                          New value of the [SyncDocument].
 * @throws SerializationException   If the [SyncDocument.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the document.
 */
suspend inline fun <reified T : Any> Documents.mutateDocument(
    sidOrUniqueName: String,
    crossinline mutator: suspend (currentData: T) -> T?
): T {
    val jsonResult =  mutateDocument(sidOrUniqueName) { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Document data cannot be null" }
            mutator(currentData)
        }
    }

    return jsonResult.let { json.decodeFromJsonElement(it) }
}

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncDocument] without opening the document.
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * document.mutateData<Counter> { counter -> counter + 1 }
 * ```
 *
 * @param sidOrUniqueName           SID or unique name of existing [SyncDocument].
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @param mutator                   Mutator which will be applied to document data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 *                                  Once this method finished the [data] property contains updated document data.
 *
 * @return                          New value of the [SyncDocument].
 * @throws SerializationException   If the [SyncDocument.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the document.
 */
suspend inline fun <reified T> Documents.mutateDocumentWithTtl(
    sidOrUniqueName: String,
    ttl: Duration,
    crossinline mutator: suspend (currentData: T) -> T?
): T {
    val jsonResult = mutateDocumentWithTtl(sidOrUniqueName, ttl) { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Document data cannot be null" }
            mutator(currentData)
        }
    }

    return jsonResult.let { json.decodeFromJsonElement(it) }
}

/**
 * Deserializes the [SyncList.Item.data] into a value of type T using a deserializer retrieved
 * from reified type parameter.
 *
 * Type T must be annotated with @Serializable.
 *
 * @throws SerializationException   If the [SyncList.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the decoded input cannot be represented as a valid instance of type T.
 */
inline fun <reified T : Any> SyncList.Item.data(): T = json.decodeFromJsonElement(data)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and adds it as a new item to the end of the [SyncList].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param itemData                  Item data to add.
 * @return                          [SyncList.Item] which has been added.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while adding the item.
 */
suspend inline fun <reified T : Any> SyncList.addItem(itemData: T): SyncList.Item =
    addItem(json.encodeToJsonElement(itemData).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and adds it as a new item to the end of the [SyncList] with a specified TTL.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param itemData                  Item data to add.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @return                          [SyncList.Item] which has been added.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while adding the item.
 */
suspend inline fun <reified T : Any> SyncList.addItemWithTtl(itemData: T, ttl: Duration): SyncList.Item =
    addItemWithTtl(json.encodeToJsonElement(itemData).jsonObject, ttl)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncList.Item].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param itemIndex                 Index of the item to set.
 * @param itemData                  Item data to set.
 * @return                          [SyncList.Item] which has been set.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the item.
 */
suspend inline fun <reified T : Any> SyncList.setItem(itemIndex: Long, itemData: T): SyncList.Item =
    setItem(itemIndex, json.encodeToJsonElement(itemData).jsonObject)

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncList.Item]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * list.mutateItem<Counter>(0) { counter -> counter + 1 }
 * ```
 *
 * @param itemIndex                 Index of the item to mutate.
 * @param mutator                   Mutator which will be applied to list item data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 * @return                          [SyncList.Item] which has been set during mutation.
 * @throws SerializationException   If the [SyncList.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> SyncList.mutateItem(
    itemIndex: Long,
    crossinline mutator: suspend (currentData: T) -> T?
): SyncList.Item =
    mutateItem(itemIndex) { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Mutated list item data cannot be null" }
            mutator(currentData)
        }
    }

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncList.Item]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * list.mutateItemWithTtl<Counter>(0, ttl = 1.days) { counter -> counter + 1 }
 * ```
 *
 * @param itemIndex                 Index of the item to mutate.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @param mutator                   Mutator which will be applied to list item data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 * @return                          [SyncList.Item] which has been set during mutation.
 * @throws SerializationException   If the [SyncList.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> SyncList.mutateItemWithTtl(
    itemIndex: Long,
    ttl: Duration,
    crossinline mutator: suspend (currentData: T) -> T?
): SyncList.Item =
    mutateItemWithTtl(itemIndex, ttl) { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Mutated list item data cannot be null" }
            mutator(currentData)
        }
    }

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and adds it as a new item to the end of the specified [SyncList].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param listSidOrUniqueName       SID or unique name of existing [SyncList].
 * @param itemData                  Item data to add.
 * @return                          [SyncList.Item] which has been added.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while adding the item.
 */
suspend inline fun <reified T : Any> Lists.addListItem(listSidOrUniqueName: String, itemData: T): SyncList.Item =
    addListItem(listSidOrUniqueName, json.encodeToJsonElement(itemData).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and adds it as a new item to the end of the specified [SyncList] with a specified TTL.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param listSidOrUniqueName       SID or unique name of existing [SyncList].
 * @param itemData                  Item data to add.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @return                          [SyncList.Item] which has been added.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while adding the item.
 */
suspend inline fun <reified T : Any> Lists.addListItemWithTtl(
    listSidOrUniqueName: String,
    itemData: T,
    ttl: Duration
): SyncList.Item =
    addListItemWithTtl(listSidOrUniqueName, json.encodeToJsonElement(itemData).jsonObject, ttl)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncList.Item].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param listSidOrUniqueName       SID or unique name of existing [SyncList].
 * @param itemIndex                 Index of the item to set.
 * @param itemData                  Item data to set.
 * @return                          [SyncList.Item] which has been set.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the item.
 */
suspend inline fun <reified T : Any> Lists.setListItem(
    listSidOrUniqueName: String,
    itemIndex: Long,
    itemData: T
): SyncList.Item =
    setListItem(listSidOrUniqueName, itemIndex, json.encodeToJsonElement(itemData).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncList.Item].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param listSidOrUniqueName       SID or unique name of existing [SyncList].
 * @param itemIndex                 Index of the item to set.
 * @param itemData                  Item data to set.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @return                          [SyncList.Item] which has been set.
 * @throws SerializationException   If the itemData value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the item.
 */
suspend inline fun <reified T : Any> Lists.setListItemWithTtl(
    listSidOrUniqueName: String,
    itemIndex: Long,
    itemData: T,
    ttl: Duration
): SyncList.Item =
    setListItemWithTtl(listSidOrUniqueName, itemIndex, json.encodeToJsonElement(itemData).jsonObject, ttl)

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncList.Item]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * list.mutateItem<Counter>(0) { counter -> counter + 1 }
 * ```
 *
 * @param listSidOrUniqueName       SID or unique name of existing [SyncList].
 * @param itemIndex                 Index of the item to mutate.
 * @param mutator                   Mutator which will be applied to list item data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 * @return                          [SyncList.Item] which has been set during mutation.
 * @throws SerializationException   If the [SyncList.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> Lists.mutateListItem(
    listSidOrUniqueName: String,
    itemIndex: Long,
    crossinline mutator: suspend (currentData: T) -> T?
): SyncList.Item =
    mutateListItem(listSidOrUniqueName, itemIndex) { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Mutated list item data cannot be null" }
            mutator(currentData)
        }
    }

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncList.Item]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * list.mutateItemWithTtl<Counter>(0, ttl = 1.days) { counter -> counter + 1 }
 * ```
 *
 * @param listSidOrUniqueName       SID or unique name of existing [SyncList].
 * @param itemIndex                 Index of the item to mutate.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @param mutator                   Mutator which will be applied to list item data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 * @return                          [SyncList.Item] which has been set during mutation.
 * @throws SerializationException   If the [SyncList.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> Lists.mutateListItemWithTtl(
    listSidOrUniqueName: String,
    itemIndex: Long,
    ttl: Duration,
    crossinline mutator: suspend (currentData: T) -> T?
): SyncList.Item =
    mutateListItemWithTtl(listSidOrUniqueName, itemIndex, ttl) { jsonObject ->
        mutatorAdapter(jsonObject) { currentData: T? ->
            checkNotNull(currentData) { "Mutated list item data cannot be null" }
            mutator(currentData)
        }
    }

/**
 * Deserializes the [SyncMap.Item.data] into a value of type T using a deserializer retrieved
 * from reified type parameter.
 *
 * Type T must be annotated with @Serializable.
 *
 * @throws SerializationException   If the [SyncMap.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the decoded input cannot be represented as a valid instance of type T.
 */
inline fun <reified T : Any> SyncMap.Item.data(): T = json.decodeFromJsonElement(data)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncMap.Item].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param itemKey                   Key of the item to set.
 * @param itemData                  Item data to set.
 * @return                          [SyncMap.Item] which has been set.
 * @throws SerializationException   If the itemData cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> SyncMap.setItem(itemKey: String, itemData: T): SyncMap.Item =
    setItem(itemKey, json.encodeToJsonElement(itemData).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncMap.Item].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param itemKey                   Key of the item to set.
 * @param itemData                  Item data to set.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @return                          [SyncMap.Item] which has been set.
 * @throws SerializationException   If the itemData value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> SyncMap.setItemWithTtl(itemKey: String, itemData: T, ttl: Duration): SyncMap.Item =
    setItemWithTtl(itemKey, json.encodeToJsonElement(itemData).jsonObject, ttl)

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncMap.Item]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
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
 * @param itemKey                   Key of the item to mutate.
 * @param mutator                   Mutator which will be applied to map item data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 * @return                          [SyncMap.Item] which has been set during mutation.
 * @throws SerializationException   If the [SyncMap.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 *
 * @throws TwilioException          When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> SyncMap.mutateItem(
    itemKey: String,
    crossinline mutator: suspend (currentData: T?) -> T?
): SyncMap.Item =
    mutateItem(itemKey) { mutatorAdapter(it, mutator) }

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncMap.Item]
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
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
 * @param itemKey                   Key of the item to mutate.
 * @param ttl                       Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @param mutator                   Mutator which will be applied to map item data.
 *
 *                                  This function will be provided with the
 *                                  previous data contents and should return new desired contents or null to abort
 *                                  mutate operation. This function is invoked on a background thread.
 *
 * @return                          [SyncMap.Item] which has been set during mutation.
 * @throws SerializationException   If the [SyncMap.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the value returned by the Mutator cannot be represented
 *                                  as a valid instance of type T.
 * @throws TwilioException          When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> SyncMap.mutateItemWithTtl(
    itemKey: String,
    ttl: Duration,
    crossinline mutator: suspend (currentData: T?) -> T?
): SyncMap.Item =
    mutateItemWithTtl(itemKey, ttl) { mutatorAdapter(it, mutator) }

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncMap.Item] without opening the map.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param mapSidOrUniqueName            SID or unique name of existing [SyncMap].
 * @param itemKey                       Key of the item to set.
 * @param itemData                      Item data to set.
 * @return                              [SyncMap.Item] which has been set.
 * @throws SerializationException       If the given value cannot be serialized to JSON object.
 * @throws TwilioException              When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> Maps.setMapItem(
    mapSidOrUniqueName: String,
    itemKey: String,
    itemData: T
): SyncMap.Item =
    setMapItem(mapSidOrUniqueName, itemKey, json.encodeToJsonElement(itemData).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and set it as value of the [SyncMap.Item] without opening the map.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param mapSidOrUniqueName            SID or unique name of existing [SyncMap].
 * @param itemKey                       Key of the item to set.
 * @param itemData                      Item data to set.
 * @param ttl                           Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @return                              [SyncMap.Item] which has been set.
 * @throws SerializationException       If the given value cannot be serialized to JSON object.
 * @throws TwilioException              When error occurred while updating the item.
 */
suspend inline fun <reified T : Any> Maps.setMapItemWithTtl(
    mapSidOrUniqueName: String,
    itemKey: String,
    itemData: T,
    ttl: Duration
): SyncMap.Item =
    setMapItemWithTtl(mapSidOrUniqueName, itemKey, json.encodeToJsonElement(itemData).jsonObject, ttl)

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncMap.Item] without opening the map.
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
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
 * @param mapSidOrUniqueName            SID or unique name of existing [SyncMap].
 * @param itemKey                       Key of the item to set.
 * @param mutator                       Mutator which will be applied to map item data.
 *
 *                                      This function will be provided with the
 *                                      previous data contents and should return new desired contents or null to abort
 *                                      mutate operation. This function is invoked on a background thread.
 *
 * @return                              [SyncMap.Item] which has been set during mutation.
 * @throws SerializationException       If the [SyncMap.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException     If the value returned by the Mutator cannot be represented
 *                                      as a valid instance of type T.
 *
 * @throws TwilioException              When error occurred while mutating the item.
 */
suspend inline fun <reified T : Any> Maps.mutateMapItem(
    mapSidOrUniqueName: String,
    itemKey: String,
    crossinline mutator: suspend (currentData: T?) -> T?
): SyncMap.Item =
    mutateMapItem(mapSidOrUniqueName, itemKey) { mutatorAdapter(it, mutator) }

/**
 * Serializes the value returned by Mutator function into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and uses the JsonObject to mutate value of the [SyncMap.Item] without opening the map.
 *
 * The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
 * is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
 * on latest document data.
 *
 * Type T must be annotated with @Serializable.
 *
 * Possible use case is to implement distributed counter:
 *
 * ```
 * @Serializable
 * data class Counter(val value: Long = 0) {
 *     operator fun plus(x: Long) = Counter(value + x)
 * }
 *
 * map.mutateMapItemWithTtl<Counter>("counter", ttl = 1.days) { counter -> counter?.let { it + 1 } ?: Counter(1) }
 * ```
 *
 * @param mapSidOrUniqueName            SID or unique name of existing [SyncMap].
 * @param itemKey                       Key of the item to set.
 * @param ttl                           Time to live from now or [Duration.INFINITE] to indicate no expiry.
 * @param mutator                       Mutator which will be applied to document data.
 *
 *                                      This function will be provided with the
 *                                      previous data contents and should return new desired contents or null to abort
 *                                      mutate operation. This function is invoked on a background thread.
 *
 * @return                              [SyncMap.Item] which has been set during mutation.
 * @throws SerializationException       If the [SyncMap.Item.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException     If the value returned by the Mutator cannot be represented
 *                                      as a valid instance of type T.
 *
 * @throws TwilioException              When error occurred while mutating the item.
 */
suspend inline fun <reified T> Maps.mutateMapItemWithTtl(
    mapSidOrUniqueName: String,
    itemKey: String,
    ttl: Duration,
    crossinline mutator: suspend (currentData: T?) -> T?
): SyncMap.Item =
    mutateMapItemWithTtl(mapSidOrUniqueName, itemKey, ttl) { mutatorAdapter(it, mutator) }

@PublishedApi
internal suspend inline fun <reified T> mutatorAdapter(
    data: JsonObject?,
    crossinline mutator: suspend (currentData: T?) -> T?
): JsonObject? {
    val parsedData: T? = data?.let { json.decodeFromJsonElement(data) }
    val mutatedData: T = mutator(parsedData) ?: return null

    val jsonElement = json.encodeToJsonElement(mutatedData)
    return jsonElement.jsonObject
}

/**
 * Deserializes the [SyncStream.Message.data] into a value of type T using a deserializer retrieved
 * from reified type parameter.
 *
 * Type T must be annotated with @Serializable.
 *
 * @throws SerializationException   If the [SyncDocument.data] is not a valid JSON input for the type T.
 * @throws IllegalArgumentException If the decoded input cannot be represented as a valid instance of type T.
 */
inline fun <reified T : Any> SyncStream.Message.data(): T = json.decodeFromJsonElement(data)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and publish it as a new message to this [SyncStream].
 *
 * Type T must be annotated with @Serializable.
 *
 * @param data                      Contains the payload of the dispatched message.
 *                                  Maximum size in serialized JSON: 4KB.
 *
 * @throws SerializationException   If the given value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> SyncStream.publishMessage(data: T) =
    publishMessage(json.encodeToJsonElement(data).jsonObject)

/**
 * Serializes the given value into an equivalent JsonObject using a serializer retrieved from
 * reified type parameter and publish it as a new message to this [SyncStream] without opening it.
 *
 * Type T must be annotated with @Serializable.
 *
 * @param sidOrUniqueName           SID or unique name of existing [SyncStream].
 * @param data                      Contains the payload of the dispatched message.
 *                                  Maximum size in serialized JSON: 4KB.
 *
 * @throws SerializationException   If the given value cannot be serialized to JSON object.
 * @throws TwilioException          When error occurred while updating the document.
 */
suspend inline fun <reified T : Any> Streams.publishMessage(sidOrUniqueName: String, data: T) =
    publishMessage(sidOrUniqueName, json.encodeToJsonElement(data).jsonObject)
