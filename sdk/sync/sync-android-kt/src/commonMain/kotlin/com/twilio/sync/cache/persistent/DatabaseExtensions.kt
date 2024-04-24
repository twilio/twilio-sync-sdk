//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.cache.persistent

import com.squareup.sqldelight.Query
import com.twilio.sync.cache.toListCacheMetadata
import com.twilio.sync.cache.toListItemCacheData
import com.twilio.sync.cache.toMapCacheMetadata
import com.twilio.sync.cache.toMapItemCacheData
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionItemId.Index
import com.twilio.sync.utils.CollectionItemId.Key
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.CollectionType.List
import com.twilio.sync.utils.CollectionType.Map
import com.twilio.sync.utils.EntitySid
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

internal val SyncDatabase.collectionMetadataQueries: CollectionMetadataQueries
    get() = CollectionMetadataQueries(this)

internal val SyncDatabase.collectionItemDataQueries: CollectionItemDataQueries
    get() = CollectionItemDataQueries(this)

internal class CollectionMetadataQueries(
    private val database: SyncDatabase
) {
    fun selectCount(
        collectionType: CollectionType,
    ): Query<Long> = when (collectionType) {
        List -> database.listMetadataQueries.selectCount()
        Map -> database.mapMetadataQueries.selectCount()
    }

    fun selectBySid(
        collectionType: CollectionType,
        collectionSid: EntitySid
    ): Query<CollectionMetadata> = when (collectionType) {
        List -> database.listMetadataQueries.selectBySid(collectionSid, listMetadataMapper)
        Map -> database.mapMetadataQueries.selectBySid(collectionSid, mapMetadataMapper)
    }

    fun selectByUniqueName(
        collectionType: CollectionType,
        collectionSid: EntitySid
    ): Query<CollectionMetadata> = when (collectionType) {
        List -> database.listMetadataQueries.selectByUniqueName(collectionSid, listMetadataMapper)
        Map -> database.mapMetadataQueries.selectByUniqueName(collectionSid, mapMetadataMapper)
    }

    fun selectBySidOrUniqueName(
        collectionType: CollectionType,
        collectionSid: EntitySid
    ): Query<CollectionMetadata> = when (collectionType) {
        List -> database.listMetadataQueries.selectBySidOrUniqueName(collectionSid, listMetadataMapper)
        Map -> database.mapMetadataQueries.selectBySidOrUniqueName(collectionSid, mapMetadataMapper)
    }

    fun upsert(collectionMetadata: CollectionMetadata) = when (collectionMetadata.collectionType) {
        List -> database.listMetadataQueries.upsert(collectionMetadata.toListCacheMetadata())
        Map -> database.mapMetadataQueries.upsert(collectionMetadata.toMapCacheMetadata())
    }

    fun deleteBySid(collectionType: CollectionType, collectionSid: EntitySid) = when (collectionType) {
        List -> database.listMetadataQueries.deleteBySid(collectionSid)
        Map -> database.mapMetadataQueries.deleteBySid(collectionSid)
    }

    fun updateIsEmpty(
        collectionType: CollectionType,
        collectionSid: EntitySid,
        isEmpty: Boolean?
    ) = when (collectionType) {
        List -> database.listMetadataQueries.updateIsEmpty(isEmpty, collectionSid)
        Map -> database.mapMetadataQueries.updateIsEmpty(isEmpty, collectionSid)
    }

    fun updateBeginId(collectionSid: EntitySid, itemId: CollectionItemId) = when (itemId) {
        is Index -> database.listMetadataQueries.updateBeginIndex(itemId.index, collectionSid)
        is Key -> database.mapMetadataQueries.updateBeginKey(itemId.key, collectionSid)
    }

    fun updateEndId(collectionSid: EntitySid, itemId: CollectionItemId) = when (itemId) {
        is Index -> database.listMetadataQueries.updateEndIndex(itemId.index, collectionSid)
        is Key -> database.mapMetadataQueries.updateEndKey(itemId.key, collectionSid)
    }

    fun resetBeginIdIfGreater(collectionSid: EntitySid, itemId: CollectionItemId) = when (itemId) {
        is Index -> database.listMetadataQueries.resetBeginIndexIfGreater(collectionSid, itemId.index)
        is Key -> database.mapMetadataQueries.resetBeginKeyIfGreater(collectionSid, itemId.key)
    }

    fun resetEndIdIfLess(collectionSid: EntitySid, itemId: CollectionItemId) = when (itemId) {
        is Index -> database.listMetadataQueries.resetEndIndexIfLess(collectionSid, itemId.index)
        is Key -> database.mapMetadataQueries.resetEndKeyIfLess(collectionSid, itemId.key)
    }

    fun resetBeginIdIfEquals(collectionSid: EntitySid, itemId: CollectionItemId) = when (itemId) {
        is Index -> database.listMetadataQueries.resetBeginIndexIfEquals(collectionSid, itemId.index)
        is Key -> database.mapMetadataQueries.resetBeginKeyIfEquals(collectionSid, itemId.key)
    }

    fun resetEndIdIfEquals(collectionSid: EntitySid, itemId: CollectionItemId) = when (itemId) {
        is Index -> database.listMetadataQueries.resetEndIndexIfEquals(collectionSid, itemId.index)
        is Key -> database.mapMetadataQueries.resetEndKeyIfEquals(collectionSid, itemId.key)
    }

    fun updateLastEventIdIfNewer(
        collectionType: CollectionType,
        collectionSid: EntitySid,
        lastEventId: Long
    ) = when (collectionType) {
        List -> database.listMetadataQueries.updateLastEventIdIfNewer(lastEventId, collectionSid)
        Map -> database.mapMetadataQueries.updateLastEventIdIfNewer(lastEventId, collectionSid)
    }

    fun updateDateUpdatedIfLater(
        collectionType: CollectionType,
        collectionSid: EntitySid,
        dateUpdated: Instant,
    ) = when (collectionType) {
        List -> database.listMetadataQueries.updateDateUpdatedIfLater(dateUpdated, collectionSid)
        Map -> database.mapMetadataQueries.updateDateUpdatedIfLater(dateUpdated, collectionSid)
    }

    fun cleanupWithLimit(
        collectionType: CollectionType,
        limit: Long,
    ) = when (collectionType) {
        List -> database.listMetadataQueries.cleanupWithLimit(limit)
        Map -> database.mapMetadataQueries.cleanupWithLimit(limit)
    }
}

internal class CollectionItemDataQueries(
    private val database: SyncDatabase
) {
    fun selectOne(collectionSid: EntitySid, itemId: CollectionItemId): Query<CollectionItemData> = when (itemId) {
        is Index -> database.listItemDataQueries.selectOne(collectionSid, itemId.index, listItemMapper)
        is Key -> database.mapItemDataQueries.selectOne(collectionSid, itemId.key, mapItemMapper)
    }

    fun selectNext(collectionSid: EntitySid, itemId: CollectionItemId): Query<CollectionItemData> = when (itemId) {
        is Index -> database.listItemDataQueries.selectNext(collectionSid, itemId.index, listItemMapper)
        is Key -> database.mapItemDataQueries.selectNext(collectionSid, itemId.key, mapItemMapper)
    }

    fun selectPrev(collectionSid: EntitySid, itemId: CollectionItemId): Query<CollectionItemData> = when (itemId) {
        is Index -> database.listItemDataQueries.selectPrev(collectionSid, itemId.index, listItemMapper)
        is Key -> database.mapItemDataQueries.selectPrev(collectionSid, itemId.key, mapItemMapper)
    }

    fun upsert(item: CollectionItemData) = when (item.itemId) {
        is Index -> database.listItemDataQueries.upsert(item.toListItemCacheData())
        is Key -> database.mapItemDataQueries.upsert(item.toMapItemCacheData())
    }

    fun deleteAllByCollectionSid(collectionType: CollectionType, collectionSid: EntitySid) = when (collectionType) {
        List -> database.listItemDataQueries.deleteAllByListSid(collectionSid)
        Map -> database.mapItemDataQueries.deleteAllByMapSid(collectionSid)
    }
}

private val listMetadataMapper = {
        sid: String,
        uniqueName: String?,
        dateCreated: Instant,
        dateUpdated: Instant,
        dateExpires: Instant?,
        revision: String,
        lastEventId: Long,
        isEmpty: Boolean?,
        beginIndex: Long?,
        endIndex: Long? ->

    CollectionMetadata(
        collectionType = List,
        sid = sid,
        uniqueName = uniqueName,
        dateCreated = dateCreated,
        dateUpdated = dateUpdated,
        dateExpires = dateExpires,
        revision = revision,
        lastEventId = lastEventId,
        isEmpty = isEmpty,
        beginId = beginIndex?.let { Index(it) },
        endId = endIndex?.let { Index(it) },
    )
}

private val mapMetadataMapper = {
        sid: String,
        uniqueName: String?,
        dateCreated: Instant,
        dateUpdated: Instant,
        dateExpires: Instant?,
        revision: String,
        lastEventId: Long,
        isEmpty: Boolean?,
        beginKey: String?,
        endKey: String? ->

    CollectionMetadata(
        collectionType = Map,
        sid = sid,
        uniqueName = uniqueName,
        dateCreated = dateCreated,
        dateUpdated = dateUpdated,
        dateExpires = dateExpires,
        revision = revision,
        lastEventId = lastEventId,
        isEmpty = isEmpty,
        beginId = beginKey?.let { Key(it) },
        endId = endKey?.let { Key(it) },
    )
}

private val listItemMapper = {
        listSid: String,
        index: Long,
        dateCreated: Instant,
        dateUpdated: Instant,
        dateExpires: Instant?,
        revision: String,
        lastEventId: Long,
        itemData: JsonObject,
        isLeftBound: Boolean,
        isRightBound: Boolean,
        isRemoved: Boolean ->

    CollectionItemData(
        collectionSid = listSid,
        itemId = Index(index),
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
}

private val mapItemMapper = {
        mapSid: String,
        key: String,
        dateCreated: Instant,
        dateUpdated: Instant,
        dateExpires: Instant?,
        revision: String,
        lastEventId: Long,
        itemData: JsonObject,
        isLeftBound: Boolean,
        isRightBound: Boolean,
        isRemoved: Boolean ->

    CollectionItemData(
        collectionSid = mapSid,
        itemId = Key(key),
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
}
