//
//  TwilioQueryOrder.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 12.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

public enum TwilioQueryOrder {
    case ascending, descending
}

extension TwilioQueryOrder {
    
    func toKotlinQueryOrder() -> Sync_android_sharedQueryOrder {
        switch self {
        case .ascending: return Sync_android_sharedQueryOrder.ascending
        case .descending: return Sync_android_sharedQueryOrder.descending
        }
    }
}
