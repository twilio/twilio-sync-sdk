//
//  Twilio Conversations Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.commands

import com.twilio.util.RetrierConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun newCommandsConfig(): CommandsConfig = CommandsConfig()

data class CommandsConfig(

    /** Timeout for single http request. */
    val httpTimeout: Duration = 10.seconds,

    /** Timeout for a whole command from the moment when it is posted to the [CommandsScheduler]. */
    val commandTimeout: Duration = 10.seconds,

    /** Maximum commands running simultaneously. */
    val maxParallelCommands: Int = 1000,

    /** Default page size for querying collection items from backend. */
    val pageSize: Int = 100,

    /** [RetrierConfig] for retrying command request. */
    val retrierConfig: RetrierConfig = RetrierConfig(
        maxAttemptsCount = null, // keep retrying
        maxAttemptsTime = commandTimeout,
    ),
)
