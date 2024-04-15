//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.twilio.sync.cache.SyncCache
import com.twilio.sync.cache.persistent.SyncDatabase
import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.sync.operations.SyncOperationsFactory
import com.twilio.sync.repository.MapItemAddedNotification
import com.twilio.sync.repository.MapItemRemovedNotification
import com.twilio.sync.repository.MapItemUpdatedNotification
import com.twilio.sync.repository.Notification
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.repository.toCollectionMetadata
import com.twilio.sync.subscriptions.RemoteEvent
import com.twilio.sync.test.util.testCollectionMetadataResponse
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionType
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.wait
import com.twilio.twilsock.commands.CommandsScheduler
import com.twilio.util.emptyJsonObject
import com.twilio.util.json
import com.twilio.util.newChildCoroutineScope
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/*
* This is test for integration SyncRepository and SyncCache. In order to test cases when events from backend come
* In wrong order (backend does not guarantee order of events).
*/
class MisorderedEventsTest {

    private lateinit var syncCache: SyncCache

    private lateinit var repository: SyncRepository

    private lateinit var eventsScope: CoroutineScope

    private val remoteEventsFlow = MutableSharedFlow<RemoteEvent>()

    @RelaxedMockK
    private lateinit var commandsScheduler: CommandsScheduler

    @RelaxedMockK
    private lateinit var operationsFactory: SyncOperationsFactory

    @RelaxedMockK
    private lateinit var links: ConfigurationLinks

    private val mapSid = "MP000"

    private val metadata = testCollectionMetadataResponse(mapSid)
        .toCollectionMetadata(CollectionType.Map)

    private val data = List(2) {
        buildJsonObject { put("key", "value$it") }
    }

    sealed interface CollectionItemEvent {
        data class Added(val data: JsonObject) : CollectionItemEvent
        data class Updated(val data: JsonObject) : CollectionItemEvent
        data class Removed(val data: JsonObject) : CollectionItemEvent
    }

    private val receivedEvents = mutableListOf<CollectionItemEvent>()

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@MisorderedEventsTest, relaxUnitFun = true)

        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SyncDatabase.Schema.create(driver)

        syncCache = SyncCache("inMemoryDatabase", driver)
        syncCache.put(metadata)

        repository = SyncRepository(
            testCoroutineScope,
            syncCache,
            commandsScheduler,
            remoteEventsFlow,
            operationsFactory,
            links
        )

        receivedEvents.clear()

        eventsScope = testCoroutineScope.newChildCoroutineScope()

        eventsScope.launch {
            repository.onCollectionItemAdded(mapSid)
                .map { CollectionItemEvent.Added(it.data) }
                .onEach { println("MapItemEvent: $it") }
                .toList(receivedEvents)
        }

        eventsScope.launch {
            repository.onCollectionItemUpdated(mapSid)
                .map { CollectionItemEvent.Updated(it.data) }
                .onEach { println("MapItemEvent: $it") }
                .toList(receivedEvents)
        }

        eventsScope.launch {
            repository.onCollectionItemRemoved(mapSid)
                .map { CollectionItemEvent.Removed(it.data) }
                .onEach { println("MapItemEvent: $it") }
                .toList(receivedEvents)
        }
    }

    @AfterTest
    fun tearDown() {
        eventsScope.cancel()
    }

    @Test
    fun orderedEvents() = runTest {
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 1, "key", data[0]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemUpdatedNotification>(eventId = 2, "key", data[1]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemRemovedNotification>(eventId = 3, "key"))

        val expectedEvents = listOf(
            CollectionItemEvent.Added(data[0]),
            CollectionItemEvent.Updated(data[1]),
            CollectionItemEvent.Removed(data[1]),
        )

        wait { receivedEvents.size == expectedEvents.size }
        assertEquals(expectedEvents, receivedEvents)

        val newMetadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!
        assertEquals(3, newMetadata.lastEventId)
    }

    @Test
    fun updatedBeforeAdded() = runTest {
        remoteEventsFlow.emit(createRemoteEvent<MapItemUpdatedNotification>(eventId = 2, "key", data[1]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 1, "key", data[0]))

        val expectedEvents = listOf<CollectionItemEvent>(
            CollectionItemEvent.Added(data[1]),
        )

        wait { receivedEvents.size == expectedEvents.size }
        assertEquals(expectedEvents, receivedEvents)

        val newMetadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!
        assertEquals(2, newMetadata.lastEventId)
    }

    @Test
    fun addedBeforeRemoved() = runTest {
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 1, "key", data[0]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 3, "key", data[1]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemRemovedNotification>(eventId = 2, "key"))

        val expectedEvents = listOf(
            CollectionItemEvent.Added(data[0]),
            CollectionItemEvent.Updated(data[1]),
        )

        wait { receivedEvents.size == expectedEvents.size }
        assertEquals(expectedEvents, receivedEvents)

        val newMetadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!
        assertEquals(3, newMetadata.lastEventId)
    }

    @Test
    fun addedAfterRemoved() = runTest {
        remoteEventsFlow.emit(createRemoteEvent<MapItemRemovedNotification>(eventId = 2, "key"))
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 1, "key", data[1]))

        delay(1.seconds)

        assertTrue(receivedEvents.isEmpty()) // Added notification has been ignored

        val item = syncCache.getCollectionItemData(mapSid, CollectionItemId.Key("key"))
        assertEquals(true, item?.isRemoved)

        val newMetadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!
        assertNull(newMetadata.isEmpty) // isEmpty is not set to false on handling of the Removed notification.
        assertEquals(2, newMetadata.lastEventId)
    }

    @Test
    fun addedAgainAfterRemoved() = runTest {
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 1, "key", data[0]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemRemovedNotification>(eventId = 2, "key"))
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 3, "key", data[1]))

        val expectedEvents = listOf(
            CollectionItemEvent.Added(data[0]),
            CollectionItemEvent.Removed(data[0]),
            CollectionItemEvent.Added(data[1]),
        )

        wait { receivedEvents.size == expectedEvents.size }
        assertEquals(expectedEvents, receivedEvents)

        val newMetadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!
        assertEquals(3, newMetadata.lastEventId)
    }

    @Test
    fun duplicateRemovedEvent() = runTest {
        remoteEventsFlow.emit(createRemoteEvent<MapItemAddedNotification>(eventId = 1, "key", data[0]))
        remoteEventsFlow.emit(createRemoteEvent<MapItemRemovedNotification>(eventId = 2, "key"))
        remoteEventsFlow.emit(createRemoteEvent<MapItemRemovedNotification>(eventId = 2, "key"))

        val expectedEvents = listOf(
            CollectionItemEvent.Added(data[0]),
            CollectionItemEvent.Removed(data[0]),
        )

        wait { receivedEvents.size == expectedEvents.size }
        delay(1.seconds) // to be sure no more events will be received
        assertEquals(expectedEvents, receivedEvents)

        val newMetadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!
        assertEquals(2, newMetadata.lastEventId)
    }

    private inline fun <reified T : Notification> createRemoteEvent(
        eventId: Long,
        key: String,
        data: JsonObject = emptyJsonObject(),
    ): RemoteEvent {
        when {
            T::class == MapItemAddedNotification::class -> {
                val notification = MapItemAddedNotification(
                    eventId = eventId,
                    mapSid = mapSid,
                    key = key,
                    data = data,
                    dateCreated = Clock.System.now(),
                    revision = "$eventId"
                )
                val eventJson = json.encodeToJsonElement(notification).jsonObject
                return RemoteEvent(mapSid, "map_item_added", eventJson)
            }

            T::class == MapItemUpdatedNotification::class -> {
                val notification = MapItemUpdatedNotification(
                    eventId = eventId,
                    mapSid = mapSid,
                    key = key,
                    data = data,
                    dateCreated = Clock.System.now(),
                    revision = "$eventId",
                )
                val eventJson = json.encodeToJsonElement(notification).jsonObject
                return RemoteEvent(mapSid, "map_item_updated", eventJson)
            }

            T::class == MapItemRemovedNotification::class -> {
                val notification = MapItemRemovedNotification(
                    eventId = eventId,
                    mapSid = mapSid,
                    key = key,
                    dateCreated = Clock.System.now(),
                )
                val eventJson = json.encodeToJsonElement(notification).jsonObject
                return RemoteEvent(mapSid, "map_item_removed", eventJson)
            }

            else -> throw IllegalArgumentException("Unsupported notification type")
        }
    }
}
