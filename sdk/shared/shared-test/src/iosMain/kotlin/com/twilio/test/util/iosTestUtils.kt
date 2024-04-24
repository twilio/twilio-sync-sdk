//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.test.util

import co.touchlab.sqliter.DatabaseFileContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.datetime.Instant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import kotlin.time.Duration

actual fun setupTestAndroidContext() = Unit // no-op

@OptIn(ExperimentalForeignApi::class)
actual fun setDatabaseLastModified(name: String, lastModified: Instant) {
    val filePath = DatabaseFileContext.databasePath(name, null)
    val attributes: Map<Any?, *> = mapOf("NSFileModificationDate" to lastModified.toNSDate())

    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        NSFileManager.defaultManager().setAttributes(attributes, filePath, error.ptr)
    }
}

actual fun createNewDatabaseFile(name: String) {
    val filePath = DatabaseFileContext.databasePath(name, null)
    NSFileManager.defaultManager().createFileAtPath(filePath, null, null)
}
