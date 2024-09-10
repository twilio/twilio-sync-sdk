//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations.media

import com.twilio.conversations.MediaCategory
import com.twilio.conversations.MediaUploadListener
import com.twilio.util.asInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import platform.Foundation.NSInputStream

fun createMediaClient(
    serviceUrl: String,
    mediaSetUrl: String,
    productId: String,
    token: String,
    maxActiveUploads: Int,
    httpConnectionTimeout: Int,
): MediaClientIos {

    val httpClient = createHttpClient(httpConnectionTimeout, "") // TODO: pass and apply certificates
    val transport = MediaTransportImpl(token, serviceUrl, mediaSetUrl, productId, httpClient)

    return MediaClientIosImpl(transport, maxActiveUploads)
}

fun createMediaUploadItem(
    inputStream: NSInputStream,
    contentType: String,
    category: MediaCategory,
    filename: String? = null,
    listener: MediaUploadListener? = null,
) = MediaUploadItem(inputStream.asInput(), contentType, category, filename, listener)

internal actual fun createHttpClient(
    httpConnectionTimeout: Int,
    certificates: String,
    useCertificates: Boolean,
    useProxy: Boolean  // TODO: useProxy is unused. Handle useProxy properly
) = HttpClient(Darwin) {
    expectSuccess = true
    engine {
        configureSession {
            timeoutIntervalForRequest = httpConnectionTimeout / 1000.0
        }
    }
    install(ContentNegotiation) {
        json(
            Json { ignoreUnknownKeys = true }
        )
    }
}
