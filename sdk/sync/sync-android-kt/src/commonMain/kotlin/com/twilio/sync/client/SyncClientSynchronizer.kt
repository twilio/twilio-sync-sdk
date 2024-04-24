//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client

import com.twilio.sync.utils.Synchronizer
import com.twilio.util.ErrorReason.ClientShutdown
import com.twilio.util.TwilioException
import com.twilio.util.logger
import kotlinx.coroutines.CoroutineScope

internal class SyncClientSynchronizer(
    scope: CoroutineScope,
    client: SyncClient,
) : Synchronizer<SyncClient>(scope, client), SyncClient {

    override val documents = DocumentsSynchronizer(scope, delegate.documents)
    override val lists = ListsSynchronizer(scope, delegate.lists)
    override val maps = MapsSynchronizer(scope, delegate.maps)
    override val streams = StreamsSynchronizer(scope, delegate.streams)

    override val connectionState get() = delegate.connectionState
    override val events get() = delegate.events

    override fun shutdown(logout: Boolean) {
        try {
            // Blocking in order to wait until cache is closed. So we can remove cache right after shutdown.
            doSynchronizeBlocking { shutdown(logout) }
        } catch (e: TwilioException) {
            if (e.errorInfo.reason == ClientShutdown) {
                logger.i("Client is already shutdown")
            } else {
                logger.w("Error while shutdown client: ", e)
            }
        }
    }
}
