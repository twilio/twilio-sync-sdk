//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.CacheResult
import com.twilio.sync.cache.DocumentCacheMetadataPatch
import com.twilio.sync.cache.SyncCache
import com.twilio.sync.cache.persistent.DriverFactory
import com.twilio.sync.cache.persistent.databaseName
import com.twilio.sync.sqldelight.cache.persistent.DocumentCacheMetadata
import com.twilio.sync.sqldelight.cache.persistent.MapItemCacheData
import com.twilio.sync.test.util.replaceAll
import com.twilio.sync.test.util.setRangeBounds
import com.twilio.sync.test.util.testCollectionItem
import com.twilio.sync.test.util.testCollectionMetadata
import com.twilio.sync.test.util.testDocumentMetadata
import com.twilio.sync.test.util.toString
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionItemId.Key
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.createAccountDescriptor
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.util.AccountDescriptor
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheTest {

    private lateinit var syncCache: SyncCache

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()

        val accountDescriptor = AccountDescriptor.createAccountDescriptor("AC", "IS", "user")
        val driver = DriverFactory().createDriver(accountDescriptor, isInMemoryDatabase = true)

        syncCache = SyncCache(accountDescriptor.databaseName, driver)
    }

    @AfterTest
    fun tearDown() {
        syncCache.close()
    }

    @Test
    fun putDocumentMetadata() = runTest {
        val documentSid = "TO000"
        val metadata0 = testDocumentMetadata(sid = documentSid, lastEventId = 0)
        val metadata1 = testDocumentMetadata(sid = documentSid, lastEventId = 1)
        val metadata2 = testDocumentMetadata(sid = documentSid, lastEventId = 2)

        assertEquals(metadata1, syncCache.put(metadata1))
        assertEquals(metadata1, syncCache.getDocumentMetadataBySid(documentSid).first())

        assertEquals(metadata1, syncCache.put(metadata0)) // ignored current revision is newer
        assertEquals(metadata1, syncCache.getDocumentMetadataBySid(documentSid).first())

        assertEquals(metadata2, syncCache.put(metadata2)) // updated to revision 2
        assertEquals(metadata2, syncCache.getDocumentMetadataBySid(documentSid).first())
    }

    @Test
    fun updateDocumentMetadata() = runTest {
        val documentSid = "TO000"
        val metadata0 = testDocumentMetadata(sid = documentSid, lastEventId = 0)
        val metadata1 = testDocumentMetadata(sid = documentSid, lastEventId = 1)
        val metadata2 = testDocumentMetadata(sid = documentSid, lastEventId = 2)

        syncCache.put(metadata1)

        // ignored current revision is newer
        assertEquals(metadata1, syncCache.updateDocumentMetadata(metadata0.toTestMetadataPatch()))
        assertEquals(metadata1, syncCache.getDocumentMetadataBySid(documentSid).first())

        // updated to revision 2
        assertEquals(metadata2, syncCache.updateDocumentMetadata(metadata2.toTestMetadataPatch()))
        assertEquals(metadata2, syncCache.getDocumentMetadataBySid(documentSid).first())
    }

    private fun DocumentCacheMetadata.toTestMetadataPatch() = DocumentCacheMetadataPatch(
        sid = sid,
        uniqueName = uniqueName,
        dateUpdated = dateUpdated,
        dateExpires = dateExpires,
        revision = revision,
        lastEventId = lastEventId,
        data = documentData,
    )

    @Test
    fun mapItemsSingleRange() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = List(10) { testCollectionItem(mapSid, "key$it", lastEventId = it.toLong()) }

        syncCache.put(
            collectionType = CollectionType.Map,
            collectionSid = mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            items.first().itemId,
            items.last().itemId
        )

        val metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()

        assertEquals(items.first().itemId, metadata?.beginId)
        assertEquals(items.last().itemId, metadata?.endId)

        var prevId: CollectionItemId? = null

        items.forEach { item ->
            val expectedItem = item.copy(
                isLeftBound = item == items.first(),
                isRightBound = item == items.last(),
            )

            val actualItem = syncCache.getNextCollectionItemData(CollectionType.Map, mapSid, itemId = prevId)
            assertEquals(expectedItem, actualItem)

            prevId = item.itemId
        }

        assertNull(syncCache.getNextCollectionItemData(CollectionType.Map, mapSid, itemId = prevId))
        prevId = null

        items.reversed().forEach { item ->
            val expectedItem = item.copy(
                isLeftBound = item == items.first(),
                isRightBound = item == items.last(),
            )

            val actualItem = syncCache.getPrevCollectionItemData(CollectionType.Map, mapSid, itemId = prevId)
            assertEquals(expectedItem, actualItem)

            prevId = item.itemId
        }

        assertNull(syncCache.getPrevCollectionItemData(CollectionType.Map, mapSid, itemId = prevId))
    }

    @Test
    fun mapItemsTwoSplitRanges() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = List(10) { testCollectionItem(mapSid, "key$it", lastEventId = it.toLong()) }

        syncCache.put(CollectionType.Map, mapSid, items.take(5), updateMetadataLastEventId = false, isCollectionEmpty = false)
        syncCache.put(CollectionType.Map, mapSid, items.takeLast(5), updateMetadataLastEventId = false, isCollectionEmpty = false)

        val metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertNull(metadata.beginId)
        assertNull(metadata.endId)

        assertNull(syncCache.getNextCollectionItemData(CollectionType.Map, mapSid, itemId = null))
        assertNull(syncCache.getPrevCollectionItemData(CollectionType.Map, mapSid, itemId = null))

        val cachedItems = mutableListOf<CollectionItemData>()

        var item = syncCache.getCollectionItemData(mapSid, itemId = items.first().itemId)

        while (item != null) {
            cachedItems += item
            item = syncCache.getNextCollectionItemData(CollectionType.Map, mapSid, itemId = item.itemId)
        }

        // check first range returned properly
        assertEquals(items.take(5).setRangeBounds(), cachedItems)

        cachedItems.clear()

        item = syncCache.getCollectionItemData(mapSid, itemId = items.last().itemId)

        while (item != null) {
            cachedItems += item
            item = syncCache.getPrevCollectionItemData(CollectionType.Map, mapSid, itemId = item.itemId)
        }

        // check second range returned properly
        assertEquals(items.takeLast(5).setRangeBounds().reversed(), cachedItems)
    }

    @Test
    fun mapItemsNewRangeIntersectsExisting() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = List(10) { testCollectionItem(mapSid, "key$it", lastEventId = it.toLong()) }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            items.take(5),
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items[0].itemId,
            endId = items[4].itemId
        )

        var metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[0].itemId, metadata.beginId)
        assertEquals(items[4].itemId, metadata.endId)

        var item0 = syncCache.getCollectionItemData(mapSid, items[0].itemId)!!
        var item4 = syncCache.getCollectionItemData(mapSid, items[4].itemId)!!

        assertTrue(item0.isLeftBound)
        assertFalse(item0.isRightBound)

        assertFalse(item4.isLeftBound)
        assertTrue(item4.isRightBound)

        syncCache.put(
            CollectionType.Map,
            mapSid,
            items.takeLast(6),
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            endId = items[9].itemId
        )

        metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[0].itemId, metadata.beginId)
        assertEquals(items[9].itemId, metadata.endId)

        item0 = syncCache.getCollectionItemData(mapSid, items[0].itemId)!!
        item4 = syncCache.getCollectionItemData(mapSid, items[4].itemId)!!
        val item9 = syncCache.getCollectionItemData(mapSid, items[9].itemId)!!

        assertTrue(item0.isLeftBound)
        assertFalse(item0.isRightBound)

        assertFalse(item4.isLeftBound)
        assertFalse(item4.isRightBound)

        assertFalse(item9.isLeftBound)
        assertTrue(item9.isRightBound)
    }

    @Test
    fun mapItemsNewRangeIncludesExisting() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = MutableList(10) { testCollectionItem(mapSid, "key$it", lastEventId = 0) }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            items.subList(1, 5),
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items[1].itemId,
            endId = items[4].itemId
        )

        var metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[1].itemId, metadata.beginId)
        assertEquals(items[4].itemId, metadata.endId)

        val item1 = syncCache.getCollectionItemData(mapSid, items[1].itemId)!!
        val item4 = syncCache.getCollectionItemData(mapSid, items[4].itemId)!!

        assertTrue(item1.isLeftBound)
        assertFalse(item1.isRightBound)

        assertFalse(item4.isLeftBound)
        assertTrue(item4.isRightBound)

        // remove item1 just to add some complexity
        syncCache.deleteCollectionItem(
            mapSid,
            items[1].itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = true,
        )

        items.replaceAll { it.copy(lastEventId = 2) }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items[0].itemId,
            endId = items[9].itemId
        )

        metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[0].itemId, metadata.beginId)
        assertEquals(items[9].itemId, metadata.endId)

        assertEquals(items.setRangeBounds(), getRangeFromCache(mapSid))
    }

    @Test
    fun mapItemsNewRangeInsideExisting() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = MutableList(10) { testCollectionItem(mapSid, "key$it", lastEventId = 0) }

        syncCache.put(
            CollectionType.Map, 
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items[0].itemId,
            endId = items[9].itemId
        )

        val metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[0].itemId, metadata.beginId)
        assertEquals(items[9].itemId, metadata.endId)

        assertEquals(items.setRangeBounds(), getRangeFromCache(mapSid))

        // remove item[2] just to add some complexity
        syncCache.deleteCollectionItem(
            mapSid,
            items[2].itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false
        )

        val range = items.subList(1, 6)
        range.replaceAll { it.copy(lastEventId = 2) } // subList returns view. So this updates items in items[] as well

        syncCache.put(
            CollectionType.Map,
            mapSid,
            range,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false
        )

        assertEquals(items.setRangeBounds(), getRangeFromCache(mapSid))
    }

    @Test
    fun mapItemsNewRangeInsideExistingOnTopOfRemovedBound() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = MutableList(10) { testCollectionItem(mapSid, "key$it", lastEventId = 0) }

        syncCache.put(
            CollectionType.Map,
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items[0].itemId,
            endId = items[9].itemId
        )

        val metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[0].itemId, metadata.beginId)
        assertEquals(items[9].itemId, metadata.endId)

        assertEquals(items.setRangeBounds(), getRangeFromCache(mapSid))

        // remove item[1]
        syncCache.deleteCollectionItem(
            mapSid,
            items[1].itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = true,
        )

        // now put range which includes item1 again
        val range = items.subList(1, 6)
        range.replaceAll { it.copy(lastEventId = 2) } // subList returns view. So this updates items in items[] as well
        syncCache.put(CollectionType.Map, mapSid, range, updateMetadataLastEventId = false, isCollectionEmpty = false)

        // as soon as new range contains previously removed item - cache now is not sure if only one item o more
        // has been added instead of removed. So it should split items into two ranges.

        val actualRange1 = getRangeFromCache(mapSid, startKey = null)
        val actualRange2 = getRangeFromCache(mapSid, startKey = items[1].itemId)

        assertEquals(items.take(1).setRangeBounds(), actualRange1)
        assertEquals(items.takeLast(9).setRangeBounds(), actualRange2)
    }

    @Test
    fun mapItemsPutNewBeginAndEndOnTopOfRemoved() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = MutableList(4) { index ->
            testCollectionItem(
                mapSid,
                key = "key" + index.toString(width = 3),
                lastEventId = 0
            )
        }

        // all items are in cache
        syncCache.put(
            CollectionType.Map,
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items.first().itemId,
            endId = items.last().itemId
        )

        // begin/end keys in metadata has been updated in the syncCache.put() call
        val metadata = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        assertEquals(items[0].itemId, metadata.beginId)
        assertEquals(items[3].itemId, metadata.endId)

        // remove item inside range
        syncCache.deleteCollectionItem(
            mapSid,
            items[0].itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false
        )

        syncCache.deleteCollectionItem(
            mapSid,
            items[3].itemId,
            eventId = 2,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false
        )

        // put removed item back
        items[0] = items[0].copy(lastEventId = 3)
        items[3] = items[3].copy(lastEventId = 4)

        syncCache.put(items[0], updateMetadataLastEventId = false)
        syncCache.put(items[3], updateMetadataLastEventId = false)

        // begin/end keys in metadata has been updated in the syncCache.put() call
        val metadata2 = syncCache.getCollectionMetadataBySid(CollectionType.Map, mapSid).first()!!

        // begin and end items has been removed then added again.
        // now SyncCache has no idea if the actually begin and end items or more items has been added on backend.
        // So now begin and end keys must be null
        assertNull(metadata2.beginId)
        assertNull(metadata2.endId)

        // now our range must be split into three separated ranges

        val item0 = syncCache.getCollectionItemData(mapSid, items[0].itemId)!!

        assertTrue(item0.isLeftBound)
        assertTrue(item0.isRightBound)

        // item1 and item2 are still in solid range

        val item1 = syncCache.getCollectionItemData(mapSid, items[1].itemId)!!

        assertTrue(item1.isLeftBound)
        assertFalse(item1.isRightBound)

        val item2 = syncCache.getCollectionItemData(mapSid, items[2].itemId)!!

        assertFalse(item2.isLeftBound)
        assertTrue(item2.isRightBound)

        val item3 = syncCache.getCollectionItemData(mapSid, items[3].itemId)!!

        assertTrue(item3.isLeftBound)
        assertTrue(item3.isRightBound)
    }

    @Test
    fun mapItemsPutNewItemBetweenExistingOnTopOfRemoved() = runTest {
        val mapSid = "mapSid"
        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        val items = MutableList(4) { index ->
            testCollectionItem(
                mapSid,
                key = "key" + index.toString(width = 3),
                lastEventId = 0
            )
        }

        // all items are in cache
        syncCache.put(
            CollectionType.Map,
            mapSid,
            items,
            updateMetadataLastEventId = false,
            isCollectionEmpty = false,
            beginId = items.first().itemId,
            endId = items.last().itemId
        )

        // remove item inside range
        syncCache.deleteCollectionItem(
            mapSid,
            items[1].itemId,
            eventId = 1,
            dateUpdated = Instant.DISTANT_PAST,
            updateMetadataLastEventId = false,
        )

        // put removed item back
        items[1] = items[1].copy(lastEventId = 2)
        syncCache.put(items[1], updateMetadataLastEventId = false)

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
    }

    private suspend fun getRangeFromCache(
        mapSid: String,
        startKey: CollectionItemId? = null
    ): MutableList<CollectionItemData> {
        val allItems = mutableListOf<CollectionItemData>()

        var item = if (startKey != null)
            syncCache.getCollectionItemData(mapSid, startKey)
        else
            syncCache.getNextCollectionItemData(CollectionType.Map, mapSid, null) // returns begin item if known

        while (item != null) {
            allItems += item
            item = syncCache.getNextCollectionItemData(CollectionType.Map, mapSid, item.itemId)
        }

        return allItems
    }

    @Test
    fun removeMapItem() = runTest {
        val mapSid = "mapSid"
        val itemId = Key("key")

        // Cache is empty now. So removing non-existing map item logically doesn't modify it.
        assertIs<CacheResult.NotModified<MapItemCacheData>>(
            syncCache.deleteCollectionItem(mapSid, itemId, eventId = 0, Instant.DISTANT_PAST, updateMetadataLastEventId = false)
        )

        // Do the same second time just in case :)
        assertIs<CacheResult.NotModified<MapItemCacheData>>(
            syncCache.deleteCollectionItem(mapSid, itemId, eventId = 0, Instant.DISTANT_PAST, updateMetadataLastEventId = false)
        )

        syncCache.put(testCollectionMetadata(mapSid, lastEventId = 0))

        // add an item into cache
        assertIs<CacheResult.Added<MapItemCacheData>>(
            syncCache.put(testCollectionItem(mapSid, itemId.key, lastEventId = 1), updateMetadataLastEventId = false)
        )

        // remove the item
        assertIs<CacheResult.Removed<MapItemCacheData>>(
            syncCache.deleteCollectionItem(
                mapSid,
                itemId,
                eventId = 2,
                Instant.DISTANT_PAST,
                updateMetadataLastEventId = false
            )
        )

        // remove the same item again with same eventId (duplicating event)
        assertIs<CacheResult.NotModified<MapItemCacheData>>(
            syncCache.deleteCollectionItem(
                mapSid,
                itemId,
                eventId = 2,
                Instant.DISTANT_PAST,
                updateMetadataLastEventId = false
            )
        )
    }
}
