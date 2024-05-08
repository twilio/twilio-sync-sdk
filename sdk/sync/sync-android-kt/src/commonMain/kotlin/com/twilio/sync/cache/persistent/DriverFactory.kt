//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache.persistent

import com.squareup.sqldelight.db.SqlDriver
import com.twilio.util.AccountDescriptor

internal expect class DriverFactory() {
    fun createDriver(accountDescriptor: AccountDescriptor, isInMemoryDatabase: Boolean): SqlDriver
}
