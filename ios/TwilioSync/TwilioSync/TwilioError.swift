//
//  TwilioError.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 30.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/// Representation of an Twilio Error object.
///
/// This error encapsulates information about an Twilio SDK error and is throwed from SDK functions.
public struct TwilioError: Error, Hashable, CustomStringConvertible {
    
    /// Error category as a ``TwilioErrorReason``.
    public let reason: TwilioErrorReason
    
    /// Error category as a classifier. Local client errors get status 0, network related errors have their HTTP error code as a status.
    public let status: Int
    
    /// Unique twilio code for this error.
    ///
    /// - SeeAlso: [Error and Warning Dictionary](https://www.twilio.com/docs/api/errors)
    public let code: Int
    
    /// Human readable description for the ``status`` property.
    public let message: String
    
    /// Human readable description for the ``code`` property.
    public let errorDescription: String
    
    init(
        reason: TwilioErrorReason = .unknown,
        status: Int = 0,
        code: Int = 0,
        message: String = "",
        codeDescription: String = ""
    ) {
        self.reason = reason
        self.status = status
        self.code = code
        self.message = message
        self.errorDescription = codeDescription
    }
    
    init(errorInfo: Shared_publicErrorInfo) {
        self.reason = errorInfo.reason.toTwilioErrorReason()
        self.status = Int(errorInfo.status)
        self.code = Int(errorInfo.code)
        self.message = errorInfo.message
        self.errorDescription = errorInfo.description_
    }

    public var description: String {
        "TwilioError(reason: \(reason), status: \(status), code: \(code), message: \(message), errorDescription: \(errorDescription))"
    }

    public static func == (lhs: TwilioError, rhs: TwilioError) -> Bool {
        return lhs.reason == rhs.reason
            && lhs.status == rhs.status
            && lhs.code == rhs.code
            && lhs.message == rhs.message
            && lhs.errorDescription == rhs.errorDescription
    }
}

/// Human readable error reason.
public enum TwilioErrorReason: Int, CustomStringConvertible {
    case unknown = 0
    case cancelled = 1
    case timeout = 2
    case transportDisconnected = 8
    case networkBecameUnreachable = 9
    case unauthorized = 10
    case tokenExpired = 11
    case tokenUpdatedLocally = 12
    case tooManyRequests = 13
    case hostnameUnverified = 14
    case sslHandshakeError = 15
    case closeMessageReceived = 16
    case cannotParse = 17
    case retrierReachedMaxAttempsCount = 18
    case retrierReachedMaxTime = 19
    case commandRecoverableError = 21
    case commandPermanentError = 22
    case createClientError = 25
    case updateTokenError = 26
    case openStreamError = 27
    case openDocumentError = 28
    case openCollectionError = 29
    case mutateOperationAborted = 30
    case mutateCollectionItemNotFound = 31
    case mismatchedLastUserAccount = 32
    case clientShutdown = 33
    case iteratorError = 34
    
    public var description: String {
        kotlinErrorReasons[self.rawValue].map { $0.description_ } ?? "Unknown TwilioErrorReason"
    }
}

private let kotlinErrorReasons: [Int: Shared_publicErrorReason] =
    Shared_publicErrorReason.allCases.reduce(into: [Int: Shared_publicErrorReason]()) { $0[Int($1.value)] = $1 }

extension Shared_publicErrorReason {
    
    func toTwilioErrorReason() -> TwilioErrorReason {
        return TwilioErrorReason(rawValue: Int(value)) ?? .unknown
    }
}

func kotlinCall<T>(
    file: StaticString = #file,
    line: UInt = #line,
    block: () async throws -> T
) async throws -> T {
    do {
        return try await block()
    } catch let error as CancellationError {
        KotlinLogger("kotlinCall").w { "CancellationError in \(file) at line \(line): \(error)" }
        throw TwilioError(reason: .cancelled, message: "CancellationError: \(error)")
    } catch let error as NSError {
        KotlinLogger("kotlinCall").w { "NSError in \(file) at line \(line): \(error)" }
        
        switch error.kotlinException {
            
        case let twilioException as Shared_publicTwilioException:
            throw TwilioError(errorInfo: twilioException.errorInfo)
            
        case _ as KotlinCancellationException:
            throw TwilioError(reason: .cancelled)
            
        default:
            throw TwilioError(reason: .unknown, message: "\(error)")
        }
    }
}

func kotlinCallSync<T>(
    file: StaticString = #file,
    line: UInt = #line,
    block: () throws -> T
) throws -> T {
    do {
        return try block()
    } catch let error as CancellationError {
        KotlinLogger("kotlinCallSync").w { "CancellationError in \(file) at line \(line): \(error)" }
        throw TwilioError(reason: .cancelled, message: "CancellationError: \(error)")
    } catch let error as NSError {
        KotlinLogger("kotlinCallSync").w { "NSError in \(file) at line \(line): \(error)" }
        
        switch error.kotlinException {
            
        case let twilioException as Shared_publicTwilioException:
            throw TwilioError(errorInfo: twilioException.errorInfo)
            
        case _ as KotlinCancellationException:
            throw TwilioError(reason: .cancelled)
            
        default:
            throw TwilioError(reason: .unknown, message: "\(error)")
        }
    }
}
