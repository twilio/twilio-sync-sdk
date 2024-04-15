//
//  TwilioLogLevel.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 30.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/// Log level constants.
public enum TwilioLogLevel {

    /// Show low-level tracing messages as well as all Debug log messages.
    case verbose

    /// Show low-level debugging messages as well as all Info log messages.
    case debug

    /// Show informational messages as well as all Warning log messages.
    case info

    /// Show warnings as well as all Critical log messages.
    case warn

    /// Show critical log messages as well as all Fatal log messages.
    case error

    /// Show fatal errors only.
    case assert

    /// Show no log messages. This is default LogLevel.
    case silent
}

extension TwilioLogLevel {
    
    func toKotlinLogLevel() -> Sync_android_sharedLogLevel {
        switch self {
        case .verbose: return Sync_android_sharedLogLevel.verbose
        case .debug: return Sync_android_sharedLogLevel.debug
        case .info: return Sync_android_sharedLogLevel.info
        case .warn: return Sync_android_sharedLogLevel.warn
        case .error: return Sync_android_sharedLogLevel.error
        case .assert: return Sync_android_sharedLogLevel.assert
        case .silent: return Sync_android_sharedLogLevel.silent
        }
    }
}
