//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client

import com.twilio.sync.entities.SyncDocument
import com.twilio.sync.entities.SyncList
import com.twilio.sync.entities.SyncMap
import com.twilio.sync.entities.SyncStream
import com.twilio.sync.utils.ConnectionState
import com.twilio.sync.utils.LogLevel
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.TwilioLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * This is a central entity used to work with Sync.
 *
 * After creating a SyncClient you can open, create and modify [SyncDocument]s,
 * [SyncList]s, [SyncMap]s and [SyncStream]s.
 *
 * Example:
 *
 * ```
 * val syncClient = runCatching { SyncClient(context) { requestToken() } }.getOrElse { t ->
 *      val errorInfo = (t as? TwilioException)?.errorInfo
 *      Log.w(TAG, "Error creating SyncClient: $errorInfo")
 *      return
 * }
 * ```
 */
interface SyncClient {

    /** Provides methods to work with [SyncDocument]s. */
    val documents: Documents
    /** Provides methods to work with [SyncList]s. */
    val lists: Lists
    /** Provides methods to work with [SyncMap]s. */
    val maps: Maps
    /** Provides methods to work with [SyncStream]s. */
    val streams: Streams

    /**
     * Current transport state.
     *
     * @see Events.onConnectionStateChanged
     */
    val connectionState: ConnectionState

    /** Provides scope of [Flow]s objects to get notified about events. */
    val events: Events

    /**
     * Cleanly shuts down the SyncClient when you are done with it.
     * After calling this method client could not be reused.
     *
     * @param logout    If `true`, on the next client creation the [ErrorReason.MismatchedLastUserAccount] error
     *                  will not be thrown, but client will not be able created offline.
     */
    fun shutdown(logout: Boolean = false)

    /**
     * This interface defines scope of [Flow]s for collecting SyncClient events.
     */
    interface Events {

        /** Emits when [SyncClient] connection state has changed. */
        val onConnectionStateChanged: StateFlow<ConnectionState>

        /** Emits when an error condition occurs. */
        val onError: SharedFlow<ErrorInfo>
    }

    companion object {

        /**
         * Set verbosity level for log messages to be printed to console.
         * Default log level is [LogLevel.Silent]
         *
         * @param level Verbosity level. See [LogLevel]] for supported options.
         */
        fun setLogLevel(logLevel: LogLevel) = TwilioLogger.setLogLevel(logLevel.value)
    }
}
