//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.client

import android.content.Context
import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.util.AccountDescriptor
import com.twilio.util.ApplicationContextHolder
import com.twilio.util.InternalTwilioApi

/**
 * Clears offline cache for specified account.
 *
 * Call this method before creating [SyncClient] instance for the account, otherwise this method does nothing.
 *
 * @param context The Application Context from your Android application.
 * @param account Account to clear cache for.
 */
@OptIn(InternalTwilioApi::class)
fun SyncClient.Companion.clearCacheForAccount(context: Context, account: AccountDescriptor) {
    ApplicationContextHolder.applicationContext = context.applicationContext
    SyncCacheCleaner.clearCacheForAccount(account)
}

/**
 * Clears offline caches for all accounts, except specified.
 *
 * Call this method before creating [SyncClient] instances.
 * This method does nothing for accounts with active [SyncClient] instances.
 *
 * @param context The Application Context from your Android application.
 * @param account Account to keep the cache for.
 */
@OptIn(InternalTwilioApi::class)
fun SyncClient.Companion.clearCacheForOtherAccounts(context: Context, account: AccountDescriptor) {
    ApplicationContextHolder.applicationContext = context.applicationContext
    SyncCacheCleaner.clearCacheForOtherAccounts(account)
}

/**
 * Clears offline caches for all accounts.
 *
 * Call this method before creating [SyncClient] instances.
 * This method does nothing for accounts with active [SyncClient] instances.
 *
 * @param context The Application Context from your Android application.
 */
@OptIn(InternalTwilioApi::class)
fun SyncClient.Companion.clearAllCaches(context: Context) {
    ApplicationContextHolder.applicationContext = context.applicationContext
    SyncCacheCleaner.clearAllCaches()
}
