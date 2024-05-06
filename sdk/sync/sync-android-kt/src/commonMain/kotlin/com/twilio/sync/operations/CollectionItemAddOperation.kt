//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.operations

import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionItemId.Index
import com.twilio.sync.utils.CollectionItemId.Key
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
import kotlinx.serialization.json.put
import kotlin.time.Duration

internal class CollectionItemAddOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionType: CollectionType,
    private val itemsUrl: String,
    private val itemId: CollectionItemId?,
    private val data: JsonObject,
    private val ttl: Duration?,
) : BaseCommand<CollectionItemData>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): CollectionItemData {
        val body = buildJsonObject {
            when (itemId) {
                null -> Unit // Do nothing
                is Index -> put("index", itemId.index)
                is Key -> put("key", itemId.key)
            }
            put("data", data)
            ttl?.let { putTtl(it) }
        }

        val request = HttpRequest(
            url = itemsUrl,
            method = HttpMethod.POST,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
            payload = body.toString(),
        )

        return when (collectionType) {
            List -> twilsock.sendRequest(request)
                .parseResponse<ListItemDataResponse>()
                .also { check(it.data == null) { "On add list item backend doesn't return any data" } }
                .copy(data = data)
                .toCollectionItemData()

            Map -> twilsock.sendRequest(request)
                .parseResponse<MapItemDataResponse>()
                .also { check(it.data == null) { "On add map item backend doesn't return any data" } }
                .copy(data = data)
                .toCollectionItemData()
        }
    }
}
