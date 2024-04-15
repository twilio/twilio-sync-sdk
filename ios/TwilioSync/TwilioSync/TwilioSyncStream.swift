//
//  TwilioSyncStream.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 30.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/**
 * Sync Pub-sub messaging primitive.
 *
 * Message Stream is a Sync primitive for real-time pub-sub messaging.
 * Stream Messages are not persisted, they exist only in transit, and will be dropped
 * if (due to congestion or network anomalies) they cannot be delivered promptly.
 *
 * You can publish ``TwilioSyncStream/Message``s and listen for incoming ``TwilioSyncStream/Message``s.
 *
 * To obtain an instance of a `TwilioSyncStream` use ``TwilioSyncClient/streams``.
 */
public class TwilioSyncStream {
    
    private let kotlinStream: SyncStream

    init(_ kotlinStream: SyncStream) {
        self.kotlinStream = kotlinStream
    }
    
    deinit {
        close()
    }
    
    /// An immutable system-assigned identifier of this ``TwilioSyncStream``.
    public var sid: String { kotlinStream.sid }

    /// An optional unique name for this stream, assigned at creation time.
    public var uniqueName: String? { kotlinStream.uniqueName }

    /// Current subscription state.
    public var subscriptionState: TwilioSubscriptionState { kotlinStream.subscriptionState.toTwilioSubscriptionState() }

    /// A date this ``TwilioSyncStream`` will expire, `nil` means will not expire.
    public var dateExpires: Date? { kotlinStream.dateExpires?.toSwiftDate() }

    /// `true` when this ``TwilioSyncStream`` has been removed on the backend, `false` otherwise.
    public var isRemoved: Bool { kotlinStream.isRemoved }

    /// `true` when this ``TwilioSyncStream`` is offline and doesn't receive updates from backend, `false` otherwise.
    public var isFromCache: Bool { kotlinStream.isFromCache }

    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public var events: Events { Events(self) }

    /// Set time to live for this ``TwilioSyncStream``.
    ///
    /// This TTL specifies the minimum time the object will live,
    /// sometime soon after this time the object will be deleted.
    ///
    /// If time to live is not specified, object lives infinitely long.
    ///
    /// TTL could be used in order to auto-recycle old unused objects,
    /// but building app logic, like timers, using TTL is not recommended.
    ///
    /// - Parameter ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating ttl.
    public func setTtl(ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await kotlinStream.setTtl(ttl: ttl.toKotlinDuration())
        }
    }

    /// Publish a new message to this ``TwilioSyncStream``.
    ///
    /// - Parameter data: Contains the payload of the dispatched message. Maximum size in serialized JSON: 4KB.
    /// - Throws: ``TwilioError`` when an error occurred while publishing message.
    /// - Returns: The published ``Message``.
    public func publishMessage(data: TwilioData) async throws -> Message {
        let kotlinMessage = try await kotlinCall {
            try await kotlinStream.publishMessage(jsonData: data.toJsonString())
        }
        
        return try kotlinMessage.toTwilioSyncStreamMessage()
    }

    /// Remove this ``TwilioSyncStream``.
    ///
    /// - Throws: ``TwilioError`` when an error occurred while removing the stream.
    public func removeStream() async throws {
        try await kotlinCall {
            try await kotlinStream.removeStream()
        }
    }

    /// Close this ``TwilioSyncStream``.
    ///
    /// After closing ``TwilioSyncStream`` stops emitting events.
    /// Call this method to cleanup resources when finish using this ``TwilioSyncStream`` object.
    public func close() {
        kotlinStream.close()
    }
    
    /// Single message in a ``TwilioSyncStream``.
    public struct Message {

        /// An immutable system-assigned identifier of this ``Message``.
        public let sid: String

        /// Payload of this ``Message``. Maximum size in serialized JSON: 4KB.
        public let data: TwilioData
    }
    
    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public struct Events {
        
        /// Emits when ``TwilioSyncStream`` subscription state has changed.
        public var onSubscriptionStateChanged: AsyncStream<TwilioSubscriptionState> {
            kotlinEvents.onSubscriptionStateChanged.asAsyncStream { $0?.toTwilioSubscriptionState() }
        }

        /// Emits when ``TwilioSyncStream`` has successfully published a message.
        public var onMessagePublished: AsyncStream<Message> {
            kotlinEvents.onMessagePublished.asAsyncStream { try? $0?.toTwilioSyncStreamMessage() }
        }

        /// Emits when the ``TwilioSyncStream`` has been removed.
        public var onRemoved: AsyncStream<TwilioSyncStream> {
            kotlinEvents.onRemoved.asAsyncStream { _ in syncStream }
        }

        private let syncStream: TwilioSyncStream
        
        private let kotlinEvents: SyncStreamEvents
        
        init(_ syncStream: TwilioSyncStream) {
            self.syncStream = syncStream
            self.kotlinEvents = syncStream.kotlinStream.events
        }
    }
}

extension TwilioSyncLib.SyncStreamMessage {
    
    func toTwilioSyncStreamMessage() throws -> TwilioSyncStream.Message {
        let twilioData = try self.data.toTwilioData()
        return TwilioSyncStream.Message(sid: self.sid, data: twilioData)
    }
}
