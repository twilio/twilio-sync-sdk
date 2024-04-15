//
//  TwilioMaps.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 11.03.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import Foundation
import TwilioSyncLib

/// `TwilioMaps` provides methods for creating, opening, and managing ``TwilioSyncMap`` objects.
public class TwilioMaps {
    
    private let maps: Maps
    
    init(_ maps: Maps) {
        self.maps = maps
    }
    
    /// Creates a new ``TwilioSyncMap`` object.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to assign to new ``TwilioSyncMap`` upon creation. Default is `nil`.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while map creation.
    /// - Returns: The created ``TwilioSyncMap``.
    public func create(uniqueName: String? = nil, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncMap {
        let kotlinMap = try await kotlinCall {
            try await maps.create(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncMap(kotlinMap)
    }
    
    /// Opens an existing ``TwilioSyncMap`` by unique name or creates a new one if the specified name does not exist.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to find existing ``TwilioSyncMap`` or to assign to new ``TwilioSyncMap`` upon creation.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while map opening or creation.
    /// - Returns: The opened or created ``TwilioSyncMap``
    public func openOrCreate(uniqueName: String, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncMap {
        let kotlinMap = try await kotlinCall {
            try await maps.openOrCreate(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncMap(kotlinMap)
    }
    
    /// Opens an existing ``TwilioSyncMap`` by SID or unique name.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name to find existing ``TwilioSyncMap``.
    /// - Throws: ``TwilioError`` when an error occurred while map opening.
    /// - Returns: The opened ``TwilioSyncMap``
    public func openExisting(sidOrUniqueName: String) async throws -> TwilioSyncMap {
        let kotlinMap = try await kotlinCall {
            try await maps.openExisting(sidOrUniqueName: sidOrUniqueName)
        }
        
        return TwilioSyncMap(kotlinMap)
    }
    
    /// Sets time to live for ``TwilioSyncMap`` without opening it.
    ///
    /// This TTL specifies the minimum time the object will live,
    /// sometime soon after this time the object will be deleted.
    ///
    /// If time to live is not specified, object lives infinitely long.
    ///
    /// TTL could be used in order to auto-recycle old unused objects,
    /// but building app logic, like timers, using TTL is not recommended.
    ///
    /// - Parameters:
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating ttl.
    public func setTtl(sidOrUniqueName: String, ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await maps.setTtl(sidOrUniqueName: sidOrUniqueName, ttl: ttl.toKotlinDuration())
        }
    }
    
    /// Removes ``TwilioSyncMap`` without opening it.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    /// - Throws: ``TwilioError`` when an error occurred while removing the map.
    public func remove(sidOrUniqueName: String) async throws {
        try await kotlinCall {
            try await maps.remove(sidOrUniqueName: sidOrUniqueName)
        }
    }
    
    /// Retrieve Item from the ``TwilioSyncMap`` without opening it.
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - itemKey: Key of the ``TwilioSyncMap/Item`` to retrieve.
    ///   - useCache:
    ///       When `true` returns cached value if found in cache.
    ///       Collect ``TwilioSyncMap/Events/onItemUpdated`` and ``TwilioSyncMap/Events/onItemRemoved`` to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: An ``Item`` for this `itemKey` or `nil` if no item associated with the key.
    /// - Throws: ``TwilioError`` when an error occurred while retrieving the item.
    public func getMapItem(mapSidOrUniqueName: String, itemKey: String, useCache: Bool = true) async throws -> TwilioSyncMap.Item? {
        let kotlinMapItem = try await kotlinCall {
            try await maps.getMapItem(mapSidOrUniqueName: mapSidOrUniqueName, itemKey: itemKey, useCache: useCache)
        }
        
        return try kotlinMapItem?.toTwilioSyncMapItem()
    }

    /// Set Item in the ``TwilioSyncMap`` without opening it.
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - itemKey: Key of the ``TwilioSyncMap/Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    /// - Returns: An ``TwilioSyncMap/Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setMapItem(mapSidOrUniqueName: String, itemKey: String, itemData: TwilioData) async throws -> TwilioSyncMap.Item {
        let kotlinMapItem = try await kotlinCall {
            try await maps.setMapItem(mapSidOrUniqueName: mapSidOrUniqueName, itemKey: itemKey, jsonData: itemData.toJsonString())
        }
        
        return try kotlinMapItem.toTwilioSyncMapItem()
    }

    /// Set Item in the ``TwilioSyncMap`` without opening it.
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - itemKey: Key of the ``TwilioSyncMap/Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``TwilioSyncMap/Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        itemData: TwilioData,
        ttl: TimeInterval
    ) async throws -> TwilioSyncMap.Item {
        let kotlinMapItem = try await kotlinCall {
            try await maps.setMapItemWithTtl(mapSidOrUniqueName: mapSidOrUniqueName, itemKey: itemKey, jsonData: itemData.toJsonString(), ttl: ttl.toKotlinDuration())
        }
        
        return try kotlinMapItem.toTwilioSyncMapItem()
    }

    /// Mutate value of the ``TwilioSyncMap/Item`` without opening ``TwilioSyncMap`` using provided Mutator function.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await twilioSyncClient.maps.mutateMapItem(mapSidOrUniqueName: "myMap", itemKey: "counter") { counter in
    ///        let value = counter?["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - itemKey: Key of the ``TwilioSyncMap/Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the map item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    /// - Returns: An ``TwilioSyncMap/Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateMapItem(
        mapSidOrUniqueName: String,
        itemKey: String,
        mutator: @escaping (_ currentData: TwilioData?) async throws -> TwilioData?
    ) async throws -> TwilioSyncMap.Item {
        let kotlinMapItem = try await kotlinCall {
            try await maps.mutateMapItem(mapSidOrUniqueName: mapSidOrUniqueName, itemKey: itemKey, mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinMapItem.toTwilioSyncMapItem()
    }

    /// Mutate value of the ``TwilioSyncMap/Item`` without opening ``TwilioSyncMap`` using 
    /// provided Mutator function and set time to live for the ``TwilioSyncMap/Item``.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await twilioSyncClient.maps.mutateMapItemWithTtl(mapSidOrUniqueName: "myMap", itemKey: "counter", ttl: 3600) { counter in
    ///        let value = counter?["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - itemKey: Key of the ``TwilioSyncMap/Item`` to mutate.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    ///   - mutator: Mutator which will be applied to the map item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    /// - Returns: An ``TwilioSyncMap/Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the
    public func mutateMapItemWithTtl(
        mapSidOrUniqueName: String,
        itemKey: String,
        ttl: TimeInterval,
        mutator: @escaping (_ currentData: TwilioData?) async throws -> TwilioData?
    ) async throws -> TwilioSyncMap.Item {
        let kotlinMapItem = try await kotlinCall {
            try await maps.mutateMapItemWithTtl(mapSidOrUniqueName: mapSidOrUniqueName, itemKey: itemKey, ttl: ttl.toKotlinDuration(), mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinMapItem.toTwilioSyncMapItem()
    }

    /// Remove ``TwilioSyncMap/Item`` from the ``TwilioSyncMap`` without opening it.
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - itemKey: Key of the ``TwilioSyncMap/Item`` to remove.
    /// - Throws: ``TwilioError`` when an error occurred while removing the item.
    public func removeMapItem(mapSidOrUniqueName: String, itemKey: String) async throws {
        try await kotlinCall {
            try await maps.removeMapItem(mapSidOrUniqueName: mapSidOrUniqueName, itemKey: itemKey)
        }
    }

    /// Retrieve items from the ``TwilioSyncMap`` without opening it.
    ///
    /// **Example:**
    ///
    /// ```swift
    ///    try await twilioSyncClient.maps.queryItems(mapSidOrUniqueName: "my-map").forEach { item in
    ///        print("\(item.key): \(item.data)")
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - mapSidOrUniqueName: SID or unique name of existing ``TwilioSyncMap``.
    ///   - startKey: Key of the first item to retrieve, `nil` means start from the first item in the ``TwilioSyncMap``.
    ///   - includeStartKey:
    ///       When `true` - result includes the item with the `startKey` (if exists in the ``TwilioSyncMap``).
    ///
    ///       When `false` - the item with the `startKey` is skipped.
    ///
    ///       Ignored when `startKey = nil`
    ///
    ///   - queryOrder: ``TwilioQueryOrder`` for sorting results.
    ///   - pageSize: Page size for querying items from the backend.
    ///   - useCache:
    ///       When `true` returns cached value if found in cache.
    ///       Collect ``TwilioSyncMap/Events/onItemUpdated`` and ``TwilioSyncMap/Events/onItemRemoved``
    ///       to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: New ``TwilioSyncIterator`` to sequentially access the elements from this ``TwilioSyncMap``.
    public func queryItems(
        mapSidOrUniqueName: String,
        startKey: String? = nil,
        includeStartKey: Bool = true,
        queryOrder: TwilioQueryOrder = .ascending,
        pageSize: Int = defaultPageSize,
        useCache: Bool = true
    ) -> TwilioSyncIterator<TwilioSyncMap.Item> {
        
        let kotlinIterator = maps.queryItems(
            mapSidOrUniqueName: mapSidOrUniqueName,
            startKey: startKey,
            includeStartKey: includeStartKey,
            queryOrder: queryOrder.toKotlinQueryOrder(),
            pageSize: Int32(pageSize),
            useCache: useCache
        )
        
        return TwilioSyncIterator<TwilioSyncMap.Item>(kotlinIterator) { item in
            guard let kotlinItem = item as? SyncMapItem else {
                throw TwilioError(message: "Cannot cast item: \(String(describing: item)) to SyncMapItem. This should never happen. " +
                                  "Please report this error to https://support.twilio.com/")
            }
            return try kotlinItem.toTwilioSyncMapItem()
        }
    }
}
