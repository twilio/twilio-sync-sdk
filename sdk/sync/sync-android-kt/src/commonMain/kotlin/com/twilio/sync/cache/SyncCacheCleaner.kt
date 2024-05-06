//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache

import com.twilio.sync.cache.SyncCacheCleaner.EntityType.Document
import com.twilio.sync.cache.SyncCacheCleaner.EntityType.List
import com.twilio.sync.cache.SyncCacheCleaner.EntityType.Map
import com.twilio.sync.cache.SyncCacheCleaner.EntityType.Stream
import com.twilio.sync.cache.persistent.DatabaseRegistry
import com.twilio.sync.cache.persistent.DriverFactory
import com.twilio.sync.cache.persistent.databaseName
import com.twilio.sync.cache.persistent.deleteSyncDatabase
import com.twilio.sync.cache.persistent.fromDatabaseName
import com.twilio.sync.cache.persistent.getDatabaseLastModified
import com.twilio.sync.cache.persistent.getDatabaseList
import com.twilio.sync.cache.persistent.getDatabaseSize
import com.twilio.sync.client.AccountStorage
import com.twilio.sync.utils.CacheConfig
import com.twilio.sync.utils.CollectionType
import com.twilio.util.AccountDescriptor
import com.twilio.util.InternalTwilioApi
import com.twilio.util.logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@InternalTwilioApi
object SyncCacheCleaner {

    fun clearCacheForAccount(account: AccountDescriptor) {
        if (DatabaseRegistry.isDatabaseRegistered(account.databaseName)) {
            logger.d { "Database for the account [$account] is in use, skip cache cleanup" }
            return
        }

        logger.d { "clearCacheForAccount: $account" }

        val accountStorage = AccountStorage()
        if (!accountStorage.isEmpty && accountStorage.account == account) {
            accountStorage.clear()
        }

        deleteSyncDatabase(account.databaseName)
    }

    fun clearCacheForOtherAccounts(account: AccountDescriptor) {
        logger.d { "clearCacheForOtherAccounts: $account" }

        getDatabaseList()
            .map { AccountDescriptor.fromDatabaseName(it) }
            .filter { it != account }
            .onEach { clearCacheForAccount(it) }
    }

    fun clearAllCaches() {
        logger.d { "clearAllCaches" }

        AccountStorage().clear() // clear account storage anyway

        getDatabaseList()
            .map { AccountDescriptor.fromDatabaseName(it) }
            .forEach { clearCacheForAccount(it) }
    }

    suspend fun cleanupOnLogin(account: AccountDescriptor, config: CacheConfig) {
        logger.d { "cleanupOnLogin" }

        if (!config.usePersistentCache) {
            logger.d { "Persistent cache is disabled, skipping cache cleanup on login" }
            return
        }

        if (config.cleanupPersistentCacheForOtherUsers) {
            clearCacheForOtherAccounts(account)
        }

        if (config.cleanupUnusedCachesAfter != Duration.INFINITE) {
            clearExpiredCacheForOtherAccounts(account, config.cleanupUnusedCachesAfter)
        }

        val maxSize = config.maxPersistentCacheSize ?: return
        clearCacheForOtherAccountsUntilFitMaxSize(account, maxSize)
        clearCacheForAccountUntilFitMaxSize(account, maxSize)
    }

    private fun clearExpiredCacheForOtherAccounts(account: AccountDescriptor, unusedPeriod: Duration) {
        logger.d { "clearExpiredCacheForOtherAccounts" }

        val now = Clock.System.now()

        getDatabaseList()
            .filter { now - getDatabaseLastModified(it) >= unusedPeriod }
            .map { AccountDescriptor.fromDatabaseName(it) }
            .filter { it != account }
            .forEach { clearCacheForAccount(it) }
    }

    private fun clearCacheForOtherAccountsUntilFitMaxSize(account: AccountDescriptor, maxSize: Long) {
        logger.d { "clearCacheForOtherAccountsUntilFitMaxSize $maxSize $account" }

        class DatabaseInfo(val name: String, val lastModified: Instant)

        val sortedDatabases = getDatabaseList()
            .map { DatabaseInfo(it, getDatabaseLastModified(it)) }
            .sortedBy { it.lastModified }

        sortedDatabases.forEach { databaseInfo ->
            val totalSize = getDatabaseList().sumOf { getDatabaseSize(it) }

            if (totalSize <= maxSize) {
                return
            }

            val databaseAccount = AccountDescriptor.fromDatabaseName(databaseInfo.name)

            if (databaseAccount != account) {
                clearCacheForAccount(databaseAccount)
            }
        }
    }

    private suspend fun clearCacheForAccountUntilFitMaxSize(account: AccountDescriptor, maxSize: Long) {
        logger.d { "clearCacheForAccountUntilFitMaxSize $account" }

        if (DatabaseRegistry.isDatabaseRegistered(account.databaseName)) {
            logger.d { "Database for the account [$account] is in use, skip cache cleanup" }
            return
        }

        val totalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
        logger.d { "clearCacheForAccountUntilFitMaxSize totalSize: $totalSize; maxSize: $maxSize" }

        if (totalSize <= maxSize) {
            logger.d { "clearCacheForAccountUntilFitMaxSize skip cleanup. We are in maxSize" }
            return
        }

        val databaseDriver = DriverFactory().createDriver(account, isInMemoryDatabase = false)
        val cache = SyncCache(account.databaseName, databaseDriver)

        try {
            // Fit to maxSize / 2 in order to don't clean it up on every login
            clearTablesUntilFitMaxSize(cache, maxSize = maxSize / 2)
        } finally {
            cache.close()
        }
    }

    private suspend fun clearTablesUntilFitMaxSize(cache: SyncCache, maxSize: Long) {
        val tables = EntityType.entries
            .map { entityType ->
                val rowCount = when (entityType) {
                    Document -> cache.getDocumentsCount()
                    List -> cache.getCollectionsCount(CollectionType.List)
                    Map -> cache.getCollectionsCount(CollectionType.Map)
                    Stream -> cache.getStreamsCount()
                }

                TableInfo(entityType, rowCount)
            }
            .sortedByDescending { it.rowCount }

        var prevSize = getDatabaseList().sumOf { getDatabaseSize(it) }

        tables.forEach { tableInfo ->
            while (cleanupTable(cache, tableInfo.entityType)) {
                var totalSize = getDatabaseList().sumOf { getDatabaseSize(it) }

                if (totalSize >= prevSize) {
                    // auto_vaccuum is disabled, so we have to shrink database explicitly
                    cache.shrinkDatabase()
                    totalSize = getDatabaseList().sumOf { getDatabaseSize(it) }
                }

                logger.d { "clearTablesUntilFitMaxSize totalSize: $totalSize; maxSize: $maxSize" }

                if (totalSize <= maxSize) {
                    return
                }

                prevSize = totalSize
            }
        }
    }

    private suspend fun cleanupTable(cache: SyncCache, entityType: EntityType): Boolean {
        logger.d { "cleanupTable: $entityType" }

        return when (entityType) {
            Document -> cache.cleanupDocumentsTable()
            List -> cache.cleanupCollectionsTable(CollectionType.List)
            Map -> cache.cleanupCollectionsTable(CollectionType.Map)
            Stream -> cache.cleanupStreamsTable()
        }
    }

    private class TableInfo(val entityType: EntityType, val rowCount: Long)

    private enum class EntityType { Document, List, Map, Stream }
}
