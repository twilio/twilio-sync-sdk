//
//  MutatorAdapters.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 06.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

// currentData is always not nil. Used for documents.
func mutatorAdapter(_ mutator: @escaping (TwilioData) async throws -> TwilioData?) -> (String) -> String? {
    let adapter = { currentData in
        do {
            let oldData = try TwilioData(currentData)
            let newData = try Task.synchronous { try await mutator(oldData) }
            return try newData?.toJsonString()
        } catch {
            KotlinLogger("mutatorAdapter").w { "Exception in mutator: \(error)" }
            return nil
        }
    }
    
    return adapter
}

// currentData can be nil. Used for collection items.
func mutatorAdapter(_ mutator: @escaping (TwilioData?) async throws -> TwilioData?) -> (String?) -> String? {
    let adapter = { currentData in
        do {
            let oldData = currentData != nil ? try TwilioData(currentData!) : nil
            let newData = try Task.synchronous { try await mutator(oldData) }
            return try newData?.toJsonString()
        } catch {
            KotlinLogger("mutatorAdapter").w { "Exception in mutator: \(error)" }
            return nil
        }
    }
    
    return adapter
}
