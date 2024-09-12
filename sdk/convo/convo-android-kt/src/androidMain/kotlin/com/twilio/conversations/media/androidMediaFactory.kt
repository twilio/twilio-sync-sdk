//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.conversations.media

import com.twilio.conversations.MediaCategory
import com.twilio.conversations.MediaUploadListenerBuilder
import com.twilio.twilsock.util.ProxyInfo
import com.twilio.twilsock.util.SslContext
import com.twilio.util.logger
import com.twilio.util.splitCertificates
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.engine.android.AndroidEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.streams.asInput
import java.io.InputStream
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import kotlinx.serialization.json.Json

internal fun createMediaClient(
    serviceUrl: String,
    mediaSetUrl: String,
    productId: String,
    token: String,
    certificates: String,
    maxActiveUploads: Int,
    httpConnectionTimeout: Int,
    useProxy: Boolean
): MediaClient {

    val httpClient = createHttpClient(httpConnectionTimeout, certificates, useProxy = useProxy)
    val transport = MediaTransportImpl(token, serviceUrl, mediaSetUrl, productId, httpClient)

    return MediaClient(transport, maxActiveUploads)
}

internal inline fun createMediaUploadItem(
    inputStream: InputStream,
    contentType: String,
    category: MediaCategory,
    filename: String = "",
    listenerBuilder: MediaUploadListenerBuilder.() -> Unit = {}
) = MediaUploadItem(inputStream.asInput(), contentType, category, filename,
                    MediaUploadListenerBuilder().apply(listenerBuilder).build())

internal actual fun createHttpClient(
    httpConnectionTimeout: Int,
    certificates: String,
    useCertificates: Boolean,
    useProxy: Boolean,
): HttpClient {
    return HttpClient(Android) {
        expectSuccess = true
        engine {
            connectTimeout = httpConnectionTimeout
            socketTimeout = httpConnectionTimeout

            if (useCertificates) {
                sslManager = {
                    it.sslSocketFactory = SslContext(splitCertificates(certificates)).socketFactory
                }
            }

            setupProxy(useProxy)
        }
        install(ContentNegotiation) {
            json(
                Json { ignoreUnknownKeys = true }
            )
        }
    }
}

private fun AndroidEngineConfig.setupProxy(useProxy: Boolean) {
    proxy = Proxy.NO_PROXY

    if (!useProxy) {
        return
    }

    val proxyInfo = ProxyInfo()

    if (proxyInfo.host == null) {
        logger.i("Proxy info is not set")
        return
    }

    logger.i("AndroidEngineConfig: Using proxy: ${proxyInfo.host}:${proxyInfo.port}")

    if (proxyInfo.user != null) {
        Authenticator.setDefault(ProxyAuthenticator(proxyInfo))
    }

    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyInfo.host, proxyInfo.port))
}

private class ProxyAuthenticator(val proxyInfo: ProxyInfo) : Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestingHost == proxyInfo.host && requestingPort == proxyInfo.port) {
            logger.i("getPasswordAuthentication: return PasswordAuthentication")
            return PasswordAuthentication(proxyInfo.user, proxyInfo.password?.toCharArray())
        }
        logger.i("getPasswordAuthentication: return null")
        return null
    }
}
