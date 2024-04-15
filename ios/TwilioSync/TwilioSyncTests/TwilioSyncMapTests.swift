//
//  TwilioSyncMapTests.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 12.03.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import XCTest
import TwilioSyncLib
@testable import TwilioSync

final class TwilioSyncMapTests: XCTestCase {

    var client: TwilioSyncClient?
    
    override func setUpWithError() throws {
        executionTimeAllowance = 60
        
        TwilioSyncClient.setLogLevel(level: .verbose)
        TwilioSyncClient.clearAllCaches()
        
        client = try Task.synchronous {
            try await TwilioSyncClient.create { try await requestToken() }
        }
    }
    
    override func tearDownWithError() throws {
        client?.shutdown()
    }

    func testCreate() async throws {
        let map = try await client!.maps.create(ttl: 3600)

        XCTAssertFalse(map.sid.isEmpty)
        XCTAssertNil(map.uniqueName)
        XCTAssertNotNil(map.dateExpires)
    }
    
    func testOpen() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        let map1 = try await client!.maps.openOrCreate(uniqueName: uniqueName)
        let map2 = try await client!.maps.openExisting(sidOrUniqueName: uniqueName)
        
        XCTAssertEqual(map1.sid, map2.sid)
        
        XCTAssertEqual(uniqueName, map2.uniqueName)
        XCTAssertEqual(map1.uniqueName, map2.uniqueName)
        XCTAssertEqual(map1.dateCreated, map2.dateCreated)
        XCTAssertEqual(map1.dateUpdated, map2.dateUpdated)

        XCTAssertNil(map1.dateExpires)
        XCTAssertNil(map1.dateExpires)
    }
    
    func testClose() async throws {
        let map = try await client!.maps.create()
        map.close()
    }
    
    func testSetTtl() async throws {
        let map = try await client!.maps.create()
        XCTAssertNil(map.dateExpires)
        
        try await map.setTtl(3600)
        XCTAssertNotNil(map.dateExpires)
    }
    
    func testSetTtlDirect() async throws {
        let map = try await client!.maps.create()
        XCTAssertNil(map.dateExpires)
        
        try await client!.maps.setTtl(sidOrUniqueName: map.sid, ttl: 3600)
        
        // wait until the map object got updated
        try await waitAndAssertTrue { map.dateExpires != nil }
    }
    
    func testSetGetItem() async throws {
        let map = try await client!.maps.create()
        XCTAssertEqual(map.dateCreated, map.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        
        let createdItem = try await map.setItem(itemKey: "item1", itemData: data1)
        let receivedItem = try await map.getItem(itemKey: "item1")
        
        XCTAssertLessThan(map.dateCreated, map.dateUpdated)
        XCTAssertEqual(map.dateUpdated, createdItem.dateUpdated)
        XCTAssertEqual(data1, createdItem.data.mapValues { $0 as? String? })
        
        XCTAssertEqual(createdItem.key, receivedItem?.key)
        XCTAssertEqual(createdItem.data.mapValues { $0 as? String }, receivedItem?.data.mapValues { $0 as? String })
        XCTAssertEqual(createdItem.dateCreated, receivedItem?.dateCreated)
        XCTAssertEqual(createdItem.dateUpdated, receivedItem?.dateUpdated)
        XCTAssertEqual(createdItem.dateExpires, receivedItem?.dateExpires)
    }
    
    func testSetGetItemDirect() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        _ = try await client!.maps.create(uniqueName: uniqueName)
        
        let data1 = ["key1": "value1", "key2": nil]
        
        let createdItem = try await client!.maps.setMapItem(mapSidOrUniqueName: uniqueName, itemKey: "item1", itemData: data1)
        let receivedItem = try await client!.maps.getMapItem(mapSidOrUniqueName: uniqueName, itemKey: "item1")!
        
        XCTAssertEqual(data1, createdItem.data.mapValues { $0 as? String? })
        
        XCTAssertEqual(createdItem.key, receivedItem.key)
        XCTAssertEqual(createdItem.data.mapValues { $0 as? String }, receivedItem.data.mapValues { $0 as? String })
        XCTAssertEqual(createdItem.dateCreated, receivedItem.dateCreated)
        XCTAssertEqual(createdItem.dateUpdated, receivedItem.dateUpdated)
        XCTAssertEqual(createdItem.dateExpires, receivedItem.dateExpires)
    }
    
    func testSetItemWithTtl() async throws {
        let map = try await client!.maps.create()
        
        let createdItem = try await map.setItem(itemKey: "item1", itemData: [:])
        
        XCTAssertNil(createdItem.dateExpires)
        XCTAssertEqual(createdItem.dateCreated, createdItem.dateUpdated)
        XCTAssertTrue(createdItem.data.isEmpty)
        
        let data1 = ["key1": "value1", "key2": nil]
        let updatedItem = try await map.setItemWithTtl(itemKey: "item1", itemData: data1, ttl: 3600)
        
        XCTAssertNotNil(updatedItem.dateExpires)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateExpires!)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateUpdated)
        XCTAssertEqual(data1, updatedItem.data.mapValues { $0 as? String? })
    }
    
    func testSetItemWithTtlDirect() async throws {
        let map = try await client!.maps.create()
        let item = try await map.setItem(itemKey: "item1", itemData: [:])
        
        XCTAssertTrue(item.data.isEmpty)
        XCTAssertNil(item.dateExpires)
        XCTAssertEqual(item.dateCreated, item.dateUpdated)

        let data1 = ["key1": "value1", "key2": nil]
        _ = try await client!.maps.setMapItemWithTtl(mapSidOrUniqueName: map.sid, itemKey: "item1", itemData: data1, ttl: 3600)

        let updatedItem = try await map.getItem(itemKey: "item1")!
        
        XCTAssertNotNil(updatedItem.dateExpires)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateExpires!)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateUpdated)
        XCTAssertEqual(data1, updatedItem.data.mapValues { $0 as? String? })
    }
    
    func testMutateItem() async throws {
        let map = try await client!.maps.create()
        XCTAssertEqual(map.dateCreated, map.dateUpdated)
        
        let createdItem = try await map.mutateItem(itemKey: "item1") { counter in
            let value = counter?["value"] as? Int ?? 0
            return [ "value" : value + 1 ]
        }
        
        let updatedItem = try await map.mutateItem(itemKey: "item1") { counter in
            let value = counter?["value"] as? Int ?? 0
            return [ "value" : value + 1 ]
        }
        
        XCTAssertLessThan(map.dateCreated, map.dateUpdated)
        
        XCTAssertEqual("item1", createdItem.key)
        XCTAssertEqual("item1", updatedItem.key)
        
        XCTAssertEqual([ "value" : 1 ], createdItem.data.mapValues { $0 as? Int })
        XCTAssertEqual([ "value" : 2 ], updatedItem.data.mapValues { $0 as? Int })
    }

    func testMutateItemAbort() async throws {
        let map = try await client!.maps.create()
        XCTAssertEqual(map.dateCreated, map.dateUpdated)
        
        await assertThrowsTwilioError(try await map.mutateItem(itemKey: "item1") { _ in nil }) { error in
            XCTAssertEqual(.commandPermanentError, error.reason)
            XCTAssertEqual("Mutate operation aborted: Mutator function has returned null as new data", error.message)
        }
        
        XCTAssertEqual(map.dateCreated, map.dateUpdated)
    }
    
    func testMutateItemDirect() async throws {
        let map = try await client!.maps.create()
        XCTAssertEqual(map.dateCreated, map.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        let item = try await client!.maps.mutateMapItem(mapSidOrUniqueName: map.sid, itemKey: "item1") { _ in data1 }
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })

        let receivedItem = try await map.getItem(itemKey: "item1")!
        
        XCTAssertLessThan(map.dateCreated, map.dateUpdated)
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
        XCTAssertEqual(data1, receivedItem.data.mapValues { $0 as? String? })
    }
    
    func testMutateItemWithTtl() async throws {
        let map = try await client!.maps.create()
        var item = try await map.setItem(itemKey: "item1", itemData: [:])
        XCTAssertNil(item.dateExpires)
        XCTAssertEqual(item.dateCreated, item.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        item = try await map.mutateItemWithTtl(itemKey: "item1", ttl: 3600) { _ in data1 }
        
        XCTAssertNotNil(item.dateExpires)
        XCTAssertLessThan(item.dateCreated, item.dateExpires!)
        XCTAssertLessThan(item.dateCreated, item.dateUpdated)
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
    }
    
    func testMutateItemWithTtlDirect() async throws {
        let map = try await client!.maps.create()
        var item = try await map.setItem(itemKey: "item1", itemData: [:])
        XCTAssertTrue(item.data.isEmpty)
        XCTAssertNil(item.dateExpires)
        XCTAssertEqual(item.dateCreated, item.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        let mutatedItem = try await client!.maps.mutateMapItemWithTtl(mapSidOrUniqueName: map.sid, itemKey: "item1", ttl: 3600) { _ in data1 }
        XCTAssertEqual(data1, mutatedItem.data.mapValues { $0 as? String? })

        item = try await map.getItem(itemKey: "item1")!
        
        XCTAssertNotNil(item.dateExpires)
        XCTAssertLessThan(item.dateCreated, item.dateExpires!)
        XCTAssertLessThan(item.dateCreated, item.dateUpdated)
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
    }
    
    func testRemoveItem() async throws {
        let map = try await client!.maps.create()
        _ = try await map.setItem(itemKey: "item1", itemData: [:])

        var item = try await map.getItem(itemKey: "item1")
        XCTAssertNotNil(item)
        
        try await map.removeItem(itemKey: "item1")
        
        item = try await map.getItem(itemKey: "item1")
        XCTAssertNil(item)
    }
    
    func testRemoveItemDirect() async throws {
        let map = try await client!.maps.create()
        _ = try await map.setItem(itemKey: "item1", itemData: [:])

        var item = try await map.getItem(itemKey: "item1")
        XCTAssertNotNil(item)

        try await client!.maps.removeMapItem(mapSidOrUniqueName: map.sid, itemKey: "item1")
        
        item = try await map.getItem(itemKey: "item1")
        XCTAssertNil(item)
    }
    
    func testRemove() async throws {
        let map = try await client!.maps.create()
        try await map.removeMap()
        
        await assertThrowsTwilioError(try await client!.maps.openExisting(sidOrUniqueName: map.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("Map not found", error.message)
        }
    }
    
    func testRemoveDirect() async throws {
        let map = try await client!.maps.create()
        try await client!.maps.remove(sidOrUniqueName: map.sid)

        await assertThrowsTwilioError(try await client!.maps.openExisting(sidOrUniqueName: map.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("Map not found", error.message)
        }
    }
    
    func testQueryItems() async throws {
        let map = try await client!.maps.create()
        
        let data1 = ["data" : "value1"]
        let data2 = ["data" : "value2"]
        
        _ = try await map.setItem(itemKey: "item1", itemData: data1)
        _ = try await map.setItem(itemKey: "item2", itemData: data2)

        var items = [[String : String]]()

        for try await item in map {
            items.append(item.data.mapValues { $0 as! String })
        }

        XCTAssertEqual([data1, data2], items)

        let iterator = map.queryItems()
        
        items = []
        while let item = try await iterator.next() {
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)

        let stream = map.queryItems().asAsyncThrowingStream()
        
        items = []
        for try await item in stream {
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
        
        items = []
        try await map.queryItems().forEach { item in
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
    }
    
    func testQueryItemsDirect() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        _ = try await client!.maps.create(uniqueName: uniqueName)
        
        let data1 = ["data" : "value1"]
        let data2 = ["data" : "value2"]
        
        _ = try await client!.maps.setMapItem(mapSidOrUniqueName: uniqueName, itemKey: "item1", itemData: data1)
        _ = try await client!.maps.setMapItem(mapSidOrUniqueName: uniqueName, itemKey: "item2", itemData: data2)
        
        let stream = client!.maps.queryItems(mapSidOrUniqueName: uniqueName).asAsyncThrowingStream()
        
        var items = [[String : String]]()
        
        for try await item in stream {
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
        
        items = []
        
        try await client!.maps.queryItems(mapSidOrUniqueName: uniqueName).forEach { item in
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
    }
    
    func testEvents() async throws {
        let map = try await client!.maps.create()
        
        let addedItems = ListActor<String>()
        let updatedItems = ListActor<String>()
        let removedItems = ListActor<String>()

        let onItemAddedListener = Task {
            for await item in map.events.onItemAdded {
                await addedItems.append(item.key)
            }
        }

        let onItemUpdatedListener = Task {
            for await item in map.events.onItemUpdated {
                await updatedItems.append(item.key)
            }
        }

        let onItemRemovedListener = Task {
            for await item in map.events.onItemRemoved {
                await removedItems.append(item.key)
            }
        }

        _ = await map.events.onSubscriptionStateChanged.first { $0 == .established }
        
        let client2 = try await TwilioSyncClient.create(useLastUserCache: false) { try await requestToken(identity: "otherUser") }
        defer { client2.shutdown() }

        let itemKeys = ["item1", "item2", "item3"]
        
        for key in itemKeys {
            _ = try await client2.maps.setMapItem(mapSidOrUniqueName: map.sid, itemKey: key, itemData: [:])
        }

        try await waitAndAssertTrue {
            await addedItems.list.count == 3
        }
        
        var addedList = await addedItems.list
        XCTAssertEqual(itemKeys, addedList)
        
        var updatedList = await updatedItems.list
        XCTAssertEqual([], updatedList)
        
        var removedList = await removedItems.list
        XCTAssertEqual([], removedList)

        for key in itemKeys {
            _ = try await client2.maps.setMapItem(mapSidOrUniqueName: map.sid, itemKey: key, itemData: [key : key])
        }

        try await waitAndAssertTrue {
            await updatedItems.list.count == 3
        }
        
        addedList = await addedItems.list
        XCTAssertEqual(itemKeys, addedList)
        
        updatedList = await updatedItems.list
        XCTAssertEqual(itemKeys, updatedList)
        
        removedList = await removedItems.list
        XCTAssertEqual([], removedList)

        for key in itemKeys {
            _ = try await client2.maps.removeMapItem(mapSidOrUniqueName: map.sid, itemKey: key)
        }

        try await waitAndAssertTrue {
            await removedItems.list.count == 3
        }
        
        addedList = await addedItems.list
        XCTAssertEqual(itemKeys, addedList)
        
        updatedList = await updatedItems.list
        XCTAssertEqual(itemKeys, updatedList)
        
        removedList = await removedItems.list
        XCTAssertEqual(itemKeys, removedList)

        // stop receiving events
        onItemAddedListener.cancel()
        onItemUpdatedListener.cancel()
        onItemRemovedListener.cancel()
        
        _ = await map.events.onSubscriptionStateChanged.first { $0 == .unsubscribed }
                
        try await client2.maps.remove(sidOrUniqueName: map.sid)
        
        // onRemoved replays last event. So we don't have to start listen it in advance before call maps.remove()
        _ = await map.events.onRemoved.first { $0 === map }
    }
}
