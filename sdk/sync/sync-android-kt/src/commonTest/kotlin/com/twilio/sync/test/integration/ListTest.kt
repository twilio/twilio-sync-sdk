//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncList
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.test.util.TestData
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.SyncClientConfig
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.addItem
import com.twilio.sync.utils.addListItem
import com.twilio.sync.utils.asFlow
import com.twilio.sync.utils.data
import com.twilio.sync.utils.forEach
import com.twilio.sync.utils.kListItemNotFound
import com.twilio.sync.utils.mutateItem
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class ListTest {

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
    fun createListDefault() = runTest {
        val list = syncClient.lists.create()

        assertTrue(list.sid.startsWith("ES"))
        assertNull(list.uniqueName)
        assertNotNull(list.dateCreated)
        assertNotNull(list.dateUpdated)
        assertNull(list.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, list.subscriptionState)
        assertTrue(list.isFromCache)
    }

    @Test
    fun createListWithUniqueName() = runTest {
        val uniqueName = generateRandomString("list")
        val list = syncClient.lists.create(uniqueName)

        assertTrue(list.sid.startsWith("ES"))
        assertEquals(uniqueName, list.uniqueName)
        assertNotNull(list.dateCreated)
        assertNotNull(list.dateUpdated)
        assertNull(list.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, list.subscriptionState)
        assertTrue(list.isFromCache)
    }

    @Test
    fun createListWithTtl() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        assertTrue(list.sid.startsWith("ES"))
        assertNull(list.uniqueName)
        assertNotNull(list.dateCreated)
        assertNotNull(list.dateUpdated)
        assertNotNull(list.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, list.subscriptionState)
        assertTrue(list.isFromCache)
    }

    @Test
    fun createListWithUniqueNameAndTtl() = runTest {
        val uniqueName = generateRandomString("list")
        val list = syncClient.lists.create(uniqueName, ttl = 1.hours)

        assertTrue(list.sid.startsWith("ES"))
        assertEquals(uniqueName, list.uniqueName)
        assertNotNull(list.dateCreated)
        assertNotNull(list.dateUpdated)
        assertNotNull(list.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, list.subscriptionState)
        assertTrue(list.isFromCache)
    }

    @Test
    fun createListError() = runTest {
        val invalidUniqueName = "AB1234567890abcdef1234567890abcdef" // uniqueName matching SID pattern
        val actualError = assertFails { syncClient.lists.create(invalidUniqueName) }
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
    fun openOrCreateListOpenExisting() = runTest {
        val uniqueName = generateRandomString("list")
        val creatingClient = TestSyncClient { requestToken() }

        val createdList = creatingClient.lists.create(uniqueName)
        val openedList = syncClient.lists.openOrCreate(uniqueName)

        assertEquals(uniqueName, openedList.uniqueName)
        assertEquals(createdList.sid, openedList.sid)
        assertEquals(createdList.dateCreated, openedList.dateCreated)
        assertEquals(createdList.dateUpdated, openedList.dateUpdated)
        assertEquals(createdList.dateExpires, openedList.dateExpires)
    }

    @Test
    fun openOrCreateListCreateNew() = runTest {
        val uniqueName = generateRandomString("list")
        val createdList = syncClient.lists.openOrCreate(uniqueName)

        assertEquals(uniqueName, createdList.uniqueName)
        assertTrue(createdList.sid.startsWith("ES"))
        assertNull(createdList.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, createdList.subscriptionState)
        assertTrue(createdList.isFromCache)
    }

    @Test
    fun openOrCreateListError() = runTest {
        val invalidUniqueName = "AB1234567890abcdef1234567890abcdef" // uniqueName matching SID pattern
        val exception = assertFailsWith<TwilioException> { syncClient.lists.openOrCreate(invalidUniqueName) }

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = HttpStatusCode.BadRequest.value,
            code = 54302,
            message = "Invalid unique name. " +
                    "Expected a string with length 1-320 not matching the SID pattern [A-Z]{2}[a-f0-9]{32}"
        )

        assertEquals(expectedError, exception.errorInfo)
    }

    @Test
    fun openExistingList() = runTest {
        val uniqueName = generateRandomString("list")
        val creatingClient = TestSyncClient { requestToken() }

        val createdList = creatingClient.lists.create(uniqueName)
        val openedList = syncClient.lists.openExisting(uniqueName)

        assertEquals(uniqueName, openedList.uniqueName)
        assertEquals(createdList.sid, openedList.sid)
        assertEquals(createdList.dateCreated, openedList.dateCreated)
        assertEquals(createdList.dateUpdated, openedList.dateUpdated)
        assertEquals(createdList.dateExpires, openedList.dateExpires)
    }

    @Test
    fun openNonExistingList() = runTest {
        val result = runCatching { syncClient.lists.openExisting("ES111") }

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
    fun setListTtlLocal() = runTest {
        val list = syncClient.lists.create()
        assertNull(list.dateExpires)

        list.setTtl(1.hours)

        assertNotNull(list.dateExpires)
    }

    @Test
    fun resetListTtlLocal() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)
        assertNotNull(list.dateExpires)

        list.setTtl(Duration.INFINITE)

        assertNull(list.dateExpires)
    }

    @Test
    fun removeListLocal() = runTest {
        val list = syncClient.lists.create()
        assertFalse(list.isRemoved)

        list.removeList()

        assertTrue(list.isRemoved)
    }

    @Test
    fun removeListRemote_SubscribeBeforeRemove() = runTest {
        val uniqueName = generateRandomString("list")
        val list = syncClient.lists.create(uniqueName)
        assertFalse(list.isRemoved)

        val onRemoved = async { list.events.onRemoved.first() }
        wait { list.subscriptionState == SubscriptionState.Established }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.remove(uniqueName)
        }

        onRemoved.await()
        assertTrue(list.isRemoved)
    }

    @Test
    fun removeListRemote_SubscribeAfterRemove() = runTest {
        val uniqueName = generateRandomString("list")
        val list = syncClient.lists.create(uniqueName)
        assertFalse(list.isRemoved)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.remove(uniqueName)
        }

        list.events.onRemoved.first()
        assertTrue(list.isRemoved)
    }

    @Test
    fun listSubscriptionState() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)
        assertEquals(SubscriptionState.Unsubscribed, list.subscriptionState)
        assertTrue(list.isFromCache)

        val listener1 = launch { list.events.onRemoved.collect() }
        val listener2 = launch { list.events.onRemoved.collect() }

        list.events.onSubscriptionStateChanged.first { it == SubscriptionState.Pending }
        list.events.onSubscriptionStateChanged.first { it == SubscriptionState.Subscribing }
        list.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, list.subscriptionState)
        assertFalse(list.isFromCache)

        wait { listener1.isActive && listener2.isActive }

        listener1.cancel()

        list.events.onSubscriptionStateChanged
            .onEach { assertEquals(SubscriptionState.Established, it) } // No state changes here
            .first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, list.subscriptionState)
        assertFalse(list.isFromCache)

        listener2.cancel()

        // Once all listeners disappeared list should unsubscribe itself
        list.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
        assertEquals(SubscriptionState.Unsubscribed, list.subscriptionState)
        assertTrue(list.isFromCache)
    }

    @Test
    fun addItemDataLocal() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }
        val onAdded = async { list.events.onItemAdded.first { it.data == addedData } }

        // add new item
        val addedItem = list.addItem(addedData)

        assertEquals(0, addedItem.index)
        assertEquals(addedData, addedItem.data)
        onAdded.await()
    }

    @Test
    fun addItemDataRemote() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }
        val onAdded = async { list.events.onItemAdded.first { it.data == addedData } }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.addListItem(list.sid, addedData)
        }

        onAdded.await()
        val addedItem = list.getItem(0)
        assertEquals(addedData, addedItem?.data)
    }

    @Test
    fun addItemDataLocalSubscribeBefore() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }

        var counter = 0
        val eventAddedItem = CompletableDeferred<SyncList.Item>()

        val listener = launch {
            list.events.onItemAdded.collect { item ->
                eventAddedItem.complete(item)
                counter++
            }
        }

        list.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        val addedItem = list.addItem(addedData)

        // wait to be sure that no more than one onItemAdded event received. See the counter check below.
        delay(2.seconds)
        listener.cancel()

        assertEquals(addedData, addedItem.data)
        assertEquals(addedData, eventAddedItem.await().data)
        assertEquals(1, counter)
    }

    @Test
    fun addItemDataLocalSubscribeInParallel() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }

        var counter = 0
        val eventAddedItem = CompletableDeferred<SyncList.Item>()

        val listener = launch {
            list.events.onItemAdded.collect { item ->
                eventAddedItem.complete(item)
                counter++
            }
        }

        // Don't wait until subscription is established

        val addedItem = list.addItem(addedData)

        delay(2.seconds)
        listener.cancel()

        assertEquals(addedData, addedItem.data)
        assertEquals(addedData, eventAddedItem.await().data)
        assertEquals(1, counter)
    }

    @Test
    fun addItemDataLocalSubscribeAfter() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }
        var counter = 0

        val addedItem = list.addItem(addedData)

        val listener = launch {
            list.events.onItemAdded.collect { item ->
                println("!!! onItemAdded: $item")
                counter++
            }
        }

        list.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }
        delay(2000)
        listener.cancel()

        assertEquals(addedData, addedItem.data)
        assertEquals(0, counter) // We missed event, because subscribed after setItem completed.
    }

    @Test
    fun setItemDataLocal() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)
        list.addItem(emptyJsonObject())

        val updatedData = buildJsonObject { put("data", "value1") }
        val onUpdated = async { list.events.onItemUpdated.first { it.index == 0L && it.data == updatedData } }

        // add new item
        val updatedItem = list.setItem(0, updatedData)

        assertEquals(0, updatedItem.index)
        assertEquals(updatedData, updatedItem.data)
        assertEquals(updatedItem, onUpdated.await())
        wait { list.dateUpdated == updatedItem.dateUpdated } // RTDSDK-4302
    }

    @Test
    fun setItemDataRemote() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)
        list.addItem(emptyJsonObject())

        val updatedData = buildJsonObject { put("data", "value1") }
        val onUpdated = async { list.events.onItemUpdated.first { it.data == updatedData } }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.setListItem(list.sid, 0, updatedData)
        }

        val updatedItem = onUpdated.await()

        assertEquals(0, updatedItem.index)
        assertEquals(updatedData, updatedItem.data)
        wait { list.dateUpdated == updatedItem.dateUpdated } // RTDSDK-4302
    }

    @Test
    fun setItemDataNonExistingIndex() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        // set item with not existing index == 10
        val exception = assertFailsWith<TwilioException> { list.setItem(10, emptyJsonObject()) }

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = 404,
            code = 54151,
            message = "Item not found"
        )

        assertEquals(expectedError, exception.errorInfo)
    }

    @Test
    fun setItemDataRemoteStress() = runTest(timeout = 2.minutes) {
        val N = 200

        val config = SyncConfig(
            syncClientConfig = SyncClientConfig(commandTimeout = 60.seconds)
        )
        val sender = TestSyncClient(config = config, useLastUserAccount = false) { requestToken("otherUser") }

        val senderList = sender.lists.create(ttl = 1.hours)
        val receiverList = syncClient.lists.openExisting(senderList.sid)

        val pendingAddedEvent = mutableMapOf<Long, JsonObject>()
        val pendingUpdatedEvents = mutableMapOf<Long, JsonObject>()

        List(N) { counter ->
            launch {
                val data = buildJsonObject { put("data1", "value$counter") }
                val item = senderList.addItem(data)
                pendingAddedEvent[item.index] = item.data
            }
        }.joinAll()

        val onItemAddedListener = launch {
            receiverList.events.onItemAdded.collect { item ->
                val addedData = pendingAddedEvent.remove(item.index)
                if (addedData == item.data) {
                    return@collect
                }

                // check if we've just received updatedData instead of addedData in the onItemAddedListener?
                // This is normal case, which happens when we receive misordered events, i.e.
                // map_item_updated come before map_item_added.
                val updatedData = pendingUpdatedEvents.remove(item.index)
                assertEquals(updatedData, item.data)
            }
        }

        val onItemUpdatedListener = launch {
            receiverList.events.onItemUpdated.collect { item ->
                val expectedData = pendingUpdatedEvents.remove(item.index)
                assertEquals(expectedData, item.data)
            }
        }

        List(N) { counter ->
            val data = buildJsonObject { put("data2", "value$counter") }
            pendingUpdatedEvents[counter.toLong()] = data
            launch { senderList.setItem(counter.toLong(), data) }
        }.joinAll()

        runCatching {
            wait(timeout = 60.seconds) { pendingUpdatedEvents.isEmpty() }
        }

        println("Still no added events: ${pendingAddedEvent.size}")
        pendingAddedEvent.forEach { println(it) }

        println("Still no updated events: ${pendingUpdatedEvents.size}")
        pendingUpdatedEvents.forEach { println(it.key) }

        assertTrue(pendingUpdatedEvents.isEmpty())
        assertTrue(pendingAddedEvent.isEmpty())

        repeat(N) { counter ->
            val updatedData = buildJsonObject { put("data2", "value$counter") }
            assertEquals(updatedData, receiverList.getItem(counter.toLong())?.data)
        }

        // Now all items added and updated. Start removing items
        // To have more fun we unsubscribe here and then re-subscribe in parallel with removing items.
        // So part of removed events come during process of establishing subscription

        onItemAddedListener.cancel()
        onItemUpdatedListener.cancel()
        wait { receiverList.subscriptionState == SubscriptionState.Unsubscribed }

        val pendingRemovedEvents = mutableMapOf<Long, JsonObject>()

        val onItemRemovedListener = launch {
            receiverList.events.onItemRemoved.collect { item -> // start re-subscribing here
                val expectedData = pendingRemovedEvents.remove(item.index)
                println("onItemRemovedListener [${item.index}]:\nexpectedData: $expectedData\nactualData: ${item.data}")
                assertEquals(expectedData, item.data)
            }
        }

        List(N) { counter ->
            val data = buildJsonObject { put("data2", "value$counter") }
            pendingRemovedEvents[counter.toLong()] = data
            launch { senderList.removeItem(counter.toLong()) }
        }.joinAll()

        runCatching {
            wait(timeout = 60.seconds) { pendingRemovedEvents.isEmpty() }
        }

        onItemRemovedListener.cancel()

        println("Still no removed events: ${pendingRemovedEvents.size}")
        pendingRemovedEvents.forEach { println(it.key) }

        assertTrue(pendingRemovedEvents.isEmpty())

        repeat(N) { counter ->
            assertNull(receiverList.getItem(counter.toLong()))
        }

        sender.shutdown()
    }

    @Test
    fun getItemData() = runTest {
        val data = buildJsonObject { put("data", "value") }

        val list = syncClient.lists.create(ttl = 1.hours)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.addListItem(list.sid, data)
        }

        val item = list.getItem(0)

        assertNotNull(item)
        assertEquals(0, item.index)
        assertEquals(data, item.data)
        assertNull(item.dateExpires)
    }

    @Test
    fun getNonExistingItemData() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val item = list.getItem(999) // 999 is an index that does not exist

        assertNull(item)
    }

    @Test
    fun getRemovedItemData() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        list.addItem(TestData(1))
        list.removeItem(0)

        assertNull(list.getItem(0))
    }

    @Test
    fun removeItemLocal() = runTest {
        val data = buildJsonObject { put("data", "value") }

        val list = syncClient.lists.create(ttl = 1.hours)
        list.addItem(data)

        val itemBeforeRemove = list.getItem(0)
        assertNotNull(itemBeforeRemove)

        list.removeItem(0)

        val itemAfterRemove = list.getItem(0)
        assertNull(itemAfterRemove)
    }

    @Test
    fun removeItemRemote() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        list.addItem(emptyJsonObject())
        list.addItem(emptyJsonObject())

        assertNotNull(list.getItem(0))
        assertNotNull(list.getItem(1))

        val onItemRemoved = async { list.events.onItemRemoved.first { it.index == 0L } }

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.removeListItem(list.sid, 0)
        }

        onItemRemoved.await()
        assertNull(list.getItem(0))
        assertNotNull(list.getItem(1))
    }

    @Test
    fun removeNonExistingItem() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val result = runCatching { list.removeItem(999) } // 999 is an index that does not exist

        assertTrue(result.isFailure)

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = 404,
            code = 54151,
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

        val list = client.lists.create(ttl = 1.hours)

        @Serializable
        data class Counter(val value: Long = 0) {
            operator fun plus(x: Long) = Counter(value + x)
        }

        // Init counter with 0, because we cannot mutate non-existing item for now
        list.addItem(Counter(0))

        List(10) {
            async {
                list.mutateItem<Counter>(0) { counter -> counter + 1 }
            }
        }.awaitAll()

        assertEquals(Counter(10), list.getItem(0)?.data())
    }

    @Test
    fun mutateData() = runTest {
        val senderList = syncClient.lists.create(ttl = 1.hours)

        val receiver = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }
        val receiverList = receiver.lists.openExisting(senderList.sid)

        senderList.addItem(emptyJsonObject())

        val data = buildJsonObject { put("data", "value1") }

        val onSenderItemUpdated = async { senderList.events.onItemUpdated.first { it.data == data } }
        val onReceiverItemUpdated = async { receiverList.events.onItemUpdated.first { it.data == data } }

        senderList.mutateItem(itemIndex = 0) { data }

        assertEquals(data, senderList.getItem(itemIndex = 0)?.data)
        awaitAll(onSenderItemUpdated, onReceiverItemUpdated)
    }

    @Test
    fun mutateCachedButRemovedFromBackendItem() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)
        list.addItem(TestData(1))

        // Now remove item from backend, but our list is not subscribed, so it still has this item in cache.
        // In this case mutateItem() method should try to mutate item first and fail with http 404 error.
        //
        // In case of list we cannot add removed item back, because items in list are always added in the end.
        // So the 404 error will be thrown outside of the method.
        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists.removeListItem(list.sid, 0)
        }

        val result = runCatching {
            list.mutateItem<TestData>(0) { TestData(2) }
        }

        val expectedError = ErrorInfo(CommandPermanentError, 404, kListItemNotFound, "Item not found")
        val actualError = result.twilioException.errorInfo

        assertEquals(expectedError, actualError)
    }

    @Test
    fun mutateRemovedItem() = runTest { // RTDSDK-4301
        val list = syncClient.lists.create(ttl = 1.hours)

        list.addItem(TestData(1))
        list.removeItem(0)

        val result = runCatching {
            list.mutateItem<TestData>(0) { TestData(2) }
        }

        val expectedError = ErrorInfo(CommandPermanentError, 404, kListItemNotFound, "Item not found")
        val actualError = result.twilioException.errorInfo

        assertEquals(expectedError, actualError)
    }

    @Test
    fun mutateItemAbort() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val addedData = buildJsonObject { put("data", "value1") }
        list.addItem(addedData)

        val result = runCatching {
            list.mutateItem(0) { null }
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
        val expectedList = List(10) { TestData(it) }

        val list = syncClient.lists.create(ttl = 1.hours)
        expectedList.forEach { list.addItem(it) }

        val actualList1 = buildList<TestData> {
            val iterator = list.queryItems()

            while (iterator.hasNext()) {
                val item = iterator.next()
                add(item.data())
            }

            iterator.close()
        }

        assertEquals(expectedList, actualList1)

        val actualList2 = buildList<TestData> {
            list.queryItems().forEach { item ->
                add(item.data())
            }
        }

        assertEquals(expectedList, actualList2)

        val actualList3 = list.queryItems()
            .asFlow()
            .toList()
            .map { it.data<TestData>() }

        assertEquals(expectedList, actualList3)

        val actualList4 = buildList<TestData> {
            for (item in list) {
                add(item.data())
            }
        }

        assertEquals(expectedList, actualList4)

        val actualList5 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.lists
                .queryItems(list.sid)
                .asFlow()
                .toList()
                .map { it.data<TestData>() }
        }

        assertEquals(expectedList, actualList5)
    }

    @Test
    fun queryItemsCheckStartValue() = runTest { // RTDSDK-4303
        val list = syncClient.lists.create(ttl = 1.hours)

        val items = List(5) { TestData(it) }
        items.forEach { list.addItem(it) }

        val iterator = list.queryItems(startIndex = 0, includeStartIndex = true, pageSize = 2)

        iterator.hasNext() // removes an element for the subsequent invocation of next.

        val actualList = buildList<TestData> {
            while (iterator.hasNext()) {
                add(iterator.next().data())
            }
        }

        val expectedList = items.takeLast(4) // first element with index 0 has been removed by extra hasNext() call.
        assertEquals(expectedList, actualList)
    }

    @Test
    fun queryItemsCancel() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours).apply {
            addItem(TestData(1))
            addItem(TestData(2))
        }

        val iterator = list.queryItems()

        assertTrue(iterator.hasNext())
        assertEquals(TestData(1), iterator.next().data())
        iterator.close()

        val result = runCatching { iterator.hasNext() }
        assertTrue(result.isFailure)
    }

    @Test
    fun queryItemsAfterRemove() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        repeat(10) { index ->
            list.addItem(TestData(index))
        }

        list.queryItems().asFlow().toList() // now all items in cache

        repeat(10) { index ->
            list.removeItem(index.toLong())
        }

        val items = list.queryItems().asFlow().toList()
        assertTrue(items.isEmpty())
    }

    @Test
    fun queryItemsWhenHavePreCached() = runTest {
        val config = SyncConfig(
            // pageSize = 1 in order to not query more items then necessary
            syncClientConfig = SyncClientConfig(pageSize = 1)
        )
        val client = TestSyncClient(config = config, useLastUserAccount = false) { requestToken("otherIdentity") }

        val list = client.lists.create(ttl = 1.hours).apply {
            addItem(TestData(1))
            addItem(TestData(2))
            addItem(TestData(3))
        }

        var expectedIndexes = listOf<Long>(0, 1, 2)
        var actualIndexes = list.queryItems().asFlow().toList().map { it.index } // get all items from backend and cache them

        assertEquals(expectedIndexes, actualIndexes)

        // add items from other client:
        // 2 items between key1 and key2 and one item between key2 and key3
        with(syncClient) {
            lists.addListItem(list.sid, TestData(11))
            lists.addListItem(list.sid, TestData(12))
            lists.addListItem(list.sid, TestData(21))
        }

        // now syncClient still doesn't aware that items were added and returns what it has in cache
        actualIndexes = list.queryItems().asFlow().toList().map { it.index }

        assertEquals(expectedIndexes, actualIndexes)

        list.getItem(3) // get item with TestData(11) and put it into cache

        // now syncClient aware that items were added after index == 2. So it re-requests them from backend.
        expectedIndexes = listOf(0, 1, 2, 3, 4, 5)
        actualIndexes = list.queryItems(pageSize = 1).asFlow().toList().map { it.index }

        assertEquals(expectedIndexes, actualIndexes)

        client.shutdown()
    }

    @Test
    fun queryItemsEmpty() = checkQueryItems(itemsCount = 0)

    @Test
    fun queryItemsEmptyNoCache() = checkQueryItems(itemsCount = 0, useCache = false)

    @Test
    fun queryItemsTwoPagesAsc() = checkQueryItems(queryOrder = QueryOrder.Ascending)

    @Test
    fun queryItemsTwoPagesAscNoCache() = checkQueryItems(queryOrder = QueryOrder.Ascending, useCache = false)

    @Test
    fun queryItemsTwoPagesDesc() = checkQueryItems(queryOrder = QueryOrder.Descending)

    @Test
    fun queryItemsTwoPagesDescNoCache() = checkQueryItems(queryOrder = QueryOrder.Descending, useCache = false)

    @Test
    fun queryItemsThreePagesAsc() = checkQueryItems(itemsCount = 10, pageSize = 4)

    @Test
    fun queryItemsThreePagesAscNoCache() = checkQueryItems(itemsCount = 10, pageSize = 4, useCache = false)

    @Test
    fun queryItemsStartKeyInclusiveAsc() =
        checkQueryItems(startIndex = 1, includeStartIndex = true, queryOrder = QueryOrder.Ascending)

    @Test
    fun queryItemsStartKeyInclusiveAscNoCache() =
        checkQueryItems(startIndex = 2, includeStartIndex = true, queryOrder = QueryOrder.Ascending, useCache = false)

    @Test
    fun queryItemsStartKeyNotInclusiveAsc() =
        checkQueryItems(startIndex = 3, includeStartIndex = false, queryOrder = QueryOrder.Ascending)

    @Test
    fun queryItemsStartKeyNotInclusiveAscNoCache() =
        checkQueryItems(startIndex = 4, includeStartIndex = false, queryOrder = QueryOrder.Ascending, useCache = false)

    @Test
    fun queryItemsStartKeyInclusiveDesc() =
        checkQueryItems(startIndex = 5, includeStartIndex = true, queryOrder = QueryOrder.Descending)

    @Test
    fun queryItemsStartKeyInclusiveDescNoCache() =
        checkQueryItems(startIndex = 6, includeStartIndex = true, queryOrder = QueryOrder.Descending, useCache = false)

    @Test
    fun queryItemsStartKeyNotInclusiveDesc() =
        checkQueryItems(startIndex = 7, includeStartIndex = false, queryOrder = QueryOrder.Descending)

    @Test
    fun queryItemsStartKeyNotInclusiveDescNoCache() =
        checkQueryItems(startIndex = 8, includeStartIndex = false, queryOrder = QueryOrder.Descending, useCache = false)

    @Test
    fun queryItemsStartKeyNotExistsAsc() =
        checkQueryItems(startIndex = 100500, queryOrder = QueryOrder.Ascending)

    @Test
    fun queryItemsStartKeyNotExistsDesc() =
        checkQueryItems(startIndex = 100500, queryOrder = QueryOrder.Descending)

    private fun checkQueryItems(
        itemsCount: Int = 10,
        startIndex: Long? = null,
        includeStartIndex: Boolean = true,
        queryOrder: QueryOrder = QueryOrder.Ascending,
        pageSize: Int = 5,
        useCache: Boolean = true
    ) = runTest {
        val listData = List(itemsCount) { TestData(it) }

        val list = syncClient.lists.create(ttl = 1.hours)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            listData.forEach { value ->
                client.lists.addListItem(list.sid, value)
            }
        }

        // convert to map in order to not shift indexes when filtering items
        val expectedMap = listData.associateBy { it.value.toLong() }
            .filterNot { (key, _) -> !includeStartIndex && key == startIndex }
            .filter { (key, _) ->
                when {
                    startIndex == null -> true
                    queryOrder == QueryOrder.Ascending -> key >= startIndex
                    queryOrder == QueryOrder.Descending -> key <= startIndex
                    else -> error("Never happens")
                }
            }

        println("expectedMap size: ${expectedMap.size}")

        // request list from backend
        val actualMap1 = buildMap<Long, TestData> {
            list.queryItems(startIndex, includeStartIndex, queryOrder, pageSize, useCache).forEach { item ->
                put(item.index, item.data())
            }
        }

        assertEquals(expectedMap, actualMap1)

        // get list from cache (when useCache = true)
        val actualMap2 = buildMap<Long, TestData> {
            list.queryItems(startIndex, includeStartIndex, queryOrder, pageSize, useCache).forEach { item ->
                put(item.index, item.data())
            }
        }

        assertEquals(expectedMap, actualMap2)
    }
}
