//
//  TwilioSyncList.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 11.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/// `TwilioSyncList` is an ordered sequence of ``Item`` objects as values.
///
/// You can add, remove and modify values associated with the each index in the list..
///
/// To obtain an instance of a `TwilioSyncList` use ``TwilioSyncClient/lists``.
///
/// **Example:**
///
/// ```swift
///    let list = try await twilioSyncClient.lists.openOrCreate(uniqueName: "my-list")
///
///    for try await item in list {
///        print("\(item.index): \(item.data)")
///    }
/// ```
public class TwilioSyncList {
    
    private let kotlinList: SyncList

    init(_ kotlinList: SyncList) {
        self.kotlinList = kotlinList
    }
    
    deinit {
        close()
    }
    
    /// An immutable system-assigned identifier of this ``TwilioSyncList``.
    public var sid: String { kotlinList.sid }

    /// An optional unique name for this ``TwilioSyncList``, assigned at creation time.
    public var uniqueName: String? { kotlinList.uniqueName }

    /// Current subscription state.
    public var subscriptionState: TwilioSubscriptionState { kotlinList.subscriptionState.toTwilioSubscriptionState() }

    /// A date when this ``TwilioSyncList`` was created.
    public var dateCreated: Date { kotlinList.dateCreated.toSwiftDate() }

    /// A date when this ``TwilioSyncList`` was last updated.
    public var dateUpdated: Date { kotlinList.dateUpdated.toSwiftDate() }

    /// A date this ``TwilioSyncList`` will expire, `nil` means will not expire.
    public var dateExpires: Date? { kotlinList.dateExpires?.toSwiftDate() }

    /// `true` when this ``TwilioSyncList`` has been removed on the backend, `false` otherwise.
    public var isRemoved: Bool { kotlinList.isRemoved }

    /// `true` when this ``TwilioSyncList`` is offline and doesn't receive updates from the backend, `false` otherwise.
    public var isFromCache: Bool { kotlinList.isFromCache }

    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public var events: Events { Events(self) }

    /// Set time to live for this ``TwilioSyncList``.
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
            try await kotlinList.setTtl(ttl: ttl.toKotlinDuration())
        }
    }

    /// Retrieve Item from the ``TwilioSyncList``.
    ///
    /// - Parameters:
    ///   - itemIndex: Index of the ``Item`` to retrieve.
    ///   - useCache:
    ///       When `true` returns cached value if found in cache.
    ///       Collect ``Events/onItemUpdated`` and ``Events/onItemRemoved`` to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: An ``Item`` for this `itemIndex` or `nil` if no item associated with the key.
    /// - Throws: ``TwilioError`` when an error occurred while retrieving the item.
    public func getItem(itemIndex: Int64, useCache: Bool = true) async throws -> Item? {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.getItem(itemIndex: itemIndex, useCache: useCache)
        }
        
        return try kotlinItem?.toTwilioSyncListItem()
    }

    /// Add Item to the ``TwilioSyncList``.
    ///
    /// - Parameters:
    ///   - itemData: Item data to add as a ``TwilioData`` object.
    /// - Returns: An ``Item`` which has been added.
    /// - Throws: ``TwilioError`` when an error occurred while adding the item.
    public func addItem(itemData: TwilioData) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.addItem(jsonData: itemData.toJsonString())
        }
        
        return try kotlinItem.toTwilioSyncListItem()
    }

    /// Add Item to the ``TwilioSyncList``.
    ///
    /// - Parameters:
    ///   - itemData: Item data to add as a ``TwilioData`` object.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``Item`` which has been added.
    /// - Throws: ``TwilioError`` when an error occurred while adding the item.
    public func addItemWithTtl(
        itemData: TwilioData,
        ttl: TimeInterval
    ) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.addItemWithTtl(jsonData: itemData.toJsonString(), ttl: ttl.toKotlinDuration())
        }
        
        return try kotlinItem.toTwilioSyncListItem()
    }
    
    /// Set Item in the ``TwilioSyncList``.
    ///
    /// - Parameters:
    ///   - itemIndex: Index of the ``Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    /// - Returns: An ``Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setItem(itemIndex: Int64, itemData: TwilioData) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.setItem(itemIndex: itemIndex, jsonData: itemData.toJsonString())
        }
        
        return try kotlinItem.toTwilioSyncListItem()
    }

    /// Set Item in the ``TwilioSyncList``.
    ///
    /// - Parameters:
    ///   - itemIndex: Index of the ``Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setItemWithTtl(
        itemIndex: Int64,
        itemData: TwilioData,
        ttl: TimeInterval
    ) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.setItemWithTtl(itemIndex: itemIndex, jsonData: itemData.toJsonString(), ttl: ttl.toKotlinDuration())
        }
        
        return try kotlinItem.toTwilioSyncListItem()
    }
    
    /// Mutate value of the existing ``Item`` using provided Mutator function.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await list.mutateItem(itemIndex: 0) { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - itemIndex: Index of the ``Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the list item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    /// - Returns: An ``Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateItem(
        itemIndex: Int64,
        mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?
    ) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.mutateItem(itemIndex: itemIndex, mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinItem.toTwilioSyncListItem()
    }

    /// Mutate value of the existing ``Item`` using provided Mutator function and set time to live for the ``Item``.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await list.mutateItemWithTtl(itemIndex: 0, ttl: 3600) { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - itemIndex: Index of the ``Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the list item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateItemWithTtl(
        itemIndex: Int64,
        ttl: TimeInterval,
        mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?
    ) async throws -> Item {
        let kotlinItem = try await kotlinCall {
            try await kotlinList.mutateItemWithTtl(itemIndex: itemIndex, ttl: ttl.toKotlinDuration(), mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinItem.toTwilioSyncListItem()
    }

    /// Remove ``Item`` from the ``TwilioSyncList``.
    ///
    /// - Parameters:
    ///   - itemIndex: Index of the ``Item`` to remove.
    /// - Throws: ``TwilioError`` when an error occurred while removing the item.
    public func removeItem(itemIndex: Int64) async throws {
        try await kotlinCall {
            try await kotlinList.removeItem(itemIndex: itemIndex)
        }
    }

    /// Retrieve items from the ``TwilioSyncList``.
    ///
    /// **Example:**
    ///
    /// ```swift
    ///    let list = try await twilioSyncClient.lists.openOrCreate(uniqueName: "my-list")
    ///
    ///    try await list.queryItems().forEach { item in
    ///        print("\(item.index): \(item.data)")
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - startIndex: Index of the first item to retrieve, `nil` means start from the first item in the ``TwilioSyncList``.
    ///   - includeStartIndex:
    ///       When `true` - result includes the item with the `startIndex` (if exists in the ``TwilioSyncList``).
    ///
    ///       When `false` - the item with the `startIndex` is skipped.
    ///
    ///       Ignored when `startIndex = nil`
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
    /// - Returns: New ``TwilioSyncIterator`` to sequentially access the elements from this ``TwilioSyncList``.
    public func queryItems(
        startIndex: Int64? = nil,
        includeStartIndex: Bool = true,
        queryOrder: TwilioQueryOrder = .ascending,
        pageSize: Int = defaultPageSize,
        useCache: Bool = true
    ) -> TwilioSyncIterator<Item> {
        
        let kotlinIterator = kotlinList.queryItems(
            startIndex: startIndex.map { KotlinLong(value: $0) },
            includeStartIndex: includeStartIndex,
            queryOrder: queryOrder.toKotlinQueryOrder(),
            pageSize: Int32(pageSize),
            useCache: useCache
        )
        
        return TwilioSyncIterator<Item>(kotlinIterator) { item in
            guard let kotlinItem = item as? SyncListItem else {
                throw TwilioError(message: "Cannot cast item \(String(describing: item)) to SyncListItem. This should never happen. " +
                                  "Please report this error to https://support.twilio.com/")
            }
            return try kotlinItem.toTwilioSyncListItem()
        }
    }
    
    /// Remove this ``TwilioSyncList``.
    ///
    /// - Throws: ``TwilioError`` when an error occurred while removing the list.
    public func removeList() async throws {
        try await kotlinCall {
            try await kotlinList.removeList()
        }
    }

    /// Close this ``TwilioSyncList``.
    ///
    /// After closing ``TwilioSyncList`` stops emitting events.
    /// Call this method to cleanup resources when finish using this ``TwilioSyncList`` object.
    public func close() {
        kotlinList.close()
    }
    
    /// Represents a value associated with each key in ``TwilioSyncList``.
    public struct Item {

        /// Index of the ``Item``.
        public let index: Int64

        /// Value of the ``Item`` as a ``TwilioData`` object.
        public let data: TwilioData

        /// A date when this ``Item`` was created.
        public let dateCreated: Date

        /// A date when this ``Item`` was last updated.
        public let dateUpdated: Date

        /// A date this ``Item`` will expire, `nil` means will not expire.
        ///
        /// - SeeAlso: ``TwilioSyncList/addItemWithTtl(itemData:ttl:)``
        public let dateExpires: Date?
    }
    
    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public struct Events {
        
        /// Emits when ``TwilioSyncList`` subscription state has changed.
        public var onSubscriptionStateChanged: AsyncStream<TwilioSubscriptionState> {
            kotlinEvents.onSubscriptionStateChanged.asAsyncStream { $0?.toTwilioSubscriptionState() }
        }

        /// Emits when the ``TwilioSyncList`` has been removed.
        public var onRemoved: AsyncStream<TwilioSyncList> {
            kotlinEvents.onRemoved.asAsyncStream { _ in syncList }
        }

        /// Emits when ``Item`` has been added.
        public var onItemAdded: AsyncStream<Item> {
            kotlinEvents.onItemAdded.asAsyncStream { try? $0?.toTwilioSyncListItem() }
        }

        /// Emits when ``Item`` has been updated.
        public var onItemUpdated: AsyncStream<Item> {
            kotlinEvents.onItemUpdated.asAsyncStream { try? $0?.toTwilioSyncListItem() }
        }

        /// Emits when ``Item`` has been removed.
        public var onItemRemoved: AsyncStream<Item> {
            kotlinEvents.onItemRemoved.asAsyncStream { try? $0?.toTwilioSyncListItem() }
        }

        private let syncList: TwilioSyncList
        
        private let kotlinEvents: SyncListEvents
        
        init(_ syncList: TwilioSyncList) {
            self.syncList = syncList
            self.kotlinEvents = syncList.kotlinList.events
        }
    }
}

extension TwilioSyncList : AsyncSequence {
    
    public typealias AsyncIterator = TwilioSyncIterator<Item>
    
    public typealias Element = Item
    
    public func makeAsyncIterator() -> AsyncIterator { queryItems() }
}

extension TwilioSyncLib.SyncListItem {
    
    func toTwilioSyncListItem() throws -> TwilioSyncList.Item {
        return TwilioSyncList.Item(
            index: self.index,
            data: try self.data.toTwilioData(),
            dateCreated: self.dateCreated.toSwiftDate(),
            dateUpdated: self.dateUpdated.toSwiftDate(),
            dateExpires: self.dateExpires?.toSwiftDate()
        )
    }
    
    
}
