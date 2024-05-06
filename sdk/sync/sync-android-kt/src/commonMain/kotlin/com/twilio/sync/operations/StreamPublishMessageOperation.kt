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
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal class StreamPublishMessageOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val messagesUrl: String,
    private val data: JsonObject,
) : BaseCommand<StreamPublishMessageResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): StreamPublishMessageResponse {
        val body = buildJsonObject {
            put("data", data)
        }

        val request = HttpRequest(
            url = messagesUrl,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
            payload = body.toString(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
