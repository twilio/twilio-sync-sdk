//
//  requestToken.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 24.01.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import Foundation

extension String: Error {}

func requestToken(identity: String = "kotlin-ios-test", ttl: Int = 3600) async throws -> String {
    let tokenUrl = "\(SYNC_ACCESS_TOKEN_SERVICE_URL)&identity=\(identity)&ttl=\(ttl)"
    
    let (data, response) = try await URLSession.shared.data(from: URL(string: tokenUrl)!)
    
    guard let urlResponse = response as? HTTPURLResponse,
          urlResponse.statusCode / 100 == 2,
          let token = String(data: data, encoding: .utf8) else {
        throw "Failed to get token with response \(response)"
    }

    return token
}
