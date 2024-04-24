//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.repository

import com.twilio.sync.cache.DocumentCacheMetadataPatch
import com.twilio.sync.operations.CollectionMetadataResponse
import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.sync.operations.StreamMetadataResponse
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionItemRemovedEvent
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType

internal fun StreamMetadataResponse.toStreamCacheMetadata() = StreamCacheMetadata(
    sid = sid,
    uniqueName = uniqueName,
    dateExpires = dateExpires,
)

internal fun DocumentMetadataResponse.toDocumentCacheMetadata() = DocumentCacheMetadata(
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    documentData = checkNotNull(data) {
        "null data in the response must be replaced with actual data before call toDocumentCacheMetadata()"
    }
)

internal fun DocumentMetadataResponse.toDocumentCacheMetadataPatch() = DocumentCacheMetadataPatch(
    sid = sid,
    uniqueName = uniqueName,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    data = data, // could be null here for a POST request response
)

internal fun DocumentUpdatedNotification.toDocumentCacheMetadataPatch() = DocumentCacheMetadataPatch(
    sid = documentSid,
    uniqueName = uniqueName,
    dateUpdated = dateCreated,
    dateExpires = dateExpires,
    data = data,
    revision = revision,
    lastEventId = eventId,
)

internal fun CollectionMetadataResponse.toCollectionMetadata(collectionType: CollectionType) = CollectionMetadata(
    collectionType = collectionType,
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    isEmpty = null,
    beginId = null,
    endId = null,
)

internal fun MapItemAddedNotification.toCollectionItemData() = CollectionItemData(
    collectionSid = mapSid,
    itemId = CollectionItemId.Key(key),
    dateCreated = dateCreated, // This is dateCreated of the event, but it matches dateCreated
    dateUpdated = dateCreated, // and dateUpdated of the just added item.
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = eventId,
    data = data,
    isLeftBound = true, // Bounds will be rewritten on put to cache anyway
    isRightBound = true,
    isRemoved = false,
)

// MapItemUpdatedNotification doesn't contain dateCreated of the item. So for now we use dateCreated of the event,
// which is not fully correct, because dateCreated of the event is the same with dateUpdated of the item,
// not dateCreated of the item. Could be fixed once SP-1843 is implemented.
internal fun MapItemUpdatedNotification.toCollectionItemData() = CollectionItemData(
    collectionSid = mapSid,
    itemId = CollectionItemId.Key(key),
    dateCreated = dateCreated, // TODO: change to item_date_created when SP-1843 is fixed
    dateUpdated = dateCreated, // The dateCreated of the event is the same with dateUpdated of the item.
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = eventId,
    data = data,
    isLeftBound = true, // Bounds will be rewritten on put to cache anyway
    isRightBound = true,
    isRemoved = false,
)

internal fun MapItemRemovedNotification.toCollectionItemRemovedEvent() = CollectionItemRemovedEvent(
    eventId = eventId,
    dateCreated = dateCreated,
    collectionSid = mapSid,
    itemId = CollectionItemId.Key(key),
)

internal fun ListItemAddedNotification.toCollectionItemData() = CollectionItemData(
    collectionSid = listSid,
    itemId = CollectionItemId.Index(index),
    dateCreated = dateCreated, // This is dateCreated of the event, but it matches dateCreated
    dateUpdated = dateCreated, // and dateUpdated of the just added item.
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = eventId,
    data = data,
    isLeftBound = true, // Bounds will be rewritten on put to cache anyway
    isRightBound = true,
    isRemoved = false,
)

// ListItemUpdatedNotification doesn't contain dateCreated of the item. So for now we use dateCreated of the event,
// which is not fully correct, because dateCreated of the event is the same with dateUpdated of the item,
// not dateCreated of the item. Could be fixed once SP-1843 is implemented.
internal fun ListItemUpdatedNotification.toCollectionItemData() = CollectionItemData(
    collectionSid = listSid,
    itemId = CollectionItemId.Index(index),
    dateCreated = dateCreated, // TODO: change to item_date_created when SP-1843 is fixed
    dateUpdated = dateCreated, // The dateCreated of the event is the same with dateUpdated of the item.
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = eventId,
    data = data,
    isLeftBound = true, // Bounds will be rewritten on put to cache anyway
    isRightBound = true,
    isRemoved = false,
)

internal fun ListItemRemovedNotification.toCollectionItemRemovedEvent() = CollectionItemRemovedEvent(
    eventId = eventId,
    dateCreated = dateCreated,
    collectionSid = listSid,
    itemId = CollectionItemId.Index(index),
)
