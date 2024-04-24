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
import com.twilio.sync.entities.SyncDocument
import com.twilio.sync.entities.SyncList
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.entities.SyncStream
import com.twilio.util.TwilioException
import com.twilio.util.json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

@Throws(TwilioException::class, CancellationException::class)
suspend fun Streams.publishMessage(sidOrUniqueName: String, jsonData: String) =
    publishMessage(sidOrUniqueName, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncStream.publishMessage(jsonData: String) =
    publishMessage(jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun Documents.updateDocument(sidOrUniqueName: String, jsonData: String) =
    updateDocument(sidOrUniqueName, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun Documents.updateDocumentWithTtl(sidOrUniqueName: String, jsonData: String, ttl: Duration) =
    updateDocumentWithTtl(sidOrUniqueName, jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun Documents.mutateDocument(sidOrUniqueName: String, mutator: (String) -> String?) =
    mutateDocument(sidOrUniqueName) { currentData: JsonObject ->
        mutator(currentData.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun Documents.mutateDocumentWithTtl(sidOrUniqueName: String, ttl: Duration, mutator: (String) -> String?) =
    mutateDocumentWithTtl(sidOrUniqueName, ttl) { currentData: JsonObject ->
        mutator(currentData.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncDocument.setData(jsonData: String) =
    setData(jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncDocument.setDataWithTtl(jsonData: String, ttl: Duration) =
    setDataWithTtl(jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncDocument.mutateData(mutator: (String) -> String?) =
    mutateData { currentData: JsonObject ->
        mutator(currentData.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncDocument.mutateDataWithTtl(ttl: Duration, mutator: (String) -> String?) =
    mutateDataWithTtl(ttl) { currentData: JsonObject ->
        mutator(currentData.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun Maps.setMapItem(mapSidOrUniqueName: String, itemKey: String, jsonData: String): SyncMap.Item =
    setMapItem(mapSidOrUniqueName, itemKey, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun Maps.setMapItemWithTtl(
    mapSidOrUniqueName: String,
    itemKey: String,
    jsonData: String,
    ttl: Duration,
): SyncMap.Item =
    setMapItemWithTtl(mapSidOrUniqueName, itemKey, jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun Maps.mutateMapItem(
    mapSidOrUniqueName: String,
    itemKey: String,
    mutator: (String?) -> String?
): SyncMap.Item
    = mutateMapItem(mapSidOrUniqueName, itemKey) { currentData: JsonObject? ->
        mutator(currentData?.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun Maps.mutateMapItemWithTtl(
    mapSidOrUniqueName: String,
    itemKey: String,
    ttl: Duration,
    mutator: (String?) -> String?
): SyncMap.Item =
    mutateMapItemWithTtl(mapSidOrUniqueName, itemKey, ttl) { currentData: JsonObject? ->
        mutator(currentData?.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncMap.setItem(itemKey: String, jsonData: String): SyncMap.Item =
    setItem(itemKey, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncMap.setItemWithTtl(
    itemKey: String,
    jsonData: String,
    ttl: Duration,
): SyncMap.Item =
    setItemWithTtl(itemKey, jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncMap.mutateItem(
    itemKey: String,
    mutator: (String?) -> String?
): SyncMap.Item =
    mutateItem(itemKey) { currentData: JsonObject? ->
        mutator(currentData?.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncMap.mutateItemWithTtl(
    itemKey: String,
    ttl: Duration,
    mutator: (String?) -> String?
): SyncMap.Item =
    mutateItemWithTtl(itemKey, ttl) { currentData: JsonObject? ->
        mutator(currentData?.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun Lists.addListItem(listSidOrUniqueName: String, jsonData: String): SyncList.Item =
    addListItem(listSidOrUniqueName, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun Lists.addListItemWithTtl(listSidOrUniqueName: String, jsonData: String, ttl: Duration): SyncList.Item =
    addListItemWithTtl(listSidOrUniqueName, jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun Lists.setListItem(listSidOrUniqueName: String, itemIndex: Long, jsonData: String): SyncList.Item =
    setListItem(listSidOrUniqueName, itemIndex, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun Lists.setListItemWithTtl(
    listSidOrUniqueName: String,
    itemIndex: Long,
    jsonData: String,
    ttl: Duration,
): SyncList.Item =
    setListItemWithTtl(listSidOrUniqueName, itemIndex, jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun Lists.mutateListItem(
    listSidOrUniqueName: String,
    itemIndex: Long,
    mutator: (String) -> String?
): SyncList.Item
        = mutateListItem(listSidOrUniqueName, itemIndex) { currentData: JsonObject ->
    mutator(currentData.toString())?.let { json.decodeFromString(it) }
}

@Throws(TwilioException::class, CancellationException::class)
suspend fun Lists.mutateListItemWithTtl(
    listSidOrUniqueName: String,
    itemIndex: Long,
    ttl: Duration,
    mutator: (String) -> String?
): SyncList.Item =
    mutateListItemWithTtl(listSidOrUniqueName, itemIndex, ttl) { currentData: JsonObject ->
        mutator(currentData.toString())?.let { json.decodeFromString(it) }
    }

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncList.addItem(jsonData: String): SyncList.Item =
    addItem(jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncList.addItemWithTtl(jsonData: String, ttl: Duration): SyncList.Item =
    addItemWithTtl(jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncList.setItem(itemIndex: Long, jsonData: String): SyncList.Item =
    setItem(itemIndex, jsonFromString(jsonData))

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncList.setItemWithTtl(
    itemIndex: Long,
    jsonData: String,
    ttl: Duration,
): SyncList.Item =
    setItemWithTtl(itemIndex, jsonFromString(jsonData), ttl)

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncList.mutateItem(
    itemIndex: Long,
    mutator: (String) -> String?
): SyncList.Item
        = mutateItem(itemIndex) { currentData: JsonObject ->
    mutator(currentData.toString())?.let { json.decodeFromString(it) }
}

@Throws(TwilioException::class, CancellationException::class)
suspend fun SyncList.mutateItemWithTtl(
    itemIndex: Long,
    ttl: Duration,
    mutator: (String) -> String?
): SyncList.Item =
    mutateItemWithTtl(itemIndex, ttl) { currentData: JsonObject ->
        mutator(currentData.toString())?.let { json.decodeFromString(it) }
    }
