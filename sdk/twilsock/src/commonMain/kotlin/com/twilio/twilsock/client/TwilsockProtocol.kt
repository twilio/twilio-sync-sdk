//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client

import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.twilsock.util.MultiMap
import com.twilio.twilsock.util.toMultiMap
import com.twilio.util.AccountDescriptor
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class ClientMetadata(
    //! Runtime or OS that runs this app.
    @SerialName("env") val env: String? = null,         // will be "native" for C++ libs - unless it's xamarin
    @SerialName("envv") val envVer: String? = null,
    @SerialName("os") val os: String? = null,           // Windows, macOS, Android, iOS, Linux etc
    @SerialName("osv") val osVer: String? = null,       // 10, 10.14, 8.1, 12.0, etc
    @SerialName("osa") val osArch: String? = null,      // amd64, armeabi-v7a etc

    //! Device information.
    @SerialName("dev") val devModel: String? = null,    // device model - Galaxy Note S3, H20 etc
    @SerialName("devv") val devVendor: String? = null,  // Samsung, LG, HTC
    @SerialName("devt") val devType: String? = null,    // mobile, laptop, etc

    //! Application name.
    @SerialName("app") val appName: String? = null,     // User app name
    @SerialName("appv") val appVer: String? = null,     // User app version

    //! SDK type and version.
    @SerialName("type") val sdkType: String? = null,    // chat, conversations, sync, etc
    @SerialName("sdk") val sdk: String? = null,         // native, react, cordova, etc
    @SerialName("sdkv") val sdkVer: String? = null,     // shipped version of sdk
)

@Serializable
internal data class InitMessageHeaders(
    @SerialName("capabilities") val capabilities: List<String>,
    @SerialName("token") val token: String,
    @SerialName("continuation_token") val continuationToken: String? = null,
    @SerialName("registrations") val registrations: List<InitRegistration>? = null,
    @SerialName("tweaks") val tweaks: JsonObject? = null,
    @SerialName("metadata") val metadata: ClientMetadata? = null,
)

@Serializable
internal data class InitRegistration(
    @SerialName("product") val productId: String,
    @SerialName("type") @Required val type: String = "ers",
    @SerialName("notification_protocol_version") @Required val notificationProtocolVersion: Int = 4,
    @SerialName("message_types") val messageTypes: Set<String>,
)

@Serializable
internal data class ServerReplyHeaders(
    @SerialName("status") val status: Status,
    @SerialName("http_status") val httpStatus: Status = Status.Ok,
    @SerialName("continuation_token") val continuationToken: String = "",
    @SerialName("http_headers") val httpHeaders: JsonObject = buildJsonObject {},
    @SerialName("account_descriptor") val accountDescriptor: AccountDescriptor? = null,
)

@Serializable
internal data class Status(
    @SerialName("status") val status: String,
    @SerialName("code") val code: Int,
    @SerialName("errorCode") val errorCode: Int? = null, // should not serialise when sending replies
    @SerialName("description") val description: String? = null,
) {
    companion object {
        val Ok = Status(code = 200, status = "ok")
        val BadRequest = Status(code = 400, status = "Bad request")
    }
}

@Serializable
internal data class ServerReplyPayload(
    @SerialName("backoff_policy") val backoffPolicy: BackoffPolicy = BackoffPolicy(),
)

@Serializable
internal data class BackoffPolicy(
    @SerialName("reconnect_min_ms") val reconnectMinMilliseconds: Int = 1000,
    @SerialName("reconnect_max_ms") val reconnectMaxMilliseconds: Int = 2000,
)

@Serializable
internal data class UpstreamRequestMessageHeaders(
    @SerialName("active_grant") val activeGrant: String,

    @Serializable(with = HttpRequestSerializer::class)
    @SerialName("http_request") val httpRequest: HttpRequest,
)

private object HttpRequestSerializer : KSerializer<HttpRequest> {
    override val descriptor: SerialDescriptor = HttpRequestSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: HttpRequest) {
        val url = URLBuilder(value.url)
        val surrogate = HttpRequestSurrogate(
            host = url.host,
            path = url.encodedPath,
            method = value.method,
            params = url.encodedParameters.toMultiMap(),
            headers = value.headers,
        )
        encoder.encodeSerializableValue(HttpRequestSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): HttpRequest = error("never used")
}

@Serializable
private class HttpRequestSurrogate(
    @SerialName("host") val host: String,
    @SerialName("path") val path: String,
    @SerialName("method") val method: HttpMethod,
    @SerialName("params") val params: MultiMap<String, String>,
    @SerialName("headers") val headers: MultiMap<String, String>,
)

@Serializable
internal data class CloseMessageHeaders(
    @SerialName("status") val status: Status,
)

@Serializable
internal data class ClientUpdateMessageHeaders(
    @SerialName("client_update_type") val clientUpdateType: String = "",
)

@Serializable
internal data class NotificationMessageHeaders(
    @SerialName("message_type") val messageType: String = "",
)
