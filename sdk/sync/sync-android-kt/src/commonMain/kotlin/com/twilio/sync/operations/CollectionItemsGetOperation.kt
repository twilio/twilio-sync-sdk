//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.operations

import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.CollectionType.List
import com.twilio.sync.utils.CollectionType.Map
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.QueryOrder.Ascending
import com.twilio.sync.utils.QueryOrder.Descending
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CoroutineScope

internal class CollectionItemsGetOperation(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
    private val collectionType: CollectionType,
    private val itemsUrl: String,
    private val startId: CollectionItemId?,
    private val queryOrder: QueryOrder,
    private val pageSize: Int,
    private val inclusive: Boolean,
) : BaseCommand<CollectionItemsDataResponse>(coroutineScope, config) {

    override suspend fun makeRequest(twilsock: Twilsock): CollectionItemsDataResponse {
        val order = when (queryOrder) {
            Ascending -> "asc"
            Descending -> "desc"
        }

        val urlBuilder = URLBuilder(itemsUrl).apply {
            parameters.append("Order", order)
            parameters.append("PageSize", "$pageSize")
            startId?.let {
                parameters.append("Bounds", if (inclusive) "inclusive" else "exclusive")
                parameters.append("From", startId.id)
            }
        }

        val request = HttpRequest(
            url = urlBuilder.buildString(),
            method = HttpMethod.GET,
            timeout = config.httpTimeout,
            headers = generateHeaders(),
        )

        return when (collectionType) {
            List -> twilsock.sendRequest(request).parseResponse<ListItemsDataResponse>().toCollectionItemsDataResponse()
            Map -> twilsock.sendRequest(request).parseResponse<MapItemsDataResponse>().toCollectionItemsDataResponse()
        }
    }
}
