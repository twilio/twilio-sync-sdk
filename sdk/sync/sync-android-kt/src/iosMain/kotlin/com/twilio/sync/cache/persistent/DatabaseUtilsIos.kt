//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:OptIn(ExperimentalForeignApi::class)

package com.twilio.sync.cache.persistent

import co.touchlab.sqliter.DatabaseFileContext
import com.twilio.util.TwilioLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager

private val logger = TwilioLogger.getLogger("DatabaseUtils")

internal actual fun deleteSyncDatabase(name: String) {
    require(name.startsWith(kSyncDatabasePrefix))
    DatabaseFileContext.deleteDatabase(name, null)
}

internal actual fun getDatabaseList(): List<String> {
    val path = DatabaseFileContext.databasePath("", null)
    logger.d { "getDatabaseList path: $path" }

    val dirContent = NSFileManager.defaultManager().contentsOfDirectoryAtPath(path, null) ?: return emptyList()
    val pathList = List(dirContent.size) { i -> dirContent[i] as String }

    return pathList
        .filter { it.startsWith(kSyncDatabasePrefix) && it.endsWith(".db") }
        .onEach { logger.d { "getDatabaseList: $it [${getDatabaseSize(it)}]"} }
}

internal actual fun getDatabaseSize(name: String): Long {
    require(name.startsWith(kSyncDatabasePrefix))

    val filePath = DatabaseFileContext.databasePath(name, null)
    val fileAttributes = NSFileManager.defaultManager().attributesOfItemAtPath(filePath, null)
    val fileSize = fileAttributes?.getValue("NSFileSize") as? Long

    return fileSize ?: 0
}

internal actual fun getDatabaseLastModified(name: String): Instant {
    require(name.startsWith(kSyncDatabasePrefix))

    val filePath = DatabaseFileContext.databasePath(name, null)
    val fileAttributes = NSFileManager.defaultManager().attributesOfItemAtPath(filePath, null)
    val fileModificationDate = fileAttributes?.getValue("NSFileModificationDate") as? NSDate
    val fileModificationInstant = fileModificationDate?.toKotlinInstant()

    logger.d { "getDatabaseLastModified: $name [$fileModificationInstant]" }

    return fileModificationInstant ?: Clock.System.now()
}
