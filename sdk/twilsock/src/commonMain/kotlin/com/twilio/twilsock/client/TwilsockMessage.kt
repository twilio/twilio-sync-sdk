//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client

import com.twilio.twilsock.client.TwilsockMessage.Headers
import com.twilio.twilsock.client.TwilsockMessage.Method.CLIENT_UPDATE
import com.twilio.twilsock.client.TwilsockMessage.Method.CLOSE
import com.twilio.twilsock.client.TwilsockMessage.Method.NOTIFICATION
import com.twilio.twilsock.client.TwilsockMessage.Method.PING
import com.twilio.twilsock.client.TwilsockMessage.Method.REPLY
import com.twilio.util.emptyJsonObject
import com.twilio.util.generateSID
import com.twilio.util.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private const val TWILSOCK_PREFIX = "TWILSOCK V3.0"

internal open class TwilsockMessage(
    val requestId: String = generateSID(prefix = "RQ"),
    val method: Method,
    val rawHeaders: String = "", // for optimise passing http response to JNI. TODO: remove when native SDK completed.
    val headers: JsonObject = emptyJsonObject(),
    val payloadType: String = "",
    val payload: String = "",
    val rawMessage: ByteArray? = null
){
    @Serializable
    enum class Method {
        @SerialName("init") INIT,
        @SerialName("update") UPDATE,
        @SerialName("ping") PING,
        @SerialName("close") CLOSE,
        @SerialName("notification") NOTIFICATION,
        @SerialName("message") UPSTREAM_REQUEST,
        @SerialName("reply") REPLY,
        @SerialName("client_update") CLIENT_UPDATE;
    }

    @Serializable
    data class Headers(
        @SerialName("method")
        val method: Method,
        @SerialName("id")
        val requestId: String,
        @SerialName("payload_size")
        val payloadSize: Int? = null,
        @SerialName("payload_type")
        val payloadType: String? = null,
    )

    companion object
}

internal class TwilsockReplyMessage(requestId: String, rawHeaders: String, headers: JsonObject, payloadType: String, payload: String)
    : TwilsockMessage(requestId, REPLY, rawHeaders, headers, payloadType, payload) {

    val replyHeaders: ServerReplyHeaders = json.decodeFromJsonElement(headers)

    val replyPayload: ServerReplyPayload by lazy {
        val result = runCatching { json.decodeFromString<ServerReplyPayload>(payload) }
        // ServerReplyPayload is optional. So return default value if it's empty.
        return@lazy result.getOrElse { ServerReplyPayload() }
    }
}

internal class TwilsockCloseMessage(requestId: String, rawHeaders: String, headers: JsonObject, payloadType: String, payload: String)
    : TwilsockMessage(requestId, CLOSE, rawHeaders, headers, payloadType, payload) {

    val status: Status = json.decodeFromJsonElement<CloseMessageHeaders>(headers).status
}

internal class TwilsockClientUpdateMessage(requestId: String, rawHeaders: String, headers: JsonObject, payloadType: String, payload: String)
    : TwilsockMessage(requestId, CLIENT_UPDATE, rawHeaders, headers, payloadType, payload) {

    val clientUpdateType: String = json.decodeFromJsonElement<ClientUpdateMessageHeaders>(headers).clientUpdateType
}

internal class TwilsockNotificationMessage(requestId: String, rawHeaders: String, headers: JsonObject, payloadType: String, payload: String)
    : TwilsockMessage(requestId, NOTIFICATION, rawHeaders, headers, payloadType, payload) {

    val messageType: String = json.decodeFromJsonElement<NotificationMessageHeaders>(headers).messageType
}

internal class TwilsockPingMessage(requestId: String, rawHeaders: String, headers: JsonObject, payloadType: String, payload: String)
    : TwilsockMessage(requestId, PING, rawHeaders, headers, payloadType, payload)

internal fun TwilsockMessage.encodeToByteArray(): ByteArray {
    return rawMessage ?: encode().encodeToByteArray()
}

internal fun TwilsockMessage.encode(): String {
    val headersToAdd = Headers(
        method,
        requestId,
        payload.encodeToByteArray().size.takeIf { payload.isNotEmpty() },
        payloadType.takeIf { payload.isNotEmpty() },
    )

    val headerJson = JsonObject(json.encodeToJsonElement(headersToAdd).jsonObject + headers)
    val header = headerJson.toString()
    val headerSize = header.encodeToByteArray().size
    val postfix = if (payload.isEmpty()) "" else "\r\n"

    return "$TWILSOCK_PREFIX $headerSize\r\n$header\r\n$payload$postfix"
}

@Throws(Throwable::class)
internal fun TwilsockMessage.Companion.parse(message: String): TwilsockMessage {
    require(message.startsWith(TWILSOCK_PREFIX)) { "Invalid twilsock prefix" }

    val (prefix, header, payload) = message.split("\r\n")
    val (_, _, headerSize) = prefix.split(' ')

    val actualHeaderSize = header.encodeToByteArray().size // To count UTF-8 bytes correctly

    require(actualHeaderSize == headerSize.toInt()) {
        "Invalid header size: expected = $headerSize; actual = $actualHeaderSize"
    }

    val headersJson = json.parseToJsonElement(header).jsonObject
    val headers = json.decodeFromJsonElement<Headers>(headersJson)
    val payloadType = headers.payloadType ?: ""

    return when (headers.method) {
        REPLY -> TwilsockReplyMessage(headers.requestId, header, headersJson, payloadType, payload)

        CLOSE -> TwilsockCloseMessage(headers.requestId, header, headersJson, payloadType, payload)

        CLIENT_UPDATE -> TwilsockClientUpdateMessage(headers.requestId, header, headersJson, payloadType, payload)

        NOTIFICATION -> TwilsockNotificationMessage(headers.requestId, header, headersJson, payloadType, payload)

        PING -> TwilsockPingMessage(headers.requestId, header, headersJson, payloadType, payload)

        else -> TwilsockMessage(headers.requestId, headers.method, header, headersJson, payloadType, payload)
    }
}
