//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.twilio.sync.client.java

import com.twilio.sync.client.SyncClient
import com.twilio.sync.client.java.SyncClientJava.Listener
import com.twilio.sync.client.java.utils.ListenableImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class SyncClientJavaImpl(
    coroutineScope: CoroutineScope,
    listenersDispatcher: CoroutineDispatcher,
    private val syncClient: SyncClient,
) : SyncClientJava, ListenableImpl<Listener>(coroutineScope, listenersDispatcher) {

    init {
        with(syncClient.events) {
            onConnectionStateChanged.connectListeners { onConnectionStateChanged(this@SyncClientJavaImpl, it) }
            onError.connectListeners { onError(this@SyncClientJavaImpl, it) }
        }
    }

    override val documents = DocumentsJavaImpl(coroutineScope, listenersDispatcher, syncClient.documents)

    override val lists = ListsJavaImpl(coroutineScope, listenersDispatcher, syncClient.lists)

    override val maps = MapsJavaImpl(coroutineScope, listenersDispatcher, syncClient.maps)

    override val streams = StreamsJavaImpl(coroutineScope, listenersDispatcher, syncClient.streams)

    override val connectionState get() = syncClient.connectionState

    override fun shutdown() = syncClient.shutdown()
}
