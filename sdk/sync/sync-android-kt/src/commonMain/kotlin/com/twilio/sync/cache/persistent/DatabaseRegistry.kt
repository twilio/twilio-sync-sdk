//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.cache.persistent

import com.twilio.util.logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal object DatabaseRegistry {

        private val lock = SynchronizedObject()

        private val databases = mutableMapOf<String, Int>()

        fun registerDatabase(name: String) = synchronized(lock) {
            val counter = databases[name] ?: 0
            databases[name] = counter + 1
            logger.d { "registerDatabase: $name ${databases[name]}" }
        }

        fun unregisterDatabase(name: String) = synchronized(lock) {
            val counter = databases[name] ?: return@synchronized

            if (counter > 1) {
                databases[name] = counter - 1
            } else {
                databases.remove(name)
            }

            logger.d { "unregisterDatabase: $name ${databases[name]}" }
        }

        fun isDatabaseRegistered(name: String): Boolean = synchronized(lock) {
            databases.containsKey(name)
        }
}
