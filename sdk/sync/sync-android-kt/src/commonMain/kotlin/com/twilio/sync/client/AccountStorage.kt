//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client

import com.twilio.util.AccountDescriptor
import com.twilio.util.json
import com.twilio.util.stringPreference
import kotlinx.serialization.encodeToString

internal class AccountStorage {
    val isEmpty: Boolean get() = serializedAccount.isEmpty()

    var account: AccountDescriptor
        get() = json.decodeFromString(serializedAccount) // throws exception when isEmpty == true
        set(value) { serializedAccount = json.encodeToString(value) }

    private var serializedAccount by stringPreference()

    fun clear() {
        serializedAccount = ""
    }
}
