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
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration

internal class CollectionItemUpdateOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionType: CollectionType,
    private val itemUrl: String,
    private val data: JsonObject?,
    private val ttl: Duration?,
) : BaseCommand<CollectionItemData>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): CollectionItemData {
        val body = buildJsonObject {
            data?.let { put("data", it) }
            ttl?.let { putTtl(it) }
        }

        val request = HttpRequest(
            url = itemUrl,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
            payload = body.toString(),
        )

        return when (collectionType) {
            List -> twilsock.sendRequest(request)
                .parseResponse<ListItemDataResponse>()
                .also { check(it.data == null) { "On update list item backend doesn't return any data" } }
                .copy(data = data)
                .toCollectionItemData()

            Map -> twilsock.sendRequest(request)
                .parseResponse<MapItemDataResponse>()
                .also { check(it.data == null) { "On update map item backend doesn't return any data" } }
                .copy(data = data)
                .toCollectionItemData()
        }
    }
}
