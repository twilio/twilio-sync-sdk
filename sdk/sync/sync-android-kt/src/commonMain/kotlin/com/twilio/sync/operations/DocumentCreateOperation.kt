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
import kotlinx.serialization.json.put

internal class DocumentCreateOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val documentsUrl: String,
    private val uniqueName: String?,
    private val ttl: Duration,
) : BaseCommand<DocumentMetadataResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): DocumentMetadataResponse {
        val body = buildJsonObject {
            uniqueName?.let { put("unique_name", it) }
            putTtl(ttl)
        }

        val request = HttpRequest(
            url = documentsUrl,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
            payload = body.toString(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
