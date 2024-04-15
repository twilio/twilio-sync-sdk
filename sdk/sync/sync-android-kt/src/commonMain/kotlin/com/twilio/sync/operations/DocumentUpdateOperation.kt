//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.operations

import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal class DocumentUpdateOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val documentUrl: String,
    private val data: JsonObject?,
    private val ttl: Duration?,
) : BaseCommand<DocumentMetadataResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): DocumentMetadataResponse {
        val body = buildJsonObject {
            data?.let { put("data", it) }
            ttl?.let { putTtl(it) }
        }

        val request = HttpRequest(
            url = documentUrl,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
            payload = body.toString(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
