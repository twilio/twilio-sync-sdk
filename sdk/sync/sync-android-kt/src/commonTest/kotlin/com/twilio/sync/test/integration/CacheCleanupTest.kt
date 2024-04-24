//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCache
import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.cache.persistent.DriverFactory
import com.twilio.sync.cache.persistent.databaseName
import com.twilio.sync.cache.persistent.getDatabaseList
import com.twilio.sync.cache.persistent.getDatabaseSize
import com.twilio.sync.test.util.testCollectionItem
import com.twilio.sync.test.util.testCollectionMetadata
import com.twilio.sync.test.util.testDocumentMetadata
import com.twilio.sync.test.util.testStreamMetadata
import com.twilio.sync.utils.CacheConfig
import com.twilio.sync.utils.CollectionType.List
import com.twilio.sync.utils.CollectionType.Map
import com.twilio.sync.utils.createAccountDescriptor
import com.twilio.test.util.IgnoreIos
import com.twilio.test.util.runTest
import com.twilio.test.util.setDatabaseLastModified
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.util.AccountDescriptor
import com.twilio.util.InternalTwilioApi
import com.twilio.util.logger
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime

@OptIn(InternalTwilioApi::class)
class CacheCleanupTest {

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()
        SyncCacheCleaner.clearAllCaches()
    }

    @Test
    fun noCacheCleanupWhenPersistentCacheIsDisabled() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = false,
        )

        createTestCache("user1", size = 1024)
        createTestCache("user2", size = 1024)
        createTestCache("user3", size = 1024)

        val expectedTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        logger.d("expectedTotalSize: $expectedTotalSize")

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    fun noCacheCleanupForOtherUsers() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = true,
            cleanupPersistentCacheForOtherUsers = false,
        )

        createTestCache("user1", size = 1024)
        createTestCache("user2", size = 1024)
        createTestCache("user3", size = 1024)

        val expectedTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        logger.d("expectedTotalSize: $expectedTotalSize")

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    fun cacheCleanupForOtherUsers() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = true,
            cleanupPersistentCacheForOtherUsers = true,
        )

        createTestCache("user1", size = 1024)
        createTestCache("user2", size = 1024)
        createTestCache("user3", size = 1024)

        val expectedTotalSize = getDatabaseList()
            .filter { it.contains("user1") }
            .sumOf { getDatabaseSize(it) }

        logger.d("expectedTotalSize: $expectedTotalSize")

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    fun noCacheCleanupForActiveUsers() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = true,
            cleanupPersistentCacheForOtherUsers = true,
        )

        createTestCache("user1", size = 1024)
        createTestCache("user2", size = 1024)
        createTestCache("user3", size = 1024)

        val accountDescriptor = testAccountDescriptor("user2")
        val driver = DriverFactory().createDriver(accountDescriptor, isInMemoryDatabase = false)
        val user2Cache = SyncCache(accountDescriptor.databaseName, driver)

        val expectedTotalSize = getDatabaseList()
            .filter { it.contains("user1") || it.contains("user2") } // user2 cache is not cleared because it is in use
            .sumOf { getDatabaseSize(it) }

        logger.d("expectedTotalSize: $expectedTotalSize")

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)
        user2Cache.close()

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    fun expiredCachesCleanup() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = true,
            cleanupPersistentCacheForOtherUsers = false,
            cleanupUnusedCachesAfter = 2.days
        )

        createTestCache("user1", size = 1024)
        createTestCache("user2", size = 1024)
        createTestCache("user3", size = 1024)

        // set user2 cache lastModified to 3 days ago
        val lastModified = Clock.System.now().minus(3.days)
        setDatabaseLastModified(testAccountDescriptor("user2").databaseName, lastModified)

        val expectedTotalSize = getDatabaseList()
            .filter { it.contains("user1") || it.contains("user3") } // user3 cache is not expired yet
            .sumOf { getDatabaseSize(it) }

        logger.d("expectedTotalSize: $expectedTotalSize")

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    fun noExpiredCachesCleanupForActiveUser() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = true,
            cleanupPersistentCacheForOtherUsers = false,
            cleanupUnusedCachesAfter = Duration.ZERO
        )

        createTestCache("user1", size = 1024)
        createTestCache("user2", size = 1024)
        createTestCache("user3", size = 1024)

        val accountDescriptor = testAccountDescriptor("user2")
        val driver = DriverFactory().createDriver(accountDescriptor, isInMemoryDatabase = false)
        val user2Cache = SyncCache(accountDescriptor.databaseName, driver)

        val expectedTotalSize = getDatabaseList()
            .filter { it.contains("user1") || it.contains("user2") } // user2 cache is not cleared because it is in use
            .sumOf { getDatabaseSize(it) }

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)
        user2Cache.close()

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    @IgnoreIos // Works locally, but fails on circleci
    fun dropOtherUserCachesToFitSize() = runTest {
        val cacheConfig = CacheConfig(
            usePersistentCache = true,
            cleanupPersistentCacheForOtherUsers = false,
            cleanupUnusedCachesAfter = Duration.INFINITE,
            maxPersistentCacheSize = 1_000_000,
        )

        createTestCache("user1", size = 800_000)
        createTestCache("user2", size = 800_000)
        createTestCache("user3", size = 800_000)

        val expectedTotalSize = getDatabaseList()
            .filter { it.contains("user1") }
            .sumOf { getDatabaseSize(it) }

        logger.d("expectedTotalSize: $expectedTotalSize")

        SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertEquals(expectedTotalSize, actualTotalSize)
    }

    @Test
    @IgnoreIos // Works locally, but fails on circleci
    fun shrinkCurrentUserCacheToFitSize() = runTest {
        checkShrinkCurrentUserCacheToFitSize { identity, size ->
            createTestCache(identity, size)
        }
    }

    @Test
    @IgnoreIos // Works locally, but fails on circleci
    fun shrinkDocumentsTable() = runTest {
        checkShrinkCurrentUserCacheToFitSize(maxSize = 200_000) { identity, size ->
            createTestCache(identity, size, documentsWeight = 1, mapsWeight = 0, listsWeight = 0, streamsWeight = 0)
        }
    }

    @Test
    @IgnoreIos // Works locally, but fails on circleci
    fun shrinkMapsTable() = runTest {
        checkShrinkCurrentUserCacheToFitSize { identity, size ->
            createTestCache(identity, size, documentsWeight = 0, mapsWeight = 1, listsWeight = 0, streamsWeight = 0)
        }
    }

    @Test
    @IgnoreIos // Works locally, but fails on circleci
    fun shrinkListsTable() = runTest {
        checkShrinkCurrentUserCacheToFitSize { identity, size ->
            createTestCache(identity, size, documentsWeight = 0, mapsWeight = 0, listsWeight = 1, streamsWeight = 0)
        }
    }

    @Test
    @IgnoreIos // Works locally, but fails on circleci
    fun shrinkStreamsTable() = runTest {
        checkShrinkCurrentUserCacheToFitSize(maxSize = 200_000) { identity, size ->
            createTestCache(identity, size, documentsWeight = 0, mapsWeight = 0, listsWeight = 0, streamsWeight = 1)
        }
    }

    @Test
    @Ignore // This test is too long. Run it manually when necessary.
    fun shrinkCurrentUserCacheStress() = runTest(timeout = Duration.INFINITE) {
        // This test takes about 15 minutes to complete from which 14:54 is spent in createTestCache
        // and ~6 seconds takes cleanup itself

        checkShrinkCurrentUserCacheToFitSize(maxSize = 100 * 1024 * 1024) { identity, size ->
            createTestCache(identity, size)
        }
    }

    private suspend inline fun checkShrinkCurrentUserCacheToFitSize(
        maxSize: Long = 2_000_000L,
        crossinline createCache: suspend (String, Long) -> Unit,
    ) {
        val cacheConfig = CacheConfig(maxPersistentCacheSize = maxSize)

        createCache("user1", maxSize + 1)

        val currentCacheSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        assertTrue(currentCacheSize > maxSize)

        logger.d("currentCacheSize: $currentCacheSize")

        val time = measureTime {
            SyncCacheCleaner.cleanupOnLogin(testAccountDescriptor("user1"), cacheConfig)
        }

        logger.d("Cleanup time: $time")

        val actualTotalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        logger.d("cleanedCacheSize: $actualTotalSize")

        assertTrue(actualTotalSize <= maxSize / 2)
    }

    private suspend fun createTestCache(
        identity: String,
        size: Long,
        documentsWeight: Int = 1,
        mapsWeight: Int = 1,
        listsWeight: Int = 1,
        streamsWeight: Int = 1,
    ) {
        val account = testAccountDescriptor(identity)
        val driver = DriverFactory().createDriver(account, isInMemoryDatabase = false)
        val syncCache = SyncCache(account.databaseName, driver)

        var currentSize = getDatabaseSize(account.databaseName)
        var counter = 0

        while (currentSize < size) {
            logger.d("createTestCache[$identity]: currentSize: $currentSize, size: $size")

            repeat(documentsWeight) {
                syncCache.put(testDocumentMetadata("docSid${counter++}"))
            }

            repeat(mapsWeight) {
                val mapSid = "mapSid${counter++}"
                syncCache.put(testCollectionMetadata(mapSid, collectionType = Map))

                val items = List(100) { testCollectionItem(mapSid, "key$it") }
                syncCache.put(Map, mapSid, items, updateMetadataLastEventId = false, isCollectionEmpty = false)
            }

            repeat(listsWeight) {
                val listSid = "listSid${counter++}"
                syncCache.put(testCollectionMetadata(listSid, collectionType = List))

                val items = List(100) { testCollectionItem(listSid, index = it.toLong()) }
                syncCache.put(List, listSid, items, updateMetadataLastEventId = false, isCollectionEmpty = false)
            }

            repeat(streamsWeight) {
                syncCache.put(testStreamMetadata("streamSid${counter++}"))
            }

            currentSize = getDatabaseSize(account.databaseName)
        }

        syncCache.close()
        logger.d("createTestCache[$identity] finished: currentSize: $currentSize, size: $size")
    }

    private fun testAccountDescriptor(identity: String) = AccountDescriptor.createAccountDescriptor("AC", "IS", identity)
}
