//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.twilio.sync.cache.persistent.DatabaseRegistry
import com.twilio.sync.cache.persistent.SyncDatabase
import com.twilio.sync.cache.persistent.collectionItemDataQueries
import com.twilio.sync.cache.persistent.collectionMetadataQueries
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.Links
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.collectionType
import com.twilio.util.emptyJsonObject
import com.twilio.util.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.math.max

internal class SyncCache(
    private val databaseName: String,
    private val driver: SqlDriver,
) {
    private val database: SyncDatabase

    init {
        DatabaseRegistry.registerDatabase(databaseName)
        database = SyncDatabase(driver)
    }

    fun close() {
        driver.close()
        DatabaseRegistry.unregisterDatabase(databaseName)
    }

    suspend inline fun getOrPutLinks(crossinline getLinks: suspend () -> Links): Links =
        withContext(Dispatchers.Default) {
            database.linksQueries.get()
                .executeAsOneOrNull()
                ?.let { return@withContext it }

            val links = getLinks()
            database.linksQueries.upsert(links)
            return@withContext links
        }

    suspend fun getStreamsCount(): Long = withContext(Dispatchers.Default) {
        database.streamMetadataQueries.selectCount().executeAsOne()
    }

    suspend fun cleanupStreamsTable(): Boolean = withContext(Dispatchers.Default) {
        cleanupTable(
            selectCount = { database.streamMetadataQueries.selectCount().executeAsOne() },
            cleanupWithLimit = { database.streamMetadataQueries.cleanupWithLimit(it) },
        )
    }

    fun getStreamMetadataBySid(streamSid: EntitySid): Flow<StreamCacheMetadata?> =
        database.streamMetadataQueries
            .selectBySid(streamSid)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged() // Unfortunately streams metadata doesn't contain lastEventId at all.
                                    // So we cannot just compare lastEventIds here as we do for documents.

    fun getStreamMetadataByUniqueName(uniqueName: String): Flow<StreamCacheMetadata?> =
        database.streamMetadataQueries
            .selectByUniqueName(uniqueName)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged() // Unfortunately streams metadata doesn't contain lastEventId at all.
                                    // So we cannot just compare lastEventIds here as we do for documents.

    suspend fun deleteStreamBySid(streamSid: EntitySid) = withContext(Dispatchers.Default) {
        logger.d { "deleteStreamBySid: $streamSid" }
        database.streamMetadataQueries.deleteBySid(streamSid)
    }

    suspend fun deleteStreamBySidOrUniqueName(sidOrUniqueName: String) = withContext(Dispatchers.Default) {
        logger.d { "deleteStreamBySidOrUniqueName: $sidOrUniqueName" }
        database.streamMetadataQueries.deleteBySidOrUniqueName(sidOrUniqueName)
    }

    suspend fun put(data: StreamCacheMetadata) = withContext(Dispatchers.Default) {
        logger.d { "put: $data" }
        database.streamMetadataQueries.upsert(data)
    }

    suspend fun put(
        metadata: DocumentCacheMetadata
    ): DocumentCacheMetadata = withContext(Dispatchers.Default) {
        logger.d { "put: $metadata" }

        return@withContext database.transactionWithResult {
            database.documentMetadataQueries
                .selectBySid(metadata.sid)
                .executeAsOneOrNull()
                ?.takeIf { it.lastEventId >= metadata.lastEventId }
                ?.let { latestMetadata ->
                    logger.i {
                        "put DocumentCacheMetadata with eventId [${metadata.lastEventId}] skipped. " +
                                "Current lastEventId [${latestMetadata.lastEventId}]"
                    }
                    return@transactionWithResult latestMetadata
                }

            database.documentMetadataQueries.upsert(metadata)
            return@transactionWithResult metadata
        }
    }

    suspend fun updateDocumentMetadata(
        patch: DocumentCacheMetadataPatch
    ): DocumentCacheMetadata? = withContext(Dispatchers.Default) {
        logger.d { "updateDocumentData: $patch" }

        val documentSid = patch.sid

        return@withContext database.transactionWithResult {
            val metadata = database.documentMetadataQueries.selectBySid(documentSid).executeAsOneOrNull() ?: run {
                logger.w { "updateDocumentData skipped: Metadata for the documentSid [$documentSid] not found" }
                return@transactionWithResult null
            }

            if (patch.lastEventId <= metadata.lastEventId) {
                logger.i { "updateDocumentData skipped. Current lastEventId [${metadata.lastEventId}] $patch" }
                return@transactionWithResult metadata
            }

            val updatedMetadata = metadata.update(patch)
            database.documentMetadataQueries.upsert(updatedMetadata)
            return@transactionWithResult updatedMetadata
        }
    }

    suspend fun getDocumentsCount(): Long = withContext(Dispatchers.Default) {
        database.documentMetadataQueries.selectCount().executeAsOne()
    }

    suspend fun cleanupDocumentsTable(): Boolean = withContext(Dispatchers.Default) {
        cleanupTable(
            selectCount = { database.documentMetadataQueries.selectCount().executeAsOne() },
            cleanupWithLimit = { database.documentMetadataQueries.cleanupWithLimit(it) },
        )
    }

    fun getDocumentMetadataBySid(documentSid: EntitySid): Flow<DocumentCacheMetadata?> =
        database.documentMetadataQueries
            .selectBySid(documentSid)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChangedBy { it?.lastEventId }

    fun getDocumentMetadataByUniqueName(uniqueName: String): Flow<DocumentCacheMetadata?> =
        database.documentMetadataQueries
            .selectByUniqueName(uniqueName)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChangedBy { it?.lastEventId }

    suspend fun deleteDocumentBySid(documentSid: EntitySid) = withContext(Dispatchers.Default) {
        logger.d { "deleteDocumentBySid: $documentSid" }
        database.documentMetadataQueries.deleteBySid(documentSid)
    }

    suspend fun deleteDocumentBySidOrUniqueName(sidOrUniqueName: String) = withContext(Dispatchers.Default) {
        logger.d { "deleteDocumentBySidOrUniqueName: $sidOrUniqueName" }
        database.documentMetadataQueries.deleteBySidOrUniqueName(sidOrUniqueName)
    }

    suspend fun put(
        metadata: CollectionMetadata
    ): CollectionMetadata = withContext(Dispatchers.Default) {
        logger.d { "put: $metadata" }

        return@withContext database.transactionWithResult {
            val cachedMetadata = database.collectionMetadataQueries
                .selectBySid(metadata.collectionType, metadata.sid)
                .executeAsOneOrNull()

            if (cachedMetadata != null && cachedMetadata.lastEventId > metadata.lastEventId) {
                logger.i {
                    "put CollectionMetadata with eventId [${metadata.lastEventId}] skipped. " +
                            "Current lastEventId [${cachedMetadata.lastEventId}]"
                }
                return@transactionWithResult cachedMetadata
            }

            val mergedMetadata = metadata.merge(cachedMetadata)
            database.collectionMetadataQueries.upsert(mergedMetadata)
            return@transactionWithResult mergedMetadata
        }
    }

    suspend fun put(
        item: CollectionItemData,
        updateMetadataLastEventId: Boolean,
    ): CacheResult<CollectionItemData> = withContext(Dispatchers.Default) {
        logger.d { "put: $item" }

        val boundedItem = item.copy(isLeftBound = true, isRightBound = true)

        return@withContext database.transactionWithResult {
            putInternal(boundedItem, updateMetadataLastEventId)
        }
    }

    suspend fun put(
        collectionType: CollectionType,
        collectionSid: EntitySid,
        items: List<CollectionItemData>,
        updateMetadataLastEventId: Boolean,
        isCollectionEmpty: Boolean,
        beginId: CollectionItemId? = null, // first item in collection if known, `null` value is ignored
        endId: CollectionItemId? = null,   // last item in collection if known, `null` value is ignored
    ): List<CacheResult<CollectionItemData>> = withContext(Dispatchers.Default) {
        logger.d { "put sid: $collectionSid; itemsCount: ${items.size}; beginId: ${beginId?.id}; endId: ${endId?.id}" }

        val boundedItems = items.mapIndexed { index, item ->
            item.copy(
                isLeftBound = index == 0,
                isRightBound = index == items.size - 1,
            )
        }

        return@withContext database.transactionWithResult {
            database.collectionMetadataQueries.updateIsEmpty(collectionType, collectionSid, isCollectionEmpty)
            val result = boundedItems.map { putInternal(it, updateMetadataLastEventId) }

            beginId?.let { database.collectionMetadataQueries.updateBeginId(collectionSid, it) }
            endId?.let { database.collectionMetadataQueries.updateEndId(collectionSid, it) }

            return@transactionWithResult result
        }
    }

    // This method must always be called from a transaction
    private fun putInternal(
        data: CollectionItemData,
        updateMetadataLastEventId: Boolean
    ): CacheResult<CollectionItemData> {

        val cachedItem = database.collectionItemDataQueries
            .selectOne(data.collectionSid, data.itemId)
            .executeAsOneOrNull()

        if (cachedItem != null && cachedItem.lastEventId >= data.lastEventId) {
            logger.i {
                "put CollectionItemData with eventId [${data.lastEventId}] skipped. " +
                        "Current lastEventId [${cachedItem.lastEventId}]"
            }

            val boundedItem = cachedItem
                .mergeBounds(data) // update bounds anyway

            database.collectionItemDataQueries.upsert(boundedItem)
            return CacheResult.NotModified(boundedItem)
        }

        if (cachedItem == null || cachedItem.isRemoved) { // so we add new item
            if (data.isLeftBound) {
                database.collectionItemDataQueries.selectPrev(data.collectionSid, data.itemId).executeAsOneOrNull()
                    ?.let { prevItem ->
                        val boundedItem = prevItem.copy(isRightBound = true)
                        database.collectionItemDataQueries.upsert(boundedItem)
                    }
            }

            if (data.isRightBound) {
                database.collectionItemDataQueries.selectNext(data.collectionSid, data.itemId).executeAsOneOrNull()
                    ?.let { nextItem ->
                        val boundedItem = nextItem.copy(isLeftBound = true)
                        database.collectionItemDataQueries.upsert(boundedItem)
                    }
            }
        }

        with(database.collectionMetadataQueries) {
            when {
                cachedItem == null -> {
                    resetBeginIdIfGreater(data.collectionSid, data.itemId)
                    resetEndIdIfLess(data.collectionSid, data.itemId)
                }

                cachedItem.isRemoved -> {
                    // Added item was removed before.
                    // Here we have no idea if more items has been added in the begin or not.
                    // So we reset the beginId in the collection in order to request it from backend next time.
                    resetBeginIdIfEquals(data.collectionSid, data.itemId)
                    // Here we have no idea if more items has been added in the end or not
                    // So we reset the endId in the collection in order to request it from backend next time.
                    resetEndIdIfEquals(data.collectionSid, data.itemId)
                }
            }

            if (updateMetadataLastEventId) {
                // lastEventId in collection's metadata should be updated only when the update is initiated by remote event.
                // Otherwise we could skip remote events after reconnection.
                updateLastEventIdIfNewer(data.collectionType, data.collectionSid, data.lastEventId)
            }

            updateDateUpdatedIfLater(data.collectionType, data.collectionSid, data.dateUpdated)

            if (!data.isRemoved) {
                // We are putting an item into collection. So the collection is not empty now.
                updateIsEmpty(data.collectionType, data.collectionSid, isEmpty = false)
            }
        }

        val boundedItem = data.mergeBounds(cachedItem)
            .fixDateCreated(cachedItem) // Do not use dateCreated from the map_item_updated remote event
                                        // if we already have it stored in cache.
                                        // Remove this line once SP-1843 is fixed.

        database.collectionItemDataQueries.upsert(boundedItem)

        return when {
            data.isRemoved -> CacheResult.Removed(boundedItem)

            cachedItem == null || cachedItem.isRemoved -> CacheResult.Added(boundedItem)

            else -> CacheResult.Updated(boundedItem)
        }
    }

    suspend fun getCollectionsCount(collectionType: CollectionType): Long = withContext(Dispatchers.Default) {
        database.collectionMetadataQueries.selectCount(collectionType).executeAsOne()
    }

    suspend fun cleanupCollectionsTable(collectionType: CollectionType): Boolean = withContext(Dispatchers.Default) {
        cleanupTable(
            selectCount = { database.collectionMetadataQueries.selectCount(collectionType).executeAsOne() },
            cleanupWithLimit = { database.collectionMetadataQueries.cleanupWithLimit(collectionType, it) },
        )
    }

    fun getCollectionMetadataBySid(collectionType: CollectionType, collectionSid: EntitySid): Flow<CollectionMetadata?> =
        database.collectionMetadataQueries
            .selectBySid(collectionType, collectionSid)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged() // Collections doesn't update lastEventId when dateExpires updates.
                                    // So we cannot just compare lastEventIds here as we do for documents.
                                    // Change to distinctUntilChangedBy { it?.lastEventId } once SP-1894 is fixed.

    fun getCollectionMetadataByUniqueName(collectionType: CollectionType, uniqueName: String): Flow<CollectionMetadata?> =
        database.collectionMetadataQueries
            .selectByUniqueName(collectionType, uniqueName)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged() // Collections doesn't update lastEventId when dateExpires updates.
                                    // So we cannot just compare lastEventIds here as we do for documents.
                                    // Change to distinctUntilChangedBy { it?.lastEventId } once SP-1894 is fixed.

    suspend fun deleteCollectionBySidOrUniqueName(
        collectionType: CollectionType,
        sidOrUniqueName: String
    ) = withContext(Dispatchers.Default) {
        logger.d { "deleteCollectionBySidOrUniqueName: $sidOrUniqueName" }

        database.transaction {
            val metadata = database.collectionMetadataQueries
                .selectBySidOrUniqueName(collectionType, sidOrUniqueName)
                .executeAsOneOrNull()
                ?: return@transaction

            database.collectionItemDataQueries.deleteAllByCollectionSid(collectionType, metadata.sid)
            database.collectionMetadataQueries.deleteBySid(collectionType, metadata.sid)
        }
    }

    suspend fun getCollectionItemData(collectionSid: EntitySid, itemId: CollectionItemId): CollectionItemData? =
        withContext(Dispatchers.Default) {
            database.collectionItemDataQueries.selectOne(collectionSid, itemId).executeAsOneOrNull()
        }

    suspend fun deleteCollectionItem(
        collectionSid: String,
        itemId: CollectionItemId,
        eventId: Long,
        dateUpdated: Instant,
        updateMetadataLastEventId: Boolean,
    ): CacheResult<CollectionItemData> =
        withContext(Dispatchers.Default) {
            logger.d { "deleteCollectionItem: $collectionSid ${itemId.id}" }

            database.transactionWithResult {
                val item = database.collectionItemDataQueries.selectOne(collectionSid, itemId).executeAsOneOrNull()

                val removedItem = item?.copy(isRemoved = true, lastEventId = eventId, dateUpdated = dateUpdated)

                if (removedItem != null) {
                    return@transactionWithResult putInternal(removedItem, updateMetadataLastEventId)
                }

                // item is not cached, but map_item_removed event arrived (probably
                // because of misordered events from backend). So we put fake
                // removed item into cache in order to store eventId and ignore
                // following map_item_added event with smaller eventId

                val fakeItem = CollectionItemData(
                    collectionSid = collectionSid,
                    itemId = itemId,
                    dateCreated = dateUpdated,
                    dateUpdated = dateUpdated,
                    dateExpires = null,
                    revision = "",
                    lastEventId = eventId,
                    data = emptyJsonObject(),
                    isLeftBound = true,
                    isRightBound = true,
                    isRemoved = true,
                )

                logger.d { "deleteCollectionItem: item not found in cache. Putting fake item: $fakeItem" }
                putInternal(fakeItem, updateMetadataLastEventId)

                // We return NotModified here because the item wasn't in cache. So technically we didn't remove it.
                return@transactionWithResult CacheResult.NotModified(fakeItem)
            }
        }

    suspend fun getNextCollectionItemData(
        collectionType: CollectionType,
        collectionSid: EntitySid,
        itemId: CollectionItemId?
    ): CollectionItemData? = withContext(Dispatchers.Default) {
        logger.d { "getNextCollectionItemData: $collectionSid $itemId" }

        database.transactionWithResult {
            val query = if (itemId == null) {
                val metadata = database.collectionMetadataQueries
                    .selectBySid(collectionType, collectionSid)
                    .executeAsOneOrNull()
                    ?: run {
                        logger.w { "getNextCollectionItemData: cannot find metadata for $collectionSid" }
                        return@transactionWithResult null
                    }

                if (metadata.beginId == null) {
                    logger.d { "getNextCollectionItemData: beginId for $collectionSid is not in cached" }
                    return@transactionWithResult null
                }

                database.collectionItemDataQueries.selectOne(collectionSid, metadata.beginId)
            } else {
                val item = database.collectionItemDataQueries.selectOne(collectionSid, itemId).executeAsOneOrNull()
                if (item != null && item.isRightBound) {
                    logger.d {
                        "getNextCollectionItemData: item with id ${itemId.id} is rightBound. " +
                                "Cannot return next item from cache (not sure if it's in cache or not)."
                    }
                    return@transactionWithResult null
                }

                database.collectionItemDataQueries.selectNext(collectionSid, itemId)
            }

            val item = query.executeAsOneOrNull()

            logger.d { "getNextCollectionItemData: $collectionSid ${itemId?.id} return: ${item?.itemId?.id}" }
            return@transactionWithResult item
        }
    }

    suspend fun getPrevCollectionItemData(
        collectionType: CollectionType,
        collectionSid: EntitySid,
        itemId: CollectionItemId?
    ): CollectionItemData? = withContext(Dispatchers.Default) {
        logger.d { "getPrevCollectionItemData: $collectionSid $itemId" }

        database.transactionWithResult {

            val query = if (itemId == null) {
                val metadata = database.collectionMetadataQueries
                    .selectBySid(collectionType, collectionSid)
                    .executeAsOneOrNull()
                    ?: run {
                        logger.w { "getPrevCollectionItemData: cannot find metadata for $collectionSid" }
                        return@transactionWithResult null
                    }

                if (metadata.endId == null) {
                    logger.d { "getPrevCollectionItemData: endId for $collectionSid is not in cached" }
                    return@transactionWithResult null
                }

                database.collectionItemDataQueries.selectOne(collectionSid, metadata.endId)
            } else {
                val item = database.collectionItemDataQueries.selectOne(collectionSid, itemId).executeAsOneOrNull()
                if (item != null && item.isLeftBound) {
                    logger.d {
                        "getPrevCollectionItemData: item with id ${itemId.id} is leftBound. " +
                                "Cannot return prev item from cache (not sure if it's in cache or not)."
                    }

                    return@transactionWithResult null
                }

                database.collectionItemDataQueries.selectPrev(collectionSid, itemId)
            }

            val item = query.executeAsOneOrNull()

            logger.d { "getPrevCollectionItemData: $collectionSid ${itemId?.id} return: ${item?.itemId?.id}" }
            return@transactionWithResult item
        }
    }

    suspend fun shrinkDatabase() = withContext(Dispatchers.Default) {
        logger.d { "shrinkDatabase" }
        database.vacuumQueries.vacuum()
    }

    private inline fun cleanupTable(
        crossinline selectCount: () -> Long,
        crossinline cleanupWithLimit: (Long) -> Unit,
    ): Boolean = database.transactionWithResult {
        val count = selectCount()

        if (count == 0L) {
            logger.d { "cleanupTable: Table is empty, nothing to cleanup" }
            return@transactionWithResult false
        }

        val cleanupLimit = max(100, count / 2)
        logger.d { "cleanupWithLimit: $cleanupLimit; count = $count" }

        cleanupWithLimit(cleanupLimit)
        return@transactionWithResult true
    }
}
