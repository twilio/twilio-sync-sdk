//
//  TwilioSyncDocument.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 06.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/**
 * `TwilioSyncDocument` is an arbitrary ``TwilioData`` value.
 *
 * You can set, get and modify this value.
 *
 * To obtain an instance of a `TwilioSyncDocument` use use ``TwilioSyncClient/documents``.
 */
public class TwilioSyncDocument {

    private let kotlinDocument: SyncDocument

    init(_ kotlinDocument: SyncDocument) {
        self.kotlinDocument = kotlinDocument
    }
    
    deinit {
        close()
    }

    /// An immutable system-assigned identifier of this ``TwilioSyncDocument``.
    public var sid: String { kotlinDocument.sid }

    /// An optional unique name for this document, assigned at creation time.
    public var uniqueName: String? { kotlinDocument.uniqueName }

    /// Current subscription state.
    public var subscriptionState: TwilioSubscriptionState { kotlinDocument.subscriptionState.toTwilioSubscriptionState() }

    /// Value of the document as a ``TwilioData`` object.
    public var data: TwilioData { (try? kotlinDocument.data.toTwilioData()) ?? TwilioData() }

    /// A date when this ``TwilioSyncDocument`` was created.
    public var dateCreated: Date { kotlinDocument.dateCreated.toSwiftDate() }

    /// A date when this ``TwilioSyncDocument`` was last updated.
    public var dateUpdated: Date { kotlinDocument.dateUpdated.toSwiftDate() }

    /// A date this ``TwilioSyncDocument`` will expire, `nil` means will not expire.
    public var dateExpires: Date? { kotlinDocument.dateExpires?.toSwiftDate() }

    /// `true` when this ``TwilioSyncDocument`` has been removed on the backend, `false` otherwise.
    public var isRemoved: Bool { kotlinDocument.isRemoved }

    /// `true` when this ``TwilioSyncDocument`` is offline and doesn't receive updates from backend, `false` otherwise.
    public var isFromCache: Bool { kotlinDocument.isFromCache }

    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public var events: Events { Events(self) }

    /// Set time to live for this ``TwilioSyncDocument``.
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
    public func setTtl(_ ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await kotlinDocument.setTtl(ttl: ttl.toKotlinDuration())
        }
    }

    /// Updates the data of this ``TwilioSyncDocument``.
    ///
    /// - Parameter data: The new data for the document.
    /// - Throws: ``TwilioError`` when an error occurred while updating the document.
    public func setData(_ data: TwilioData) async throws {
        try await kotlinCall {
            try await kotlinDocument.setData(jsonData: data.toJsonString())
        }
    }

    /// Updates the data of this ``TwilioSyncDocument`` and sets time to live for the document.
    ///
    /// - Parameters:
    ///   - data: The new data for the document.
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    /// - Throws: ``TwilioError`` when an error occurred while updating the document.
    public func setData(_ data: TwilioData, ttl: TimeInterval) async throws {
        try await kotlinCall {
            try await kotlinDocument.setDataWithTtl(jsonData: data.toJsonString(), ttl: ttl.toKotlinDuration())
        }
    }

    /// Mutates the data of an existing ``TwilioSyncDocument`` using provided Mutator function.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest document data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    try await document.mutateData { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameter mutator: The closure that will be called with the current data of the document and
    /// should return the new data for the document.
    ///
    /// - Throws: ``TwilioError`` when an error occurred while mutating the document.
    public func mutateData(mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?) async throws {
        try await kotlinCall {
            try await kotlinDocument.mutateData(mutator: mutatorAdapter(mutator))
        }
    }

    /// Mutates the data of an existing ``TwilioSyncDocument`` using provided Mutator function and
    /// sets time to live for the document.
    ///
    /// The Mutator function could be invoked more than once in case of data collision, i.e. if data on backend
    /// is modified while the mutate operation is executing. It guarantees that Mutator generates new value based
    /// on latest document data.
    ///
    /// Possible use case is to implement distributed counter:
    ///
    /// ```swift
    ///    try await document.mutateDataWithTtl(3600) { counter in
    ///        let value = counter["value"] as? Int ?? 0
    ///        return [ "value" : value + 1 ]
    ///    }
    /// ```
    ///
    /// - Parameters:
    ///   - ttl: Time to live from now or `TimeInterval.infinity` to indicate no expiry.
    ///   - mutator: The closure that will be called with the current data of the document and
    /// should return the new data for the document.
    ///
    /// - Throws: ``TwilioError`` when an error occurred while mutating the document.
    public func mutateDataWithTtl(_ ttl: TimeInterval, mutator: @escaping (_ currentData: TwilioData) async throws -> TwilioData?) async throws {
        try await kotlinCall {
            try await kotlinDocument.mutateDataWithTtl(ttl: ttl.toKotlinDuration(), mutator: mutatorAdapter(mutator))
        }
    }

    /// Removes this ``TwilioSyncDocument``.
    ///
    /// - Throws: ``TwilioError`` when an error occurred while removing the document.
    public func removeDocument() async throws {
        try await kotlinCall {
            try await kotlinDocument.removeDocument()
        }
    }

    /// Close this ``TwilioSyncDocument``.
    ///
    /// After closing ``TwilioSyncDocument`` stops emitting events.
    /// Call this method to cleanup resources when finish using this ``TwilioSyncDocument`` object.
    public func close() {
        kotlinDocument.close()
    }

    /// Provides scope of ``AsyncSequence`` objects to get notified about events.
    public struct Events {

        /// Emits when ``TwilioSyncDocument`` subscription state has changed.
        public var onSubscriptionStateChanged: AsyncStream<TwilioSubscriptionState> {
            kotlinEvents.onSubscriptionStateChanged.asAsyncStream { $0?.toTwilioSubscriptionState() }
        }

        /// Emits when the ``TwilioSyncDocument`` has been updated.
        public var onUpdated: AsyncStream<TwilioSyncDocument> {
            kotlinEvents.onUpdated.asAsyncStream { _ in syncDocument }
        }

        /// Emits when the ``TwilioSyncDocument`` has been removed.
        public var onRemoved: AsyncStream<TwilioSyncDocument> {
            kotlinEvents.onRemoved.asAsyncStream { _ in syncDocument }
        }

        private let syncDocument: TwilioSyncDocument
        
        private let kotlinEvents: SyncDocumentEvents
        
        init(_ syncDocument: TwilioSyncDocument) {
            self.syncDocument = syncDocument
            self.kotlinEvents = syncDocument.kotlinDocument.events
        }
    }
}
