//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:Suppress("MatchingDeclarationName")
package com.twilio.sync.client

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.Versions
import com.twilio.twilsock.util.ClientMetadataIOS
import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.util.AccountDescriptor
import com.twilio.util.InternalTwilioApi
import com.twilio.util.TwilioException
import com.twilio.util.logger
import kotlin.coroutines.cancellation.CancellationException

object SyncClientFactory {

    @Throws(TwilioException::class, CancellationException::class)
    suspend fun create(
        useLastUserCache: Boolean = true,
        config: SyncConfig = SyncConfig(),
        connectivityMonitor: ConnectivityMonitor,
        tokenProvider: () -> String,
    ): SyncClient {
        val clientMetadata = ClientMetadataIOS(Versions.fullVersionName, sdkType = "sync")
        val result = runCatching {
            createSyncClient(useLastUserCache, config, clientMetadata, connectivityMonitor, tokenProvider)
        }
        return result.getOrElse { t ->
            logger.e("Failed to create SyncClient", t)
            throw t
        }
    }

    @OptIn(InternalTwilioApi::class)
    fun clearCacheForAccount(account: AccountDescriptor) = SyncCacheCleaner.clearCacheForAccount(account)

    @OptIn(InternalTwilioApi::class)
    fun clearCacheForOtherAccounts(account: AccountDescriptor) = SyncCacheCleaner.clearCacheForOtherAccounts(account)

    @OptIn(InternalTwilioApi::class)
    fun clearAllCaches() = SyncCacheCleaner.clearAllCaches()
}
