//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

const val kDefaultPageSize = 100

data class SyncConfig(

    /** Client configuration parameters. See [SyncClientConfig]. */
    val syncClientConfig: SyncClientConfig = SyncClientConfig(),

    /** Cache configuration parameters. See [CacheConfig]. */
    val cacheConfig: CacheConfig = CacheConfig(),
)

data class SyncClientConfig(

    /** Timeout for single http request. */
    val httpTimeout: Duration = 10.seconds,

    /**
     * Timeout for a command (like update document, insert list item etc.).
     * Sync Client retries http requests until this timeout happens or non-retryable error is received from backend.
     */
    val commandTimeout: Duration = 10.seconds,

    /** Default page size for querying collection items from backend. */
    val pageSize: Int = kDefaultPageSize,

    /**
     * Twilio server region to connect to, such as `us1` or `ie1`.
     * Instances exist in specific regions, so this should only be changed if needed.
     */
    val region: String = "us1",

    /**
     * Defer certificate trust decisions to OS, overriding the default of
     * certificate pinning for Twilio back-end connections.
     *
     * Twilio client SDKs utilize certificate pinning to prevent man-in-the-middle attacks
     * against your connections to our services.  Customers in certain very specific
     * environments may need to opt-out of this if custom certificate authorities must
     * be allowed to intentionally intercept communications for security or policy reasons.
     *
     * Setting this property to `true` for a Sync Client instance will defer to OS to
     * establish whether or not a given connection is providing valid and trusted TLS certificates.
     *
     * Setting this property `false` allows the Twilio client SDK
     * to determine trust when communicating with our servers.
     */
    val deferCertificateTrustToPlatform: Boolean = true,

    /**
     * If useProxy flag is `true` a Sync Client will try to read and
     * apply proxy settings in the following order:
     * <ol>
     *   <li>
     *   If there is no proxysettings.properties file in the assets folder of your app
     *      then android system proxy settings will be applied.
     *   <li>
     *   If the proxysettings.properties file exists in the assets folder of your app
     *      then proxy configuration will be read from it and android system settings will be ignored.
     *   <li>
     *   If proxy settings cannot be read either from the proxysettings.properties file and from
     *      android system settings {@link ConversationsClient} will use direct connection.
     * </ol>
     * If this flag is `false` all proxy settings will be ignored and direct connection will be used.
     *
     * <b>Example of the proxysettings.properties file:</b>
     * <pre><code>
    host=192.168.8.108
    port=8080
    user=myUser
    password=myPassword
     * </code><pre>
     */
    val useProxy: Boolean = false,
)

data class CacheConfig(
    /**
     * When `true` all persistent caches are dropped once `Unauthorised` response received from backend
     * on connect.
     */
    val dropAllCachesOnUnauthorised: Boolean = true,

    /** When `true` persistent caches is used, when `false` - only in-memory cache is used. */
    val usePersistentCache: Boolean = true,

    /** Maximum device storage size in bytes used for all persistent caches.
     *
     * Persistent cache for each [AccountDescriptor] is stored in separate database.
     *
     * When [maxPersistentCacheSize] is exceeded a Sync Client first remove old (looking at dataModified
     * of the database file) databases first. In the last database, least recently used records are removed
     * (if necessary) in order to fit into the size limit.
     *
     * Default value is 100Mb. `null` value is INFINITE size.
     */
    val maxPersistentCacheSize: Long? = 100 * 1024 * 1024,

    /**
     * Persistent cache for each [AccountDescriptor] is stored in separate database.
     *
     * When [cleanupPersistentCacheForOtherUsers] is `true` - all unopened persistent cache databases for
     * other accounts will be removed on login.
     */
    val cleanupPersistentCacheForOtherUsers: Boolean = true,

    /**
     * Persistent cache for each [AccountDescriptor] is stored in separate database.
     *
     * All unopened persistent cache databases which are older than [cleanupUnusedCachesAfter] (looking at
     * dataModified of the database file) will be removed on login.
     */
    val cleanupUnusedCachesAfter: Duration = 7.days,
)
