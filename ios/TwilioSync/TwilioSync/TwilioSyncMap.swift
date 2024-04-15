//
//  TwilioSyncMap.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 11.03.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import Foundation
import TwilioSyncLib

/// `TwilioSyncMap` is a key-value store with Strings as keys and ``Item`` objects as values.
///
/// You can add, remove and modify values associated with the keys.
///
/// To obtain an instance of a `TwilioSyncMap` use ``TwilioSyncClient/maps``.
///
/// **Example:**
///
/// ```swift
///    let map = try await twilioSyncClient.maps.openOrCreate(uniqueName: "my-map")
///
///    for try await item in map {
///        print("\(item.key): \(item.data)")
///    }
/// ```
public class TwilioSyncMap {
    
    private let kotlinMap: SyncMap

    init(_ kotlinMap: SyncMap) {
        self.kotlinMap = kotlinMap
    }
    
    deinit {
        close()
    }
    
    /// An immutable system-assigned identifier of this ``TwilioSyncMap``.
    public var sid: String { kotlinMap.sid }

    /// An optional unique name for this ``TwilioSyncMap``, assigned at creation time.
    public var uniqueName: String? { kotlinMap.uniqueName }

    /// Current subscription state.
    public var subscriptionState: TwilioSubscriptionState { kotlinMap.subscriptionState.toTwilioSubscriptionState() }

    /// A date when this ``TwilioSyncMap`` was created.
    public var dateCreated: Date { kotlinMap.dateCreated.toSwiftDate() }

    /// A date when this ``TwilioSyncMap`` was last updated.
    public var dateUpdated: Date { kotlinMap.dateUpdated.toSwiftDate() }

    /// A date this ``TwilioSyncMap`` will expire, `nil` means will not expire.
    public var dateExpires: Date? { kotlinMap.dateExpires?.toSwiftDate() }

    /// `true` when this ``TwilioSyncMap`` has been removed on the backend, `false` otherwise.
    public var isRemoved: Bool { kotlinMap.isRemoved }

    /// `true` when this ``TwilioSyncMap`` is offline and doesn't receive updates from the backend, `false` otherwise.
    public var isFromCache: Bool { kotlinMap.isFromCache }

    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public var events: Events { Events(self) }

    /// Set time to live for this ``TwilioSyncMap``.
    ///
    /// This TTL specifies the minimum time the object will live,
    /// sometime soon after this time the object will be deleted.
    ///
    /// If time to live is not specified, object lives infinitely long.
    ///
    /// TTL could be used in order to auto-recycle old unused objects,
    /// but building app logic, like timers, using TTL is not recommended.
    ///
    /// - Parameter ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating ttl.
    public func setTtl(_ ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await kotlinMap.setTtl(ttl: ttl.toKotlinDuration())
        }
    }

    /// Retrieve Item from the ``TwilioSyncMap``.
    ///
    /// - Parameters:
    ///   - itemKey: Key of the ``Item`` to retrieve.
    ///   - useCache:
    ///       When `true` returns cached value if found in cache.
    ///       Collect ``Events/onItemUpdated`` and ``Events/onItemRemoved`` to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: An ``Item`` for this `itemKey` or `nil` if no item associated with the key.
    /// - Throws: ``TwilioError`` when an error occurred while retrieving the item.
    public func getItem(itemKey: String, useCache: Bool = true) async throws -> Item? {
        let kotlinItem = try await kotlinCall {
            try await kotlinMap.getItem(itemKey: itemKey, useCache: useCache)
        }
        
        return try kotlinItem?.toTwilioSyncMapItem()
    }

    /// Set Item in the ``TwilioSyncMap``.
    ///
    /// - Parameters:
    ///   - itemKey: Key of the ``Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    /// - Returns: An ``Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setItem(itemKey: String, itemData: TwilioData) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinMap.setItem(itemKey: itemKey, jsonData: itemData.toJsonString())
        }
        
        return try kotlinItem.toTwilioSyncMapItem()
    }

    /// Set Item in the ``TwilioSyncMap``.
    ///
    /// - Parameters:
    ///   - itemKey: Key of the ``Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setItemWithTtl(
        itemKey: String,
        itemData: TwilioData,
        ttl: TimeInterval
    ) async throws -> TwilioSyncMap.Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinMap.setItemWithTtl(itemKey: itemKey, jsonData: itemData.toJsonString(), ttl: ttl.toKotlinDuration())
        }
        
        return try kotlinItem.toTwilioSyncMapItem()
    }
    
    /// Mutate value of the ``Item`` using provided Mutator function.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await map.mutateItem(itemKey: "counter") { counter in
    ///        let value = counter?["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - itemKey: Key of the ``Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the map item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    /// - Returns: An ``Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateItem(
        itemKey: String,
        mutator: @escaping (_ currentData: TwilioData?) async throws -> TwilioData?
    ) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinMap.mutateItem(itemKey: itemKey, mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinItem.toTwilioSyncMapItem()
    }

    /// Mutate value of the ``Item`` using provided Mutator function and set time to live for the ``Item``.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await map.mutateItemWithTtl(itemKey: "counter", ttl: 3600) { counter in
    ///        let value = counter?["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - itemKey: Key of the ``Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the map item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateItemWithTtl(
        itemKey: String,
        ttl: TimeInterval,
        mutator: @escaping (_ currentData: TwilioData?) async throws -> TwilioData?
    ) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinMap.mutateItemWithTtl(itemKey: itemKey, ttl: ttl.toKotlinDuration(), mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinItem.toTwilioSyncMapItem()
    }

    /// Remove ``Item`` from the ``TwilioSyncMap``.
    ///
    /// - Parameters:
    ///   - itemKey: Key of the ``Item`` to remove.
    /// - Throws: ``TwilioError`` when an error occurred while removing the item.
    public func removeItem(itemKey: String) async throws {
        try await kotlinCall {
            try await kotlinMap.removeItem(itemKey: itemKey)
        }
    }

    /// Retrieve items from the ``TwilioSyncMap``.
    ///
    /// **Example:**
    ///
    /// ```swift
    ///    let map = try await twilioSyncClient.maps.openOrCreate(uniqueName: "my-map")
    ///
    ///    try await map.queryItems().forEach { item in
    ///        print("\(item.key): \(item.data)")
    ///    }
    /// ```
    ///
    /// - Parameters:
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
    ///       Collect ``Events/onItemUpdated`` and ``Events/onItemRemoved``
    ///       to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: New ``TwilioSyncIterator`` to sequentially access the elements from this ``TwilioSyncMap``.
    public func queryItems(
        startKey: String? = nil,
        includeStartKey: Bool = true,
        queryOrder: TwilioQueryOrder = .ascending,
        pageSize: Int = defaultPageSize,
        useCache: Bool = true
    ) -> TwilioSyncIterator<Item> {
        
        let kotlinIterator = kotlinMap.queryItems(
            startKey: startKey,
            includeStartKey: includeStartKey,
            queryOrder: queryOrder.toKotlinQueryOrder(),
            pageSize: Int32(pageSize),
            useCache: useCache
        )
        
        return TwilioSyncIterator<Item>(kotlinIterator) { item in
            guard let kotlinItem = item as? SyncMapItem else {
                throw TwilioError(message: "Cannot cast item \(String(describing: item)) to SyncMapItem. This should never happen. " +
                                  "Please report this error to https://support.twilio.com/")
            }
            return try kotlinItem.toTwilioSyncMapItem()
        }
    }
    
    /// Remove this ``TwilioSyncMap``.
    ///
    /// - Throws: ``TwilioError`` when an error occurred while removing the map.
    public func removeMap() async throws {
        try await kotlinCall {
            try await kotlinMap.removeMap()
        }
    }

    /// Close this ``TwilioSyncMap``.
    ///
    /// After closing ``TwilioSyncMap`` stops emitting events.
    /// Call this method to cleanup resources when finish using this ``TwilioSyncMap`` object.
    public func close() {
        kotlinMap.close()
    }
    
    /// Represents a value associated with each key in ``TwilioSyncMap``.
    public struct Item {

        /// Key of the ``Item``.
        public let key: String

        /// Value of the ``Item`` as a ``TwilioData`` object.
        public let data: TwilioData

        /// A date when this ``Item`` was created.
        public let dateCreated: Date

        /// A date when this ``Item`` was last updated.
        public let dateUpdated: Date

        /// A date this ``Item`` will expire, `nil` means will not expire.
        ///
        /// - SeeAlso: ``TwilioSyncMap/setItemWithTtl(itemKey:itemData:ttl:)``
        public let dateExpires: Date?
    }
    
    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public struct Events {
        
        /// Emits when ``TwilioSyncMap`` subscription state has changed.
        public var onSubscriptionStateChanged: AsyncStream<TwilioSubscriptionState> {
            kotlinEvents.onSubscriptionStateChanged.asAsyncStream { $0?.toTwilioSubscriptionState() }
        }

        /// Emits when the ``TwilioSyncMap`` has been removed.
        public var onRemoved: AsyncStream<TwilioSyncMap> {
            kotlinEvents.onRemoved.asAsyncStream { _ in syncMap }
        }

        /// Emits when ``Item`` has been added.
        public var onItemAdded: AsyncStream<Item> {
            kotlinEvents.onItemAdded.asAsyncStream { try? $0?.toTwilioSyncMapItem() }
        }

        /// Emits when ``Item`` has been updated.
        public var onItemUpdated: AsyncStream<Item> {
            kotlinEvents.onItemUpdated.asAsyncStream { try? $0?.toTwilioSyncMapItem() }
        }

        /// Emits when ``Item`` has been removed.
        public var onItemRemoved: AsyncStream<Item> {
            kotlinEvents.onItemRemoved.asAsyncStream { try? $0?.toTwilioSyncMapItem() }
        }

        private let syncMap: TwilioSyncMap
        
        private let kotlinEvents: SyncMapEvents
        
        init(_ syncMap: TwilioSyncMap) {
            self.syncMap = syncMap
            self.kotlinEvents = syncMap.kotlinMap.events
        }
    }
}

extension TwilioSyncMap : AsyncSequence {
    
    public typealias AsyncIterator = TwilioSyncIterator<Item>
    
    public typealias Element = Item
    
    public func makeAsyncIterator() -> AsyncIterator { queryItems() }
}

extension TwilioSyncLib.SyncMapItem {
    
    func toTwilioSyncMapItem() throws -> TwilioSyncMap.Item {
        return TwilioSyncMap.Item(
            key: self.key,
            data: try self.data.toTwilioData(),
            dateCreated: self.dateCreated.toSwiftDate(),
            dateUpdated: self.dateUpdated.toSwiftDate(),
            dateExpires: self.dateExpires?.toSwiftDate()
        )
    }
    
    
}
