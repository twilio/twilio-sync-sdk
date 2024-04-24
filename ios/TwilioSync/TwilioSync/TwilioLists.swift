//
//  TwilioLists.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 11.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/// `TwilioLists` provides methods for creating, opening, and managing ``TwilioSyncList`` objects.
public class TwilioLists {
    
    private let lists: Lists
    
    init(_ lists: Lists) {
        self.lists = lists
    }
    
    /// Creates a new ``TwilioSyncList`` object.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to assign to new ``TwilioSyncList`` upon creation. Default is `nil`.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while list creation.
    /// - Returns: The created ``TwilioSyncList``.
    public func create(uniqueName: String? = nil, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncList {
        let kotlinList = try await kotlinCall {
            try await lists.create(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncList(kotlinList)
    }
    
    /// Opens an existing ``TwilioSyncList`` by unique name or creates a new one if the specified name does not exist.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to find existing ``TwilioSyncList`` or to assign to new ``TwilioSyncList`` upon creation.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while list opening or creation.
    /// - Returns: The opened or created ``TwilioSyncList``
    public func openOrCreate(uniqueName: String, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncList {
        let kotlinList = try await kotlinCall {
            try await lists.openOrCreate(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncList(kotlinList)
    }
    
    /// Opens an existing ``TwilioSyncList`` by SID or unique name.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name to find existing ``TwilioSyncList``.
    /// - Throws: ``TwilioError`` when an error occurred while list opening.
    /// - Returns: The opened ``TwilioSyncList``
    public func openExisting(sidOrUniqueName: String) async throws -> TwilioSyncList {
        let kotlinList = try await kotlinCall {
            try await lists.openExisting(sidOrUniqueName: sidOrUniqueName)
        }
        
        return TwilioSyncList(kotlinList)
    }
    
    /// Sets time to live for ``TwilioSyncList`` without opening it.
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
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating ttl.
    public func setTtl(sidOrUniqueName: String, ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await lists.setTtl(sidOrUniqueName: sidOrUniqueName, ttl: ttl.toKotlinDuration())
        }
    }
    
    /// Removes ``TwilioSyncList`` without opening it.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    /// - Throws: ``TwilioError`` when an error occurred while removing the list.
    public func remove(sidOrUniqueName: String) async throws {
        try await kotlinCall {
            try await lists.remove(sidOrUniqueName: sidOrUniqueName)
        }
    }
    
    /// Retrieve Item from the ``TwilioSyncList`` without opening it.
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemIndex: Index of the ``TwilioSyncList/Item`` to retrieve.
    ///   - useCache:
    ///       When `true` returns cached value if found in cache.
    ///       Collect ``TwilioSyncList/Events/onItemUpdated`` and ``TwilioSyncList/Events/onItemRemoved`` to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: An ``TwilioSyncList/Item`` for this `itemIndex` or `nil` if no item associated with the index.
    /// - Throws: ``TwilioError`` when an error occurred while retrieving the item.
    public func getListItem(listSidOrUniqueName: String, itemIndex: Int64, useCache: Bool = true) async throws -> TwilioSyncList.Item? {
        let kotlinListItem = try await kotlinCall {
            try await lists.getListItem(listSidOrUniqueName: listSidOrUniqueName, itemIndex: itemIndex, useCache: useCache)
        }
        
        return try kotlinListItem?.toTwilioSyncListItem()
    }

    /// Add Item to the ``TwilioSyncList`` without opening it.
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemData: Item data to add as a ``TwilioData`` object.
    /// - Returns: An ``TwilioSyncList/Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while adding the item.
    public func addListItem(listSidOrUniqueName: String, itemData: TwilioData) async throws -> TwilioSyncList.Item {
        let kotlinListItem = try await kotlinCall {
            try await lists.addListItem(listSidOrUniqueName: listSidOrUniqueName, jsonData: itemData.toJsonString())
        }
        
        return try kotlinListItem.toTwilioSyncListItem()
    }

    /// Add Item to the ``TwilioSyncList`` without opening it and set time to live for the ``TwilioSyncList/Item``.
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemData: Item data to add as a ``TwilioData`` object.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``TwilioSyncList/Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while adding the item.
    public func addListItemWithTtl(listSidOrUniqueName: String, itemData: TwilioData, ttl: TimeInterval) async throws -> TwilioSyncList.Item {
        let kotlinListItem = try await kotlinCall {
            try await lists.addListItemWithTtl(listSidOrUniqueName: listSidOrUniqueName, jsonData: itemData.toJsonString(), ttl: ttl.toKotlinDuration())
        }
        
        return try kotlinListItem.toTwilioSyncListItem()
    }

    /// Set Item in the ``TwilioSyncList`` without opening it.
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemIndex: Index of the ``TwilioSyncList/Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    /// - Returns: An ``TwilioSyncList/Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setListItem(
        listSidOrUniqueName: String,
        itemIndex: Int64,
        itemData: TwilioData
    ) async throws -> TwilioSyncList.Item {
        let kotlinListItem = try await kotlinCall {
            try await lists.setListItem(listSidOrUniqueName: listSidOrUniqueName, itemIndex: itemIndex, jsonData: itemData.toJsonString())
        }
        
        return try kotlinListItem.toTwilioSyncListItem()
    }
    
    /// Set Item in the ``TwilioSyncList`` without opening it and set time to live for the ``TwilioSyncList/Item``.
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemIndex: Index of the ``TwilioSyncList/Item`` to set.
    ///   - itemData: Item data to set as a ``TwilioData`` object.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``TwilioSyncList/Item`` which has been set.
    /// - Throws: ``TwilioError`` when an error occurred while setting the item.
    public func setListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Int64,
        itemData: TwilioData,
        ttl: TimeInterval
    ) async throws -> TwilioSyncList.Item {
        let kotlinListItem = try await kotlinCall {
            try await lists.setListItemWithTtl(listSidOrUniqueName: listSidOrUniqueName, itemIndex: itemIndex, jsonData: itemData.toJsonString(), ttl: ttl.toKotlinDuration())
        }
        
        return try kotlinListItem.toTwilioSyncListItem()
    }
    
    /// Mutate value of the existing ``TwilioSyncList/Item`` without opening ``TwilioSyncList`` using provided Mutator function.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await twilioSyncClient.lists.mutateListItem(listSidOrUniqueName: "myList", itemIndex: 0) { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemIndex: Index of the ``TwilioSyncList/Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the list item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    /// - Returns: An ``TwilioSyncList/Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateListItem(
        listSidOrUniqueName: String,
        itemIndex: Int64,
        mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?
    ) async throws -> TwilioSyncList.Item {
        let kotlinListItem = try await kotlinCall {
            try await lists.mutateListItem(listSidOrUniqueName: listSidOrUniqueName, itemIndex: itemIndex, mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinListItem.toTwilioSyncListItem()
    }

    /// Mutate value of the existing ``TwilioSyncList/Item`` without opening ``TwilioSyncList`` using 
    /// provided Mutator function and set time to live for the ``TwilioSyncList/Item``.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest item data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    let item = try await twilioSyncClient.lists.mutateListItemWithTtl(listSidOrUniqueName: "myList", itemIndex: 0, ttl: 3600) { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemIndex: Index of the ``TwilioSyncList/Item`` to mutate.
    ///   - mutator: Mutator which will be applied to the list item.
    ///
    ///       This function will be provided with the
    ///       previous data contents and should return new desired contents or `nil` to abort
    ///       mutate operation. This function is invoked on a background thread.
    ///
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Returns: An ``TwilioSyncList/Item`` which has been set during mutation.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the item.
    public func mutateListItemWithTtl(
        listSidOrUniqueName: String,
        itemIndex: Int64,
        ttl: TimeInterval,
        mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?
    ) async throws -> TwilioSyncList.Item {
        let kotlinListItem = try await kotlinCall {
            try await lists.mutateListItemWithTtl(listSidOrUniqueName: listSidOrUniqueName, itemIndex: itemIndex, ttl: ttl.toKotlinDuration(), mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinListItem.toTwilioSyncListItem()
    }

    /// Remove ``TwilioSyncList/Item`` from the ``TwilioSyncList`` without opening it.
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
    ///   - itemIndex: Index of the ``TwilioSyncList/Item`` to remove.
    /// - Throws: ``TwilioError`` when an error occurred while removing the item.
    public func removeListItem(listSidOrUniqueName: String, itemIndex: Int64) async throws {
        try await kotlinCall {
            try await lists.removeListItem(listSidOrUniqueName: listSidOrUniqueName, itemIndex: itemIndex)
        }
    }

    /// Retrieve items from the ``TwilioSyncList`` without opening it.
    ///
    /// **Example:**
    ///
    /// ```swift
    ///    try await twilioSyncClient.lists.queryItems(listSidOrUniqueName: "my-list").forEach { item in
    ///        print("\(item.index): \(item.data)")
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - listSidOrUniqueName: SID or unique name of existing ``TwilioSyncList``.
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
    ///       Collect ``TwilioSyncList/Events/onItemUpdated`` and ``TwilioSyncList/Events/onItemUpdated``
    ///       to receive notifications about the item changes.
    ///
    ///       When `false` - performs network request to get latest data from the backend.
    ///
    /// - Returns: New ``TwilioSyncIterator`` to sequentially access the elements from this ``TwilioSyncList``.
    public func queryItems(
        listSidOrUniqueName: String,
        startIndex: Int64? = nil,
        includeStartIndex: Bool = true,
        queryOrder: TwilioQueryOrder = .ascending,
        pageSize: Int = defaultPageSize,
        useCache: Bool = true
    ) -> TwilioSyncIterator<TwilioSyncList.Item> {
        
        let kotlinIterator = lists.queryItems(
            listSidOrUniqueName: listSidOrUniqueName,
            startIndex: startIndex.map { KotlinLong(value: $0) },
            includeStartIndex: includeStartIndex,
            queryOrder: queryOrder.toKotlinQueryOrder(),
            pageSize: Int32(pageSize),
            useCache: useCache
        )
        
        return TwilioSyncIterator<TwilioSyncList.Item>(kotlinIterator) { item in
            guard let kotlinItem = item as? SyncListItem else {
                throw TwilioError(message: "Cannot cast item: \(String(describing: item)) to SyncListItem. This should never happen. " +
                                  "Please report this error to https://support.twilio.com/")
            }
            return try kotlinItem.toTwilioSyncListItem()
        }
    }
}
