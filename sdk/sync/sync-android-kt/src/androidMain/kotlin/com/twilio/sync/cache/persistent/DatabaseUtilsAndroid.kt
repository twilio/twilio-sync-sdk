//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache.persistent

import com.twilio.util.ApplicationContextHolder
import com.twilio.util.TwilioLogger
import kotlinx.datetime.Instant

private val logger = TwilioLogger.getLogger("DatabaseUtils")

internal actual fun deleteSyncDatabase(name: String) {
    require(name.startsWith(kSyncDatabasePrefix))

    val context = ApplicationContextHolder.applicationContext
    context.deleteDatabase(name)
}

internal actual fun getDatabaseList(): List<String> {
    val context = ApplicationContextHolder.applicationContext
    return context.databaseList()
        .filter { it.startsWith(kSyncDatabasePrefix) }
        .onEach { logger.d { "getDatabaseList: $it [${getDatabaseSize(it)}]"} }
        .filterNot { it.endsWith("-journal") }
        .toList()
}

internal actual fun getDatabaseSize(name: String): Long {
    require(name.startsWith(kSyncDatabasePrefix))

    val context = ApplicationContextHolder.applicationContext
    val file = context.getDatabasePath(name)
    return file.length()
}

internal actual fun getDatabaseLastModified(name: String): Instant {
    require(name.startsWith(kSyncDatabasePrefix))

    val context = ApplicationContextHolder.applicationContext
    val file = context.getDatabasePath(name)
    return Instant.fromEpochMilliseconds(file.lastModified())
}
