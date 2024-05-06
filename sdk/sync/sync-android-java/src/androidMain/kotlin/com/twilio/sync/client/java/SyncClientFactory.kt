//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java

import android.content.Context
import com.twilio.sync.client.SyncClient
import com.twilio.sync.client.clearAllCaches
import com.twilio.sync.client.clearCacheForAccount
import com.twilio.sync.client.clearCacheForOtherAccounts
import com.twilio.sync.client.java.utils.SuccessListener
import com.twilio.sync.client.java.utils.TokenProvider
import com.twilio.sync.client.java.utils.createSyncClient
import com.twilio.sync.utils.SyncConfig
import com.twilio.twilsock.util.toClientMetadata
import com.twilio.util.AccountDescriptor
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.InternalTwilioApi

/**
 * Factory for creating [SyncClientJava] objects.
 */
object SyncClientFactory {

    /**
     * Creates a new Sync client instance.
     *
     * Callback listener is invoked with reference to [SyncClient] when client
     * initialization is completed.
     *
     * @param context           The Application Context from your Android application.
     * @param tokenProvider     Access token provider for Sync service.
     *                          This provider will be called multiple time: first before creating the client
     *                          and later each time when token is about to expire.
     * @param useLastUserCache  Try to use offline cache for last logged in user. Set this parameter to `false`
     *                          when you are not sure that current token is for the same [AccountDescriptorJava] which
     *                          was logged in last time, otherwise created client will be shut down once token will
     *                          be verified.
     *                          When this parameter is set to `true` and offline cache from last session
     *                          is present - client will be returned immediately (even if device is offline). In this
     *                          case the client will return cached data and deliver updates when come online.
     *                          When this parameter is set to `false` client will be returned only after connection
     *                          to backend is established.
     * @param config            Configuration parameters for SyncClient instance.
     * @param listener          Callback listener that will receive reference to
     *                          the newly initialized SyncClient.
     */
    @OptIn(InternalTwilioApi::class)
    @JvmStatic
    @JvmOverloads
    fun create(
        context: Context,
        tokenProvider: TokenProvider,
        useLastUserCache:Boolean = true,
        config: SyncConfig = SyncConfig(),
        listener: SuccessListener<SyncClientJava>,
    ) {
        ApplicationContextHolder.applicationContext = context.applicationContext
        val clientMetadata = context.toClientMetadata(BuildConfig.VERSION_NAME, sdkType = "sync")
        createSyncClient(tokenProvider, useLastUserCache, config, clientMetadata, listener)
    }

    /**
     * Clears offline cache for specified account.
     *
     * Call this method before creating [SyncClient] instance for the account, otherwise this method does nothing.
     *
     * @param context The Application Context from your Android application.
     * @param account Account to clear cache for.
     */
    @JvmStatic
    fun clearCacheForAccount(context: Context, account: AccountDescriptor) =
        SyncClient.clearCacheForAccount(context, account)

    /**
     * Clears offline caches for all accounts, except specified.
     *
     * Call this method before creating [SyncClient] instances.
     * This method does nothing for accounts with active [SyncClient] instances.
     *
     * @param context The Application Context from your Android application.
     * @param account Account to keep the cache for.
     */
    @JvmStatic
    fun clearCacheForOtherAccounts(context: Context, account: AccountDescriptor) =
        SyncClient.clearCacheForOtherAccounts(context, account)

    /**
     * Clears offline caches for all accounts.
     *
     * Call this method before creating [SyncClient] instances.
     * This method does nothing for accounts with active [SyncClient] instances.
     *
     * @param context The Application Context from your Android application.
     */
    @JvmStatic
    fun clearAllCaches(context: Context) =
        SyncClient.clearAllCaches(context)
}
