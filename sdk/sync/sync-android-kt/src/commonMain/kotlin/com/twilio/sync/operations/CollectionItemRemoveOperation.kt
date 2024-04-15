//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.operations

import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.CollectionType.List
import com.twilio.sync.utils.CollectionType.Map
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.emptyJsonObject
import kotlinx.coroutines.CoroutineScope

internal class CollectionItemRemoveOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionType: CollectionType,
    private val url: String,
) : BaseCommand<CollectionItemData>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): CollectionItemData {
        val request = HttpRequest(
            url = url,
            method = HttpMethod.DELETE,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
        )

        return when (collectionType) {
            List -> twilsock.sendRequest(request)
                .parseResponse<ListItemDataResponse>()
                .copy(data = emptyJsonObject())
                .toCollectionItemData()

            Map -> twilsock.sendRequest(request)
                .parseResponse<MapItemDataResponse>()
                .copy(data = emptyJsonObject())
                .toCollectionItemData()
        }
    }
}
