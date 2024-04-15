//
//  TwilioDocuments.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 07.03.2024.
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//

import Foundation
import TwilioSyncLib

/// `TwilioDocuments` provides methods for creating, opening, and managing ``TwilioSyncDocument`` objects.
public class TwilioDocuments {
    
    private let documents: Documents
    
    init(_ documents: Documents) {
        self.documents = documents
    }
    
    /// Creates a new ``TwilioSyncDocument`` object.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to assign to new ``TwilioSyncDocument`` upon creation. Default is `nil`.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while document creation.
    /// - Returns: The created ``TwilioSyncDocument``.
    public func create(uniqueName: String? = nil, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncDocument {
        let kotlinDocument = try await kotlinCall {
            try await documents.create(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncDocument(kotlinDocument)
    }
    
    /// Opens an existing ``TwilioSyncDocument`` by unique name or creates a new one if the specified name does not exist.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to find existing ``TwilioSyncDocument`` or to assign to new ``TwilioSyncDocument`` upon creation.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while document opening or creation.
    /// - Returns: The opened or created ``TwilioSyncDocument``
    public func openOrCreate(uniqueName: String, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncDocument {
        let kotlinDocument = try await kotlinCall {
            try await documents.openOrCreate(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncDocument(kotlinDocument)
    }
    
    /// Opens an existing ``TwilioSyncDocument`` by SID or unique name.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name to find existing ``TwilioSyncDocument``.
    /// - Throws: ``TwilioError`` when an error occurred while document opening.
    /// - Returns: The opened ``TwilioSyncDocument``
    public func openExisting(sidOrUniqueName: String) async throws -> TwilioSyncDocument {
        let kotlinDocument = try await kotlinCall {
            try await documents.openExisting(sidOrUniqueName: sidOrUniqueName)
        }
        
        return TwilioSyncDocument(kotlinDocument)
    }
    
    /// Sets time to live for ``TwilioSyncDocument`` without opening it.
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
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncDocument``.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating ttl.
    public func setTtl(sidOrUniqueName: String, ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await documents.setTtl(sidOrUniqueName: sidOrUniqueName, ttl: ttl.toKotlinDuration())
        }
    }

    /// Updates the data of an existing ``TwilioSyncDocument`` without opening it.
    ///
    /// - Parameters:
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncDocument``.
    ///   - data: The new data for the document.
    /// - Throws: ``TwilioError`` when an error occurred while updating the document.
    public func update(sidOrUniqueName: String, data: TwilioData) async throws {
        try await kotlinCall {
            try await documents.updateDocument(sidOrUniqueName: sidOrUniqueName, jsonData: data.toJsonString())
        }
    }

    /// Updates the data of an existing ``TwilioSyncDocument`` without opening it and sets time to live for the document.
    ///
    /// - Parameters:
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncDocument``.
    ///   - data: The new data for the document.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating the document.
    public func updateWithTtl(sidOrUniqueName: String, data: TwilioData, ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await documents.updateDocumentWithTtl(sidOrUniqueName: sidOrUniqueName, jsonData: data.toJsonString(), ttl: ttl.toKotlinDuration())
        }
    }

    /// Mutates the data of an existing ``TwilioSyncDocument`` without opening it using provided Mutator function.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest document data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    try await syncClient.documents.mutateDocument("counter") { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncDocument``.
    ///   - mutator: The closure that will be called with the current data of the document and should return the new data for the document.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the document.
    /// - Returns: New value of the ``TwilioSyncDocument`` as a ``TwilioData`` object.
    public func mutate(
        sidOrUniqueName: String,
        mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?
    ) async throws -> TwilioData {
        let kotlinData = try await kotlinCall {
            try await documents.mutateDocument(sidOrUniqueName: sidOrUniqueName, mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinData.toTwilioData()
    }

    /// Mutates the data of an existing ``TwilioSyncDocument`` without opening it
    /// using provided Mutator function and sets time to live for the document.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest document data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    try await syncClient.documents.mutateDocumentWithTtl("counter", 3600) { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncDocument``.
    ///   - mutator: The closure that will be called with the current data of the document and should return the new data for the document.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while mutating the document.
    /// - Returns: New value of the ``TwilioSyncDocument`` as a ``TwilioData`` object.
    public func mutateWithTtl(
        sidOrUniqueName: String,
        ttl: TimeInterval,
        mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?
    ) async throws -> TwilioData {
        let kotlinData = try await kotlinCall {
            try await documents.mutateDocumentWithTtl(sidOrUniqueName: sidOrUniqueName, ttl: ttl.toKotlinDuration(), mutator: mutatorAdapter(mutator))
        }
        
        return try kotlinData.toTwilioData()
    }

    /// Removes an existing ``TwilioSyncDocument`` by SID or unique name.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name of existing ``TwilioSyncDocument``.
    /// - Throws: ``TwilioError`` when an error occurred while removing the document.
    public func remove(sidOrUniqueName: String) async throws {
        try await kotlinCall {
            try await documents.remove(sidOrUniqueName: sidOrUniqueName)
        }
    }
}
