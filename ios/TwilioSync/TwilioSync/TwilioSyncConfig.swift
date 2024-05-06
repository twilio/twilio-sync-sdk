//
//  Configuration.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 29.01.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import Foundation
import TwilioSyncLib

/// Default page size for querying collection items from backend.
public let defaultPageSize = 100

/// `TwilioSyncConfig` is a struct that contains the configuration parameters for the ``TwilioSyncClient``
public struct TwilioSyncConfig {

    /// `SyncClientConfig` is a struct that contains the general configuration parameters for the ``TwilioSyncClient``
    public struct SyncClientConfig {

        /// Timeout for a single HTTP request in seconds.
        public let httpTimeout: TimeInterval

        /// Timeout for a command (like update document, insert list item etc.).
        /// Sync Client retries HTTP requests until this timeout happens or non-retryable error is received from backend.
        public let commandTimeout: TimeInterval

        /// Default page size for querying collection items from backend.
        public let pageSize: Int

        /// Twilio server region to connect to, such as `us1` or `ie1`.
        /// Instances exist in specific regions, so this should only be changed if needed.
        public let region: String

        /// - If useProxy flag is `true` then system proxy settings will be applied.
        /// If no proxy set in the system settings then direct connection will be used.
        ///
        /// - If this flag is `false` all proxy settings will be ignored and direct connection will be used.
        public let useProxy: Bool

        public init(
            httpTimeout: TimeInterval = 10,
            commandTimeout: TimeInterval = 10,
            pageSize: Int = defaultPageSize,
            region: String = "us1",
            useProxy: Bool = false
        ) {
            self.httpTimeout = httpTimeout
            self.commandTimeout = commandTimeout
            self.pageSize = pageSize
            self.region = region
            self.useProxy = useProxy
        }
    }

    /// `CacheConfig` is a struct that contains the cache configuration parameters for the ``TwilioSyncClient``
    public struct CacheConfig {

        /// When `true` all persistent caches are dropped once `Unauthorised`
        /// response received from backend on connect.
        public let dropAllCachesOnUnauthorised: Bool

        /// When `true` persistent caches is used, when `false` - only in-memory cache is used.
        public let usePersistentCache: Bool

        /// Maximum device storage size in bytes used for all persistent caches.
        /// Persistent cache for each ``TwilioAccountDescriptor`` is stored in separate database.
        /// When `maxPersistentCacheSize` is exceeded a Sync Client first remove old (looking at dataModified
        /// of the database file) databases first. In the last database, least recently used records are removed
        /// (if necessary) in order to fit into the size limit.
        /// Default value is 100Mb. `nil` value is INFINITE size.
        public let maxPersistentCacheSize: Int64?

        /// Persistent cache for each ``TwilioAccountDescriptor`` is stored in separate database.
        /// When `cleanupPersistentCacheForOtherUsers` is `true` - all unopened persistent 
        /// cache databases for other accounts will be removed on login.
        public let cleanupPersistentCacheForOtherUsers: Bool

        /// Persistent cache for each ``TwilioAccountDescriptor`` is stored in separate database.
        /// All unopened persistent cache databases which are older than `cleanupUnusedCachesAfter`
        /// (looking at dataModified of the database file) will be removed on login.
        public let cleanupUnusedCachesAfter: TimeInterval

        public init(
            dropAllCachesOnUnauthorised: Bool = true,
            usePersistentCache: Bool = true,
            maxPersistentCacheSize: Int64? = 100 * 1024 * 1024,
            cleanupPersistentCacheForOtherUsers: Bool = true,
            cleanupUnusedCachesAfter: TimeInterval = 60 * 60 * 24 * 7
        ) {
            self.dropAllCachesOnUnauthorised = dropAllCachesOnUnauthorised
            self.usePersistentCache = usePersistentCache
            self.maxPersistentCacheSize = maxPersistentCacheSize
            self.cleanupPersistentCacheForOtherUsers = cleanupPersistentCacheForOtherUsers
            self.cleanupUnusedCachesAfter = cleanupUnusedCachesAfter
        }
    }

    /// General configuration parameters for the ``TwilioSyncClient``
    public let syncClientConfig: SyncClientConfig

    /// Cache configuration parameters for the ``TwilioSyncClient``
    public let cacheConfig: CacheConfig

    public init(
        syncClientConfig: SyncClientConfig = SyncClientConfig(),
        cacheConfig: CacheConfig = CacheConfig()
    ) {
        self.syncClientConfig = syncClientConfig
        self.cacheConfig = cacheConfig
    }
}

extension TwilioSyncConfig {
    
    func toKotlinSyncConfig() -> Sync_android_sharedSyncConfig {
        let clientConfig = Sync_android_sharedSyncClientConfig(
            httpTimeout: syncClientConfig.httpTimeout.toKotlinDuration(),
            commandTimeout: syncClientConfig.commandTimeout.toKotlinDuration(),
            pageSize: Int32(syncClientConfig.pageSize),
            region: syncClientConfig.region,
            deferCertificateTrustToPlatform: true,
            useProxy: syncClientConfig.useProxy
        )
        
        let cacheConfig = Sync_android_sharedCacheConfig(
            dropAllCachesOnUnauthorised: cacheConfig.dropAllCachesOnUnauthorised,
            usePersistentCache: cacheConfig.usePersistentCache,
            maxPersistentCacheSize: cacheConfig.maxPersistentCacheSize.map { KotlinLong(value: $0) },
            cleanupPersistentCacheForOtherUsers: cacheConfig.cleanupPersistentCacheForOtherUsers,
            cleanupUnusedCachesAfter: cacheConfig.cleanupUnusedCachesAfter.toKotlinDuration()
        )
        
        return Sync_android_sharedSyncConfig(syncClientConfig: clientConfig, cacheConfig: cacheConfig)
    }
}
