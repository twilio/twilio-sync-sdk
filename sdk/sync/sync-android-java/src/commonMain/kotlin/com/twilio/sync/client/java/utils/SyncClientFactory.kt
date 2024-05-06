//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.twilio.sync.client.java.utils

import com.twilio.sync.client.createInternal
import com.twilio.sync.client.java.SyncClientJava
import com.twilio.sync.client.java.SyncClientJavaImpl
import com.twilio.sync.utils.SyncConfig
import com.twilio.twilsock.client.ClientMetadata
import com.twilio.util.ErrorReason
import com.twilio.util.InternalTwilioApi
import com.twilio.util.NextLong
import com.twilio.util.currentThreadDispatcher
import com.twilio.util.newSerialCoroutineContext
import com.twilio.util.toTwilioException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(InternalTwilioApi::class)
internal fun createSyncClient(
    tokenProvider: TokenProvider,
    useLastUserCache:Boolean = true,
    config: SyncConfig = SyncConfig(),
    clientMetadata: ClientMetadata = ClientMetadata(),
    listener: SuccessListener<SyncClientJava>,
) {
    val coroutineContext = newSerialCoroutineContext()
    val coroutineScope = CoroutineScope(coroutineContext + SupervisorJob())
    val listenersDispatcher = currentThreadDispatcher()

    coroutineScope.launch {
        runCatching {
            createInternal(
                coroutineContext,
                coroutineScope,
                useLastUserCache,
                config,
                clientMetadata,
                connectivityMonitor = null, // Use default connectivity monitor implementation
            ) { tokenProvider.getToken() }
        }.onSuccess { syncClient ->
            val syncClientJava = SyncClientJavaImpl(
                coroutineScope,
                listenersDispatcher,
                syncClient
            )
            withContext(listenersDispatcher) { listener.onSuccess(syncClientJava) }
        }.onFailure { t ->
            val errorInfo = t.toTwilioException(ErrorReason.CreateClientError).errorInfo
            withContext(listenersDispatcher) { listener.onFailure(errorInfo) }
        }
    }
}
