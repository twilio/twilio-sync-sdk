//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:Suppress("DEPRECATION")

package com.twilio.conversations.media

import com.twilio.util.ErrorReason.MediaFetchError
import com.twilio.util.ErrorReason.MediaUploadError
import com.twilio.util.getOrThrowTwilioException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.escapeIfNeeded
import io.ktor.utils.io.core.Input
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class MediaSetItem(
    @SerialName("media_record")
    val mediaResponse: MediaResponse,
)

@Serializable
private data class MediaResponse(
    val sid: String,
    val links: Links,
)

@Serializable
private data class Links(
    @SerialName("content_direct_temporary")
    val temporaryContentUrl: String = ""
)

@Serializable
private data class MediaSetCommand(
    @Required
    val command: String = "get",
    val list: List<MediaSid>
)

@Serializable
private data class MediaSid(
    @SerialName("media_sid")
    val mediaSid: String
)

internal interface MediaTransport {

    var token: String

    suspend fun uploadFile(filename: String?, contentType: String, category: String, fileInput: Input): String

    suspend fun downloadFileAsText(url: String): String

    suspend fun getTemporaryContentUrl(mediaSid: String): String

    suspend fun getTemporaryContentUrlList(mediaSids: List<String>): Map<String, String>

    fun shutdown()
}

internal class MediaTransportImpl(
    override var token: String,
    private val serviceUrl: String,
    private val mediaSetUrl: String,
    private val productId: String,
    private val httpClient: HttpClient,
) : MediaTransport {

    private val buildHeaders: HttpRequestBuilder.() -> Unit = {
        headers {
            append("X-Twilio-Token", token)
            append("X-Twilio-Product-Id", productId)
        }
    }

    override suspend fun uploadFile(filename: String?, contentType: String, category: String, fileInput: Input): String {
        val fileHeaders = Headers.build {
            filename?.let { append(HttpHeaders.ContentDisposition, "filename=${filename.escapeIfNeeded()}") }
            append(HttpHeaders.ContentType, contentType)
        }

        val formData = formData {
            appendInput("file", fileHeaders) { fileInput }
        }

        val result = runCatching {
            val url = "$serviceUrl?Category=${category.escapeIfNeeded()}"
            httpClient.submitFormWithBinaryData(url, formData, buildHeaders).body<MediaResponse>()
        }

        val mediaResponse = result.getOrThrowTwilioException(MediaUploadError)
        return mediaResponse.sid
    }

    override suspend fun downloadFileAsText(url: String): String = httpClient.get(url).bodyAsText()

    override suspend fun getTemporaryContentUrl(mediaSid: String): String {
        val url = URLBuilder(serviceUrl)
            .appendPathSegments(mediaSid)
            .build()

        val result = runCatching { httpClient.get(url, buildHeaders).body<MediaResponse>() }
        val mediaResponse = result.getOrThrowTwilioException(MediaFetchError)
        return mediaResponse.links.temporaryContentUrl
    }

    override suspend fun getTemporaryContentUrlList(mediaSids: List<String>): Map<String, String> {
        val result = runCatching {
            httpClient.post(mediaSetUrl) {
                buildHeaders()
                contentType(ContentType.Application.Json)
                setBody(MediaSetCommand(list = mediaSids.map { MediaSid(it) }))
            }.body<List<MediaSetItem>>()
        }
        val mediaSetItems = result.getOrThrowTwilioException(MediaFetchError)
        return mediaSetItems
            .map { it.mediaResponse }
            .associate { it.sid to it.links.temporaryContentUrl }
    }

    override fun shutdown() = httpClient.close()
}
