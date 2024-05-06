//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.operations

import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.buildJsonObject

internal class StreamUpdateMetadataOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val streamUrl: String,
    private val ttl: Duration,
) : BaseCommand<StreamMetadataResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): StreamMetadataResponse {
        val body = buildJsonObject {
            putTtl(ttl)
        }

        val request = HttpRequest(
            url = streamUrl,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
            payload = body.toString(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
