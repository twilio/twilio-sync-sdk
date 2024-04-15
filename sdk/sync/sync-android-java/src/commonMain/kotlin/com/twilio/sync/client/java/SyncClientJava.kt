//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client.java

import com.twilio.sync.client.java.utils.Listenable
import com.twilio.sync.utils.ConnectionState
import com.twilio.sync.utils.LogLevel
import com.twilio.util.ErrorInfo
import com.twilio.util.TwilioLogger
import kotlin.jvm.JvmStatic

/**
 * This is a central entity used to work with Sync.
 *
 * After creating a SyncClientJava you can open, create and modify [SyncDocumentJava]s,
 * [SyncListJava]s, [SyncMapJava]s and [SyncStreamJava]s.
 *
 * Example:
 *
 * ```
 * SyncClientFactory.create(context, tokenProvider, new SuccessListener<SyncClientJava>() {
 *
 *     @Override
 *     public void onSuccess(@NonNull SyncClientJava result) {
 *         future.set(result);
 *     }
 *
 *     @Override
 *     public void onFailure(@NonNull ErrorInfo errorInfo) {
 *         fail("Cannot create SyncClient: " + errorInfo);
 *     }
 * });
 * ```
 */
interface SyncClientJava : Listenable<SyncClientJava.Listener> {

    /** Provides methods to work with sync documents. */
    val documents: DocumentsJava

    /** Provides methods to work with sync lists. */
    val lists: ListsJava

    /** Provides methods to work with sync maps. */
    val maps: MapsJava

    /** Provides methods to work with sync streams. */
    val streams: StreamsJava

    /**
     * Current transport state.
     *
     * @see Listener.onConnectionStateChanged
     */
    val connectionState: ConnectionState

    /**
     * Method to add listener for this [SyncClientJava].
     *
     * @param listener the listener to add.
     */
    override fun addListener(listener: Listener)

    /**
     * Method to Method to remove listener from this [SyncClientJava].
     *
     * @param listener the listener to remove.
     */
    override fun removeListener(listener: Listener)

    /**
     * Method to remove all listeners from this [SyncClientJava].
     */
    override fun removeAllListeners()

    /**
     * Cleanly shuts down the SyncClient when you are done with it.
     * After calling this method client could not be reused.
     */
    fun shutdown()

    /**
     * This interface defines SyncClient callback methods.
     */
    interface Listener {

        /**
         * Called when [SyncClientJava] connection state has changed.
         *
         * @param client          Instance of [SyncClientJava] which triggered the event.
         * @param connectionState New connection state.
         */
        fun onConnectionStateChanged(client: SyncClientJava, connectionState: ConnectionState) {}

        /**
         * Called when an error condition occurs.
         *
         * @param client    Instance of [SyncClientJava] which triggered the event.
         * @param errorInfo [ErrorInfo] object containing error details.
         */
        fun onError(client: SyncClientJava, errorInfo: ErrorInfo) {}
    }

    companion object {

        /**
         * Set verbosity level for log messages to be printed to console.
         * Default log level is [LogLevel.Silent]
         *
         * @param level Verbosity level. See [LogLevel]] for supported options.
         */
        @JvmStatic
        fun setLogLevel(logLevel: LogLevel) = TwilioLogger.setLogLevel(logLevel.value)
    }
}
