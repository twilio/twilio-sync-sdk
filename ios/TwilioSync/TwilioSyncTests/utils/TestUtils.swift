//
//  TestUtils.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 31.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import XCTest

func generateRandomString(prefix: String = "randomString") -> String {
    let randomNumber = Int.random(in: 1..<1_000_000)
    return "\(prefix)\(randomNumber)"
}

func waitAndAssertTrue(
    timeout: TimeInterval = 10,
    predicate: () async -> Bool,
    file: StaticString = #filePath,
    line: UInt = #line
) async throws {
    let start = Date()
    while !(await predicate()) {
        if Date().timeIntervalSince(start) > timeout {
            XCTFail("Timed out waiting for condition to be true", file: file, line: line)
            return
        }
        try await Task.sleep(nanoseconds: UInt64(0.1 * 1_000_000_000)) // 100ms
    }
}
