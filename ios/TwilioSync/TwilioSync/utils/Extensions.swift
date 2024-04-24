//
//  Extensions.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 24.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

extension Task where Success == Never, Failure == Error {

    private class Enclosure<T> {
        var result: Result<T, Error>?
    }
    
    static internal func synchronous<T>(priority: TaskPriority? = nil, operation: @escaping () async throws -> T) throws -> T {
        
        let semaphore = DispatchSemaphore(value: 0)
        let enclosure = Enclosure<T>()

        Task<Void, Never>(priority: priority) {
            do {
                enclosure.result = .success(try await operation())
            } catch {
                enclosure.result = .failure(error)
            }
            semaphore.signal()
        }

        semaphore.wait()
        
        return try enclosure.result!.get()
    }
}

extension TimeInterval {
    
    func toKotlinDuration() -> Int64 {
        switch self {
        case TimeInterval.zero: return KotlinDuration.shared.ZERO
        case TimeInterval.infinity: return KotlinDuration.shared.INFINITE
        default: return KotlinDuration.shared.fromMills(Int64(self * 1000))
        }
    }
}

extension Kotlinx_datetimeInstant {
    
    func toSwiftDate() -> Date {
        Date(timeIntervalSince1970: TimeInterval(self.toEpochMilliseconds()) / 1000)
    }
}
