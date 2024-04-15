//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.repository

import com.twilio.sync.subscriptions.RemoteEvent
import com.twilio.util.json
import com.twilio.util.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal fun Flow<RemoteEvent>.parseNotification(): Flow<Notification> {
    return map { remoteEvent ->
        val result = runCatching { remoteEvent.toNotification() }
        val notification = result.getOrElse { t ->
            logger.w(t) { "Cannot parse remote event $remoteEvent" }
            return@map UnknownNotification(remoteEvent)
        }
        return@map notification
    }
}

private fun RemoteEvent.toNotification() = when(eventType) {

    "stream_message_published" ->
        json.decodeFromJsonElement<StreamMessagePublishedNotification>(event)

    "stream_removed" ->
        json.decodeFromJsonElement<StreamRemovedNotification>(event)

    "document_updated" ->
        json.decodeFromJsonElement<DocumentUpdatedNotification>(event)

    "document_removed" ->
        json.decodeFromJsonElement<DocumentRemovedNotification>(event)

    "map_item_added" ->
        json.decodeFromJsonElement<MapItemAddedNotification>(event)

    "map_item_updated" ->
        json.decodeFromJsonElement<MapItemUpdatedNotification>(event)

    "map_item_removed" ->
        json.decodeFromJsonElement<MapItemRemovedNotification>(event)

    "map_removed" ->
        json.decodeFromJsonElement<MapRemovedNotification>(event)

    "list_item_added" ->
        json.decodeFromJsonElement<ListItemAddedNotification>(event)

    "list_item_updated" ->
        json.decodeFromJsonElement<ListItemUpdatedNotification>(event)

    "list_item_removed" ->
        json.decodeFromJsonElement<ListItemRemovedNotification>(event)

    "list_removed" ->
        json.decodeFromJsonElement<ListRemovedNotification>(event)

    else -> UnknownNotification(remoteEvent = this)
}

@Serializable
internal sealed interface Notification

internal data class UnknownNotification(
    val remoteEvent: RemoteEvent,
) : Notification

@Serializable
internal data class StreamMessagePublishedNotification(
    @SerialName("stream_sid") val streamSid: String,
    @SerialName("message_sid") val messageSid: String,
    @SerialName("message_data") val messageData: JsonObject,
) : Notification

@Serializable
internal data class StreamRemovedNotification(
    @SerialName("stream_sid") val streamSid: String,
) : Notification

@Serializable
internal data class DocumentUpdatedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("document_revision") val revision: String,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("document_sid") val documentSid: String,
    @SerialName("document_data") val data: JsonObject,
    @SerialName("document_unique_name") val uniqueName: String? = null,
    @SerialName("date_expires") val dateExpires: Instant? = null,
) : Notification

@Serializable
internal data class DocumentRemovedNotification(
    @SerialName("document_sid") val documentSid: String,
) : Notification

@Serializable
internal data class MapItemAddedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("map_sid") val mapSid: String,
    @SerialName("item_key") val key: String,
    @SerialName("item_data") val data: JsonObject,
    @SerialName("item_revision") val revision: String,
    @SerialName("date_expires") val dateExpires: Instant? = null,
) : Notification

@Serializable
internal data class MapItemUpdatedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("map_sid") val mapSid: String,
    @SerialName("item_key") val key: String,
    @SerialName("item_data") val data: JsonObject,
    @SerialName("item_revision") val revision: String,
    @SerialName("date_expires") val dateExpires: Instant? = null,
) : Notification

@Serializable
internal data class MapItemRemovedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("map_sid") val mapSid: String,
    @SerialName("item_key") val key: String,
) : Notification

@Serializable
internal data class MapRemovedNotification(
    @SerialName("map_sid") val mapSid: String,
) : Notification

@Serializable
internal data class ListItemAddedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("list_sid") val listSid: String,
    @SerialName("item_index") val index: Long,
    @SerialName("item_data") val data: JsonObject,
    @SerialName("item_revision") val revision: String,
    @SerialName("date_expires") val dateExpires: Instant? = null,
) : Notification

@Serializable
internal data class ListItemUpdatedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("list_sid") val listSid: String,
    @SerialName("item_index") val index: Long,
    @SerialName("item_data") val data: JsonObject,
    @SerialName("item_revision") val revision: String,
    @SerialName("date_expires") val dateExpires: Instant? = null,
) : Notification

@Serializable
internal data class ListItemRemovedNotification(
    @SerialName("id") val eventId: Long,
    @SerialName("date_created") val dateCreated: Instant,
    @SerialName("list_sid") val listSid: String,
    @SerialName("item_index") val index: Long,
) : Notification

@Serializable
internal data class ListRemovedNotification(
    @SerialName("list_sid") val listSid: String,
) : Notification
