//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.operations

import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.CollectionType
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.QueryOrder.Ascending
import com.twilio.twilsock.commands.CommandsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class SyncOperationsFactory(
    private val coroutineScope: CoroutineScope,
    private val commandsConfig: CommandsConfig,
) {

    fun streamCreateOperation(streamsUrl: String, uniqueName: String?, ttl: Duration) =
        StreamCreateOperation(coroutineScope, commandsConfig, streamsUrl, uniqueName, ttl)

    fun streamOpenOperation(streamUrl: String) =
        StreamOpenOperation(coroutineScope, commandsConfig, streamUrl)

    fun streamUpdateMetadataOperation(streamUrl: String, ttl: Duration) =
        StreamUpdateMetadataOperation(coroutineScope, commandsConfig, streamUrl, ttl)

    fun streamPublishMessageOperation(messagesUrl: String, data: JsonObject) =
        StreamPublishMessageOperation(coroutineScope, commandsConfig, messagesUrl, data)

    fun streamRemoveOperation(streamUrl: String) =
        RemoveOperation(coroutineScope, commandsConfig, streamUrl)

    fun documentCreateOperation(documentsUrl: String, uniqueName: String?, ttl: Duration) =
        DocumentCreateOperation(coroutineScope, commandsConfig, documentsUrl, uniqueName, ttl)

    fun documentOpenOperation(documentUrl: String) =
        DocumentOpenOperation(coroutineScope, commandsConfig, documentUrl)

    fun documentUpdateOperation(documentUrl: String, data: JsonObject? = null, ttl: Duration? = null) =
        DocumentUpdateOperation(coroutineScope, commandsConfig, documentUrl, data, ttl)

    fun documentMutateDataOperation(
        documentUrl: String,
        currentData: BaseMutateOperation.CurrentData,
        ttl: Duration?,
        mutateData: suspend (currentData: JsonObject) -> JsonObject?
    ) = DocumentMutateDataOperation(
        coroutineScope = coroutineScope,
        config = commandsConfig,
        documentUrl = documentUrl,
        currentData = currentData,
        mutateData = mutateData,
        ttl = ttl
    )

    fun documentRemoveOperation(documentUrl: String) =
        RemoveOperation(coroutineScope, commandsConfig, documentUrl)

    fun collectionCreateOperation(collectionsUrl: String, uniqueName: String?, ttl: Duration) =
        CollectionCreateOperation(coroutineScope, commandsConfig, collectionsUrl, uniqueName, ttl)

    fun collectionOpenOperation(collectionUrl: String) =
        CollectionOpenOperation(coroutineScope, commandsConfig, collectionUrl)

    fun collectionUpdateMetadataOperation(collectionUrl: String, ttl: Duration) =
        CollectionUpdateMetadataOperation(coroutineScope, commandsConfig, collectionUrl, ttl)

    fun collectionRemoveOperation(collectionUrl: String) =
        RemoveOperation(coroutineScope, commandsConfig, collectionUrl)

    fun collectionItemAddOperation(
        collectionType: CollectionType,
        itemsUrl: String,
        itemId: CollectionItemId?,
        data: JsonObject,
        ttl: Duration?
    ) =
        CollectionItemAddOperation(coroutineScope, commandsConfig, collectionType, itemsUrl, itemId, data, ttl)

    fun collectionItemUpdateOperation(collectionType: CollectionType, itemUrl: String, data: JsonObject?, ttl: Duration?) =
        CollectionItemUpdateOperation(coroutineScope, commandsConfig, collectionType, itemUrl, data, ttl)

    fun collectionItemsGetOperation(
        collectionType: CollectionType,
        itemsUrl: String,
        startId: CollectionItemId? = null,
        queryOrder: QueryOrder = Ascending,
        pageSize: Int? = null,
        inclusive: Boolean = true,
    ) = CollectionItemsGetOperation(
        coroutineScope,
        commandsConfig,
        collectionType,
        itemsUrl,
        startId,
        queryOrder,
        pageSize ?: commandsConfig.pageSize,
        inclusive
    )

    fun collectionItemRemoveOperation(collectionType: CollectionType, itemUrl: String) =
        CollectionItemRemoveOperation(coroutineScope, commandsConfig, collectionType, itemUrl)

    fun collectionMutateItemDataOperation(
        collectionType: CollectionType,
        itemUrl: String,
        currentData: BaseMutateOperation.CurrentData?,
        ttl: Duration?,
        mutateData: suspend (currentData: JsonObject) -> JsonObject?
    ) = CollectionMutateItemDataOperation(
        coroutineScope,
        commandsConfig,
        collectionType,
        itemUrl,
        currentData,
        ttl,
        mutateData
    )
}
