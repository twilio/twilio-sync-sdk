//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client

import com.twilio.util.stringPreference

interface ContinuationTokenStorage {
    var continuationToken: String
}

internal class ContinuationTokenStorageImpl() : ContinuationTokenStorage {
    override var continuationToken: String by stringPreference()
}
