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

internal class CollectionOpenOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionUrl: String,
) : BaseCommand<CollectionMetadataResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): CollectionMetadataResponse {
        val request = HttpRequest(
            url = collectionUrl,
            method = HttpMethod.GET,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
