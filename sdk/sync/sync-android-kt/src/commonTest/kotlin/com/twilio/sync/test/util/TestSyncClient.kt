//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.util

import com.twilio.sync.client.SyncClient
import com.twilio.sync.utils.SyncConfig
import com.twilio.twilsock.client.ClientMetadata

@Suppress("FunctionName")
expect suspend fun TestSyncClient(
    useLastUserAccount: Boolean = true,
    config: SyncConfig = SyncConfig(),
    tokenProvider: suspend () -> String,
): SyncClient

@Suppress("FunctionName")
expect suspend fun TestMetadata(): ClientMetadata
