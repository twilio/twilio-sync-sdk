//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache.persistent

import com.twilio.sync.utils.createAccountDescriptor
import com.twilio.sync.utils.syncInstanceSid
import com.twilio.util.AccountDescriptor
import kotlinx.datetime.Instant

internal val kSyncDatabasePrefix = "sync-"

internal val AccountDescriptor.databaseName: String
    get() = "$kSyncDatabasePrefix$identity-$syncInstanceSid-$accountSid.db"

internal fun AccountDescriptor.Companion.fromDatabaseName(name: String): AccountDescriptor {
    require(name.startsWith(kSyncDatabasePrefix))

    val index = name.lastIndexOf('.')
    val fileNameWithoutExtension = if (index != -1) name.substring(0, index) else name

    val parts = fileNameWithoutExtension
        .removePrefix(kSyncDatabasePrefix)
        .split("-")

    check(parts.size >= 3) { "Cannot parse AccountDescriptor.fromDatabaseName $name: parts.size: ${parts.size}" }

    return AccountDescriptor.createAccountDescriptor(
        accountSid = parts[parts.size - 1],
        syncInstanceSid = parts[parts.size - 2],
        identity = parts.subList(0, parts.size - 2).joinToString("-")
    )
}

internal expect fun deleteSyncDatabase(name: String)

internal expect fun getDatabaseList(): List<String>

internal expect fun getDatabaseSize(name: String): Long

internal expect fun getDatabaseLastModified(name: String): Instant
