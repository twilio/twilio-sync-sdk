//
//  TwilioSyncIterator.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 11.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/**
 * An iterator over a collection. Allows to sequentially access the elements.
 *
 * Instances of ``TwilioSyncIterator``  are *not thread-safe* and shall not be used
 * from concurrent threads..
 *
 * **Example 1:**
 *
 * ```swift
 *    let iterator = map.queryItems()
 *
 *    while let item = try await iterator.next() {
 *        print("\(item.key): \(item.data)")
 *    }
 * ```
 *
 * **Example 2:**
 *
 * ```swift
 *    let stream = map.queryItems().asAsyncThrowingStream()
 *
 *    for try await item in stream {
 *        print("\(item.key): \(item.data)")
 *    }
 * ```
 *
 * **Example 3:**
 *
 * ```swift
 *    try await map.queryItems().forEach { item in
 *        print("\(item.key): \(item.data)")
 *    }
 * ```
 */
public class TwilioSyncIterator<T> : AsyncIteratorProtocol {
    
    private let kotlinIterator: SyncIterator
    
    private let transform: (Any?) throws -> T
    
    init(_ kotlinIterator: SyncIterator, transform: @escaping (_ kotlinElement: Any?) throws -> T) {
        self.kotlinIterator = kotlinIterator
        self.transform = transform
    }
    
    deinit {
        kotlinIterator.close()
    }
    
    public func next() async throws -> T? {
        try Task.checkCancellation()

        return try await kotlinCall {
            if (try await kotlinIterator.hasNext().boolValue) {
                return try transform(kotlinIterator.next())
            }
            
            return nil
        }
    }
}

extension TwilioSyncIterator {
    
    func asAsyncThrowingStream() -> AsyncThrowingStream<Element, Error> {
        return self.kotlinIterator.asFlow().asAsyncThrowingStream { try self.transform($0 as Any?) }
    }
    
    func forEach(block: (Element) -> Void) async throws {
        while let item = try await next() {
            block(item)
        }
    }
}
