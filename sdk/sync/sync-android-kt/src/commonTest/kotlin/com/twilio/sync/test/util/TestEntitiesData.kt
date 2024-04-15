//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.util

import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.EntitySid
import com.twilio.util.emptyJsonObject
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class TestData(val value: Int)

internal inline fun <T> MutableList<T>.replaceAll(transform: (T) -> T) {
    for (i in indices) {
        this[i] = transform(this[i])
    }
}

internal fun Int.toString(width: Int = 0): String {
    val str = this.toString()
    val padding = "0".repeat(width - str.length)
    return padding + str
}

internal fun List<CollectionItemData>.setRangeBounds() = map { item ->
    item.copy(
        isLeftBound = item.itemId == first().itemId,
        isRightBound = item.itemId == last().itemId
    )
}

internal fun testDocumentMetadata(sid: EntitySid, lastEventId: Long = 0) = DocumentCacheMetadata(
    sid = sid,
    uniqueName = null,
    dateCreated = Instant.fromEpochMilliseconds(0),
    dateUpdated = Instant.fromEpochMilliseconds(0),
    dateExpires = null,
    revision = "$lastEventId",
    lastEventId = lastEventId,
    documentData = emptyJsonObject(),
)

internal fun testStreamMetadata(sid: EntitySid) = StreamCacheMetadata(
    sid = sid,
    uniqueName = null,
    dateExpires = null,
)

internal fun testCollectionMetadata(
    sid: EntitySid,
    lastEventId: Long = 0,
    collectionType: CollectionType = CollectionType.Map
) = CollectionMetadata(
    collectionType = collectionType,
    sid = sid,
    uniqueName = null,
    dateCreated = Instant.fromEpochMilliseconds(0),
    dateUpdated = Instant.fromEpochMilliseconds(0),
    dateExpires = null,
    revision = "$lastEventId",
    lastEventId = lastEventId,
    isEmpty = null,
    beginId = null,
    endId = null
)

internal fun testCollectionItem(mapSid: EntitySid, key: String, lastEventId: Long = 0) = CollectionItemData(
    collectionSid = mapSid,
    itemId = CollectionItemId.Key(key),
    dateCreated = Instant.fromEpochMilliseconds(0),
    dateUpdated = Instant.fromEpochMilliseconds(0),
    dateExpires = null,
    revision = "$lastEventId",
    lastEventId = lastEventId,
    data = emptyJsonObject(),
    isLeftBound = true,
    isRightBound = true,
    isRemoved = false,
)

internal fun testCollectionItem(listSid: EntitySid, index: Long, lastEventId: Long = 0) = CollectionItemData(
    collectionSid = listSid,
    itemId = CollectionItemId.Index(index),
    dateCreated = Instant.fromEpochMilliseconds(0),
    dateUpdated = Instant.fromEpochMilliseconds(0),
    dateExpires = null,
    revision = "$lastEventId",
    lastEventId = lastEventId,
    data = emptyJsonObject(),
    isLeftBound = true,
    isRightBound = true,
    isRemoved = false,
)
