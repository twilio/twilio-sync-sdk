//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:Suppress("MatchingDeclarationName")
package com.twilio.sync.client

import android.content.Context
import com.twilio.sync.utils.SyncConfig
import com.twilio.twilsock.util.toClientMetadata
import com.twilio.util.AccountDescriptor
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.TwilioException

/**
 * Creates a new Sync client instance.
 *
 * @param context           The Application Context from your Android application.
 * @param useLastUserAccount
 * Try to use offline cache for last logged in user.
 *
 * Set this parameter to `false`
 * when you are not sure that current token is for the same [AccountDescriptor] which
 * was logged in last time, otherwise created client will be shut down once token will
 * be verified after setup network connection.
 *
 * When this parameter is set to `true` and offline cache from last session
 * is present (See [SyncConfig.cacheConfig] usePersistentCache parameter) - client will
 * be returned immediately (even if device is offline). In this
 * case the client will return cached data and start deliver updates when come online.
 *
 * When this parameter is set to `false` client will be returned only after connection
 * to backend is established and access to persistent cache will be granted after user's token
 * gets validated by backend. So when cache contains sensitive user data or there are
 * other critical security concerns - always set this parameter to `false`.
 *
 * @param config            Configuration parameters for SyncClient instance.
 * @param tokenProvider     Access token provider for Sync service.
 *                          This provider will be called multiple time: first before creating the client
 *                          and later each time when token is about to expire.
 * @return                  New [SyncClient] instance.
 * @throws TwilioException  When error occurred while client creation.
 */
@Suppress("FunctionName")
@Throws(TwilioException::class)
suspend fun SyncClient(
    context: Context,
    useLastUserAccount: Boolean = true,
    config: SyncConfig = SyncConfig(),
    tokenProvider: suspend () -> String,
): SyncClient {
    ApplicationContextHolder.applicationContext = context.applicationContext
    val metadata = context.toClientMetadata(BuildConfig.VERSION_NAME, sdkType = "sync")
    return createSyncClient(
        useLastUserAccount, config, metadata, connectivityMonitor = null /* Use default implementation */, tokenProvider)
}
