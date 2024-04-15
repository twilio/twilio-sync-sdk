//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.util

import com.twilio.sync.client.SyncClient
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.Versions
import com.twilio.twilsock.util.toClientMetadata
import com.twilio.util.ApplicationContextHolder

@Suppress("FunctionName")
actual suspend fun TestSyncClient(
    useLastUserAccount: Boolean,
    config: SyncConfig,
    tokenProvider: suspend () -> String,
): SyncClient = SyncClient(
    context = ApplicationContextHolder.applicationContext,
    useLastUserAccount = useLastUserAccount,
    config = config,
    tokenProvider = tokenProvider
)

@Suppress("FunctionName")
actual suspend fun TestMetadata() =
    ApplicationContextHolder.applicationContext.toClientMetadata(Versions.fullVersionName, sdkType = "sync")
