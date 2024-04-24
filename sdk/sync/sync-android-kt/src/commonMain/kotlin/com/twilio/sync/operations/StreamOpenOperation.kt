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
import kotlinx.coroutines.CoroutineScope

internal class StreamOpenOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val streamUrl: String,
) : BaseCommand<StreamMetadataResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): StreamMetadataResponse {
        val request = HttpRequest(
            url = streamUrl,
            method = HttpMethod.GET,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
