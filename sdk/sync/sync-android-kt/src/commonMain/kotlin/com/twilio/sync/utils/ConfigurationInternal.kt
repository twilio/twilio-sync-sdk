//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import com.twilio.sync.operations.ConfigurationLinks
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.util.RetrierConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Twilsock endpoint. */
internal val SyncConfig.twilsockUrl: String
    get() = "wss://tsock.${syncClientConfig.region}.twilio.com:443/v3/wsconnect"

/** Configuration endpoint. All other [ConfigurationLinks] are received from this endpoint. */
internal val SyncConfig.configurationUrl: String
    get() =  "https://cds.${syncClientConfig.region}.twilio.com/v3/Configuration"

internal fun SyncConfig.toSyncConfigInternal(
    subscriptionsConfig: SubscriptionsConfig,
    commandsConfig: CommandsConfig,
    configurationLinks: ConfigurationLinks,
) = SyncConfigInternal(syncClientConfig, cacheConfig, subscriptionsConfig, commandsConfig, configurationLinks)

internal fun SyncConfig.defaultSubscriptionsConfig(url: String) = SubscriptionsConfig(
    url = url,
    httpTimeout = syncClientConfig.httpTimeout
)

internal fun SyncConfig.defaultCommandsConfig() = CommandsConfig(
    httpTimeout = syncClientConfig.httpTimeout,
    commandTimeout = syncClientConfig.commandTimeout,
    pageSize = syncClientConfig.pageSize,
    retrierConfig = CommandsConfig().retrierConfig.copy(maxAttemptsTime = syncClientConfig.commandTimeout)
)

/**
 * This class extends [SyncConfig] by adding internal configuration parameters [SubscriptionsConfig]
 * [commandsConfig] and [ConfigurationLinks].
 * As soon as data classes are final in kotlin. Me just duplicate fields from [SyncConfig] here.
 */
internal data class SyncConfigInternal(
    val syncClientConfig: SyncClientConfig,
    val cacheConfig: CacheConfig,
    val subscriptionsConfig: SubscriptionsConfig,
    val commandsConfig: CommandsConfig,
    val links: ConfigurationLinks,
)

internal data class SubscriptionsConfig(

    /** Subscription endpoint. Should be received from backend. */
    val url: String,

    /** Subscriptions protocol version */
    val eventProtocolVersion: Int = 4,

    /**
     *  Max size of batches in first subscription attempt. After answer to the first batch received -
     *  `max_batch_size` field from the answer is used to compose following batches.
     */
    val maxInitialBatchSize: Int = 1000,

    /** Timeout for single http request. */
    val httpTimeout: Duration = 10.seconds,

    /** [RetrierConfig] for retrying subscription attempts */
    val retrierConfig: RetrierConfig = RetrierConfig(
        startDelay = 100.milliseconds, // For composing first batch of subscriptions
        maxAttemptsCount = null, // keep retrying
        maxAttemptsTime = Duration.INFINITE,
    )
)
