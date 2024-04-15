//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache

import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.ListCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.ListItemCacheData
import com.twilio.sync.sqldelight.cache.persistent.MapCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.MapItemCacheData
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

internal data class DocumentCacheMetadataPatch(
    val sid: String,
    val dateUpdated: Instant,
    val revision: String,
    val lastEventId: Long,
    val data: JsonObject?, // if null will NOT be patched (data is never null, backend replaces missing data with '{}')
    val uniqueName: String?, // if null will be patched to null (i.e. no uniqueName)
    val dateExpires: Instant?, // if null will be patched to null (i.e. never expires)
)

internal fun DocumentCacheMetadata.update(patch: DocumentCacheMetadataPatch): DocumentCacheMetadata {
    require(patch.sid == sid)

    return copy(
        dateUpdated = patch.dateUpdated,
        revision = patch.revision,
        lastEventId = patch.lastEventId,
        uniqueName = patch.uniqueName,
        dateExpires = patch.dateExpires,
        documentData = patch.data ?: documentData,
    )
}

internal fun CollectionMetadata.merge(other: CollectionMetadata?): CollectionMetadata = copy(
    isEmpty = other?.isEmpty ?: this.isEmpty,
    beginId = other?.beginId ?: this.beginId,
    endId = other?.endId ?: this.endId,
)

internal fun CollectionItemData.mergeBounds(other: CollectionItemData?): CollectionItemData {
    if (other == null || other.isRemoved) {
        return this
    }

    return copy(
        isLeftBound = this.isLeftBound && other.isLeftBound,
        isRightBound = this.isRightBound && other.isRightBound,
    )
}

internal fun CollectionItemData.fixDateCreated(other: CollectionItemData?): CollectionItemData =
    other?.let { copy(dateCreated = it.dateCreated) } ?: this

internal sealed interface CacheResult<T> {
    val data: T

    data class Added<T>(override val data: T) : CacheResult<T>
    data class Updated<T>(override val data: T) : CacheResult<T>
    data class Removed<T>(override val data: T) : CacheResult<T>
    data class NotModified<T>(override val data: T) : CacheResult<T>
}

internal fun ListCacheMetadata.toCollectionMetadata() = CollectionMetadata(
    collectionType = CollectionType.List,
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    isEmpty = isEmpty,
    beginId = beginIndex?.let { CollectionItemId.Index(it) },
    endId = endIndex?.let { CollectionItemId.Index(it) },
)

internal fun MapCacheMetadata.toCollectionMetadata() = CollectionMetadata(
    collectionType = CollectionType.Map,
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    isEmpty = isEmpty,
    beginId = beginKey?.let { CollectionItemId.Key(it) },
    endId = endKey?.let { CollectionItemId.Key(it) },
)

internal fun CollectionMetadata.toListCacheMetadata() = ListCacheMetadata(
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    isEmpty = isEmpty,
    beginIndex = beginId?.let { (it as CollectionItemId.Index).index },
    endIndex = endId?.let { (it as CollectionItemId.Index).index },
)

internal fun CollectionMetadata.toMapCacheMetadata() = MapCacheMetadata(
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    isEmpty = isEmpty,
    beginKey = beginId?.let { (it as CollectionItemId.Key).key },
    endKey = endId?.let { (it as CollectionItemId.Key).key },
)

internal fun ListItemCacheData.toCollectionItemData() = CollectionItemData(
    collectionSid = listSid,
    itemId = CollectionItemId.Index(itemIndex),
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    data = itemData,
    isLeftBound = isLeftBound,
    isRightBound = isRightBound,
    isRemoved = isRemoved,
)

internal fun MapItemCacheData.toCollectionItemData() = CollectionItemData(
    collectionSid = mapSid,
    itemId = CollectionItemId.Key(key),
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    data = itemData,
    isLeftBound = isLeftBound,
    isRightBound = isRightBound,
    isRemoved = isRemoved,
)

internal fun CollectionItemData.toListItemCacheData() = ListItemCacheData(
    listSid = collectionSid,
    itemIndex = (itemId as CollectionItemId.Index).index,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    itemData = data,
    isLeftBound = isLeftBound,
    isRightBound = isRightBound,
    isRemoved = isRemoved,
)

internal fun CollectionItemData.toMapItemCacheData() = MapItemCacheData(
    mapSid = collectionSid,
    key = (itemId as CollectionItemId.Key).key,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    itemData = data,
    isLeftBound = isLeftBound,
    isRightBound = isRightBound,
    isRemoved = isRemoved,
)
