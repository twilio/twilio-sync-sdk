//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.operations

import com.twilio.sync.utils.CollectionItemData
import com.twilio.sync.utils.CollectionItemId
import com.twilio.sync.utils.QueryOrder
import com.twilio.sync.utils.QueryOrder.Ascending
import com.twilio.sync.utils.QueryOrder.Descending
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ConfigurationResponse(
    @SerialName("links") val links: ConfigurationLinks,
)

@Serializable
internal data class ConfigurationLinks(
    // Example: "https://cds.dev-us1.twilio.com/v4/Subscriptions"
    @SerialName("subscriptions") val subscriptions: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Maps"
    @SerialName("maps") val maps: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Maps/{mapId}/Items",
    @SerialName("map_items") val mapItems: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Maps/{mapId}/Items/{itemId}"
    @SerialName("map_item") val mapItem: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Maps/{mapId}",
    // TODO:
    // Cannot remove map using received endpoint. So temporary hardcode it until the reason is clear:
    // See: https://twilio.slack.com/archives/C0XM22HFF/p1676112447571609
    // @SerialName("map") val map: String,
    @Transient val map: String = URLBuilder(mapItems)
        .apply { encodedPath = "${pathSegments[1]}/${pathSegments[2]}/${pathSegments[3]}/Maps/{mapId}" }
        .buildString(),

    // Example: "https://cds.dev-us1.twilio.com/v3/Lists",
    @SerialName("lists") val lists: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Lists/{listId}/Items"
    @SerialName("list_items") val listItems: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Lists/{listId}/Items/{itemId}",
    @SerialName("list_item") val listItem: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Lists/{listId}",
    // TODO:
    // Cannot remove list using received endpoint. So temporary hardcode it until the reason is clear:
    // See: https://twilio.slack.com/archives/C0XM22HFF/p1676112447571609
    // @SerialName("list") val list: String,
    @Transient val list: String = URLBuilder(listItems)
        .apply { encodedPath = "${pathSegments[1]}/${pathSegments[2]}/${pathSegments[3]}/Lists/{listId}" }
        .buildString(),

    // Example: "https://cds.dev-us1.twilio.com/v3/Documents"
    @SerialName("documents") val documents: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Documents/{id}"
    // TODO:
    // Cannot remove document using received endpoint. So temporary hardcode it until the reason is clear:
    // See: https://twilio.slack.com/archives/C0XM22HFF/p1676112447571609
    // @SerialName("document") val document: String,
    @Transient val document: String = URLBuilder(listItems)
        .apply { encodedPath = "${pathSegments[1]}/${pathSegments[2]}/${pathSegments[3]}/Documents/{documentId}" }
        .buildString(),

    // Example: "https://cds.dev-us1.twilio.com/v3/Streams"
    @SerialName("streams") val streams: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Streams/{streamId}",
    // TODO:
    // Cannot remove stream using received endpoint. So temporary hardcode it until the reason is clear:
    // See: https://twilio.slack.com/archives/C0XM22HFF/p1676112447571609
    // @SerialName("stream")
    @Transient val stream: String = URLBuilder(listItems)
        .apply { encodedPath = "${pathSegments[1]}/${pathSegments[2]}/${pathSegments[3]}/Streams/{streamId}" }
        .buildString(),

    // Example: "https://cds.dev-us1.twilio.com/v3/Services/IS58f069e48ec6d14f7b491641bef99db3/Streams/{streamId}/Messages",
    @SerialName("stream_messages") val streamMessages: String,

    // Example: "https://cds.dev-us1.twilio.com/v3/Insights/{indexName}/Items",
    @SerialName("insights_items") val insightsItems: String,
)

@Serializable
internal data class StreamMetadataResponse(
    @SerialName("sid") val sid: String,
    @SerialName("unique_name") val uniqueName: String? = null,
    @SerialName("date_expires") val dateExpires: Instant? = null,
)

@Serializable
internal data class StreamPublishMessageResponse(
    @SerialName("sid") val sid: String,
)

@Serializable
internal data class DocumentMetadataResponse(
    @SerialName("sid") val sid: String,
    @SerialName("unique_name") val uniqueName: String? = null,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("date_updated") val dateUpdated: Instant,
    @SerialName("date_expires") val dateExpires: Instant? = null,
    @SerialName("revision") val revision: String,
    @SerialName("last_event_id") val lastEventId: Long,
    // The 'data' field present in reply to GET request to the documentUrl, but missed in reply to POST request
    @SerialName("data") val data: JsonObject? = null,
)

@Serializable
internal data class CollectionMetadataResponse(
    @SerialName("sid") val sid: String,
    @SerialName("unique_name") val uniqueName: String? = null,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("date_updated") val dateUpdated: Instant,
    @SerialName("date_expires") val dateExpires: Instant? = null,
    @SerialName("revision") val revision: String,
    @SerialName("last_event_id") val lastEventId: Long,
)

@Serializable
internal data class MapItemsDataResponse(
    @SerialName("items") val items: List<MapItemDataResponse>,
    @SerialName("meta") val meta: CollectionItemMetadataResponse,
)

@Serializable
internal data class MapItemDataResponse(
    @SerialName("map_sid") val mapSid: String,
    @SerialName("key") val key: String,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("date_updated") val dateUpdated: Instant,
    @SerialName("date_expires") val dateExpires: Instant? = null,
    @SerialName("revision") val revision: String,
    @SerialName("last_event_id") val lastEventId: Long,
    // The 'data' field present in reply to GET requests, but absent from the reply to POST and DELETE requests
    @SerialName("data") val data: JsonObject? = null,
)

@Serializable
internal data class ListItemsDataResponse(
    @SerialName("items") val items: List<ListItemDataResponse>,
    @SerialName("meta") val meta: CollectionItemMetadataResponse,
)

@Serializable
internal data class ListItemDataResponse(
    @SerialName("list_sid") val listSid: String,
    @SerialName("index") val index: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("date_updated") val dateUpdated: Instant,
    @SerialName("date_expires") val dateExpires: Instant? = null,
    @SerialName("revision") val revision: String,
    @SerialName("last_event_id") val lastEventId: Long,
    // The 'data' field present in reply to GET requests, but absent from the reply to POST and DELETE requests
    @SerialName("data") val data: JsonObject? = null,
)

@Serializable
internal data class CollectionItemMetadataResponse(
    @SerialName("next_token") val nextToken: String? = null,
    @SerialName("previous_token") val prevToken: String? = null,
)

internal data class CollectionItemsDataResponse(
    val items: List<CollectionItemData>,
    val meta: CollectionItemMetadataResponse,
)

internal val CollectionItemsDataResponse.isCollectionEmpty: Boolean
    get() = meta.prevToken == null && meta.nextToken == null && items.isEmpty()

internal fun CollectionItemsDataResponse.beginCollectionItemIdOrNull(queryOrder: QueryOrder) = when (queryOrder) {
    Ascending -> if (meta.prevToken == null) items.firstOrNull()?.itemId else null
    Descending -> if (meta.nextToken == null) items.lastOrNull()?.itemId else null
}

internal fun CollectionItemsDataResponse.endCollectionItemIdOrNull(queryOrder: QueryOrder) = when (queryOrder) {
    Ascending -> if (meta.nextToken == null) items.lastOrNull()?.itemId else null
    Descending -> if (meta.prevToken == null) items.firstOrNull()?.itemId else null
}

internal fun MapItemsDataResponse.toCollectionItemsDataResponse() = CollectionItemsDataResponse(
    items = items.map { it.toCollectionItemData() },
    meta = meta,
)

internal fun ListItemsDataResponse.toCollectionItemsDataResponse() = CollectionItemsDataResponse(
    items = items.map { it.toCollectionItemData() },
    meta = meta,
)

internal fun MapItemDataResponse.toCollectionItemData() = CollectionItemData(
    collectionSid = mapSid,
    itemId = CollectionItemId.Key(key),
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    data = checkNotNull(data) {
        "null data in the MapItemDataResponse must be replaced with actual data before " +
                "call toCollectionItemDataResponse()"
    },
    isLeftBound = true, // Bounds will be rewritten on put to cache anyway
    isRightBound = true,
    isRemoved = false,
)

internal fun ListItemDataResponse.toCollectionItemData() = CollectionItemData(
    collectionSid = listSid,
    itemId = CollectionItemId.Index(index),
    dateCreated = dateCreated,
    dateUpdated = dateUpdated,
    dateExpires = dateExpires,
    revision = revision,
    lastEventId = lastEventId,
    data = checkNotNull(data) {
        "null data in the ListItemDataResponse must be replaced with actual data before " +
                "call toCollectionItemDataResponse()"
    },
    isLeftBound = true, // Bounds will be rewritten on put to cache anyway
    isRightBound = true,
    isRemoved = false,
)
