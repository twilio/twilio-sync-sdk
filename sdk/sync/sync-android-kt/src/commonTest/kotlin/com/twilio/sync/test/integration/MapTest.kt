//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.test.util.TestData
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.QueryOrder.Ascending
import com.twilio.sync.utils.QueryOrder.Descending
import com.twilio.sync.utils.SyncClientConfig
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.asFlow
import com.twilio.sync.utils.data
import com.twilio.sync.utils.forEach
import com.twilio.sync.utils.mutateItem
import com.twilio.sync.utils.setItem
import com.twilio.sync.utils.setMapItem
import com.twilio.sync.utils.setMapItemWithTtl
import com.twilio.sync.utils.use
import com.twilio.test.util.generateRandomString
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.twilioException
import com.twilio.test.util.wait
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.ErrorReason.MutateOperationAborted
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.InternalTwilioApi
import com.twilio.util.TwilioException
import com.twilio.util.emptyJsonObject
import com.twilio.util.toTwilioException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class MapTest {

    lateinit var syncClient: SyncClient

    open val config = SyncConfig()

    @OptIn(InternalTwilioApi::class)
    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()
        SyncCacheCleaner.clearAllCaches()
        syncClient = TestSyncClient(config = config) { requestToken() }
    }

    @AfterTest
    fun tearDown() {
        syncClient.shutdown()
    }

    @Test
    fun createMapDefault() = runTest {
        val map = syncClient.maps.create()

        assertTrue(map.sid.startsWith("MP"))
        assertNull(map.uniqueName)
        assertNotNull(map.dateCreated)
        assertNotNull(map.dateUpdated)
        assertNull(map.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, map.subscriptionState)
        assertTrue(map.isFromCache)
    }

    @Test
    fun createMapWithUniqueName() = runTest {
        val uniqueName = generateRandomString("map")
        val map = syncClient.maps.create(uniqueName)

        assertTrue(map.sid.startsWith("MP"))
        assertEquals(uniqueName, map.uniqueName)
        assertNotNull(map.dateCreated)
        assertNotNull(map.dateUpdated)
        assertNull(map.dateExpires)
    }

    @Test
    fun createMapWithTtl() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        assertTrue(map.sid.startsWith("MP"))
        assertNull(map.uniqueName)
        assertNotNull(map.dateCreated)
        assertNotNull(map.dateUpdated)
        assertNotNull(map.dateExpires)
    }

    @Test
    fun createMapWithUniqueNameAndTtl() = runTest {
        val uniqueName = generateRandomString("map")
        val map = syncClient.maps.create(uniqueName, 1.hours)

        assertTrue(map.sid.startsWith("MP"))
        assertEquals(uniqueName, map.uniqueName)
        assertNotNull(map.dateExpires)
    }

    @Test
    fun createMapError() = runTest {
        val map = syncClient.maps.create()
        val actualError = assertFails { syncClient.maps.create(uniqueName = map.sid) }
            .toTwilioException(Unknown).errorInfo

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = HttpStatusCode.BadRequest.value,
            code = 54302,
            message = "Invalid unique name. " +
                    "Expected a string with length 1-320 not matching the SID pattern [A-Z]{2}[a-f0-9]{32}"
        )

        assertEquals(expectedError, actualError)
    }

    @Test
    fun openOrCreateMapOpenExisting() = runTest {
        val uniqueName = generateRandomString("map")
        val creatingClient = TestSyncClient { requestToken() }

        val createdMap = creatingClient.maps.create(uniqueName)
        val openedMap = syncClient.maps.openOrCreate(uniqueName)

        assertEquals(uniqueName, openedMap.uniqueName)
        assertEquals(createdMap.sid, openedMap.sid)
        assertEquals(createdMap.dateCreated, openedMap.dateCreated)
        assertEquals(createdMap.dateUpdated, openedMap.dateUpdated)
        assertEquals(createdMap.dateExpires, openedMap.dateExpires)
    }

    @Test
    fun openOrCreateMaptCreateNew() = runTest {
        val uniqueName = generateRandomString("map")
        val createdMap = syncClient.maps.openOrCreate(uniqueName)

        assertEquals(uniqueName, createdMap.uniqueName)
        assertTrue(createdMap.sid.startsWith("MP"))
        assertNull(createdMap.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, createdMap.subscriptionState)
        assertTrue(createdMap.isFromCache)
    }

    @Test
    fun openOrCreateMapError() = runTest {
        val map = syncClient.maps.create()
        val actualError = assertFails { syncClient.maps.openOrCreate(uniqueName = map.sid) }
            .toTwilioException(Unknown).errorInfo

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = HttpStatusCode.BadRequest.value,
            code = 54302,
            message = "Invalid unique name. " +
                    "Expected a string with length 1-320 not matching the SID pattern [A-Z]{2}[a-f0-9]{32}"
        )

        assertEquals(expectedError, actualError)
    }

    @Test
    fun openExistingMap() = runTest {
        val uniqueName = generateRandomString("map")

        val creatingClient = TestSyncClient { requestToken() }
        val createdMap = creatingClient.maps.create(uniqueName)

        val openedBySid = syncClient.maps.openExisting(createdMap.sid)

        assertEquals(uniqueName, openedBySid.uniqueName)
        assertEquals(createdMap.sid, openedBySid.sid)
        assertEquals(createdMap.dateExpires, openedBySid.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, openedBySid.subscriptionState)
        assertTrue(openedBySid.isFromCache)

        val openedByUniqueName = syncClient.maps.openExisting(uniqueName)

        assertEquals(uniqueName, openedByUniqueName.uniqueName)
        assertEquals(createdMap.sid, openedByUniqueName.sid)
        assertEquals(createdMap.dateExpires, openedByUniqueName.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, openedByUniqueName.subscriptionState)
        assertTrue(openedByUniqueName.isFromCache)
    }

    @Test
    fun openNonExistingMap() = runTest {
        val result = runCatching { syncClient.maps.openExisting("TO111") }

        assertTrue(result.isFailure)

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = 404,
            code = 54300,
            message = "Unique name not found"
        )

        assertEquals(expectedError, result.twilioException.errorInfo)
    }

    @Test
    fun setMapTtlLocal() = runTest {
        val map = syncClient.maps.create()
        assertNull(map.dateExpires)

        map.setTtl(1.hours)

        assertNotNull(map.dateExpires)
    }

    @Test
    fun resetMapTtlLocal() = runTest {
        val map1 = syncClient.maps.create(ttl = 1.hours)
        val map2 = syncClient.maps.create(ttl = 1.hours)

        assertNotNull(map1.dateExpires)
        assertNotNull(map2.dateExpires)

        map1.setTtl(Duration.INFINITE)
        map2.setTtl(Duration.ZERO)

        assertNull(map1.dateExpires)
        assertNull(map2.dateExpires)
    }

    @Test
    fun removeMapLocal() = runTest {
        val map = syncClient.maps.create()
        assertFalse(map.isRemoved)

        map.removeMap()

        map.events.onRemoved.first()
        assertTrue(map.isRemoved)
    }

    @Test
    fun removeMapRemote_SubscribeBeforeRemove() = runTest {
        val uniqueName = generateRandomString("map")
        val map = syncClient.maps.create(uniqueName)
        assertFalse(map.isRemoved)

        var counter = 0
        val listener = map.events.onRemoved
            .onEach { counter++ }
            .launchIn(this)

        wait { map.subscriptionState == SubscriptionState.Established }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.remove(uniqueName)
        }

        wait { counter >= 1 }
        assertTrue(map.isRemoved)
        assertEquals(1, counter)

        delay(1.seconds) // to be sure that no more than one event received. RTDSDK-4256
        assertEquals(1, counter)

        listener.cancel()
    }

    @Test
    fun removeMapRemote_AddItemBeforeRemove() = runTest { // RTDSDK-4267
        val map1 = syncClient.maps.create()

        val client2 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }
        val map2 = client2.maps.openExisting(map1.sid)

        var counter1 = 0
        val listener1 = map1.events.onRemoved
            .onEach { counter1++ }
            .launchIn(this)

        var counter2 = 0
        val listener2 = map2.events.onRemoved
            .onEach { counter2++ }
            .launchIn(this)

        map1.setItem("key1", TestData(1))
        map1.removeMap()

        wait { counter1 >= 1 && counter2 >= 1}

        delay(1.seconds) // to be sure no more onRemoved events arrived

        assertEquals(1, counter1)
        assertEquals(1, counter2)

        listener1.cancel()
        listener2.cancel()
    }

    @Test
    fun removeMapRemote_SubscribeAfterRemove() = runTest {
        val uniqueName = generateRandomString("map")
        val map = syncClient.maps.create(uniqueName)
        assertFalse(map.isRemoved)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.remove(uniqueName)
        }

        map.events.onRemoved.first()
        assertTrue(map.isRemoved)
    }

    @Test
    fun mapSubscriptionState() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)
        assertEquals(SubscriptionState.Unsubscribed, map.subscriptionState)
        assertTrue(map.isFromCache)

        val listener1 = launch { map.events.onRemoved.collect() }
        val listener2 = launch { map.events.onRemoved.collect() }

        map.events.onSubscriptionStateChanged.first { it == SubscriptionState.Pending }
        map.events.onSubscriptionStateChanged.first { it == SubscriptionState.Subscribing }
        map.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, map.subscriptionState)
        assertFalse(map.isFromCache)

        wait { listener1.isActive && listener2.isActive }

        listener1.cancel()

        map.events.onSubscriptionStateChanged
            .onEach { assertEquals(SubscriptionState.Established, it) } // No state changes here
            .first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, map.subscriptionState)
        assertFalse(map.isFromCache)

        listener2.cancel()

        // Once all listeners disappeared map should unsubscribe itself
        map.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
        assertEquals(SubscriptionState.Unsubscribed, map.subscriptionState)
        assertTrue(map.isFromCache)
    }

    @Test
    fun setItemDataLocal() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }
        val onAdded = async { map.events.onItemAdded.first { it.data == addedData } }

        // add new item
        val addedItem = map.setItem("key", addedData)

        assertEquals(addedData, addedItem.data)
        onAdded.await()

        val updatedData = buildJsonObject { put("data", "value2") }
        val onUpdated = async { map.events.onItemUpdated.first { it.data == updatedData } }

        // update existing item
        val updatedItem = map.setItem("key", updatedData)

        assertEquals(updatedData, updatedItem.data)
        onUpdated.await()

        // Now the map has no listeners. So it should unsubscribe itself.
        wait { map.subscriptionState == SubscriptionState.Unsubscribed }

        // While our map is unsubscribed - remove the item from other client.
        // So it's still in our map's cache. In this case setItem() should try first to update existing item.
        // Once the update request failed - setItem() should fallback with adding new item.
        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.removeMapItem(map.sid, "key")
        }

        // Don't listen for any events here in order to keep the map unsubscribed.
        // Otherwise we will not be able to check that setItem() fallbacked to add new item.

        val addedData2 = buildJsonObject { put("data2", "value1") }
        val addedItem2 = map.setItem("key", addedData2)
        assertEquals(addedData2, addedItem2.data)
    }

    @Test
    fun setItemDataRemote() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val onItemAdded = async { map.events.onItemAdded.first() }
        val onItemUpdated = async { map.events.onItemUpdated.first() }
        val onItemRemoved = async { map.events.onItemRemoved.first() }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            val addedData = Pair("data", "value1")
            val addedItem = client.maps.setMapItemWithTtl(map.sid, "key", addedData, 1.hours)
            assertEquals(addedItem, onItemAdded.await())

            val updatedData = Pair("data", "value2")
            val updatedItem = client.maps.setMapItem(map.sid, "key", updatedData)
            assertEquals(updatedItem, onItemUpdated.await())

            client.maps.removeMapItem(map.sid, "key")
            // dateUpdated changes to the date when the item was removed
            val removedItem = updatedItem.copy(dateUpdated = onItemRemoved.await().dateUpdated)
            assertEquals(removedItem, onItemRemoved.await())

            assertEquals(addedData, addedItem.data())
            assertEquals(updatedData, updatedItem.data())

            assertEquals(addedItem.dateCreated, updatedItem.dateCreated)
            assertEquals(addedItem.dateCreated, removedItem.dateCreated)

            assertTrue(addedItem.dateUpdated < updatedItem.dateUpdated)
            assertTrue(updatedItem.dateUpdated < removedItem.dateUpdated)

            assertNotNull(addedItem.dateExpires)
            assertEquals(addedItem.dateExpires, updatedItem.dateExpires)
            assertEquals(addedItem.dateExpires, removedItem.dateExpires)
        }
    }

    @Test
    fun setItemDataLocalSubscribeBefore() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }

        var counter = 0
        val eventAddedItem = CompletableDeferred<SyncMap.Item>()

        val listener = launch {
            map.events.onItemAdded.collect { item ->
                println("!!! onItemAdded: $item")
                eventAddedItem.complete(item)
                counter++
            }
        }

        map.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        val addedItem = map.setItem("key", addedData)

        // wait to be sure that no more than one onItemAdded event received. See the counter check below.
        delay(2.seconds)
        listener.cancel()

        assertEquals(addedData, addedItem.data)
        assertEquals(addedData, eventAddedItem.await().data)
        assertEquals(1, counter)
    }

    @Test
    fun setItemDataLocalSubscribeInParallel() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }

        var counter = 0
        val eventAddedItem = CompletableDeferred<SyncMap.Item>()

        val listener = launch {
            map.events.onItemAdded.collect { item ->
                println("!!! onItemAdded: $item")
                eventAddedItem.complete(item)
                counter++
            }
        }

        // Don't wait until subscription is established

        val addedItem = map.setItem("key", addedData)

        delay(2.seconds)
        listener.cancel()

        assertEquals(addedData, addedItem.data)
        assertEquals(addedData, eventAddedItem.await().data)
        assertEquals(1, counter)
    }

    @Test
    fun setItemDataLocalSubscribeAfter() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }
        var counter = 0

        val addedItem = map.setItem("key", addedData)

        val listener = launch {
            map.events.onItemAdded.collect { item ->
                println("!!! onItemAdded: $item")
                counter++
            }
        }

        map.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }
        delay(2000)
        listener.cancel()

        assertEquals(addedData, addedItem.data)
        assertEquals(0, counter) // We missed event, because subscribed after setItem completed.
    }

    @Test
    fun setItemDataRemoteStress() = runTest(timeout = 2.minutes) {
        val N = 200
        val itemKey = "key"

        val config = SyncConfig(
            syncClientConfig = SyncClientConfig(commandTimeout = 60.seconds)
        )
        val sender = TestSyncClient(config = config, useLastUserAccount = false) { requestToken("otherUser") }

        val senderMap = sender.maps.create(ttl = 1.hours)
        val receiverMap = syncClient.maps.openExisting(senderMap.sid)

        val pendingAddedEvent = mutableMapOf<String, JsonObject>()
        val pendingUpdatedEvents = mutableMapOf<String, JsonObject>()

        List(N) { counter ->
            val data = buildJsonObject { put("data1", "value$counter") }
            pendingAddedEvent["$itemKey$counter"] = data
            launch { senderMap.setItem("$itemKey$counter", data) }
        }.joinAll()

        val onItemAddedListener = launch {
            receiverMap.events.onItemAdded.collect { item ->
                val addedData = pendingAddedEvent.remove(item.key)
                if (addedData == item.data) {
                    return@collect
                }

                // check if we've just received updatedData instead of addedData in the onItemAddedListener?
                // This is normal case, which happens when we receive misordered events, i.e.
                // map_item_updated come before map_item_added.
                val updatedData = pendingUpdatedEvents.remove(item.key)
                assertEquals(updatedData, item.data)
            }
        }

        val onItemUpdatedListener = launch {
            receiverMap.events.onItemUpdated.collect { item ->
                val expectedData = pendingUpdatedEvents.remove(item.key)
                assertEquals(expectedData, item.data)
            }
        }

        List(N) { counter ->
            val data = buildJsonObject { put("data2", "value$counter") }
            pendingUpdatedEvents["$itemKey$counter"] = data
            launch { senderMap.setItem("$itemKey$counter", data) }
        }.joinAll()

        runCatching {
            wait(timeout = 60.seconds) { pendingUpdatedEvents.isEmpty() }
        }

        println("Still no added events: ${pendingAddedEvent.size}")
        pendingAddedEvent.forEach { println(it.key) }

        println("Still no updated events: ${pendingUpdatedEvents.size}")
        pendingUpdatedEvents.forEach { println(it.key) }

        assertTrue(pendingUpdatedEvents.isEmpty())
        assertTrue(pendingAddedEvent.isEmpty())

        repeat(N) { counter ->
            val updatedData = buildJsonObject { put("data2", "value$counter") }
            assertEquals(updatedData, receiverMap.getItem("$itemKey$counter")?.data)
        }

        // Now all items added and updated. Start removing items
        // To have more fun we unsubscribe here and then re-subscribe in parallel with removing items.
        // So part of removed events come during process of establishing subscription

        onItemAddedListener.cancel()
        onItemUpdatedListener.cancel()
        wait { receiverMap.subscriptionState == SubscriptionState.Unsubscribed }

        val pendingRemovedEvents = mutableMapOf<String, JsonObject>()

        val onItemRemovedListener = launch {
            receiverMap.events.onItemRemoved.collect { item -> // start re-subscribing here
                val expectedData = pendingRemovedEvents.remove(item.key)
                println("onItemRemovedListener [${item.key}]:\nexpectedData: $expectedData\nactualData: ${item.data}")
                assertEquals(expectedData, item.data)
            }
        }

        List(N) { counter ->
            val data = buildJsonObject { put("data2", "value$counter") }
            pendingRemovedEvents["$itemKey$counter"] = data
            launch { senderMap.removeItem("$itemKey$counter") }
        }.joinAll()

        runCatching {
            wait(timeout = 60.seconds) { pendingRemovedEvents.isEmpty() }
        }

        onItemRemovedListener.cancel()

        println("Still no removed events: ${pendingRemovedEvents.size}")
        pendingRemovedEvents.forEach { println(it.key) }

        assertTrue(pendingRemovedEvents.isEmpty())

        repeat(N) { counter ->
            assertNull(receiverMap.getItem("$itemKey$counter"))
        }

        sender.shutdown()
    }

    @Test
    fun getItemData() = runTest {
        val key = "key"
        val data = buildJsonObject { put("data", "value") }

        val map = syncClient.maps.create(ttl = 1.hours)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.setMapItem(map.sid, key, data)
        }

        val item = map.getItem(key)

        assertNotNull(item)
        assertEquals(key, item.key)
        assertEquals(data, item.data)
        assertNull(item.dateExpires)
    }

    @Test
    fun getNonExistingItemData() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        assertNull(map.getItem("key"))
    }

    @Test
    fun getRemovedItemData() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        map.setItem("key", TestData(0))
        map.removeItem("key")

        assertNull(map.getItem("key"))
    }

    @Test
    fun removeItemLocal() = runTest {
        val key1 = "key1"
        val key2 = "key2"
        val data = buildJsonObject { put("data", "value") }

        val map = syncClient.maps.create(ttl = 1.hours)

        map.setItem(key1, data)
        map.setItem(key2, data)

        assertNotNull(map.getItem(key1))
        assertNotNull(map.getItem(key2))

        val onItemRemoved = async { map.events.onItemRemoved.first { it.key == key1 } }

        map.removeItem(key1)

        onItemRemoved.await()
        assertNull(map.getItem(key1))
        assertNotNull(map.getItem(key2))
    }

    @Test
    fun removeItemRemote() = runTest {
        val key1 = "key1"
        val key2 = "key2"
        val data = buildJsonObject { put("data", "value") }

        val map = syncClient.maps.create(ttl = 1.hours)

        map.setItem(key1, data)
        map.setItem(key2, data)

        assertNotNull(map.getItem(key1))
        assertNotNull(map.getItem(key2))

        val onItemRemoved = async { map.events.onItemRemoved.first { it.key == key1 } }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.removeMapItem(map.sid, key1)
        }

        onItemRemoved.await()
        assertNull(map.getItem(key1))
        assertNotNull(map.getItem(key2))
    }

    @Test
    fun removeNonExistingItem() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val result = runCatching {
            map.removeItem("nonExistingKey")
        }

        assertTrue(result.isFailure)

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = 404,
            code = 54201,
            message = "Item not found"
        )

        assertEquals(expectedError, result.twilioException.errorInfo)
    }

    @Test
    fun mutateCollision() = runTest {
        val config = SyncConfig(
            syncClientConfig = SyncClientConfig(commandTimeout = 60.seconds)
        )
        val client = TestSyncClient(config = config, useLastUserAccount = false) { requestToken("otherUser") }
        val map = client.maps.create(ttl = 1.hours)

        @Serializable
        data class Counter(val value: Long = 0) {
            operator fun plus(x: Long) = Counter(value + x)
        }

        List(10) {
            async {
                map.mutateItem<Counter>("counter") { counter -> counter?.let { it + 1 } ?: Counter(1) }
            }
        }.awaitAll()

        assertEquals(Counter(10), map.getItem("counter")?.data())
    }

    @Test
    fun mutateData() = runTest {
        val itemKey = "key"
        val senderMap = syncClient.maps.create(ttl = 1.hours)

        val receiver = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }
        val receiverMap = receiver.maps.openExisting(senderMap.sid)

        val newData = buildJsonObject { put("data", "value") }

        val onSenderItemUpdated = async { senderMap.events.onItemUpdated.first { it.data == newData } }
        val onReceiverItemUpdated = async { receiverMap.events.onItemUpdated.first { it.data == newData } }

        senderMap.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }
        receiverMap.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        senderMap.setItem(itemKey, emptyJsonObject())
        senderMap.mutateItem(itemKey) { newData }

        awaitAll(onSenderItemUpdated, onReceiverItemUpdated)

        assertEquals(newData, senderMap.getItem(itemKey)?.data)
        assertEquals(newData, receiverMap.getItem(itemKey)?.data)
    }

    @Test
    fun mutateCachedButRemovedFromBackendItem() = runTest {
        val itemKey = "key"
        val map = syncClient.maps.create(ttl = 1.hours)
        map.setItem(itemKey, TestData(1))

        // Now remove item from backend, but our map is not subscribed, so it still has this item in cache.
        // In this case mutateItem() method should try to mutate item first and fail with http 404 error.
        // Then it should fallback to add new item with the same key and new data.
        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.removeMapItem(map.sid, itemKey)
        }

        val expectedCurrentData = mutableListOf(TestData(1), null)

        map.mutateItem<TestData>(itemKey) { currentData ->
            assertEquals(expectedCurrentData.removeFirst(), currentData)
            TestData(2)
        }

        assertTrue { expectedCurrentData.isEmpty() }
        assertEquals(TestData(2), map.getItem(itemKey)?.data())
    }

    @Test
    fun mutateRemovedItem() = runTest { // RTDSDK-4301
        val itemKey = "key"
        val map = syncClient.maps.create(ttl = 1.hours)

        map.setItem(itemKey, TestData(1))
        map.removeItem(itemKey)

        map.mutateItem<TestData>(itemKey) { TestData(2) }

        assertEquals(TestData(2), map.getItem(itemKey)?.data())
    }

    @Test
    fun mutateNonCachedButExistingItem() = runTest {
        val itemKey = "key"
        val map = syncClient.maps.create(ttl = 1.hours)

        // Now add an item to backend, but our map is not subscribed, so it still doesn't have this item in cache.
        // In this case mutateItem() method should try to add new item first and fail with http 409 error.
        // Then it should fallback to mutate the existing item.
        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps.setMapItem(map.sid, itemKey, TestData(1))
        }

        val expectedCurrentData = mutableListOf(null, TestData(1))

        map.mutateItem<TestData>(itemKey) { currentData ->
            assertEquals(expectedCurrentData.removeFirst(), currentData)
            TestData(2)
        }

        assertTrue { expectedCurrentData.isEmpty() }
        assertEquals(TestData(2), map.getItem(itemKey)?.data())
    }

    @Test
    fun mutateItemAbort() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)
        map.setItem("key", emptyJsonObject())

        val result = runCatching {
            map.mutateItem("key") { null }
        }

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            message = "Mutate operation aborted: Mutator function has returned null as new data"
        )
        val expectedCause = ErrorInfo(reason = MutateOperationAborted)

        assertEquals(expectedError, result.twilioException.errorInfo)
        assertEquals(expectedCause, (result.twilioException.cause as TwilioException).errorInfo)
    }

    @Test
    fun queryItemsTestInterfaces() = runTest {
        val expectedMap = buildMap {
            repeat(10) {
                put("key$it", TestData(it))
            }
        }

        val map = syncClient.maps.create(ttl = 1.hours)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            expectedMap.map { (key, value) ->
                launch { client.maps.setMapItem(map.sid, key, value) }
            }.joinAll()
        }

        val actualMap1 = buildMap<String, TestData> {
            val iterator = map.queryItems()

            while (iterator.hasNext()) {
                val item = iterator.next()
                put(item.key, item.data())
            }

            iterator.close()
        }

        assertEquals(expectedMap, actualMap1)

        val actualMap2 = buildMap<String, TestData> {
            map.queryItems().forEach { item ->
                put(item.key, item.data())
            }
        }

        assertEquals(expectedMap, actualMap2)

        val actualMap3 = map.queryItems()
            .asFlow()
            .toList()
            .associate { it.key to it.data<TestData>() }

        assertEquals(expectedMap, actualMap3)

        val actualMap4 = buildMap<String, TestData> {
            for (item in map) {
                put(item.key, item.data())
            }
        }

        assertEquals(expectedMap, actualMap4)

        val actualMap5 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.maps
                .queryItems(map.sid)
                .asFlow()
                .toList()
                .associate { it.key to it.data<TestData>() }
        }

        assertEquals(expectedMap, actualMap5)
    }

    @Test
    fun queryItemsCancel() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours).apply {
            setItem("key1", TestData(1))
            setItem("key2", TestData(2))
        }

        val iterator = map.queryItems()

        assertTrue(iterator.hasNext())
        assertEquals(TestData(1), iterator.next().data())

        iterator.close()

        val result = runCatching { iterator.hasNext() }
        assertTrue(result.isFailure)
    }

    @Test
    fun queryItemsAfterRemove() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours).apply {
            setItem("key1", TestData(1))
            setItem("key2", TestData(2))
        }

        map.queryItems().asFlow().toList() // now both items in cache

        map.removeItem("key1")
        map.removeItem("key2")

        val items = map.queryItems().asFlow().toList()
        assertEquals(emptyList(), items)
    }

    @Test
    fun queryItemsWhenHavePreCached() = runTest {
        val config = SyncConfig(
            // pageSize = 1 in order to not query more items then necessary
            syncClientConfig = SyncClientConfig(pageSize = 1)
        )
        val client = TestSyncClient(config = config, useLastUserAccount = false) { requestToken("otherIdentity") }

        val map = client.maps.create(ttl = 1.hours).apply {
            setItem("key1", TestData(1))
            setItem("key2", TestData(2))
            setItem("key3", TestData(3))
        }

        var expectedKeys = listOf("key1", "key2", "key3")
        var actualKeys = map.queryItems().asFlow().toList().map { it.key } // get all items from backend and cache them

        assertEquals(expectedKeys, actualKeys)

        // add items from other client:
        // 2 items between key1 and key2 and one item between key2 and key3
        with(syncClient) {
            maps.setMapItem(map.sid, "key11", TestData(11))
            maps.setMapItem(map.sid, "key12", TestData(12))
            maps.setMapItem(map.sid, "key21", TestData(21))
        }

        // now syncClient still doesn't aware that items were added and returns what it has in cache
        actualKeys = map.queryItems().asFlow().toList().map { it.key }

        assertEquals(expectedKeys, actualKeys)

        map.getItem("key11") // get item with key11 and put it into cache

        // now syncClient aware that items were added between key1 and key2. So it re-requests them from backend,
        // but it still doesn't aware about the item key21, because pageSize is 1
        expectedKeys = listOf("key1", "key11", "key12", "key2", "key3")
        actualKeys = map.queryItems(pageSize = 1).asFlow().toList().map { it.key }

        assertEquals(expectedKeys, actualKeys)

        // now we re-request all items from backend without using cache. So all items should be there
        expectedKeys = listOf("key1", "key11", "key12", "key2", "key21", "key3")
        actualKeys = map.queryItems(pageSize = 1, useCache = false).asFlow().toList().map { it.key }

        assertEquals(expectedKeys, actualKeys)

        client.shutdown()
    }

    @Test
    fun queryItemsEmpty() = checkQueryItems(itemsCount = 0)

    @Test
    fun queryItemsEmptyNoCache() = checkQueryItems(itemsCount = 0, useCache = false)

    @Test
    fun queryItemsTwoPagesAsc() = checkQueryItems(queryOrder = Ascending)

    @Test
    fun queryItemsTwoPagesAscNoCache() = checkQueryItems(queryOrder = Ascending, useCache = false)

    @Test
    fun queryItemsTwoPagesDesc() = checkQueryItems(queryOrder = Descending)

    @Test
    fun queryItemsTwoPagesDescNoCache() = checkQueryItems(queryOrder = Descending, useCache = false)

    @Test
    fun queryItemsThreePagesAsc() = checkQueryItems(itemsCount = 10, pageSize = 4)

    @Test
    fun queryItemsThreePagesAscNoCache() = checkQueryItems(itemsCount = 10, pageSize = 4, useCache = false)

    @Test
    fun queryItemsStartKeyInclusiveAsc() =
        checkQueryItems(startKey = "key1", includeStartKey = true, queryOrder = Ascending)

    @Test
    fun queryItemsStartKeyInclusiveAscNoCache() =
        checkQueryItems(startKey = "key2", includeStartKey = true, queryOrder = Ascending, useCache = false)

    @Test
    fun queryItemsStartKeyNotInclusiveAsc() =
        checkQueryItems(startKey = "key3", includeStartKey = false, queryOrder = Ascending)

    @Test
    fun queryItemsStartKeyNotInclusiveAscNoCache() =
        checkQueryItems(startKey = "key4", includeStartKey = false, queryOrder = Ascending, useCache = false)

    @Test
    fun queryItemsStartKeyInclusiveDesc() =
        checkQueryItems(startKey = "key5", includeStartKey = true, queryOrder = Descending)

    @Test
    fun queryItemsStartKeyInclusiveDescNoCache() =
        checkQueryItems(startKey = "key6", includeStartKey = true, queryOrder = Descending, useCache = false)

    @Test
    fun queryItemsStartKeyNotInclusiveDesc() =
        checkQueryItems(startKey = "key7", includeStartKey = false, queryOrder = Descending)

    @Test
    fun queryItemsStartKeyNotInclusiveDescNoCache() =
        checkQueryItems(startKey = "key8", includeStartKey = false, queryOrder = Descending, useCache = false)

    @Test
    fun queryItemsStartKeyNotExistsAsc() =
        checkQueryItems(startKey = "nonExistingKey", queryOrder = Ascending)

    @Test
    fun queryItemsStartKeyNotExistsDesc() =
        checkQueryItems(startKey = "nonExistingKey", queryOrder = Descending)

    @Test
    fun queryItemsStartKeyNotExistsInBetween() =
        checkQueryItems(startKey = "key31", queryOrder = Ascending)

    private fun checkQueryItems(
        itemsCount: Int = 10,
        startKey: String? = null,
        includeStartKey: Boolean = true,
        queryOrder: QueryOrder = Ascending,
        pageSize: Int = 5,
        useCache: Boolean = true
    ) = runTest {
        val mapData = buildMap {
            repeat(itemsCount) {
                put("key$it", TestData(it))
            }
        }

        val map = syncClient.maps.create(ttl = 1.hours)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            mapData.map { (key, value) ->
                launch { client.maps.setMapItem(map.sid, key, value) }
            }.joinAll()
        }

        val expectedMap = mapData
            .filterNot { (key, _) -> !includeStartKey && key == startKey }
            .filterKeys { key ->
                when {
                    startKey == null -> true
                    queryOrder == Ascending -> key >= startKey
                    queryOrder == Descending -> key <= startKey
                    else -> error("Never happens")
                }
            }

        println("expectedMap size: ${expectedMap.size}")

        // request map from backend
        val actualMap1 = buildMap<String, TestData> {
            map.queryItems(startKey, includeStartKey, queryOrder, pageSize, useCache).forEach { item ->
                put(item.key, item.data())
            }
        }

        assertEquals(expectedMap, actualMap1)

        // get map from cache (when useCache = true)
        val actualMap2 = buildMap<String, TestData> {
            map.queryItems(startKey, includeStartKey, queryOrder, pageSize, useCache).forEach { item ->
                put(item.key, item.data())
            }
        }

        assertEquals(expectedMap, actualMap2)
    }
}
