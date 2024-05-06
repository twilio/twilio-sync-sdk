//
//  TwilioSyncStreamTests.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 23.01.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import XCTest
import TwilioSyncLib
@testable import TwilioSync

final class TwilioSyncStreamTests: XCTestCase {

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
        let stream = try await client!.streams.create(ttl: 3600)

        XCTAssertFalse(stream.sid.isEmpty)
        XCTAssertNil(stream.uniqueName)
        XCTAssertNotNil(stream.dateExpires)
        
        print("dateExpires: \(stream.dateExpires!)")
    }
    
    func testOpen() async throws {
        let uniqueName = generateRandomString(prefix: "uniqueName")
        let stream1 = try await client!.streams.openOrCreate(uniqueName: uniqueName)
        let stream2 = try await client!.streams.openExisting(sidOrUniqueName: uniqueName)
        
        XCTAssertEqual(stream1.sid, stream2.sid)
        
        XCTAssertEqual(uniqueName, stream1.uniqueName)
        XCTAssertEqual(stream1.uniqueName, stream2.uniqueName)
        
        XCTAssertNil(stream1.dateExpires)
        XCTAssertNil(stream2.dateExpires)
    }
    
    func testClose() async throws {
        let stream = try await client!.streams.create()
        stream.close()
    }
    
    func testSetTtl() async throws {
        let stream = try await client!.streams.create()
        XCTAssertNil(stream.dateExpires)
        
        try await stream.setTtl(ttl: 3600)
        XCTAssertNotNil(stream.dateExpires)
    }
    
    func testSetTtlDirect() async throws {
        let stream = try await client!.streams.create()
        XCTAssertNil(stream.dateExpires)
        
        try await client!.streams.setTtl(sidOrUniqueName: stream.sid, ttl: 3600)
        
        // wait until the stream object got updated
        try await waitAndAssertTrue { stream.dateExpires != nil }
    }
    
    func testRemove() async throws {
        let stream = try await client!.streams.create()
        try await stream.removeStream()
        
        await assertThrowsTwilioError(try await client!.streams.openExisting(sidOrUniqueName: stream.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("Stream not found", error.message)
        }
    }
    
    func testRemoveDirect() async throws {
        let stream = try await client!.streams.create()
        try await client!.streams.remove(sidOrUniqueName: stream.sid)

        await assertThrowsTwilioError(try await client!.streams.openExisting(sidOrUniqueName: stream.sid)) { error in
            XCTAssertEqual(404, error.status)
            XCTAssertEqual("Stream not found", error.message)
        }
    }
    
    func testPublishMessage() async throws {
        let stream = try await client!.streams.create()
        
        let data = ["key1": 1, "key2": 2]
        let message = try await stream.publishMessage(data: data)
        
        XCTAssertFalse(message.sid.isEmpty)
        XCTAssertEqual(data, message.data as? [String : Int])
    }
    
    func testPublishMessageDirect() async throws {
        let streamName = generateRandomString(prefix: "streamName")
        _ = try await client!.streams.create(uniqueName: streamName)
        
        let data = ["key1": "value1", "key2": nil]
        let message = try await client!.streams.publishMessage(sidOrUniqueName: streamName, data: data)
        
        XCTAssertFalse(message.sid.isEmpty)
        XCTAssertEqual(data, message.data as? [String : String?])
    }
    
    func testEvents() async throws {
        let stream = try await client!.streams.create()
        
        let receivedMessages = ListActor<TwilioSyncStream.Message>()

        let receiveTask = Task {
            for await message in stream.events.onMessagePublished {
                await receivedMessages.append(message)
            }
        }

        _ = await stream.events.onSubscriptionStateChanged.first { $0 == .established }
        
        let client2 = try await TwilioSyncClient.create(useLastUserCache: false) { try await requestToken(identity: "otherUser") }
        defer { client2.shutdown() }

        let data1 = ["key1": "value1", "key2": nil]
        let data2 = ["key1": "value2", "key2": nil]

        let sentMessage1 = try await client2.streams.publishMessage(sidOrUniqueName: stream.sid, data: data1)

        try await waitAndAssertTrue {
            await receivedMessages.list.count == 1
        }

        let sentMessage2 = try await client2.streams.publishMessage(sidOrUniqueName: stream.sid, data: data2)

        try await waitAndAssertTrue {
            await receivedMessages.list.count == 2
        }

        let receivedMessage1 = await receivedMessages.list[0]
        let receivedMessage2 = await receivedMessages.list[1]
        
        XCTAssertEqual(sentMessage1.sid, receivedMessage1.sid)
        XCTAssertEqual(sentMessage2.sid, receivedMessage2.sid)

        XCTAssertEqual(data1, sentMessage1.data.mapValues { $0 as? String })
        XCTAssertEqual(data1, receivedMessage1.data.mapValues { $0 as? String })

        XCTAssertEqual(data2, sentMessage2.data.mapValues { $0 as? String })
        XCTAssertEqual(data2, receivedMessage2.data.mapValues { $0 as? String })
        
        receiveTask.cancel() // stop receiving onMessagePublished events
        _ = await stream.events.onSubscriptionStateChanged.first { $0 == .unsubscribed }
                
        try await client2.streams.remove(sidOrUniqueName: stream.sid)
        
        // onRemoved replays last event. So we don't have to start listen it in advance before call streams.remove()
        _ = await stream.events.onRemoved.first { $0 === stream }
    }
}
