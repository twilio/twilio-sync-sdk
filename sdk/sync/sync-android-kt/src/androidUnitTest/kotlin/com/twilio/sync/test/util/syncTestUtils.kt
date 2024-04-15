//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.util

import com.twilio.sync.operations.CollectionItemMetadataResponse
import com.twilio.sync.operations.CollectionItemsDataResponse
import com.twilio.sync.operations.CollectionMetadataResponse
import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.kNameAlreadyExist
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.emptyJsonObject
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

internal fun testDocumentMetadataResponse(
    sid: EntitySid,
    uniqueName: String? = null,
    data: JsonObject? = null,
) = DocumentMetadataResponse(
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = Instant.DISTANT_PAST,
    dateUpdated = Instant.DISTANT_PAST,
    revision = "0",
    lastEventId = 0,
    data = data,
)

internal fun testCollectionMetadataResponse(
    sid: EntitySid,
    uniqueName: String? = null,
) = CollectionMetadataResponse(
    sid = sid,
    uniqueName = uniqueName,
    dateCreated = Instant.DISTANT_PAST,
    dateUpdated = Instant.DISTANT_PAST,
    revision = "0",
    lastEventId = 0,
)

internal fun testCollectionItemData(
    mapSid: EntitySid,
    itemId: CollectionItemId,
    dateCreated: Instant = Instant.fromEpochMilliseconds(0),
    dateUpdated: Instant = Instant.fromEpochMilliseconds(0),
    dateExpires: Instant? = null,
    revision: String = "0",
    lastEventId: Long = 0,
    data: JsonObject = emptyJsonObject(),
) =
    CollectionItemData(mapSid, itemId, dateCreated, dateUpdated, dateExpires, revision, lastEventId, data,
        isLeftBound = true,
        isRightBound = true,
        isRemoved = false,
    )

internal fun testCollectionItemsDataResponse(
    vararg items: CollectionItemData,
    meta: CollectionItemMetadataResponse = CollectionItemMetadataResponse(),
) = CollectionItemsDataResponse(items.toList(), meta)

internal fun testCollectionItemsDataResponse(
    items: List<CollectionItemData> = emptyList(),
    meta: CollectionItemMetadataResponse = CollectionItemMetadataResponse(),
) = CollectionItemsDataResponse(items, meta)

internal fun List<CollectionItemData>.setRangeBounds() = map { item ->
    item.copy(
        isLeftBound = item.itemId == first().itemId,
        isRightBound = item.itemId == last().itemId
    )
}

val testErrorAlreadyExists = ErrorInfo(
    reason = ErrorReason.CommandPermanentError,
    status = HttpStatusCode.Conflict.value,
    code = kNameAlreadyExist,
    message = "Unique name already exists",
)
