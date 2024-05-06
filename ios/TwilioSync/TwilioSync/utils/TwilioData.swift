//
//  Types.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 31.01.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import Foundation
import TwilioSyncLib

/// Representation of a data item.
public typealias TwilioData = [String: Any?]

typealias KotlinData = [String : Kotlinx_serialization_jsonJsonElement]

extension TwilioData {
    
    init (_ jsonString: String) throws {
        guard let jsonData = jsonString.data(using: .utf8) else {
            throw TwilioError(reason: .cannotParse, message: "Error converting string to data")
        }
  
        guard let json = try JSONSerialization.jsonObject(with: jsonData) as? TwilioData else {
            KotlinLogger("KotlinData.toTwilioData").e { "Error converting KotlinData to TwilioData: \(jsonString)" }
            throw TwilioError(reason: .cannotParse, message: "Error converting KotlinData to TwilioData")
        }
        
        self = json
    }
    
    func toJsonString() throws -> String {
        let jsonData = try JSONSerialization.data(withJSONObject: self,
                                                  options: [.withoutEscapingSlashes,
                                                            .sortedKeys])
        
        return String(data: jsonData, encoding: .utf8)!
    }
}

extension KotlinData {
    
    func toTwilioData() throws -> TwilioData {
        let jsonString = try kotlinCallSync {
            try KotlinJsonKt.jsonToString(jsonMap: self)
        }
                
        return try TwilioData(jsonString)
    }
}
