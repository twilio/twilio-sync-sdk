//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.util

import com.twilio.sync.client.SyncClient
import com.twilio.sync.client.SyncClientFactory
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.Versions
import com.twilio.test.util.TestConnectivityMonitor
import com.twilio.test.util.testCoroutineContext
import com.twilio.twilsock.util.ClientMetadataIOS
import kotlinx.coroutines.runBlocking

@Suppress("FunctionName")
actual suspend fun TestSyncClient(
    useLastUserAccount: Boolean,
    config: SyncConfig,
    tokenProvider: suspend () -> String,
): SyncClient = SyncClientFactory.create(
    useLastUserCache = useLastUserAccount,
    config = config,
    connectivityMonitor = TestConnectivityMonitor(),
    tokenProvider = {
        runBlocking(testCoroutineContext) { tokenProvider() }
    }
)

@Suppress("FunctionName")
actual suspend fun TestMetadata() = ClientMetadataIOS(Versions.fullVersionName, sdkType = "sync")
