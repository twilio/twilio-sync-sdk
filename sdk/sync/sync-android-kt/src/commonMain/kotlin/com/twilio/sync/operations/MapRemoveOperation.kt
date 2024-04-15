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

internal class MapRemoveOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionUrl: String,
) : BaseCommand<Unit>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock) {
        val request = HttpRequest(
            url = collectionUrl,
            method = HttpMethod.DELETE,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
        )

        return twilsock.sendRequest(request).parseResponse()
    }
}
