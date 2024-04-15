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

internal class GetConfigurationOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val configurationUrl: String,
) : BaseCommand<ConfigurationResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): ConfigurationResponse {
        val request = HttpRequest(
            url = configurationUrl,
            method = HttpMethod.GET,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
