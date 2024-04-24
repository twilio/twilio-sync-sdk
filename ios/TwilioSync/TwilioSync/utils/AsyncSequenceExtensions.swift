//
//  AsyncSequenceExtensions.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 06.02.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation

fileprivate extension AsyncStream {
    init<Base: AsyncSequence>(from sequence: Base, file: StaticString = #file, line: UInt = #line, transform: @escaping (Base.Element?) async -> Element?) {
        var iterator = sequence.makeAsyncIterator()
        // FIXME: In later swift versions, AsyncSequence protocol will likely have an associated error type.
        // FIXME: For now, produce an assertionFailure to let developer know to use an AsyncThrowingStream instead.
        self.init {
            do {
                guard let next = try await iterator.next() else { return nil }
                return await transform(next)
            } catch {
                assertionFailure("AsyncSequence threw \(error.localizedDescription). Use AsyncThrowingStream instead", file: file, line: line)
                return nil
            }
        }
    }
}

fileprivate extension AsyncThrowingStream {
    init<Base: AsyncSequence>(from sequence: Base, transform: @escaping (Base.Element?) async throws -> Element?) where Failure == Error {
        var iterator = sequence.makeAsyncIterator()
        self.init {
            guard let next = try await iterator.next() else { return nil }
            return try await transform(next)
        }
    }
}

extension AsyncSequence {
    func asAsyncStream<T>(file: StaticString = #file, line: UInt = #line, transform: @escaping (Element?) async -> T?) -> AsyncStream<T> {
        AsyncStream(from: self, file: file, line: line, transform: transform)
    }
    
    func asAsyncThrowingStream<T>(transform: @escaping (Element?) async throws -> T?) -> AsyncThrowingStream<T, Error> {
        AsyncThrowingStream(from: self, transform: transform)
    }
}
