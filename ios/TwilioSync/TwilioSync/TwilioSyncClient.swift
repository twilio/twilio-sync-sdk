//
//  TwilioSyncClient.swift
//  TwilioSyncClient
//
//  Created by Dmitry Kalita on 22.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/**
 * This is a central entity used to work with Sync.
 *
 * After creating a `TwilioSyncClient` you can open, create and modify ``TwilioSyncDocument``s,
 * ``TwilioSyncList``s, ``TwilioSyncMap``s and ``TwilioSyncStream``s.
 *
 * **Example:**
 *
 * ```swift
 * let client = try await TwilioSyncClient.create {
 *     try await requestToken()
 * }
 * ```
 */
public class TwilioSyncClient {
    
    private let syncClient: SyncClient
    
    private init(_ syncClient: SyncClient) {
        self.syncClient = syncClient
        self.streams = TwilioStreams(syncClient.streams)
        self.documents = TwilioDocuments(syncClient.documents)
        self.maps = TwilioMaps(syncClient.maps)
        self.lists = TwilioLists(syncClient.lists)
    }
    
    /** Provides methods to work with ``TwilioSyncStream``s. */
    public let streams: TwilioStreams
    
    /** Provides methods to work with ``TwilioSyncDocument``s. */
    public let documents: TwilioDocuments
    
    /** Provides methods to work with ``TwilioSyncMap``s. */
    public let maps: TwilioMaps
    
    /** Provides methods to work with ``TwilioSyncList``s. */
    public let lists: TwilioLists
    
    /**
     * Cleanly shuts down the ``TwilioSyncClient`` when you are done with it.
     * After calling this method client could not be reused.
     *
     * @param logout If `true`, on the next client creation 
     * the ``TwilioErrorReason/mismatchedLastUserAccount`` error
     * will not be thrown, but client will not be able created offline.
     */
    public func shutdown(logout: Bool = false) {
        syncClient.shutdown(logout: logout)
    }
}

extension TwilioSyncClient {

    /**
     Creates a new ``TwilioSyncClient`` instance.

     - Parameters:
        - useLastUserAccount: Try to use offline cache for last logged in user.
     
            Set this parameter to `false`when you are not sure that current
            token is for the same ``TwilioAccountDescriptor`` which was logged
            in last time, otherwise created client will be shut down once
            token will be verified after setup network connection.
     
            When this parameter is set to `true` and offline cache from last
            session is present (See ``TwilioSyncConfig/CacheConfig-swift.struct/usePersistentCache``
            parameter) - client will be returned immediately (even if device is
            offline). In this case the client will return cached data and start
            deliver updates when come online.
     
            When this parameter is set to `false` client will be returned only 
            after connection to backend is established and access to persistent
            cache will be granted after user's token gets validated by backend.
            So when cache contains sensitive user data or there are
            other critical security concerns - always set this parameter to `false`.
     
        - config: Configuration parameters for ``TwilioSyncClient`` instance.
     
        - tokenProvider: Access token provider for Sync service.
     This provider will be called multiple time: first before creating the client and later
     each time when token is about to expire.
     
     - Returns: New ``TwilioSyncClient`` instance.
     - Throws: ``TwilioError``  when error occurred while client creation.
     */
    static public func create(
        useLastUserCache: Bool = true,
        config: TwilioSyncConfig = TwilioSyncConfig(),
        tokenProvider: @escaping () async throws -> String
    ) async throws -> TwilioSyncClient {
        
        let wrappedTokenProvider =  {
            do {
                return try Task.synchronous(operation: tokenProvider)
            } catch {
                KotlinLogger("wrappedTokenProvider").w { "Exception in tokenProvider: \(error)" }
                return ""
            }
        }
        
        let client = try await kotlinCall {
            try await SyncClientFactory.shared.create(
                useLastUserCache: useLastUserCache, 
                config: config.toKotlinSyncConfig(),
                connectivityMonitor: ConnectivityMonitor(),
                tokenProvider: wrappedTokenProvider
            )
        }
        
        return TwilioSyncClient(client)
    }
    
    /**
     * Set verbosity level for log messages to be printed to console.
     * Default log level is ``TwilioLogLevel/silent``
     *
     * @param level: Verbosity level. See ``TwilioLogLevel`` for supported options.
     */
    static public func setLogLevel(level: TwilioLogLevel) {
        KotlinLogger.companion.setLogLevel(level: level.toKotlinLogLevel())
    }
        
    /// Clears offline cache for specified account.
    /// Call this method before creating ``TwilioSyncClient`` instance for the account, otherwise this method does nothing.
    /// - Parameter account: Account to clear cache for.
    static public func clearCacheForAccount(account: TwilioAccountDescriptor) {
        SyncCacheCleaner.shared.clearCacheForAccount(account: account.toKotlinAccountDescriptor())
    }
    
    /// Clears offline caches for all accounts, except specified.
    /// Call this method before creating ``TwilioSyncClient`` instances.
    /// This method does nothing for accounts with active SyncClient instances.
    /// - Parameter account: Account to keep the cache for.
    static public func clearCacheForOtherAccounts(account: TwilioAccountDescriptor) {
        SyncCacheCleaner.shared.clearCacheForOtherAccounts(account: account.toKotlinAccountDescriptor())
    }
    
    /// Clears offline caches for all accounts.
    /// Call this method before creating SyncClient instances.
    /// This method does nothing for accounts with active SyncClient instances.
    static public func clearAllCaches() {
        SyncCacheCleaner.shared.clearAllCaches()
    }
}
