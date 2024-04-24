//
//  TwilioSubscriptionState.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 31.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/**
 Represents the state of a subscription for sync entities,
 such as ``TwilioSyncDocument``, ``TwilioSyncList``, ``TwilioSyncMap`` and ``TwilioSyncStream``.
 */
public enum TwilioSubscriptionState : Hashable {
    
    /// The initial state, when no one listens for events from sync entity.
    case unsubscribed
    
    /// Sync entity has subscribers, but a subscription request hasn't been made yet.
    case pending
    
    /// The subscription request has been made but not yet acknowledged by the server.
    case subscribing
    
    /// The subscription has been successfully established.
    case established
    
    /// The subscription request has failed.
    case failed(TwilioError)
}

extension TwilioSyncLib.SubscriptionState {
    
    func toTwilioSubscriptionState() -> TwilioSubscriptionState {
        switch onEnum(of: self) {
        case .unsubscribed: return .unsubscribed
        case .pending: return .pending
        case .subscribing: return .subscribing
        case .established: return .established
        case .failed(let failed): return .failed(TwilioError(errorInfo: failed.errorInfo))
        }
    }
}
