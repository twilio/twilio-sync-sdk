//
//  TwilioSyncClientTests.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 23.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import XCTest
@testable import TwilioSync

final class TwilioSyncClientTests: XCTestCase {

    override func setUpWithError() throws {
        TwilioSyncClient.setLogLevel(level: .verbose)
        TwilioSyncClient.clearAllCaches()
    }

    func testCreateShutdown() async throws {
        let client = try await TwilioSyncClient.create { try await requestToken() }
        client.shutdown()
    }

    func testCreateWithInvalidToken() async {
        let expectedError = TwilioError(
            reason: .unauthorized,
            status: 401,
            code: 20101,
            message: "UNAUTHORIZED",
            codeDescription: "Invalid Access Token"
        )
        
        await assertThrowsTwilioError(try await TwilioSyncClient.create { "INVALID_TOKEN" }) { error in
            XCTAssertEqual(expectedError, error)
        }
    }
    
    func testCreateDocumentAfterShutdown() async throws {
        let client = try await TwilioSyncClient.create { try await requestToken() }
        client.shutdown()

        await assertThrowsTwilioError(try await client.documents.create()) { error in
            XCTAssertEqual(.clientShutdown, error.reason)
        }
    }
}
