//
//  TwilioSyncDocumentTests.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 07.03.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import XCTest
import TwilioSyncLib
@testable import TwilioSync

final class TwilioSyncDocumentTests: XCTestCase {

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
        let document = try await client!.documents.create(ttl: 3600)

        XCTAssertFalse(document.sid.isEmpty)
        XCTAssertNil(document.uniqueName)
        XCTAssertNotNil(document.dateExpires)
        
        print("dateExpires: \(document.dateExpires!)")
    }
    
    func testOpen() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        let document1 = try await client!.documents.openOrCreate(uniqueName: uniqueName)
        let document2 = try await client!.documents.openExisting(sidOrUniqueName: uniqueName)
        
        XCTAssertEqual(document1.sid, document2.sid)
        
        XCTAssertEqual(uniqueName, document2.uniqueName)
        XCTAssertEqual(document1.uniqueName, document2.uniqueName)
        XCTAssertEqual(document1.dateCreated, document2.dateCreated)
        XCTAssertEqual(document1.dateUpdated, document2.dateUpdated)

        XCTAssertNil(document1.dateExpires)
        XCTAssertNil(document1.dateExpires)
    }
    
    func testClose() async throws {
        let document = try await client!.documents.create()
        document.close()
    }
    
    func testSetTtl() async throws {
        let document = try await client!.documents.create()
        XCTAssertNil(document.dateExpires)
        
        try await document.setTtl(3600)
        XCTAssertNotNil(document.dateExpires)
    }
    
    func testSetTtlDirect() async throws {
        let document = try await client!.documents.create()
        XCTAssertNil(document.dateExpires)
        
        try await client!.documents.setTtl(sidOrUniqueName: document.sid, ttl: 3600)
        
        // wait until the document object got updated
        try await waitAndAssertTrue { document.dateExpires != nil }
    }
    
    func testSetData() async throws {
        let document = try await client!.documents.create()
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        try await document.setData(data1)
        
        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testSetDataDirect() async throws {
        let document = try await client!.documents.create()
        XCTAssertTrue(document.data.isEmpty)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        try await client!.documents.update(sidOrUniqueName: document.sid, data: data1)
        
        // wait until the document object got updated
        try await waitAndAssertTrue { !document.data.isEmpty }

        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testSetDataWithTtl() async throws {
        let document = try await client!.documents.create()
        XCTAssertNil(document.dateExpires)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        try await document.setData(data1, ttl: 3600)
        
        XCTAssertNotNil(document.dateExpires)
        XCTAssertLessThan(document.dateCreated, document.dateExpires!)
        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testSetDataWithTtlDirect() async throws {
        let document = try await client!.documents.create()
        XCTAssertTrue(document.data.isEmpty)
        XCTAssertNil(document.dateExpires)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        try await client!.documents.updateWithTtl(sidOrUniqueName: document.sid, data: data1, ttl: 3600)
        
        // wait until the document object got updated
        try await waitAndAssertTrue { !document.data.isEmpty }

        XCTAssertNotNil(document.dateExpires)
        XCTAssertLessThan(document.dateCreated, document.dateExpires!)
        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testMutateData() async throws {
        let document = try await client!.documents.create()
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        try await document.mutateData { counter in
            let value = counter["value"] as? Int ?? 0
            return [ "value" : value + 1 ]
        }
        
        try await document.mutateData { counter in
            let value = counter["value"] as? Int ?? 0
            return [ "value" : value + 1 ]
        }
        
        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual([ "value" : 2 ], document.data.mapValues { $0 as? Int })
    }

    func testMutateAbort() async throws {
        let document = try await client!.documents.create()
        XCTAssertTrue(document.data.isEmpty)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        await assertThrowsTwilioError(try await document.mutateData { _ in nil }) { error in
            XCTAssertEqual(.commandPermanentError, error.reason)
            XCTAssertEqual("Mutate operation aborted: Mutator function has returned null as new data", error.message)
        }
        
        
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        XCTAssertTrue(document.data.isEmpty)
    }
    
    func testMutateDataDirect() async throws {
        let document = try await client!.documents.create()
        XCTAssertTrue(document.data.isEmpty)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        let mutatedData = try await client!.documents.mutate(sidOrUniqueName: document.sid) { _ in data1 }
        XCTAssertEqual(data1, mutatedData.mapValues { $0 as? String? })

        // wait until the document object got updated
        try await waitAndAssertTrue { !document.data.isEmpty }

        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testMutateDataWithTtl() async throws {
        let document = try await client!.documents.create()
        XCTAssertNil(document.dateExpires)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        try await document.mutateDataWithTtl(3600) { _ in data1 }
        
        XCTAssertNotNil(document.dateExpires)
        XCTAssertLessThan(document.dateCreated, document.dateExpires!)
        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testMutateDataWithTtlDirect() async throws {
        let document = try await client!.documents.create()
        XCTAssertTrue(document.data.isEmpty)
        XCTAssertNil(document.dateExpires)
        XCTAssertEqual(document.dateCreated, document.dateUpdated)
        
        let data1 = ["key1": "value1", "key2": nil]
        let mutatedData = try await client!.documents.mutateWithTtl(sidOrUniqueName: document.sid, ttl: 3600) { _ in data1 }
        XCTAssertEqual(data1, mutatedData.mapValues { $0 as? String? })

        // wait until the document object got updated
        try await waitAndAssertTrue { !document.data.isEmpty }

        XCTAssertNotNil(document.dateExpires)
        XCTAssertLessThan(document.dateCreated, document.dateExpires!)
        XCTAssertLessThan(document.dateCreated, document.dateUpdated)
        XCTAssertEqual(data1, document.data.mapValues { $0 as? String? })
    }
    
    func testRemove() async throws {
        let document = try await client!.documents.create()
        try await document.removeDocument()
        
        await assertThrowsTwilioError(try await client!.documents.openExisting(sidOrUniqueName: document.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("Document not found", error.message)
        }
    }
    
    func testRemoveDirect() async throws {
        let document = try await client!.documents.create()
        try await client!.documents.remove(sidOrUniqueName: document.sid)

        await assertThrowsTwilioError(try await client!.documents.openExisting(sidOrUniqueName: document.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("Document not found", error.message)
        }
    }
    
    func testEvents() async throws {
        let document = try await client!.documents.create()
        
        let receivedUpdates = ListActor<TwilioData>()

        let receiveTask = Task {
            for await doc in document.events.onUpdated {
                await receivedUpdates.append(doc.data)
            }
        }

        _ = await document.events.onSubscriptionStateChanged.first { $0 == .established }
        
        try await waitAndAssertTrue {
            await receivedUpdates.list.count == 1 // first update with current (empty) data
        }

        let client2 = try await TwilioSyncClient.create(useLastUserCache: false) { try await requestToken(identity: "otherUser") }
        defer { client2.shutdown() }

        let data1 = ["key1": "value1", "key2": nil]
        let data2 = ["key1": "value2", "key2": nil]

        try await client2.documents.update(sidOrUniqueName: document.sid, data: data1)

        try await waitAndAssertTrue {
            await receivedUpdates.list.count == 2
        }
        
        try await client2.documents.update(sidOrUniqueName: document.sid, data: data2)

        try await waitAndAssertTrue {
            await receivedUpdates.list.count == 3
        }
        
        let receivedUpdate0 = await receivedUpdates.list[0]
        let receivedUpdate1 = await receivedUpdates.list[1]
        let receivedUpdate2 = await receivedUpdates.list[2]
        
        XCTAssertTrue(receivedUpdate0.isEmpty)
        XCTAssertEqual(data1, receivedUpdate1.mapValues { $0 as? String })
        XCTAssertEqual(data2, receivedUpdate2.mapValues { $0 as? String })

        receiveTask.cancel() // stop receiving onMessagePublished events
        _ = await document.events.onSubscriptionStateChanged.first { $0 == .unsubscribed }
                
        try await client2.documents.remove(sidOrUniqueName: document.sid)
        
        // onRemoved replays last event. So we don't have to start listen it in advance before call documents.remove()
        _ = await document.events.onRemoved.first { $0 === document }
    }
}
