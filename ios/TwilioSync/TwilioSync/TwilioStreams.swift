//
//  TwilioStreams.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 30.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/// `TwilioStreams` provides methods for creating, opening, and managing ``TwilioSyncStream`` objects.
public class TwilioStreams {
    
    private let streams: Streams
    
    init(_ streams: Streams) {
        self.streams = streams
    }
    
    /// Creates a new ``TwilioSyncStream`` object.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to assign to new ``TwilioSyncStream`` upon creation. Default is `nil`.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while stream creation.
    /// - Returns: The created ``TwilioSyncStream``.
    public func create(uniqueName: String? = nil, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncStream {
        let kotlinStream = try await kotlinCall {
            try await streams.create(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncStream(kotlinStream)
    }
    
    /// Opens an existing ``TwilioSyncStream`` by unique name or creates a new one if the specified name does not exist.
    ///
    /// - Parameters:
    ///   - uniqueName: Unique name to find existing stream or to assign to new stream upon creation.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry. Default is `TimeInterval.infinity`.
    /// - Throws: ``TwilioError`` when an error occurred while stream opening or creation.
    /// - Returns: The opened or created ``TwilioSyncStream``
    public func openOrCreate(uniqueName: String, ttl: TimeInterval = TimeInterval.infinity) async throws -> TwilioSyncStream {
        let kotlinStream = try await kotlinCall {
            try await streams.openOrCreate(uniqueName: uniqueName, ttl: ttl.toKotlinDuration())
        }
        
        return TwilioSyncStream(kotlinStream)
    }
    
    /// Opens an existing ``TwilioSyncStream`` by SID or unique name.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name to find existing ``TwilioSyncStream``.
    /// - Throws: ``TwilioError`` when an error occurred while stream opening.
    /// - Returns: The opened ``TwilioSyncStream``
    public func openExisting(sidOrUniqueName: String) async throws -> TwilioSyncStream {
        let kotlinStream = try await kotlinCall {
            try await streams.openExisting(sidOrUniqueName: sidOrUniqueName)
        }
        
        return TwilioSyncStream(kotlinStream)
    }
    
    /// Sets time to live for ``TwilioSyncStream`` without opening it.
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
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncStream``.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating ttl.
    public func setTtl(sidOrUniqueName: String, ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await streams.setTtl(sidOrUniqueName: sidOrUniqueName, ttl: ttl.toKotlinDuration())
        }
    }
    
    /// Publishes a new message to ``TwilioSyncStream`` without opening it.
    ///
    /// - Parameters:
    ///   - sidOrUniqueName: SID or unique name of existing ``TwilioSyncStream``.
    ///   - data: Contains the payload of the dispatched message. Maximum size in serialized JSON: 4KB.
    /// - Throws: ``TwilioError`` when an error occurred while publishing message.
    /// - Returns: The published ``TwilioSyncStream/Message``.
    public func publishMessage(sidOrUniqueName: String, data: TwilioData) async throws -> TwilioSyncStream.Message {
        let kotlinMessage = try await kotlinCall {
            try await streams.publishMessage(sidOrUniqueName: sidOrUniqueName, jsonData: data.toJsonString())
        }
        
        return try kotlinMessage.toTwilioSyncStreamMessage()
    }
    
    /// Removes ``TwilioSyncStream`` without opening it.
    ///
    /// - Parameter sidOrUniqueName: SID or unique name of existing ``TwilioSyncStream``.
    /// - Throws: ``TwilioError`` when an error occurred while removing the stream.
    public func remove(sidOrUniqueName: String) async throws {
        try await kotlinCall {
            try await streams.remove(sidOrUniqueName: sidOrUniqueName)
        }
    }
}
