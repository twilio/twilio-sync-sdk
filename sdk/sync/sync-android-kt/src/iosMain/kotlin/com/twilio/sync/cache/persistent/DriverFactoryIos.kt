//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.cache.persistent

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import com.squareup.sqldelight.drivers.native.wrapConnection
import com.twilio.util.AccountDescriptor

internal actual class DriverFactory actual constructor() {

    actual fun createDriver(accountDescriptor: AccountDescriptor, isInMemoryDatabase: Boolean): SqlDriver {
        val fileName = accountDescriptor.databaseName
        val schema = SyncDatabase.Schema

        return NativeSqliteDriver(
            DatabaseConfiguration(
                name = fileName,
                version = schema.version,
                create = { connection ->
                    wrapConnection(connection) { schema.create(it) }
                },
                upgrade = { connection, oldVersion, newVersion ->
                    wrapConnection(connection) {
                        schema.migrate(it, oldVersion, newVersion)
                    }
                },
                inMemory = isInMemoryDatabase,
                journalMode = JournalMode.DELETE,
            )
        )
    }
}
