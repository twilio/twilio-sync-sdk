//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.repository

import com.twilio.sync.cache.CacheResult
import com.twilio.sync.cache.SyncCache
import com.twilio.sync.operations.BaseMutateOperation
import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.sync.operations.InvokeMutator
import com.twilio.sync.operations.SyncOperationsFactory
import com.twilio.sync.operations.beginCollectionItemIdOrNull
import com.twilio.sync.operations.endCollectionItemIdOrNull
import com.twilio.sync.operations.isCollectionEmpty
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.subscriptions.RemoteEvent
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionItemRemovedEvent
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.QueryOrder.Ascending
import com.twilio.sync.utils.QueryOrder.Descending
import com.twilio.sync.utils.collectionType
import com.twilio.sync.utils.isItemNotFound
import com.twilio.sync.utils.isKeyAlreadyExist
import com.twilio.sync.utils.isNameAlreadyExist
import com.twilio.twilsock.commands.CommandsScheduler
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.TwilioException
import com.twilio.util.emptyJsonObject
import com.twilio.util.logger
import com.twilio.util.toTwilioException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal sealed interface OpenOptions {
    data class CreateNew(val uniqueName: String?, val ttl: Duration = Duration.INFINITE) : OpenOptions
    data class OpenOrCreate(val uniqueName: String, val ttl: Duration = Duration.INFINITE) : OpenOptions
    data class OpenExisting(val sidOrUniqueName: String) : OpenOptions
}

internal class SyncRepository(
    private val coroutineScope: CoroutineScope,
    private val syncCache: SyncCache,
    private val commandsScheduler: CommandsScheduler,
    private val remoteEventsFlow: SharedFlow<RemoteEvent>,
    private val operationsFactory: SyncOperationsFactory,
    private val links: ConfigurationLinks,
) {
    private val notificationsFlow = remoteEventsFlow
        .parseNotification()
        .onEach { logger.d { "New notification received: $it" } }
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    private val collectionItemAddedFlow = MutableSharedFlow<CollectionItemData>()

    fun onCollectionItemAdded(collectionSid: EntitySid): Flow<CollectionItemData> =
        collectionItemAddedFlow.filter { it.collectionSid == collectionSid }

    private val collectionItemUpdatedFlow = MutableSharedFlow<CollectionItemData>()

    fun onCollectionItemUpdated(collectionSid: EntitySid): Flow<CollectionItemData> =
        collectionItemUpdatedFlow.filter { it.collectionSid == collectionSid }

    private val collectionItemRemovedFlow = MutableSharedFlow<CollectionItemData>()

    fun onCollectionItemRemoved(collectionSid: EntitySid): Flow<CollectionItemData> =
        collectionItemRemovedFlow.filter { it.collectionSid == collectionSid }

    init {
        coroutineScope.launch {
            notificationsFlow.collect { handleRemoteNotification(it) }
        }
    }

    private suspend fun handleRemoteNotification(notification: Notification) {
        try {
            when (notification) {
                /** We don't store stream messages in cache. So this is handled directly in [getStreamMessages]. */
                is StreamMessagePublishedNotification -> Unit

                is StreamRemovedNotification -> handleStreamRemovedNotification(notification)

                is DocumentUpdatedNotification -> handleDocumentUpdatedNotification(notification)

                is DocumentRemovedNotification -> handleDocumentRemovedNotification(notification)

                is MapItemAddedNotification -> handleCollectionItemAdded(notification.toCollectionItemData())

                is MapItemUpdatedNotification -> handleCollectionItemUpdated(notification.toCollectionItemData())

                is MapItemRemovedNotification -> handleCollectionItemRemoved(notification.toCollectionItemRemovedEvent())

                is MapRemovedNotification -> handleCollectionRemovedNotification(CollectionType.Map, notification.mapSid)

                is ListItemAddedNotification -> handleCollectionItemAdded(notification.toCollectionItemData())

                is ListItemUpdatedNotification -> handleCollectionItemUpdated(notification.toCollectionItemData())

                is ListItemRemovedNotification -> handleCollectionItemRemoved(notification.toCollectionItemRemovedEvent())

                is ListRemovedNotification -> handleCollectionRemovedNotification(CollectionType.List, notification.listSid)

                is UnknownNotification ->
                    logger.w {
                        "Unknown notification type '${notification.remoteEvent.eventType}' in " +
                                "remote event: ${notification.remoteEvent}"
                    }
            }
        } catch (e: CancellationException) {
            // ignore
        } catch (e: Throwable) {
            logger.w(e) { "Error handling remote notification: $notification" }
        }
    }

    suspend fun getStreamMetadata(options: OpenOptions): Flow<StreamCacheMetadata?> = when (options) {
        is OpenOptions.CreateNew -> createNewStream(options)

        is OpenOptions.OpenOrCreate -> openOrCreateStream(options)

        is OpenOptions.OpenExisting -> openExistingStream(options)
    }

    private suspend fun createNewStream(options: OpenOptions.CreateNew): Flow<StreamCacheMetadata?> {
        logger.d { "createNewStream" }

        val operation = operationsFactory.streamCreateOperation(links.streams, options.uniqueName, options.ttl)
        val response = commandsScheduler.post(operation)
        val metadata = response.toStreamCacheMetadata()
        syncCache.put(metadata)

        logger.d { "createNewStream: Got metadata from backend $metadata" }

        return syncCache.getStreamMetadataBySid(metadata.sid)
    }

    private fun openOrCreateStream(options: OpenOptions.OpenOrCreate): Flow<StreamCacheMetadata?> {
        logger.d { "openOrCreateStream" }

        var isFetching = false

        suspend fun fetchFromBackend() {
            if (isFetching) {
                logger.d("openOrCreateStream: Fetching from backend already in progress")
                return
            }
            isFetching = true

            logger.d { "openOrCreateStream: No stream metadata in cache, trying to create new stream..." }

            val createOperation =
                operationsFactory.streamCreateOperation(links.streams, options.uniqueName, options.ttl)
            val result = runCatching { commandsScheduler.post(createOperation) }

            val response = result.getOrElse { t ->
                val errorInfo = t.toTwilioException(Unknown).errorInfo
                if (!errorInfo.isNameAlreadyExist) {
                    logger.d(t) { "openOrCreateStream: Create stream error. openOrCreateStream failed" }
                    throw t
                }

                logger.d(t) { "openOrCreateStream: Create stream error. Trying to open existing stream..." }

                val streamUrl = links.getStreamUrl(options.uniqueName)
                val openOperation = operationsFactory.streamOpenOperation(streamUrl)
                commandsScheduler.post(openOperation)
            }

            val metadata = response.toStreamCacheMetadata()
            syncCache.put(metadata)

            logger.d { "openOrCreateStream: Got stream metadata from backend $metadata" }
        }

        return syncCache.getStreamMetadataByUniqueName(options.uniqueName)
            .dropWhile { data ->
                if (data != null) return@dropWhile false

                fetchFromBackend()
                return@dropWhile true
            }
    }

    private fun openExistingStream(options: OpenOptions.OpenExisting): Flow<StreamCacheMetadata?> {
        logger.d { "openExistingStream" }

        var isFetching = false

        suspend fun fetchFromBackend() {
            if (isFetching) {
                logger.d("openExistingStream: Fetching from backend already in progress")
                return
            }
            isFetching = true

            logger.d { "openExistingStream: No stream metadata in cache, performing network request..." }

            val streamUrl = links.getStreamUrl(options.sidOrUniqueName)
            val operation = operationsFactory.streamOpenOperation(streamUrl)
            val response = commandsScheduler.post(operation)
            val metadata = response.toStreamCacheMetadata()
            syncCache.put(metadata)

            logger.d { "openExistingStream: Got stream metadata from backend $metadata" }
        }

        val flow = combine(
            syncCache.getStreamMetadataBySid(options.sidOrUniqueName),
            syncCache.getStreamMetadataByUniqueName(options.sidOrUniqueName),
        ) { metadataBySid, metadataByUniqueName -> metadataBySid ?: metadataByUniqueName }

        return flow.dropWhile { data ->
            if (data != null) return@dropWhile false

            fetchFromBackend()
            return@dropWhile true

        }
    }

    fun getStreamMessages(streamSid: EntitySid): Flow<StreamMessagePublishedNotification> {
        return notificationsFlow.filterIsInstance<StreamMessagePublishedNotification>()
            .filter { it.streamSid == streamSid }
    }

    suspend fun setStreamTtl(sidOrUniqueName: String, ttl: Duration): StreamCacheMetadata {
        logger.d { "setStreamTtl: $sidOrUniqueName $ttl" }

        val streamUrl = links.getStreamUrl(sidOrUniqueName)
        val operation = operationsFactory.streamUpdateMetadataOperation(streamUrl, ttl)
        val response = commandsScheduler.post(operation)
        val updatedMetadata = response.toStreamCacheMetadata()
        syncCache.put(updatedMetadata)

        logger.d { "setStreamTtl completed successfully $updatedMetadata" }

        return updatedMetadata
    }

    suspend fun publishStreamMessage(sidOrUniqueName: String, data: JsonObject): String {
        logger.d { "publishMessage: $sidOrUniqueName $data" }

        val messagesUrl = links.getStreamMessagesUrl(sidOrUniqueName)
        val operation = operationsFactory.streamPublishMessageOperation(messagesUrl, data)
        val response = commandsScheduler.post(operation)

        logger.d { "publishMessage completed successfully ${response.sid}  $sidOrUniqueName $data" }

        return response.sid
    }

    suspend fun removeStream(sidOrUniqueName: String) {
        logger.d { "removeStream: $sidOrUniqueName" }

        val streamUrl = links.getStreamUrl(sidOrUniqueName)
        val operation = operationsFactory.streamRemoveOperation(streamUrl)
        commandsScheduler.post(operation)
        syncCache.deleteStreamBySidOrUniqueName(sidOrUniqueName)

        logger.d { "removeStream completed successfully $sidOrUniqueName" }
    }

    suspend fun removeStreamFromCache(streamSid: EntitySid) {
        logger.d { "removeStreamFromCache: $streamSid" }
        syncCache.deleteStreamBySid(streamSid)
    }

    private suspend fun handleStreamRemovedNotification(notification: StreamRemovedNotification) {
        syncCache.deleteStreamBySid(notification.streamSid)
    }

    suspend fun getDocumentMetadata(options: OpenOptions): Flow<DocumentCacheMetadata?> =
        when (options) {
            is OpenOptions.CreateNew -> createNewDocument(options)

            is OpenOptions.OpenOrCreate -> openOrCreateDocument(options)

            is OpenOptions.OpenExisting -> openExistingDocument(options)
        }

    private suspend fun createNewDocument(options: OpenOptions.CreateNew): Flow<DocumentCacheMetadata?> {
        logger.d { "createNewDocument" }

        val operation = operationsFactory.documentCreateOperation(links.documents, options.uniqueName, options.ttl)
        val response = commandsScheduler.post(operation)

        check(response.data == null) { "On create backend doesn't return any body, but stores empty json" }
        val metadata = response.copy(data = emptyJsonObject()).toDocumentCacheMetadata()
        syncCache.put(metadata)

        logger.d { "createNewDocument: Got metadata from backend $response" }

        return syncCache.getDocumentMetadataBySid(response.sid)
    }

    private fun openOrCreateDocument(options: OpenOptions.OpenOrCreate): Flow<DocumentCacheMetadata?> {
        logger.d { "openOrCreateDocument" }

        var isFetching = false

        suspend fun fetchFromBackend() {
            if (isFetching) {
                logger.d("openOrCreateDocument: Fetching from backend already in progress")
                return
            }
            isFetching = true

            logger.d { "openOrCreateDocument: No metadata in cache, trying to create new document..." }

            val createOperation = operationsFactory.documentCreateOperation(
                links.documents, options.uniqueName, options.ttl
            )

            val response = runCatching { commandsScheduler.post(createOperation) }.fold(
                onSuccess = { response ->
                    check(response.data == null) { "On create backend doesn't return any body, but stores empty json" }
                    response.copy(data = emptyJsonObject())
                },
                onFailure = { t ->
                    val errorInfo = t.toTwilioException(Unknown).errorInfo
                    if (!errorInfo.isNameAlreadyExist) {
                        logger.d(t) { "openOrCreateDocument: Create document error. openOrCreateDocument failed" }
                        throw t
                    }

                    logger.d(t) { "openOrCreateDocument: Create document error. Trying to open existing document..." }

                    val documentUrl = links.getDocumentUrl(options.uniqueName)
                    val openOperation = operationsFactory.documentOpenOperation(documentUrl)
                    commandsScheduler.post(openOperation)
                }
            )

            syncCache.put(response.toDocumentCacheMetadata())

            logger.d { "openOrCreateDocument: Got metadata from backend $response" }
        }

        return syncCache.getDocumentMetadataByUniqueName(options.uniqueName)
            .dropWhile { data ->
                if (data != null) return@dropWhile false

                fetchFromBackend()
                return@dropWhile true
            }
    }

    private fun openExistingDocument(options: OpenOptions.OpenExisting): Flow<DocumentCacheMetadata?> {
        logger.d { "openExistingDocument" }

        var isFetching = false

        suspend fun fetchFromBackend() {
            if (isFetching) {
                logger.d("openExistingDocument: Fetching from backend already in progress")
                return
            }
            isFetching = true

            logger.d { "openExistingDocument: No metadata in cache, performing network request..." }

            val documentUrl = links.getDocumentUrl(options.sidOrUniqueName)
            val operation = operationsFactory.documentOpenOperation(documentUrl)
            val response = commandsScheduler.post(operation)

            syncCache.put(response.toDocumentCacheMetadata())

            logger.d { "openExistingDocument: Got metadata from backend $response" }
        }

        val flow = combine(
            syncCache.getDocumentMetadataBySid(options.sidOrUniqueName),
            syncCache.getDocumentMetadataByUniqueName(options.sidOrUniqueName),
        ) { metadataBySid, metadataByUniqueName -> metadataBySid ?: metadataByUniqueName }

        logger.d { "openExistingDocument return flow" }

        return flow.dropWhile { data ->
            logger.d { "openExistingDocument dropWhile: $data" }
            if (data != null) return@dropWhile false

            fetchFromBackend()
            return@dropWhile true
        }
    }

    suspend fun setDocumentTtl(sidOrUniqueName: String, ttl: Duration): DocumentCacheMetadata? {
        logger.d { "setDocumentTtl: $sidOrUniqueName $ttl" }

        val documentUrl = links.getDocumentUrl(sidOrUniqueName)
        val operation = operationsFactory.documentUpdateOperation(documentUrl, ttl = ttl)
        val response = commandsScheduler.post(operation)

        logger.d { "setDocumentTtl completed successfully $response" }

        return syncCache.updateDocumentMetadata(response.toDocumentCacheMetadataPatch())
    }

    suspend fun setDocumentData(
        sidOrUniqueName: String,
        data: JsonObject,
        ttl: Duration?
    ): DocumentCacheMetadata {
        logger.d { "setDocumentData: $sidOrUniqueName $data" }

        val documentUrl = links.getDocumentUrl(sidOrUniqueName)
        val operation = operationsFactory.documentUpdateOperation(documentUrl, data, ttl)
        val response = commandsScheduler.post(operation)

        logger.d { "setDocumentData completed successfully $response" }

        // data == null in the response from backend
        val metadata = response.copy(data = data).toDocumentCacheMetadata()
        return syncCache.put(metadata)
    }

    suspend fun mutateDocumentData(
        metadata: DocumentCacheMetadata,
        ttl: Duration?,
        mutateData: suspend (currentData: JsonObject) -> JsonObject?
    ): DocumentCacheMetadata {
        logger.d { "mutateDocumentData: $metadata" }

        val operation = operationsFactory.documentMutateDataOperation(
            documentUrl = links.getDocumentUrl(metadata.sid),
            currentData = BaseMutateOperation.CurrentData(metadata.revision, metadata.documentData),
            ttl = ttl,
            mutateData = mutateData
        )
        val response = commandsScheduler.post(operation)

        logger.d { "mutateDocumentData completed successfully $response" }

        val updatedMetadata = response.toDocumentCacheMetadata()
        return syncCache.put(updatedMetadata)
    }

    suspend fun mutateDocumentData(
        sidOrUniqueName: String,
        ttl: Duration?,
        mutateData: suspend (currentData: JsonObject) -> JsonObject?
    ): DocumentCacheMetadata {
        logger.d { "mutateDocumentData: $sidOrUniqueName" }

        val metadata = openExistingDocument(OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenDocumentError))

        return mutateDocumentData(metadata, ttl, mutateData)
    }

    suspend fun removeDocument(sidOrUniqueName: String) {
        logger.d { "removeDocument: $sidOrUniqueName" }

        val documentUrl = links.getDocumentUrl(sidOrUniqueName)
        val operation = operationsFactory.documentRemoveOperation(documentUrl)
        commandsScheduler.post(operation)
        syncCache.deleteDocumentBySidOrUniqueName(sidOrUniqueName)

        logger.d { "removeDocument completed successfully $sidOrUniqueName" }
    }

    suspend fun removeDocumentFromCache(documentSid: EntitySid) {
        logger.d { "removeDocumentFromCache: $documentSid" }
        syncCache.deleteDocumentBySid(documentSid)
    }

    private suspend fun handleDocumentUpdatedNotification(notification: DocumentUpdatedNotification) {
        syncCache.updateDocumentMetadata(notification.toDocumentCacheMetadataPatch())
    }

    private suspend fun handleDocumentRemovedNotification(notification: DocumentRemovedNotification) {
        syncCache.deleteDocumentBySid(notification.documentSid)
    }

    suspend fun getCollectionMetadata(collectionType: CollectionType, options: OpenOptions): Flow<CollectionMetadata?> =
        when (options) {
            is OpenOptions.CreateNew -> createNewCollection(collectionType, options)

            is OpenOptions.OpenOrCreate -> openOrCreateCollection(collectionType, options)

            is OpenOptions.OpenExisting -> openExistingCollection(collectionType, options)
        }

    private suspend fun createNewCollection(
        collectionType: CollectionType,
        options: OpenOptions.CreateNew
    ): Flow<CollectionMetadata?> {

        logger.d { "createNewCollection" }

        val collectionsUrl = links.getCollectionsUrl(collectionType)
        val operation = operationsFactory.collectionCreateOperation(collectionsUrl, options.uniqueName, options.ttl)
        val response = commandsScheduler.post(operation)

        val metadata = response.toCollectionMetadata(collectionType)
        syncCache.put(metadata)

        logger.d { "createNewCollection: Got metadata from backend $response" }

        return syncCache.getCollectionMetadataBySid(collectionType, response.sid)
    }

    private fun openOrCreateCollection(
        collectionType: CollectionType,
        options: OpenOptions.OpenOrCreate
    ): Flow<CollectionMetadata?> {

        logger.d { "openOrCreateCollection" }

        var isFetching = false

        suspend fun fetchFromBackend() {
            if (isFetching) {
                logger.d("openOrCreateCollection: Fetching from backend already in progress")
                return
            }
            isFetching = true

            logger.d { "openOrCreateCollection: No metadata in cache, trying to create new document..." }

            val collectionsUrl = links.getCollectionsUrl(collectionType)
            val createOperation = operationsFactory.collectionCreateOperation(
                collectionsUrl, options.uniqueName, options.ttl
            )

            val response = runCatching { commandsScheduler.post(createOperation) }.getOrElse { t ->
                val errorInfo = t.toTwilioException(Unknown).errorInfo
                if (!errorInfo.isNameAlreadyExist) {
                    logger.d(t) { "openOrCreateCollection: Create collection error. openOrCreateCollection failed" }
                    throw t
                }

                logger.d(t) { "openOrCreateCollection: Create collection error. Trying to open existing collection..." }

                val collectionUrl = links.getCollectionUrl(collectionType, options.uniqueName)
                val openOperation = operationsFactory.collectionOpenOperation(collectionUrl)
                commandsScheduler.post(openOperation)
            }

            syncCache.put(response.toCollectionMetadata(collectionType))

            logger.d { "openOrCreateCollection: Got metadata from backend $response" }
        }

        return syncCache.getCollectionMetadataByUniqueName(collectionType, options.uniqueName)
            .dropWhile { data ->
                if (data != null) return@dropWhile false

                fetchFromBackend()
                return@dropWhile true
            }
    }

    private fun openExistingCollection(
        collectionType: CollectionType,
        options: OpenOptions.OpenExisting
    ): Flow<CollectionMetadata?> {

        logger.d { "openExistingCollection" }

        var isFetching = false

        suspend fun fetchFromBackend() {
            if (isFetching) {
                logger.d("openExistingCollection: Fetching from backend already in progress")
                return
            }
            isFetching = true

            logger.d { "openExistingCollection: No metadata in cache, performing network request..." }

            val collectionUrl = links.getCollectionUrl(collectionType, options.sidOrUniqueName)
            val operation = operationsFactory.collectionOpenOperation(collectionUrl)
            val response = commandsScheduler.post(operation)

            syncCache.put(response.toCollectionMetadata(collectionType))

            logger.d { "openExistingCollection: Got metadata from backend $response" }
        }

        val flow = combine(
            syncCache.getCollectionMetadataBySid(collectionType, options.sidOrUniqueName),
            syncCache.getCollectionMetadataByUniqueName(collectionType, options.sidOrUniqueName),
        ) { metadataBySid, metadataByUniqueName -> metadataBySid ?: metadataByUniqueName }

        logger.d { "openExistingCollection return flow" }

        return flow.dropWhile { data ->
            logger.d { "openExistingCollection dropWhile: $data" }
            if (data != null) return@dropWhile false

            fetchFromBackend()
            return@dropWhile true
        }
    }

    suspend fun setCollectionTtl(
        collectionType: CollectionType,
        sidOrUniqueName: String,
        ttl: Duration
    ): CollectionMetadata {

        logger.d { "setCollectionTtl: $sidOrUniqueName $ttl" }

        val collectionUrl = links.getCollectionUrl(collectionType, sidOrUniqueName)
        val operation = operationsFactory.collectionUpdateMetadataOperation(collectionUrl, ttl)
        val response = commandsScheduler.post(operation)

        logger.d { "setCollectionTtl completed successfully $response" }

        return syncCache.put(response.toCollectionMetadata(collectionType))
    }

    suspend fun addCollectionItemData(
        collectionType: CollectionType,
        sidOrUniqueName: String,
        itemId: CollectionItemId?,
        itemData: JsonObject,
        ttl: Duration?
    ): CollectionItemData {

        logger.d { "addCollectionItemData: ${itemId?.id} $itemData" }

        val itemsUrl = links.getCollectionItemsUrl(collectionType, sidOrUniqueName)
        val addOperation = operationsFactory.collectionItemAddOperation(collectionType, itemsUrl, itemId, itemData, ttl)
        val item = commandsScheduler.post(addOperation)

        logger.d { "addCollectionItemData completed successfully $item" }

        return upsertCollectionItemInCache(item, updateMetadataLastEventId = false)
    }

    suspend fun updateCollectionItemData(
        sidOrUniqueName: String,
        itemId: CollectionItemId,
        itemData: JsonObject,
        ttl: Duration?
    ): CollectionItemData {

        logger.d { "updateCollectionItemData: ${itemId.id} $itemData" }

        val itemUrl = links.getCollectionItemUrl(sidOrUniqueName, itemId)
        val updateOperation = operationsFactory.collectionItemUpdateOperation(
            itemId.collectionType, itemUrl, itemData, ttl)

        val item = commandsScheduler.post(updateOperation)

        logger.d { "updateCollectionItemData completed successfully $item" }

        return upsertCollectionItemInCache(item, updateMetadataLastEventId = false)
    }

    suspend fun setCollectionItemData(
        sidOrUniqueName: String,
        itemId: CollectionItemId,
        itemData: JsonObject,
        ttl: Duration?
    ): CollectionItemData {

        logger.d { "setCollectionItemData: ${itemId.id} $itemData" }

        val collectionType = itemId.collectionType
        suspend fun addCollectionItem() = addCollectionItemData(collectionType, sidOrUniqueName, itemId, itemData, ttl)
        suspend fun updateCollectionItem() = updateCollectionItemData(sidOrUniqueName, itemId, itemData, ttl)

        val metadata = openExistingCollection(collectionType, OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenCollectionError))

        val isCachedAndNotRemoved = syncCache.getCollectionItemData(metadata.sid, itemId)?.isRemoved == false

        val item = if (isCachedAndNotRemoved) {
            // in case the item is cached, attempt update of existing item,
            // and automatically recover on key not found (http status 404)
            runCatching { updateCollectionItem() }.getOrElse { t ->
                val errorInfo = t.toTwilioException(Unknown).errorInfo
                if (!errorInfo.isItemNotFound) {
                    logger.d(t) { "setCollectionItemData: Update collection item error. setCollectionItemData failed" }
                    throw t
                }

                logger.d(t) { "setCollectionItemData: Update collection item error. Trying to add new item..." }
                addCollectionItem()
            }
        } else {
            // in case the item is not cached, try adding a new item,
            // and automatically recover on key conflict (http status 409)
            runCatching { addCollectionItem() }.getOrElse { t ->
                val errorInfo = t.toTwilioException(Unknown).errorInfo
                if (!errorInfo.isKeyAlreadyExist) {
                    logger.d(t) { "setCollectionItemData: Add collection item error. setCollectionItemData failed" }
                    throw t
                }

                logger.d(t) { "setCollectionItemData: Add collection item error. Trying to update existing item..." }
                updateCollectionItem()
            }
        }

        logger.d { "setCollectionItemData completed successfully ${itemId.id} $item" }

        return item
    }

    suspend fun mutateOrAddCollectionItemData(
        sidOrUniqueName: String,
        itemId: CollectionItemId,
        ttl: Duration?,
        mutateData: suspend (currentData: JsonObject?) -> JsonObject?
    ): CollectionItemData {

        logger.d { "mutateOrAddCollectionItemData: ${itemId.id}" }

        val collectionType = itemId.collectionType
        suspend fun invokeMutator() = InvokeMutator { mutateData(null) }
        suspend fun addCollectionItem() =
            addCollectionItemData(collectionType, sidOrUniqueName, itemId, itemData = invokeMutator(), ttl)
        suspend fun mutateCollectionItem() = mutateCollectionItemData(sidOrUniqueName, itemId, ttl, mutateData)

        val metadata = openExistingCollection(collectionType, OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenCollectionError))

        val isCachedAndNotRemoved = syncCache.getCollectionItemData(metadata.sid, itemId)?.isRemoved == false

        val item = if (isCachedAndNotRemoved) {
            // in case the item is cached, attempt mutate of existing item,
            // and automatically recover on key not found (http status 404)
            runCatching { mutateCollectionItem() }.getOrElse { t ->
                val errorInfo = t.toTwilioException(Unknown).errorInfo
                if (!errorInfo.isItemNotFound) {
                    logger.d(t) { "mutateOrAddCollectionItemData: Mutate item error. mutateOrAdd failed" }
                    throw t
                }

                logger.d(t) { "mutateOrAddCollectionItemData: Mutate item error. Trying to add new item..." }
                addCollectionItem()
            }
        } else {
            // in case the item is not cached, try adding a new item,
            // and automatically recover on key conflict (http status 409)
            runCatching { addCollectionItem() }.getOrElse { t ->
                val errorInfo = t.toTwilioException(Unknown).errorInfo
                if (!errorInfo.isKeyAlreadyExist) {
                    logger.d(t) { "mutateOrAddCollectionItemData: Add item error. setCollectionItemData failed" }
                    throw t
                }

                logger.d(t) { "mutateOrAddCollectionItemData: Add item error. Trying to mutate existing item..." }
                mutateCollectionItem()
            }
        }

        logger.d { "mutateOrAddCollectionItemData completed successfully ${itemId.id} $item" }

        return item
    }

    suspend fun mutateCollectionItemData(
        sidOrUniqueName: String,
        itemId: CollectionItemId,
        ttl: Duration?,
        mutateData: suspend (currentData: JsonObject) -> JsonObject?
    ): CollectionItemData {
        logger.d { "mutateCollectionItemData: $sidOrUniqueName ${itemId.id}" }

        val metadata = openExistingCollection(itemId.collectionType, OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenCollectionError))

        val cachedItem = syncCache.getCollectionItemData(metadata.sid, itemId)?.takeIf { !it.isRemoved }
        logger.d { "mutateCollectionItemData: cachedItem: $cachedItem" }

        val collectionItemUrl = links.getCollectionItemUrl(sidOrUniqueName, itemId)
        val currentData = cachedItem?.let { BaseMutateOperation.CurrentData(it.revision, it.data) }
        val operation = operationsFactory.collectionMutateItemDataOperation(
            itemId.collectionType, collectionItemUrl, currentData, ttl, mutateData)

        val item = commandsScheduler.post(operation)

        logger.d { "mutateCollectionItemData completed successfully $item" }

        return upsertCollectionItemInCache(item, updateMetadataLastEventId = false)
    }

    suspend fun getCollectionItemData(
        sidOrUniqueName: String,
        itemId: CollectionItemId,
        useCache: Boolean
    ): CollectionItemData? {
        val metadata = openExistingCollection(itemId.collectionType, OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenCollectionError))

        return getCollectionItemData(metadata, itemId, useCache)
    }

    suspend fun getCollectionItemData(
        metadata: CollectionMetadata,
        itemId: CollectionItemId,
        useCache: Boolean,
        pageSize: Int? = null,
    ): CollectionItemData? {
        logger.d { "getCollectionItemData: ${metadata.sid} ${itemId.id}; useCache: $useCache" }

        if (useCache) {
            syncCache.getCollectionItemData(metadata.sid, itemId)?.let { item ->
                val cachedItem = item.takeIf { !it.isRemoved }
                logger.d { "getCollectionItemData: return item from cache: $cachedItem" }
                return cachedItem
            }
        }

        logger.d { "getCollectionItemData: requesting ${metadata.sid} ${itemId.id} from backend..." }

        val itemsUrl = links.getCollectionItemsUrl(metadata.collectionType, metadata.sid)
        val operation = operationsFactory.collectionItemsGetOperation(
            metadata.collectionType, itemsUrl, itemId, pageSize = pageSize)

        val response = commandsScheduler.post(operation)

        logger.d { "getCollectionItemData completed successfully $response" }

        val collectionType = itemId.collectionType
        val items = response.items
        val isCollectionEmpty = response.isCollectionEmpty

        return syncCache.put(collectionType, metadata.sid, items, updateMetadataLastEventId = false, isCollectionEmpty)
            .firstOrNull() // when the item is exist - it is returned as first result
            ?.data
            ?.takeIf { it.itemId == itemId } // when the item is not exist - backend still returns page with next items
    }

    fun getCollectionItemsData(
        collectionType: CollectionType,
        sidOrUniqueName: String,
        startId: CollectionItemId?,
        includeStartId: Boolean,
        queryOrder: QueryOrder,
        pageSize: Int,
        useCache: Boolean,
    ): ReceiveChannel<CollectionItemData> = flow {
        logger.d {
            "getCollectionItemsData: $sidOrUniqueName; " +
                    "startId = ${startId?.id}; " +
                    "includeStartKey = $includeStartId; " +
                    "queryOrder = $queryOrder; " +
                    "pageSize = $pageSize; " +
                    "useCache = $useCache"
        }

        val metadata = openExistingCollection(collectionType, OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenCollectionError))

        if (useCache && metadata.isEmpty == true) {
            logger.d { "getCollectionItemsData: return empty ${metadata.collectionType} from cache" }
            return@flow
        }

        var prevItem: CollectionItemData? = null

        if (includeStartId && startId != null) {
            getCollectionItemData(metadata, startId, useCache, pageSize)?.let { firstItem ->
                if (!firstItem.isRemoved) {
                    // Emit the item to the flow returned by this method.
                    // Suspend here until the item is consumed.
                    logger.d { "before firstItem" }
                    emit(firstItem)
                    logger.d { "after firstItem" }
                }
                prevItem = firstItem
            }
        }

        var beginId = metadata.beginId
        var endId = metadata.endId

        fun hasNext(): Boolean {
            val result = when (queryOrder) {
                Ascending -> endId == null || prevItem?.itemId != endId
                Descending -> beginId == null || prevItem?.itemId != beginId
            }

            logger.d {
                "getCollectionItemsData hasNext: $result; prevItem: ${prevItem?.itemId?.id}; " +
                        "beginKey: ${beginId?.id}; endKey: ${endId?.id}"
            }

            return result
        }

        while (hasNext()) {
            val prevItemId = prevItem?.itemId ?: startId

            val cachedItem = when {
                useCache -> when (queryOrder) {
                    Ascending -> syncCache.getNextCollectionItemData(metadata.collectionType, metadata.sid, prevItemId)
                    Descending -> syncCache.getPrevCollectionItemData(metadata.collectionType, metadata.sid, prevItemId)
                }

                else -> null
            }

            if (cachedItem != null) {
                logger.d { "getCollectionItemsData got item ${cachedItem.itemId.id} from cache" }
                if (!cachedItem.isRemoved) {
                    // Send the item to the channel returned by this method.
                    // Suspend here until the item is consumed.
                    emit(cachedItem)
                }
                prevItem = cachedItem
            } else {
                logger.d {
                    "getCollectionItemsData: the item next after $prevItemId is not found in cache, " +
                            "requesting it from the backend..."
                }

                val itemsUrl = links.getCollectionItemsUrl(metadata.collectionType, metadata.sid)

                // get it inclusive in order to connect left/right bounds with existing ranges in cache
                // as soon as we request items inclusive we add 1 to pageSize in order to request at least one item
                // after the prevItem
                val operation = operationsFactory.collectionItemsGetOperation(
                    metadata.collectionType, itemsUrl, prevItemId, queryOrder, pageSize + 1, inclusive = true
                )

                val response = commandsScheduler.post(operation)

                val beginIdToUpdate = response.beginCollectionItemIdOrNull(queryOrder)
                val endIdToUpdate = response.endCollectionItemIdOrNull(queryOrder)

                beginIdToUpdate?.let { beginId = it }
                endIdToUpdate?.let { endId = it }

                val cachedItems =
                    syncCache.put(
                        metadata.collectionType,
                        metadata.sid,
                        response.items,
                        updateMetadataLastEventId = false,
                        response.isCollectionEmpty,
                        beginIdToUpdate,
                        endIdToUpdate,
                    )
                        .map { it.data }
                        .filterNot { it.itemId == prevItemId } // we made inclusive request now remove the previous item

                cachedItems.forEach { item ->
                    logger.d { "getCollectionItemsData got item ${item.itemId.id} from backend" }

                    // Emit the item to the flow returned by this method.
                    // Suspend here until the item is consumed.
                    emit(item)
                }

                prevItem = cachedItems.lastOrNull() ?: return@flow
            }
        }
    }.buffer(capacity = 0).produceIn(coroutineScope)

    suspend fun removeCollectionItem(sidOrUniqueName: String, itemId: CollectionItemId) {
        val metadata = openExistingCollection(itemId.collectionType, OpenOptions.OpenExisting(sidOrUniqueName)).first()
            ?: throw TwilioException(ErrorInfo(ErrorReason.OpenCollectionError))

        removeCollectionItem(metadata, itemId)
    }

    suspend fun removeCollectionItem(metadata: CollectionMetadata, itemId: CollectionItemId) {
        logger.d { "removeCollectionItem: ${metadata.sid} ${itemId.id}" }

        val collectionItemUrl = links.getCollectionItemUrl(metadata.sid, itemId)
        val operation = operationsFactory.collectionItemRemoveOperation(metadata.collectionType, collectionItemUrl)
        val response = commandsScheduler.post(operation)

        val result = syncCache.deleteCollectionItem(
            metadata.sid,
            itemId,
            response.lastEventId,
            response.dateUpdated,
            updateMetadataLastEventId = false,
        )

        // result is CacheResult.NotModified (which means it is not cached, result.data contains fake item) -
        // could be only in case where collection (map or list) is unsubscribed at the moment.
        // In this case we have no data to emit event, but no one is listening for the event anyway.
        //
        // Well... actually it could be a race when listener has been just added and item data is not arrived yet,
        // because subscription establishing still in progress. In this case itemRemoved event will not be delivered to
        // the listener because we don't have full itemData.
        //
        // Possible approach to solve this is to emit itemRemovedEvent with itemKey only (DELETE request response
        // doesn't contain itemData), instead of full itemData. As soon as we emit the itemRemovedEvent
        // from handleCollectionItemRemoved() only in case if item is cached anyway - we avoiding event duplication here.
        //
        // For now lets keep interfaces according to the specification.

        when (result) {
            is CacheResult.Removed -> collectionItemRemovedFlow.emit(result.data)

            is CacheResult.NotModified -> {
                logger.i { "Skip emitting the ItemRemoved event to avoid duplicate/misordered events: $result" }
            }

            is CacheResult.Added,
            is CacheResult.Updated -> error("Added/Updated result never happens in removeCollectionItem: $result")
        }

        logger.d { "removeCollectionItem completed successfully ${metadata.sid} ${itemId.id}" }
    }

    suspend fun removeCollection(collectionType: CollectionType, sidOrUniqueName: String) {
        logger.d { "removeCollection: $sidOrUniqueName" }

        val collectionUrl = links.getCollectionUrl(collectionType, sidOrUniqueName)
        val operation = operationsFactory.collectionRemoveOperation(collectionUrl)
        commandsScheduler.post(operation)
        syncCache.deleteCollectionBySidOrUniqueName(collectionType, sidOrUniqueName)

        logger.d { "removeCollection completed successfully $sidOrUniqueName" }
    }

    suspend fun removeCollectionFromCache(collectionType: CollectionType, collectionSid: EntitySid) {
        logger.d { "removeCollectionFromCache: $collectionSid" }
        syncCache.deleteCollectionBySidOrUniqueName(collectionType, collectionSid)
    }

    private suspend fun upsertCollectionItemInCache(
        item: CollectionItemData,
        updateMetadataLastEventId: Boolean,
    ): CollectionItemData {
        val result = syncCache.put(item, updateMetadataLastEventId)
        logger.d { "upsertCollectionItemInCache result: $result" }

        when (result) {
            is CacheResult.Added -> collectionItemAddedFlow.emit(result.data)
            is CacheResult.Updated -> collectionItemUpdatedFlow.emit(result.data)
            is CacheResult.NotModified -> // Event has been already emitted for the latest item data.
                logger.i { "Skip emitting remote event to avoid duplicate/misordered events: $result" }

            is CacheResult.Removed -> error("Removed result never happens in upsertCollectionItemInCache: $result")
        }

        return result.data
    }

    private suspend fun handleCollectionItemAdded(item: CollectionItemData) {
        upsertCollectionItemInCache(item, updateMetadataLastEventId = true)
    }

    private suspend fun handleCollectionItemUpdated(item: CollectionItemData) {
        upsertCollectionItemInCache(item, updateMetadataLastEventId = true)
    }

    private suspend fun handleCollectionItemRemoved(event: CollectionItemRemovedEvent) {
        val result = syncCache.deleteCollectionItem(
            event.collectionSid,
            event.itemId,
            event.eventId,
            event.dateCreated,
            updateMetadataLastEventId = true,
        )

        when (result) {
            is CacheResult.Removed -> collectionItemRemovedFlow.emit(result.data)
            is CacheResult.NotModified ->
                logger.i { "Skip emitting the ItemRemoved event to avoid duplicate/misordered events: $result" }

            is CacheResult.Added,
            is CacheResult.Updated -> error("Added/Updated result never happens in handleCollectionItemRemoved: $result")
        }
    }

    private suspend fun handleCollectionRemovedNotification(collectionType: CollectionType, collectionSid: EntitySid) {
        removeCollectionFromCache(collectionType, collectionSid)
    }

    fun close() {
        syncCache.close()
    }
}
