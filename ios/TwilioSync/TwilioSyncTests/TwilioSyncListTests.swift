//
//  TwilioSyncListTests.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 12.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import XCTest
import TwilioSyncLib
@testable import TwilioSync

final class TwilioSyncListTests: XCTestCase {

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
        let list = try await client!.lists.create(ttl: 3600)

        XCTAssertFalse(list.sid.isEmpty)
        XCTAssertNil(list.uniqueName)
        XCTAssertNotNil(list.dateExpires)
    }
    
    func testOpen() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        let list1 = try await client!.lists.openOrCreate(uniqueName: uniqueName)
        let list2 = try await client!.lists.openExisting(sidOrUniqueName: uniqueName)
        
        XCTAssertEqual(list1.sid, list2.sid)
        
        XCTAssertEqual(uniqueName, list2.uniqueName)
        XCTAssertEqual(list1.uniqueName, list2.uniqueName)
        XCTAssertEqual(list1.dateCreated, list2.dateCreated)
        XCTAssertEqual(list1.dateUpdated, list2.dateUpdated)

        XCTAssertNil(list1.dateExpires)
        XCTAssertNil(list1.dateExpires)
    }
    
    func testClose() async throws {
        let list = try await client!.lists.create()
        list.close()
    }
    
    func testSetTtl() async throws {
        let list = try await client!.lists.create()
        XCTAssertNil(list.dateExpires)
        
        try await list.setTtl(3600)
        XCTAssertNotNil(list.dateExpires)
    }
    
    func testSetTtlDirect() async throws {
        let list = try await client!.lists.create()
        XCTAssertNil(list.dateExpires)
        
        try await client!.lists.setTtl(sidOrUniqueName: list.sid, ttl: 3600)
        
        // wait until the list object got updated
        try await waitAndAssertTrue { list.dateExpires != nil }
    }
    
    func testAddGetItem() async throws {
        let list = try await client!.lists.create()
        XCTAssertEqual(list.dateCreated, list.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        
        let createdItem = try await list.addItem(itemData: data1)
        let receivedItem = try await list.getItem(itemIndex: 0)
        
        XCTAssertLessThan(list.dateCreated, list.dateUpdated)
        XCTAssertEqual(list.dateUpdated, createdItem.dateUpdated)
        XCTAssertEqual(data1, createdItem.data.mapValues { $0 as? String? })
        
        XCTAssertEqual(0, createdItem.index)
        XCTAssertEqual(createdItem.index, receivedItem?.index)
        XCTAssertEqual(createdItem.data.mapValues { $0 as? String }, receivedItem?.data.mapValues { $0 as? String })
        XCTAssertEqual(createdItem.dateCreated, receivedItem?.dateCreated)
        XCTAssertEqual(createdItem.dateUpdated, receivedItem?.dateUpdated)
        XCTAssertEqual(createdItem.dateExpires, receivedItem?.dateExpires)
    }
    
    func testAddGetItemDirect() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        _ = try await client!.lists.create(uniqueName: uniqueName)
        
        let data1 = ["key1": "value1", "key2": nil]
        
        let createdItem = try await client!.lists.addListItem(listSidOrUniqueName: uniqueName, itemData: data1)
        let receivedItem = try await client!.lists.getListItem(listSidOrUniqueName: uniqueName, itemIndex: 0)!
        
        XCTAssertEqual(data1, createdItem.data.mapValues { $0 as? String? })
        
        XCTAssertEqual(0, createdItem.index)
        XCTAssertEqual(createdItem.index, receivedItem.index)
        XCTAssertEqual(createdItem.data.mapValues { $0 as? String }, receivedItem.data.mapValues { $0 as? String })
        XCTAssertEqual(createdItem.dateCreated, receivedItem.dateCreated)
        XCTAssertEqual(createdItem.dateUpdated, receivedItem.dateUpdated)
        XCTAssertEqual(createdItem.dateExpires, receivedItem.dateExpires)
    }
    
    func testAddItemWithTtl() async throws {
        let list = try await client!.lists.create()
        
        let createdItem = try await list.addItemWithTtl(itemData: [:], ttl: 3600)
        
        XCTAssertNotNil(createdItem.dateExpires)
        XCTAssertEqual(createdItem.dateCreated, createdItem.dateUpdated)
        XCTAssertTrue(createdItem.data.isEmpty)
    }
    
    func testAddItemWithTtlDirect() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        _ = try await client!.lists.create(uniqueName: uniqueName)

        let createdItem = try await client!.lists.addListItemWithTtl(listSidOrUniqueName: uniqueName, itemData: [:], ttl: 3600)
        
        XCTAssertNotNil(createdItem.dateExpires)
        XCTAssertEqual(createdItem.dateCreated, createdItem.dateUpdated)
        XCTAssertTrue(createdItem.data.isEmpty)
    }
    
    func testSetItemWithTtl() async throws {
        let list = try await client!.lists.create()
        
        let createdItem = try await list.addItem(itemData: [:])
        
        XCTAssertNil(createdItem.dateExpires)
        XCTAssertEqual(createdItem.dateCreated, createdItem.dateUpdated)
        XCTAssertTrue(createdItem.data.isEmpty)
        
        let data1 = ["key1": "value1", "key2": nil]
        let updatedItem = try await list.setItemWithTtl(itemIndex: 0, itemData: data1, ttl: 3600)
        
        XCTAssertNotNil(updatedItem.dateExpires)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateExpires!)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateUpdated)
        XCTAssertEqual(data1, updatedItem.data.mapValues { $0 as? String? })
    }
    
    func testSetItemWithTtlDirect() async throws {
        let list = try await client!.lists.create()
        let item = try await list.addItem(itemData: [:])
        
        XCTAssertTrue(item.data.isEmpty)
        XCTAssertNil(item.dateExpires)
        XCTAssertEqual(item.dateCreated, item.dateUpdated)

        let data1 = ["key1": "value1", "key2": nil]
        _ = try await client!.lists.setListItemWithTtl(listSidOrUniqueName: list.sid, itemIndex: 0, itemData: data1, ttl: 3600)

        let updatedItem = try await list.getItem(itemIndex: 0)!
        
        XCTAssertNotNil(updatedItem.dateExpires)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateExpires!)
        XCTAssertLessThan(updatedItem.dateCreated, updatedItem.dateUpdated)
        XCTAssertEqual(data1, updatedItem.data.mapValues { $0 as? String? })
    }
    
    func testMutateItem() async throws {
        let list = try await client!.lists.create()
        XCTAssertEqual(list.dateCreated, list.dateUpdated)
        
        _ = try await list.addItem(itemData: [:])
        
        let updatedItem = try await list.mutateItem(itemIndex: 0) { counter in
            let value = counter["value"] as? Int ?? 0
            return [ "value" : value + 1 ]
        }
        
        XCTAssertLessThan(list.dateCreated, list.dateUpdated)
        
        XCTAssertEqual([ "value" : 1 ], updatedItem.data.mapValues { $0 as? Int })
    }

    func testMutateItemAbort() async throws {
        let list = try await client!.lists.create()
        
        let item = try await list.addItem(itemData: [:])

        try await waitAndAssertTrue { list.dateUpdated == item.dateUpdated }
        
        await assertThrowsTwilioError(try await list.mutateItem(itemIndex: 0) { _ in nil }) { error in
            XCTAssertEqual(.commandPermanentError, error.reason)
            XCTAssertEqual("Mutate operation aborted: Mutator function has returned null as new data", error.message)
        }
        
        XCTAssertEqual(item.dateUpdated, list.dateUpdated)
    }
    
    func testMutateItemDirect() async throws {
        let list = try await client!.lists.create()
        XCTAssertEqual(list.dateCreated, list.dateUpdated)
        
        _ = try await list.addItem(itemData: [:])
        
        let data1 = ["key1": "value1", "key2": nil]
        let item = try await client!.lists.mutateListItem(listSidOrUniqueName: list.sid, itemIndex: 0) { _ in data1 }
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
        
        let receivedItem = try await list.getItem(itemIndex: 0)!
        
        XCTAssertLessThan(list.dateCreated, list.dateUpdated)
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
        XCTAssertEqual(data1, receivedItem.data.mapValues { $0 as? String? })
    }
    
    func testMutateItemWithTtl() async throws {
        let list = try await client!.lists.create()
        var item = try await list.addItem(itemData: [:])
        XCTAssertNil(item.dateExpires)
        XCTAssertEqual(item.dateCreated, item.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        item = try await list.mutateItemWithTtl(itemIndex: 0, ttl: 3600) { _ in data1 }
        
        XCTAssertNotNil(item.dateExpires)
        XCTAssertLessThan(item.dateCreated, item.dateExpires!)
        XCTAssertLessThan(item.dateCreated, item.dateUpdated)
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
    }
    
    func testMutateItemWithTtlDirect() async throws {
        let list = try await client!.lists.create()
        var item = try await list.addItem(itemData: [:])
        XCTAssertTrue(item.data.isEmpty)
        XCTAssertNil(item.dateExpires)
        XCTAssertEqual(item.dateCreated, item.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        let mutatedItem = try await client!.lists.mutateListItemWithTtl(listSidOrUniqueName: list.sid, itemIndex: 0, ttl: 3600) { _ in data1 }
        XCTAssertEqual(data1, mutatedItem.data.mapValues { $0 as? String? })

        item = try await list.getItem(itemIndex: 0)!
        
        XCTAssertNotNil(item.dateExpires)
        XCTAssertLessThan(item.dateCreated, item.dateExpires!)
        XCTAssertLessThan(item.dateCreated, item.dateUpdated)
        XCTAssertEqual(data1, item.data.mapValues { $0 as? String? })
    }
    
    func testRemoveItem() async throws {
        let list = try await client!.lists.create()
        _ = try await list.addItem(itemData: [:])

        var item = try await list.getItem(itemIndex: 0)
        XCTAssertNotNil(item)
        
        try await list.removeItem(itemIndex: 0)
        
        item = try await list.getItem(itemIndex: 0)
        XCTAssertNil(item)
    }
    
    func testRemoveItemDirect() async throws {
        let list = try await client!.lists.create()
        _ = try await list.addItem(itemData: [:])

        var item = try await list.getItem(itemIndex: 0)
        XCTAssertNotNil(item)

        try await client!.lists.removeListItem(listSidOrUniqueName: list.sid, itemIndex: 0)
        
        item = try await list.getItem(itemIndex: 0)
        XCTAssertNil(item)
    }
    
    func testRemove() async throws {
        let list = try await client!.lists.create()
        try await list.removeList()
        
        await assertThrowsTwilioError(try await client!.lists.openExisting(sidOrUniqueName: list.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("List not found", error.message)
        }
    }
    
    func testRemoveDirect() async throws {
        let list = try await client!.lists.create()
        try await client!.lists.remove(sidOrUniqueName: list.sid)

        await assertThrowsTwilioError(try await client!.lists.openExisting(sidOrUniqueName: list.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("List not found", error.message)
        }
    }
    
    func testQueryItems() async throws {
        let list = try await client!.lists.create()
        
        let data1 = ["data" : "value1"]
        let data2 = ["data" : "value2"]
        
        _ = try await list.addItem(itemData: data1)
        _ = try await list.addItem(itemData: data2)

        var items = [[String : String]]()

        for try await item in list {
            items.append(item.data.mapValues { $0 as! String })
        }

        XCTAssertEqual([data1, data2], items)

        let iterator = list.queryItems()
        
        items = []
        while let item = try await iterator.next() {
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)

        let stream = list.queryItems().asAsyncThrowingStream()
        
        items = []
        for try await item in stream {
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
        
        items = []
        try await list.queryItems().forEach { item in
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
    }
    
    func testQueryItemsDirect() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        _ = try await client!.lists.create(uniqueName: uniqueName)
        
        let data1 = ["data" : "value1"]
        let data2 = ["data" : "value2"]
        
        _ = try await client!.lists.addListItem(listSidOrUniqueName: uniqueName, itemData: data1)
        _ = try await client!.lists.addListItem(listSidOrUniqueName: uniqueName, itemData: data2)
        
        let stream = client!.lists.queryItems(listSidOrUniqueName: uniqueName).asAsyncThrowingStream()
        
        var items = [[String : String]]()
        
        for try await item in stream {
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
        
        items = []
        
        try await client!.lists.queryItems(listSidOrUniqueName: uniqueName).forEach { item in
            items.append(item.data.mapValues { $0 as! String })
        }
        
        XCTAssertEqual([data1, data2], items)
    }
    
    func testEvents() async throws {
        let list = try await client!.lists.create()
        
        let addedItems = ListActor<Int64>()
        let updatedItems = ListActor<Int64>()
        let removedItems = ListActor<Int64>()

        let onItemAddedListener = Task {
            for await item in list.events.onItemAdded {
                await addedItems.append(item.index)
            }
        }

        let onItemUpdatedListener = Task {
            for await item in list.events.onItemUpdated {
                await updatedItems.append(item.index)
            }
        }

        let onItemRemovedListener = Task {
            for await item in list.events.onItemRemoved {
                await removedItems.append(item.index)
            }
        }

        _ = await list.events.onSubscriptionStateChanged.first { $0 == .established }
        
        let client2 = try await TwilioSyncClient.create(useLastUserCache: false) { try await requestToken(identity: "otherUser") }
        defer { client2.shutdown() }

        let itemIndecies: [Int64] = [0, 1, 2]
        
        for index in itemIndecies {
            let item = try await client2.lists.addListItem(listSidOrUniqueName: list.sid, itemData: [:])
            XCTAssertEqual(index, item.index)
        }

        try await waitAndAssertTrue {
            await addedItems.list.count == 3
        }
        
        var addedList = await addedItems.list
        XCTAssertEqual(itemIndecies, addedList)
        
        var updatedList = await updatedItems.list
        XCTAssertEqual([], updatedList)
        
        var removedList = await removedItems.list
        XCTAssertEqual([], removedList)

        for index in itemIndecies {
            _ = try await client2.lists.setListItem(listSidOrUniqueName: list.sid, itemIndex: index, itemData: ["\(index)" : index])
        }

        try await waitAndAssertTrue {
            await updatedItems.list.count == 3
        }
        
        addedList = await addedItems.list
        XCTAssertEqual(itemIndecies, addedList)
        
        updatedList = await updatedItems.list
        XCTAssertEqual(itemIndecies, updatedList)
        
        removedList = await removedItems.list
        XCTAssertEqual([], removedList)

        for index in itemIndecies {
            _ = try await client2.lists.removeListItem(listSidOrUniqueName: list.sid, itemIndex: index)
        }

        try await waitAndAssertTrue {
            await removedItems.list.count == 3
        }
        
        addedList = await addedItems.list
        XCTAssertEqual(itemIndecies, addedList)
        
        updatedList = await updatedItems.list
        XCTAssertEqual(itemIndecies, updatedList)
        
        removedList = await removedItems.list
        XCTAssertEqual(itemIndecies, removedList)

        // stop receiving events
        onItemAddedListener.cancel()
        onItemUpdatedListener.cancel()
        onItemRemovedListener.cancel()
        
        _ = await list.events.onSubscriptionStateChanged.first { $0 == .unsubscribed }
                
        try await client2.lists.remove(sidOrUniqueName: list.sid)
        
        // onRemoved replays last event. So we don't have to start listen it in advance before call lists.remove()
        _ = await list.events.onRemoved.first { $0 === list }
    }
}
