//
//  TwilioAccountDescriptor.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 30.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import TwilioSyncLib

/// Represents account information for a user.
public struct TwilioAccountDescriptor {
    
    /// Twilio account SID.
    public let accountSid: String
    
    /// Map of Twilio grants (i.e. "data\_sync", "ip\_messaging" etc.) to Twilio service SIDs.
    public let instanceSids: [String: String]
    
    /// User identity.
    public let identity: String
}

extension TwilioAccountDescriptor {
    
    func toKotlinAccountDescriptor() -> Shared_publicAccountDescriptor {
        return Shared_publicAccountDescriptor(accountSid: accountSid, instanceSids: instanceSids, identity: identity)
    }
}
