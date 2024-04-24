//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit

import com.twilio.sync.cache.CacheResult
import com.twilio.sync.cache.SyncCache
import com.twilio.sync.operations.CollectionCreateOperation
import com.twilio.sync.operations.CollectionItemsGetOperation
import com.twilio.sync.operations.CollectionMetadataResponse
import com.twilio.sync.operations.CollectionOpenOperation
import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.sync.operations.DocumentCreateOperation
import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.sync.operations.DocumentOpenOperation
import com.twilio.sync.operations.StreamCreateOperation
import com.twilio.sync.operations.StreamMetadataResponse
import com.twilio.sync.operations.StreamOpenOperation
import com.twilio.sync.operations.SyncOperationsFactory
import com.twilio.sync.repository.DocumentRemovedNotification
import com.twilio.sync.repository.DocumentUpdatedNotification
import com.twilio.sync.repository.MapRemovedNotification
import com.twilio.sync.repository.OpenOptions
import com.twilio.sync.repository.StreamMessagePublishedNotification
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.repository.toCollectionMetadata
import com.twilio.sync.repository.toDocumentCacheMetadata
import com.twilio.sync.repository.toDocumentCacheMetadataPatch
import com.twilio.sync.repository.toStreamCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.StreamCacheMetadata
import com.twilio.sync.subscriptions.RemoteEvent
import com.twilio.sync.test.util.testCollectionItemData
import com.twilio.sync.test.util.testCollectionItemsDataResponse
import com.twilio.sync.test.util.testCollectionMetadataResponse
import com.twilio.sync.test.util.testDocumentMetadataResponse
import com.twilio.sync.test.util.testErrorAlreadyExists
import com.twilio.sync.utils.CollectionItemId.*
import com.twilio.sync.utils.CollectionMetadata
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.QueryOrder
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureException
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.twilioException
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.commands.CommandsScheduler
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.StateMachine.Matcher.Companion.any
import com.twilio.util.TwilioException
import com.twilio.util.emptyJsonObject
import com.twilio.util.json
import com.twilio.util.toTwilioException
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@ExcludeFromInstrumentedTests
class RepositoryTest {

    private lateinit var repository: SyncRepository

    private val remoteEventsFlow = MutableSharedFlow<RemoteEvent>()

    @RelaxedMockK
    private lateinit var syncCache: SyncCache

    @RelaxedMockK
    private lateinit var commandsScheduler: CommandsScheduler

    @RelaxedMockK
    private lateinit var operationsFactory: SyncOperationsFactory

    @RelaxedMockK
    private lateinit var links: ConfigurationLinks

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@RepositoryTest, relaxUnitFun = true)

        repository = SyncRepository(
            testCoroutineScope,
            syncCache,
            commandsScheduler,
            remoteEventsFlow,
            operationsFactory,
            links
        )
    }

    @Test
    fun createNewStream() = runTest {
        val operation = mockk<StreamCreateOperation>()
        every { operationsFactory.streamCreateOperation(any(), any(), any()) } returns operation

        val response = StreamMetadataResponse(sid = "TO000")
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toStreamCacheMetadata()
        val metadataFlow = mockk<Flow<StreamCacheMetadata?>>()
        every { syncCache.getStreamMetadataBySid(metadata.sid) } returns metadataFlow

        val result = repository.getStreamMetadata(OpenOptions.CreateNew(uniqueName = null))

        coVerify { syncCache.put(metadata) }
        assertSame(result, metadataFlow)
    }

    @Test
    fun createNewStreamError() = runTest {
        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post<StreamMetadataResponse>(any()) } throws expectedException

        val result = runCatching { repository.getStreamMetadata(OpenOptions.CreateNew(uniqueName = null)) }

        assertTrue(result.isFailure)
        assertSame(expectedException, result.twilioException)
    }

    @Test
    fun openExistingStreamError() = runTest {
        every { syncCache.getStreamMetadataBySid(any()) } returns flowOf(null)
        every { syncCache.getStreamMetadataByUniqueName(any()) } returns flowOf(null)

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post<StreamMetadataResponse>(any()) } throws expectedException

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenExisting(sidOrUniqueName = "TO000"))

        assertSame(expectedException, metadataFlow.captureException())
    }

    @Test
    fun openExistingStreamFromBackendBySid() = runTest {
        val streamSid = "TO000"

        val cachedMetadataBySidFlow = MutableSharedFlow<StreamCacheMetadata?>()

        every { syncCache.getStreamMetadataBySid(streamSid) } returns
                cachedMetadataBySidFlow.onSubscription { emit(null) }

        every { syncCache.getStreamMetadataByUniqueName(any()) } returns flowOf(null)

        val operation = mockk<StreamOpenOperation>()
        every { operationsFactory.streamOpenOperation(any()) } returns operation

        val response = StreamMetadataResponse(streamSid)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toStreamCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataBySidFlow.emit(metadata) }
        }

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenExisting(streamSid))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openExistingStreamFromBackendByUniqueName() = runTest {
        val uniqueName = "uniqueName"

        val cachedMetadataByUniqueNameFlow = MutableSharedFlow<StreamCacheMetadata?>()

        every { syncCache.getStreamMetadataBySid(uniqueName) } returns flowOf(null)

        every { syncCache.getStreamMetadataByUniqueName(any()) } returns
                cachedMetadataByUniqueNameFlow.onSubscription { emit(null) }

        val operation = mockk<StreamOpenOperation>()
        every { operationsFactory.streamOpenOperation(any()) } returns operation

        val response = StreamMetadataResponse(sid = "TO000", uniqueName)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toStreamCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataByUniqueNameFlow.emit(metadata) }
        }

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenExisting(uniqueName))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateStreamCreateNew() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<StreamCacheMetadata?>()

        every { syncCache.getStreamMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val operation = mockk<StreamCreateOperation>()
        every { operationsFactory.streamCreateOperation(any(), uniqueName, ttl) } returns operation

        val response = StreamMetadataResponse(sid = "TO000", uniqueName)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toStreamCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataFlow.emit(metadata) }
        }

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateStreamOpenExisting() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<StreamCacheMetadata?>()

        every { syncCache.getStreamMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<StreamCreateOperation>()
        val openOperation = mockk<StreamOpenOperation>()

        every { operationsFactory.streamCreateOperation(any(), uniqueName, ttl) } returns createOperation
        every { operationsFactory.streamOpenOperation(any()) } returns openOperation

        val response = StreamMetadataResponse(sid = "TO000", uniqueName)
        coEvery { commandsScheduler.post(createOperation) } throws
                TwilioException(testErrorAlreadyExists)
        coEvery { commandsScheduler.post(openOperation) } returns
                response

        val metadata = response.toStreamCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataFlow.emit(metadata) }
        }

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
        coVerify(exactly = 1) { commandsScheduler.post(openOperation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateStreamErrorCreate() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<StreamCacheMetadata?>()

        every { syncCache.getStreamMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<StreamCreateOperation>()
        every { operationsFactory.streamCreateOperation(any(), uniqueName, ttl) } returns createOperation

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post(createOperation) } throws expectedException

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertSame(expectedException, metadataFlow.captureException())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
    }

    @Test
    fun openOrCreateStreamErrorOpen() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<StreamCacheMetadata?>()

        every { syncCache.getStreamMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<StreamCreateOperation>()
        val openOperation = mockk<StreamOpenOperation>()

        every { operationsFactory.streamCreateOperation(any(), uniqueName, ttl) } returns createOperation
        every { operationsFactory.streamOpenOperation(any()) } returns openOperation

        coEvery { commandsScheduler.post(createOperation) } throws
                TwilioException(testErrorAlreadyExists)

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post(openOperation) } throws expectedException

        val metadataFlow = repository.getStreamMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertSame(expectedException, metadataFlow.captureException())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
        coVerify(exactly = 1) { commandsScheduler.post(openOperation) }
    }

    @Test
    fun getStreamMessages() = runTest {
        val streamSid = "TO000"

        val messagesFlow = repository.getStreamMessages(streamSid)
        val deferredEvent = async { messagesFlow.first() }

        val data = buildJsonObject { put("data", "value") }
        val event = StreamMessagePublishedNotification(streamSid, "messageSid", data)
        val eventJson = json.encodeToJsonElement(event).jsonObject
        val remoteEvent = RemoteEvent(streamSid, "stream_message_published", eventJson)
        remoteEventsFlow.emit(remoteEvent)

        assertEquals(event, deferredEvent.await())
    }

    @Test
    fun createNewDocument() = runTest {
        val operation = mockk<DocumentCreateOperation>()
        every { operationsFactory.documentCreateOperation(any(), any(), any()) } returns operation

        val response = testDocumentMetadataResponse(sid = "TO000")
        coEvery { commandsScheduler.post(operation) } returns response

        val metadataFlow = mockk<Flow<DocumentCacheMetadata?>>()
        every { syncCache.getDocumentMetadataBySid(response.sid) } returns metadataFlow

        val result = repository.getDocumentMetadata(OpenOptions.CreateNew(uniqueName = null))

        val metadata = response.copy(data = emptyJsonObject()).toDocumentCacheMetadata()
        coVerify { syncCache.put(metadata) }
        assertSame(result, metadataFlow)
    }

    @Test
    fun createNewDocumentError() = runTest {
        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post<DocumentMetadataResponse>(any()) } throws expectedException

        val result = runCatching { repository.getDocumentMetadata(OpenOptions.CreateNew(uniqueName = null)) }

        assertTrue(result.isFailure)
        assertSame(expectedException, result.twilioException)
    }

    @Test
    fun openExistingDocumentError() = runTest {
        every { syncCache.getDocumentMetadataBySid(any()) } returns flowOf(null)
        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns flowOf(null)

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post<DocumentMetadataResponse>(any()) } throws expectedException

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenExisting(sidOrUniqueName = "TO000"))

        assertSame(expectedException, metadataFlow.captureException())
    }

    @Test
    fun openExistingDocumentFromBackendBySid() = runTest {
        val documentSid = "TO000"

        val cachedMetadataBySidFlow = MutableSharedFlow<DocumentCacheMetadata?>()

        every { syncCache.getDocumentMetadataBySid(documentSid) } returns
                cachedMetadataBySidFlow.onSubscription { emit(null) }

        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns flowOf(null)

        val operation = mockk<DocumentOpenOperation>()
        every { operationsFactory.documentOpenOperation(any()) } returns operation

        val response = testDocumentMetadataResponse(documentSid, data = emptyJsonObject())
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.copy(data = emptyJsonObject()).toDocumentCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataBySidFlow.emit(metadata) }
            metadata
        }

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenExisting(documentSid))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openExistingDocumentFromBackendByUniqueName() = runTest {
        val uniqueName = "uniqueName"

        val cachedMetadataByUniqueNameFlow = MutableSharedFlow<DocumentCacheMetadata?>()

        every { syncCache.getDocumentMetadataBySid(uniqueName) } returns flowOf(null)

        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns
                cachedMetadataByUniqueNameFlow.onSubscription { emit(null) }

        val operation = mockk<DocumentOpenOperation>()
        every { operationsFactory.documentOpenOperation(any()) } returns operation

        val response = testDocumentMetadataResponse(sid = "TO000", uniqueName, data = emptyJsonObject())
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toDocumentCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataByUniqueNameFlow.emit(metadata) }
            metadata
        }

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenExisting(uniqueName))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateDocumentCreateNew() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<DocumentCacheMetadata?>()

        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val operation = mockk<DocumentCreateOperation>()
        every { operationsFactory.documentCreateOperation(any(), uniqueName, ttl) } returns operation

        val response = testDocumentMetadataResponse(sid = "TO000", uniqueName)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.copy(data = emptyJsonObject()).toDocumentCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataFlow.emit(metadata) }
            metadata
        }

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateDocumentOpenExisting() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<DocumentCacheMetadata?>()

        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<DocumentCreateOperation>()
        val openOperation = mockk<DocumentOpenOperation>()

        every { operationsFactory.documentCreateOperation(any(), uniqueName, ttl) } returns createOperation
        every { operationsFactory.documentOpenOperation(any()) } returns openOperation

        val response = testDocumentMetadataResponse(sid = "TO000", uniqueName, data = emptyJsonObject())
        coEvery { commandsScheduler.post(createOperation) } throws
                TwilioException(testErrorAlreadyExists)
        coEvery { commandsScheduler.post(openOperation) } returns
                response

        val metadata = response.copy(data = emptyJsonObject()).toDocumentCacheMetadata()
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataFlow.emit(metadata) }
            metadata
        }

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
        coVerify(exactly = 1) { commandsScheduler.post(openOperation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateDocumentErrorCreate() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<DocumentCacheMetadata?>()

        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<DocumentCreateOperation>()

        every { operationsFactory.documentCreateOperation(any(), uniqueName, ttl) } returns createOperation

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post(createOperation) } throws expectedException

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertSame(expectedException, metadataFlow.captureException())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
    }

    @Test
    fun openOrCreateDocumentErrorOpen() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<DocumentCacheMetadata?>()

        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<DocumentCreateOperation>()
        val openOperation = mockk<DocumentOpenOperation>()

        every { operationsFactory.documentCreateOperation(any(), uniqueName, ttl) } returns createOperation
        every { operationsFactory.documentOpenOperation(any()) } returns openOperation

        coEvery { commandsScheduler.post(createOperation) } throws
                TwilioException(testErrorAlreadyExists)

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post(openOperation) } throws expectedException

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertSame(expectedException, metadataFlow.captureException())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
        coVerify(exactly = 1) { commandsScheduler.post(openOperation) }
    }

    @Test
    fun documentUpdated() = runTest {
        val documentSid = "TO000"
        val cachedMetadata = testDocumentMetadataResponse(sid = documentSid)
            .copy(data = emptyJsonObject())
            .toDocumentCacheMetadata()

        val cachedMetadataFlow = MutableSharedFlow<DocumentCacheMetadata?>()
        every { syncCache.getDocumentMetadataBySid(documentSid) } returns
                cachedMetadataFlow.onSubscription { emit(cachedMetadata) }
        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns flowOf(null)

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenExisting(cachedMetadata.sid))
        val deferredMetadataList = async { metadataFlow.take(2).toList() }

        val data = buildJsonObject { put("data", "value") }
        val notification = DocumentUpdatedNotification(
            eventId = 1,
            revision = "1",
            dateCreated = Instant.DISTANT_PAST,
            documentSid = documentSid,
            data = data,
        )
        val eventJson = json.encodeToJsonElement(notification).jsonObject
        val remoteEvent = RemoteEvent(documentSid, "document_updated", eventJson)
        remoteEventsFlow.emit(remoteEvent)

        val patch = notification.toDocumentCacheMetadataPatch()
        waitAndVerify { syncCache.updateDocumentMetadata(patch) }

        val updatedMetadata = cachedMetadata.copy(
            revision = notification.revision,
            lastEventId = notification.eventId,
            documentData = notification.data
        )
        cachedMetadataFlow.emit(updatedMetadata)

        val metadataList = deferredMetadataList.await()
        assertEquals(cachedMetadata, metadataList[0])
        assertEquals(updatedMetadata, metadataList[1])
    }

    @Test
    fun documentRemoved() = runTest {
        val documentSid = "TO000"
        val cachedMetadata = testDocumentMetadataResponse(sid = documentSid)
            .copy(data = emptyJsonObject())
            .toDocumentCacheMetadata()

        val cachedMetadataFlow = MutableSharedFlow<DocumentCacheMetadata?>()
        every { syncCache.getDocumentMetadataBySid(documentSid) } returns
                cachedMetadataFlow.onSubscription { emit(cachedMetadata) }
        every { syncCache.getDocumentMetadataByUniqueName(any()) } returns flowOf(null)

        val metadataFlow = repository.getDocumentMetadata(OpenOptions.OpenExisting(cachedMetadata.sid))
        val deferredMetadataList = async { metadataFlow.take(2).toList() }

        val notification = DocumentRemovedNotification(documentSid)
        val eventJson = json.encodeToJsonElement(notification).jsonObject
        val remoteEvent = RemoteEvent(documentSid, "document_removed", eventJson)
        remoteEventsFlow.emit(remoteEvent)

        waitAndVerify { syncCache.deleteDocumentBySid(documentSid) }
        cachedMetadataFlow.emit(null)

        val metadataList = deferredMetadataList.await()
        assertEquals(cachedMetadata, metadataList[0])
        assertEquals(null, metadataList[1])
    }

    @Test
    fun createNewMap() = runTest {
        val operation = mockk<CollectionCreateOperation>()
        every { operationsFactory.collectionCreateOperation(any(), any(), any()) } returns operation

        val response = testCollectionMetadataResponse(sid = "MP000")
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toCollectionMetadata(CollectionType.Map)
        val metadataFlow = mockk<Flow<CollectionMetadata?>>()
        every { syncCache.getCollectionMetadataBySid(CollectionType.Map, metadata.sid) } returns metadataFlow

        val result = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.CreateNew(uniqueName = null))

        coVerify { syncCache.put(metadata) }
        assertSame(result, metadataFlow)
    }

    @Test
    fun createNewMapError() = runTest {
        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post<CollectionMetadataResponse>(any()) } throws expectedException

        val result = runCatching { repository.getCollectionMetadata(CollectionType.Map, OpenOptions.CreateNew(uniqueName = null)) }

        assertTrue(result.isFailure)
        assertSame(expectedException, result.twilioException)
    }

    @Test
    fun openExistingMapError() = runTest {
        every { syncCache.getCollectionMetadataBySid(any(), any()) } returns flowOf(null)
        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns flowOf(null)

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post<CollectionMetadataResponse>(any()) } throws expectedException

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenExisting(sidOrUniqueName = "TO000"))

        assertSame(expectedException, metadataFlow.captureException())
    }

    @Test
    fun openExistingMapFromBackendBySid() = runTest {
        val mapSid = "TO000"

        val cachedMetadataBySidFlow = MutableSharedFlow<CollectionMetadata?>()

        every { syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid) } returns
                cachedMetadataBySidFlow.onSubscription { emit(null) }

        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns flowOf(null)

        val operation = mockk<CollectionOpenOperation>()
        every { operationsFactory.collectionOpenOperation(any()) } returns operation

        val response = testCollectionMetadataResponse(mapSid)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toCollectionMetadata(CollectionType.Map)
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataBySidFlow.emit(metadata) }
            return@answers metadata
        }

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenExisting(mapSid))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openExistingMapFromBackendByUniqueName() = runTest {
        val uniqueName = "uniqueName"

        val cachedMetadataByUniqueNameFlow = MutableSharedFlow<CollectionMetadata?>()

        every { syncCache.getCollectionMetadataBySid(any(), uniqueName) } returns flowOf(null)

        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns
                cachedMetadataByUniqueNameFlow.onSubscription { emit(null) }

        val operation = mockk<CollectionOpenOperation>()
        every { operationsFactory.collectionOpenOperation(any()) } returns operation

        val response = testCollectionMetadataResponse(sid = "MP000", uniqueName)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toCollectionMetadata(CollectionType.Map)
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataByUniqueNameFlow.emit(metadata) }
            return@answers metadata
        }

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenExisting(uniqueName))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateMapCreateNew() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<CollectionMetadata?>()

        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val operation = mockk<CollectionCreateOperation>()
        every { operationsFactory.collectionCreateOperation(any(), uniqueName, ttl) } returns operation

        val response = testCollectionMetadataResponse(sid = "MP000", uniqueName)
        coEvery { commandsScheduler.post(operation) } returns response

        val metadata = response.toCollectionMetadata(CollectionType.Map)
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataFlow.emit(metadata) }
            return@answers metadata
        }

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateMapOpenExisting() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<CollectionMetadata?>()

        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<CollectionCreateOperation>()
        val openOperation = mockk<CollectionOpenOperation>()

        every { operationsFactory.collectionCreateOperation(any(), uniqueName, ttl) } returns createOperation
        every { operationsFactory.collectionOpenOperation(any()) } returns openOperation

        val response = testCollectionMetadataResponse(sid = "MP000", uniqueName)
        coEvery { commandsScheduler.post(createOperation) } throws
                TwilioException(testErrorAlreadyExists)
        coEvery { commandsScheduler.post(openOperation) } returns
                response

        val metadata = response.toCollectionMetadata(CollectionType.Map)
        coEvery { syncCache.put(metadata) } answers {
            launch { cachedMetadataFlow.emit(metadata) }
            return@answers metadata
        }

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertEquals(metadata, metadataFlow.first())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
        coVerify(exactly = 1) { commandsScheduler.post(openOperation) }
        coVerify(exactly = 1) { syncCache.put(metadata) }
    }

    @Test
    fun openOrCreateMapErrorCreate() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<CollectionMetadata?>()

        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<CollectionCreateOperation>()
        every { operationsFactory.collectionCreateOperation(any(), uniqueName, ttl) } returns createOperation

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post(createOperation) } throws expectedException

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertSame(expectedException, metadataFlow.captureException())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
    }

    @Test
    fun openOrCreateMapErrorOpen() = runTest {
        val uniqueName = "uniqueName"
        val ttl = 1.hours

        val cachedMetadataFlow = MutableSharedFlow<CollectionMetadata?>()

        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns
                cachedMetadataFlow.onSubscription { emit(null) }

        val createOperation = mockk<CollectionCreateOperation>()
        val openOperation = mockk<CollectionOpenOperation>()

        every { operationsFactory.collectionCreateOperation(any(), uniqueName, ttl) } returns createOperation
        every { operationsFactory.collectionOpenOperation(any()) } returns openOperation

        coEvery { commandsScheduler.post(createOperation) } throws TwilioException(testErrorAlreadyExists)

        val expectedException = TwilioException(ErrorInfo(CommandPermanentError))
        coEvery { commandsScheduler.post(openOperation) } throws expectedException

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenOrCreate(uniqueName, ttl))

        assertSame(expectedException, metadataFlow.captureException())

        coVerify(exactly = 1) { commandsScheduler.post(createOperation) }
        coVerify(exactly = 1) { commandsScheduler.post(openOperation) }
    }

    @Test
    fun mapRemoved() = runTest {
        val mapSid = "MP000"
        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        val cachedMetadataFlow = MutableSharedFlow<CollectionMetadata?>()
        every { syncCache.getCollectionMetadataBySid(any(), mapSid) } returns
                cachedMetadataFlow.onSubscription { emit(cachedMetadata) }
        every { syncCache.getCollectionMetadataByUniqueName(any(), any()) } returns flowOf(null)

        val metadataFlow = repository.getCollectionMetadata(CollectionType.Map, OpenOptions.OpenExisting(cachedMetadata.sid))
        val deferredMetadataList = async { metadataFlow.take(2).toList() }

        val notification = MapRemovedNotification(mapSid)
        val eventJson = json.encodeToJsonElement(notification).jsonObject
        val remoteEvent = RemoteEvent(mapSid, "map_removed", eventJson)
        remoteEventsFlow.emit(remoteEvent)

        waitAndVerify { syncCache.deleteCollectionBySidOrUniqueName(CollectionType.Map, mapSid) }
        cachedMetadataFlow.emit(null)

        val metadataList = deferredMetadataList.await()
        assertEquals(cachedMetadata, metadataList[0])
        assertEquals(null, metadataList[1])
    }

    @Test
    fun getMapItemTimeout() = runTest {
        val mapSid = "MP000"
        val itemId = Key("key")
        val expectedException = TwilioException(ErrorInfo(ErrorReason.Timeout))

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        coEvery { syncCache.getCollectionItemData(mapSid, itemId) } returns null

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any()) } returns operation
        coEvery { commandsScheduler.post(operation) } answers { throw expectedException }


        val actualException = assertFails { repository.getCollectionItemData(cachedMetadata, itemId, useCache = true) }
            .toTwilioException(ErrorReason.Unknown)

        assertEquals(expectedException.errorInfo, actualException.errorInfo)
    }

    @Test
    fun getNotCachedMapItemFromBackend() = runTest {
        val mapSid = "MP000"
        val itemId = Key("key")

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        coEvery { syncCache.getCollectionItemData(mapSid, itemId) } returns null

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any()) } returns operation

        val expectedItem = testCollectionItemData(mapSid, itemId)
        val response = testCollectionItemsDataResponse(expectedItem)
        coEvery { commandsScheduler.post(operation) } returns response

        coEvery { syncCache.put(CollectionType.Map, mapSid, listOf(expectedItem), any(), any()) } returns
                listOf(CacheResult.Added(expectedItem))

        val actualItem = repository.getCollectionItemData(cachedMetadata, itemId, useCache = true)

        assertEquals(expectedItem, actualItem)

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) {
            syncCache.put(CollectionType.Map, mapSid, listOf(expectedItem), updateMetadataLastEventId = false, isCollectionEmpty = false)
        }
    }

    @Test
    fun getCachedMapItemFromCache() = runTest {
        val mapSid = "MP000"
        val itemId = Key("key")

        val expectedItem = testCollectionItemData(mapSid, itemId)

        coEvery { syncCache.getCollectionItemData(mapSid, itemId) } returns expectedItem

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        val actualItem = repository.getCollectionItemData(cachedMetadata, itemId, useCache = true)

        assertEquals(expectedItem, actualItem)
        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions
    }

    @Test
    fun getCachedMapItemFromBackend() = runTest {
        val mapSid = "MP000"
        val itemId = Key("key")

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any()) } returns operation

        val expectedItem = testCollectionItemData(mapSid, itemId)
        val response = testCollectionItemsDataResponse(expectedItem)
        coEvery { commandsScheduler.post(operation) } returns response

        coEvery { syncCache.put(any(), mapSid, listOf(expectedItem), any(), any()) } returns
                listOf(CacheResult.Added(expectedItem))

        val actualItem = repository.getCollectionItemData(cachedMetadata, itemId, useCache = false)

        assertEquals(expectedItem, actualItem)

        // didn't try to get item from cache because useCache = false
        coVerify(exactly = 0) { syncCache.getCollectionItemData(mapSid, itemId) }

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) {
            syncCache.put(any(), mapSid, listOf(expectedItem), updateMetadataLastEventId = false, isCollectionEmpty = false)
        }
    }

    @Test
    fun getRemovedMapItemFromCache() = runTest {
        val mapSid = "MP000"
        val itemId = Key("key")

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        val cachedItem = testCollectionItemData(mapSid, itemId)
            .copy(isRemoved = true)

        coEvery { syncCache.getCollectionItemData(mapSid, itemId) } returns cachedItem

        val actualItem = repository.getCollectionItemData(cachedMetadata, itemId, useCache = true)

        assertEquals(null, actualItem) // repository returns null without any network interactions
        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions
    }

    @Test
    fun getRemovedMapItemFromBackend() = runTest {
        val mapSid = "MP000"
        val itemId = Key("key")

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any()) } returns operation

        val response = testCollectionItemsDataResponse(emptyList())
        coEvery { commandsScheduler.post(operation) } returns response

        val actualItem = repository.getCollectionItemData(cachedMetadata, itemId, useCache = false)

        assertEquals(null, actualItem)

        // didn't try to get item from cache because useCache = false
        coVerify(exactly = 0) { syncCache.getCollectionItemData(mapSid, itemId) }

        coVerify(exactly = 1) { commandsScheduler.post(operation) }
        coVerify(exactly = 1) {
            syncCache.put(any(), mapSid, emptyList(), updateMetadataLastEventId = false, isCollectionEmpty = true)
        }
    }

    @Test
    fun getMapItemsTimeout() = runTest {
        val mapSid = "MP000"
        val expectedException = TwilioException(ErrorInfo(ErrorReason.Timeout))

        val cachedMetadata = testCollectionMetadataResponse(mapSid)
            .toCollectionMetadata(CollectionType.Map)

        coEvery { syncCache.getCollectionMetadataBySid(any(), mapSid) } returns flowOf(cachedMetadata)
        coEvery { syncCache.getCollectionMetadataByUniqueName(any(), mapSid) } returns flowOf(null)
        coEvery { syncCache.getNextCollectionItemData(any(), mapSid, any()) } returns null

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any(), any(), any(), any()) } returns operation
        coEvery { commandsScheduler.post(operation) } answers { throw expectedException }

        val channel = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true,
        )

        val iterator = channel.iterator()
        val actualException = assertFails { iterator.hasNext() }.toTwilioException(ErrorReason.Unknown)

        assertEquals(expectedException.errorInfo, actualException.errorInfo)
    }
}
