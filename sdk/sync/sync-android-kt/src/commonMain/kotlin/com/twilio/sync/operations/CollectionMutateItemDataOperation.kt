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
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class CollectionMutateItemDataOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionType: CollectionType,
    private val itemUrl: String,
    private val currentData: CurrentData?,
    private val ttl: Duration?,
    private val mutateData: suspend (currentData: JsonObject) -> JsonObject?,
) : BaseMutateOperation<CollectionItemData>(coroutineScope, config, itemUrl, currentData, ttl, mutateData) {

    override suspend fun sendRequest(twilsock: Twilsock, request: HttpRequest, data: JsonObject): CollectionItemData {
        // data == null in the response from backend by the spec:
        // https://docs.google.com/document/d/1rfz5Gic6HQAhS0G_5TA4Y0X2-uYeU-PbXBPMMQmV5cc/edit#heading=h.ccax3v5x6c0t
        return when (collectionType) {
            List -> twilsock.sendRequest(request)
                .parseResponse<ListItemDataResponse>()
                .also { check(it.data == null) }
                .copy(data = data)
                .toCollectionItemData()

            Map -> twilsock.sendRequest(request)
                .parseResponse<MapItemDataResponse>()
                .also { check(it.data == null) }
                .copy(data = data)
                .toCollectionItemData()
        }
    }

    override suspend fun requestCurrentData(twilsock: Twilsock): CurrentData {
        val request = HttpRequest(
            url = itemUrl,
            method = HttpMethod.GET,
            timeout = config.httpTimeout,
            headers = generateHeaders()
        )

        val itemData = when (collectionType) {
            List -> twilsock.sendRequest(request).parseResponse<ListItemDataResponse>().toCollectionItemData()
            Map -> twilsock.sendRequest(request).parseResponse<MapItemDataResponse>().toCollectionItemData()
        }

        return CurrentData(itemData.revision, itemData.data)
    }
}
