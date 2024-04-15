//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client

import com.twilio.util.stringPreference

interface ContinuationTokenStorage {
    var continuationToken: String
}

internal class ContinuationTokenStorageImpl() : ContinuationTokenStorage {
    override var continuationToken: String by stringPreference()
}
