//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.operations

import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class DocumentMutateDataOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val documentUrl: String,
    private val currentData: CurrentData,
    private val ttl: Duration?,
    private val mutateData: suspend (currentData: JsonObject) -> JsonObject?,
) : BaseMutateOperation<DocumentMetadataResponse>(
        coroutineScope, config, documentUrl, currentData, ttl, mutateData) {

    override suspend fun sendRequest(
        twilsock: Twilsock,
        request: HttpRequest,
        data: JsonObject
    ): DocumentMetadataResponse {
        val response = twilsock.sendRequest(request).parseResponse<DocumentMetadataResponse>()
        return response.copy(data = data) // data == null in the response from backend
    }

    override suspend fun requestCurrentData(twilsock: Twilsock): CurrentData {
        val request = HttpRequest(
            url = documentUrl,
            method = HttpMethod.GET,
            timeout = config.httpTimeout,
            headers = generateHeaders()
        )

        val metadata = twilsock.sendRequest(request).parseResponse<DocumentMetadataResponse>()

        return CurrentData(
            metadata.revision,
            checkNotNull(metadata.data) { "Data must be not null in response for GET request" }
        )
    }
}
