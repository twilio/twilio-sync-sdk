//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.cache.persistent

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import com.twilio.util.AccountDescriptor
import com.twilio.util.ApplicationContextHolder

internal actual class DriverFactory actual constructor() {

    actual fun createDriver(accountDescriptor: AccountDescriptor, isInMemoryDatabase: Boolean): SqlDriver {
        val context = ApplicationContextHolder.applicationContext

        /**
         * null for in-memory database. See [SupportSQLiteOpenHelper.Configuration.Builder.name].
         * https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteOpenHelper.Configuration.Builder#name
         */
        val fileName = if (isInMemoryDatabase) null else accountDescriptor.databaseName

        val callback = object : AndroidSqliteDriver.Callback(SyncDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                setPragma(db, "JOURNAL_MODE = OFF")
            }

            private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                val cursor = db.query("PRAGMA $pragma")
                cursor.moveToFirst()
                cursor.close()
            }
        }

        return AndroidSqliteDriver(SyncDatabase.Schema, context, fileName, callback = callback)
    }
}
