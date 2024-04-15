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
import com.twilio.sync.operations.CollectionItemMetadataResponse
import com.twilio.sync.operations.CollectionItemsGetOperation
import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.sync.operations.SyncOperationsFactory
import com.twilio.sync.repository.SyncRepository
import com.twilio.sync.repository.toCollectionMetadata
import com.twilio.sync.subscriptions.RemoteEvent
import com.twilio.sync.test.util.setRangeBounds
import com.twilio.sync.test.util.testCollectionItemData
import com.twilio.sync.test.util.testCollectionItemsDataResponse
import com.twilio.sync.test.util.testCollectionMetadataResponse
import com.twilio.sync.utils.CollectionItemId.Key
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.QueryOrder
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.commands.CommandsScheduler
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
* This is test for integration SyncRepository and SyncCache.
* Here we can check that repository performs network requests when expected and only when expected.
*/
class RepositoryCacheTest {

    private lateinit var syncCache: SyncCache

    private lateinit var repository: SyncRepository

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

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@RepositoryCacheTest, relaxUnitFun = true)

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
    }

    @Test
    fun getNonExistingMapItem() = runTest {
        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any(), any(), any(), any()) } returns operation

        coEvery { commandsScheduler.post(operation) } returns testCollectionItemsDataResponse(items = emptyList())

        val item = repository.getCollectionItemData(metadata, Key("key"), useCache = true)

        assertEquals(null, item)
        coVerify(exactly = 1) { commandsScheduler.post(operation) } // network operation performed
    }

    @Test
    fun getRemovedMapItemFromCache() = runTest {
        val itemId = Key("key")

        val cachedItem = testCollectionItemData(mapSid, itemId)

        syncCache.put(cachedItem, updateMetadataLastEventId = false)

        syncCache.deleteCollectionItem(
            mapSid,
            itemId,
            eventId = 1,
            updateMetadataLastEventId = false,
            dateUpdated = Instant.DISTANT_PAST,
        )

        val actualItem = repository.getCollectionItemData(metadata, itemId, useCache = true)

        assertEquals(null, actualItem) // repository returns null without any network interactions
        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions
    }

    @Test
    fun getCollectionItemsDataAllCached() = runTest {
        val expectedItems = MutableList(10) { index ->
            testCollectionItemData(mapSid, Key("key$index"))
        }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            expectedItems,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = expectedItems.first().itemId,
            endId = expectedItems.last().itemId,
        )

        expectedItems.replaceAll { item ->
            // Bounds expected to be rewritten by the SyncCache.put() call
            item.copy(
                isLeftBound = item.itemId == expectedItems.first().itemId,
                isRightBound = item.itemId == expectedItems.last().itemId,
            )
        }

        val actualItemsAll = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        assertEquals(expectedItems, actualItemsAll)

        val actualItemsLast2 = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = expectedItems[expectedItems.lastIndex - 1].itemId,
            includeStartId = true,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        assertEquals(expectedItems.takeLast(2), actualItemsLast2)

        val actualItemsLast1 = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = expectedItems[expectedItems.lastIndex - 1].itemId,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        assertEquals(expectedItems.takeLast(1), actualItemsLast1)

        var item = expectedItems.removeAt(0)

        syncCache.deleteCollectionItem(
            mapSid,
            item.itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false,
        )

        item = expectedItems.removeAt(0)

        syncCache.deleteCollectionItem(
            mapSid,
            item.itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false,
        )

        item = expectedItems.removeAt(expectedItems.lastIndex)

        syncCache.deleteCollectionItem(
            mapSid,
            item.itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false
        )

        val actualItemsAfterRemove = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        assertEquals(expectedItems, actualItemsAfterRemove)

        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions
    }

    @Test
    fun getCollectionItemsDataTwoConnectedRanges() = runTest {
        val firstRange = List(3) { index ->
            val key = "key%03d".format(index)
            testCollectionItemData(mapSid, Key(key))
        }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            firstRange,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = firstRange.first().itemId,
            endId = null,
        )

        val secondRange = List(3) { index ->
            val key = "key%03d".format(firstRange.size + index)
            testCollectionItemData(mapSid, Key(key))
        }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            secondRange,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = null,
            endId = secondRange.last().itemId,
        )

        val actualFirstRange = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        )
            .consumeAsFlow()
            .take(firstRange.size)
            .toList()

        val expectedItemsFirstRange = firstRange.setRangeBounds()

        assertEquals(expectedItemsFirstRange, actualFirstRange)
        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions

        val actualSecondRange = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = secondRange.first().itemId,
            includeStartId = true,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        )
            .consumeAsFlow()
            .take(secondRange.size)
            .toList()

        val expectedItemsSecondRange = secondRange.setRangeBounds()

        assertEquals(expectedItemsSecondRange, actualSecondRange)
        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions

        val operation = mockk<CollectionItemsGetOperation>()
        every {
            operationsFactory.collectionItemsGetOperation(
                any(), any(), startId = firstRange.last().itemId, any(), any(), inclusive = true)
        } returns operation

        val response = testCollectionItemsDataResponse(
            items = listOf(firstRange.last()) + secondRange, // ranges are connected
            meta = CollectionItemMetadataResponse(
                prevToken = "prevToken",
                nextToken = null,
            )
        )

        coEvery { commandsScheduler.post(operation) } returns response

        val actualItems = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        val expectedItems1 = (firstRange + secondRange).map { item ->
            // Bounds expected to be rewritten by the SyncCache
            item.copy(
                isLeftBound = item.itemId == firstRange.first().itemId,

                // firstRange.last() is returned from cache with isRightBound === true
                // then request to backend runs
                isRightBound = item.itemId == firstRange.last().itemId || item.itemId == secondRange.last().itemId,
            )
        }

        assertEquals(expectedItems1, actualItems)

        // network request to query items in between firstRange and secondRange (if any)
        coVerify(exactly = 1) { commandsScheduler.post(operation) }

        val actualItemsFromCache = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        // now ranges are connected in cache and all items returned from cache
        // so only secondRange.last() has the rightBound flag set.
        val expectedItems2 = (firstRange + secondRange).setRangeBounds()

        assertEquals(expectedItems2, actualItemsFromCache)

        // second time items returned from cache. So no more network interactions
        confirmVerified(commandsScheduler)
    }

    @Test
    fun getCollectionItemsDataTwoDisconnectedRanges() = runTest {
        val firstRange = List(3) { index ->
            val key = "key%03d".format(index)
            testCollectionItemData(mapSid, Key(key))
        }

        val secondRange = List(3) { index ->
            val key = "key%03d".format(firstRange.size + index)
            testCollectionItemData(mapSid, Key(key))
        }

        val thirdRange = List(3) { index ->
            val key = "key%03d".format(firstRange.size + secondRange.size + index)
            testCollectionItemData(mapSid, Key(key))
        }

        // put two disconnected ranges into cache: firstRange and thirdRange

        syncCache.put(
            CollectionType.Map,
            mapSid,
            firstRange,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = firstRange.first().itemId,
            endId = null
        )

        syncCache.put(
            CollectionType.Map,
            mapSid,
            thirdRange,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = null,
            endId = thirdRange.last().itemId
        )

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any(), any(), any()) } returns operation

        val response = testCollectionItemsDataResponse(
            items = listOf(firstRange.last()) + secondRange + thirdRange, // ranges are connected
            meta = CollectionItemMetadataResponse(
                prevToken = "prevToken",
                nextToken = null,
            )
        )
        coEvery { commandsScheduler.post(operation) } returns response

        val actualItems = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        val expectedItems1 = (firstRange + secondRange + thirdRange).map { item ->
            // Bounds expected to be rewritten by the SyncCache
            item.copy(
                isLeftBound = item.itemId == firstRange.first().itemId,

                // firstRange.last() is returned from cache with isRightBound === true
                // then request to backend runs
                isRightBound = item.itemId == firstRange.last().itemId || item.itemId == thirdRange.last().itemId,
            )
        }

        assertEquals(expectedItems1, actualItems)

        // network request to query items in between firstRange and secondRange (if any)
        coVerify(exactly = 1) {
            operationsFactory.collectionItemsGetOperation(
                any(), any(), startId = firstRange.last().itemId, any(), any(), any())
        }
        coVerify(exactly = 1) { commandsScheduler.post(operation) }

        val actualItemsFromCache = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        // now ranges are connected in cache and all items returned from cache
        // so only thirdRange.last() has the rightBound flag set.
        val expectedItems2 = (firstRange + secondRange + thirdRange).setRangeBounds()

        assertEquals(expectedItems2, actualItemsFromCache)

        // second time items returned from cache. So no network interactions
        confirmVerified(commandsScheduler)
    }


    @Test
    fun putNewBeginEndItems() = runTest {
        val items = List(3) { index ->
            val key = "key%03d".format(index)
            testCollectionItemData(mapSid, Key(key))
        }

        // all items are in cache
        syncCache.put(
            CollectionType.Map,
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items.first().itemId,
            endId = items.last().itemId,
        )

        // begin/end keys in metadata has been updated in the syncCache.put() call
        var metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items.first().itemId, metadata.beginId)
        assertEquals(items.last().itemId, metadata.endId)

        val newBeginId = Key("key")
        val newEndId = Key("key999")

        // just to be sure
        assertTrue(newBeginId.key < items.first().itemId.id)
        assertTrue(newEndId.key > items.last().itemId.id)

        val newBeginItem = testCollectionItemData(mapSid, newBeginId)
        val newEndItem = testCollectionItemData(mapSid, newEndId)

        syncCache.put(newBeginItem, updateMetadataLastEventId = false)
        syncCache.put(newEndItem, updateMetadataLastEventId = false)

        // begin/end keys in metadata has been updated in the syncCache.put() call
        metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        // cache understood that old begin/end keys are not valid anymore, but has no info about new begin/end keys
        // so begin/end keys must be null now
        assertNull(metadata.beginId)
        assertNull(metadata.endId)

        val operation = mockk<CollectionItemsGetOperation>()
        every { operationsFactory.collectionItemsGetOperation(any(), any(), any(), any(), any(), any()) } returns operation

        val response = testCollectionItemsDataResponse(
            items = listOf(newBeginItem) + items + newEndItem,
            meta = CollectionItemMetadataResponse(
                prevToken = null,
                nextToken = null,
            )
        )
        coEvery { commandsScheduler.post(operation) } returns response

        val actualItems = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        val expectedItems = (listOf(newBeginItem) + items + newEndItem).setRangeBounds()

        assertEquals(expectedItems, actualItems)

        // begin/end keys in metadata has been updated
        metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(newBeginId, metadata.beginId)
        assertEquals(newEndId, metadata.endId)


        // network request to query items in between firstRange and secondRange (if any)
        coVerify(exactly = 1) { commandsScheduler.post(operation) }

        val actualItemsFromCache = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        assertEquals(expectedItems, actualItemsFromCache)

        // second time items returned from cache. So no network interactions
        confirmVerified(commandsScheduler)
    }

    @Test
    fun putNewItemBetweenExisting() = runTest {
        val items = MutableList(3) { index ->
            val key = "key%03d".format(index)
            testCollectionItemData(mapSid, Key(key))
        }

        // all items are in cache
        syncCache.put(
            CollectionType.Map,
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items.first().itemId,
            endId = items.last().itemId,
        )

        val newItemKey = items[0].itemId.id + "1"

        // just to be sure the item is in between item[0] and item[1]
        assertTrue(items[0].itemId.id < newItemKey && newItemKey < items[1].itemId.id)

        val newItem = testCollectionItemData(mapSid, Key(newItemKey))

        syncCache.put(newItem, updateMetadataLastEventId = false)

        items.add(1, newItem)

        // now our range must be split into three separated ranges, because at this moment SyncCache
        // is sure that initial range is not solid anymore, but has no idea if the newItem is only one
        // came in the middle of the range or more items has been added on backend side

        val item0 = syncCache.getCollectionItemData(mapSid, items[0].itemId)!!

        assertTrue(item0.isLeftBound)
        assertTrue(item0.isRightBound)

        val item1 = syncCache.getCollectionItemData(mapSid, items[1].itemId)!!

        assertTrue(item1.isLeftBound)
        assertTrue(item1.isRightBound)

        // item2 and item3 are still in solid range

        val item2 = syncCache.getCollectionItemData(mapSid, items[2].itemId)!!

        assertTrue(item2.isLeftBound)
        assertFalse(item2.isRightBound)

        val item3 = syncCache.getCollectionItemData(mapSid, items[3].itemId)!!

        assertFalse(item3.isLeftBound)
        assertTrue(item3.isRightBound)


        val actualRange1 = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        )
            .consumeAsFlow()
            .take(1)
            .toList()

        val expectedRange1 = listOf(items[0]).setRangeBounds()
        assertEquals(expectedRange1, actualRange1)

        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions

        val actualRange2 = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = Key(newItemKey),
            includeStartId = true,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        )
            .consumeAsFlow()
            .take(1)
            .toList()

        val expectedRange2 = listOf(items[1]).setRangeBounds()
        assertEquals(expectedRange2, actualRange2)

        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions

        val actualRange3 = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = items[2].itemId,
            includeStartId = true,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        )
            .consumeAsFlow()
            .take(2)
            .toList()

        val expectedRange3 = listOf(items[2], items[3]).setRangeBounds()
        assertEquals(expectedRange3, actualRange3)

        coVerify(exactly = 0) { commandsScheduler.post(any()) } // no network interactions

        val operation = mockk<CollectionItemsGetOperation>()
        every {
            operationsFactory.collectionItemsGetOperation(any(), any(), startId = items[0].itemId, any(), any(), any())
        } returns operation

        val response = testCollectionItemsDataResponse(
            items = items,
            meta = CollectionItemMetadataResponse(
                prevToken = null,
                nextToken = null,
            )
        )
        coEvery { commandsScheduler.post(operation) } returns response

        val actualItems = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        val expectedItems = items.map { item ->
            item.copy(
                isLeftBound = item.itemId == items[0].itemId,

                // items[0] is returned from cache with isRightBound === true
                // then request to backend runs
                isRightBound = item.itemId == items[0].itemId || item.itemId == items[3].itemId
            )
        }

        assertEquals(expectedItems, actualItems)

        // network request to query items in between firstRange and secondRange (if any)
        coVerify(exactly = 1) { commandsScheduler.post(operation) }

        val actualItemsFromCache = repository.getCollectionItemsData(
            CollectionType.Map,
            mapSid,
            startId = null,
            includeStartId = false,
            queryOrder = QueryOrder.Ascending,
            pageSize = 100,
            useCache = true
        ).toList()

        assertEquals(items.setRangeBounds(), actualItemsFromCache)

        // second time items returned from cache. So no network interactions
        confirmVerified(commandsScheduler)
    }
}
