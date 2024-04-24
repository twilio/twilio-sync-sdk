//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client

import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketError.HOSTNAME_UNVERIFIED
import com.neovisionaries.ws.client.WebSocketError.SSL_HANDSHAKE_ERROR
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketExtension
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import com.twilio.twilsock.client.TwilsockTransport.State.CONNECTED
import com.twilio.twilsock.client.TwilsockTransport.State.CONNECTING
import com.twilio.twilsock.client.TwilsockTransport.State.DISCONNECTED
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.HostnameUnverified
import com.twilio.util.ErrorReason.SslHandshakeError
import com.twilio.util.ErrorReason.TransportDisconnected
import com.twilio.util.ErrorReason.Unknown
import com.twilio.twilsock.util.ProxyInfo
import com.twilio.twilsock.util.SslContext
import com.twilio.util.logger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

actual class TwilsockTransport actual constructor(
    private val coroutineScope: CoroutineScope,
    connectTimeout: Duration,
    certificates: List<String>,
    private val listener: TwilsockTransportListener
) {
    private val mState = AtomicReference(DISCONNECTED)

    private val webSocketFactory = WebSocketFactory()

    private var webSocket: WebSocket? = null

    @Synchronized
    actual fun connect(url: String, useProxy: Boolean) {
        logger.i("connect: $url")

        if (!mState.compareAndSet(DISCONNECTED, CONNECTING)) {
            logger.w("cannot connect in state" + mState.get() + ". Ignored.")
            return
        }

        try {
            setupProxy(useProxy)
            webSocket = webSocketFactory.createSocket(url).apply {
                addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
                isMissingCloseFrameAllowed = true
                addListener(object : WebSocketAdapter() {
                    override fun onConnected(websocket: WebSocket, headers: Map<String, List<String>>?) {
                        super.onConnected(websocket, headers)
                        val prevState = mState.getAndSet(CONNECTED)
                        logger.i("Connected: $prevState")
                        if (prevState != CONNECTED) {
                            notifyListener { onTransportConnected() }
                        }
                    }

                    override fun onConnectError(websocket: WebSocket, exception: WebSocketException?) {
                        super.onConnectError(websocket, exception)
                        logger.e("onConnectError: ", exception)

                        val errorInfo = when (exception?.error) {
                            HOSTNAME_UNVERIFIED -> ErrorInfo(HostnameUnverified)

                            SSL_HANDSHAKE_ERROR -> ErrorInfo(SslHandshakeError)

                            else -> ErrorInfo(Unknown, message = "Failed to connect")
                        }

                        doDisconnect(errorInfo)
                    }

                    override fun onDisconnected(
                        websocket: WebSocket,
                        serverCloseFrame: WebSocketFrame?,
                        clientCloseFrame: WebSocketFrame?,
                        closedByServer: Boolean
                    ) {
                        super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
                        logger.i("onDisconnected: by server=$closedByServer"
                                + "\nserverCloseFrame: " + serverCloseFrame
                                + "\nclientCloseFrame: " + clientCloseFrame)
                        doDisconnect(ErrorInfo(TransportDisconnected, message = "onDisconnected: by server=$closedByServer"))
                    }

                    override fun onBinaryMessage(websocket: WebSocket, binary: ByteArray) {
                        super.onBinaryMessage(websocket, binary)
                        logger.v { "onBinaryMessage: ${binary.decodeToString()}" }
                        notifyListener { onMessageReceived(binary) }
                    }
                })
            }
            webSocket!!.connectAsynchronously()
        } catch (t: Throwable) {
            logger.e("Error in connect: ", t)
            doDisconnect(ErrorInfo(Unknown, message = "Error in connect: ${t.stackTraceToString()}"))
        }
    }

    actual fun sendMessage(bytes: ByteArray) {
        webSocket?.sendBinary(bytes) ?: error("TwilsockTransport is not ready. Call connect() first")
        logger.v { "sendMessage: ${bytes.decodeToString()}" }
    }

    actual fun disconnect(reason: String) {
        doDisconnect(ErrorInfo(TransportDisconnected, message = "Disconnect called: $reason"))
    }

    @Synchronized
    private fun doDisconnect(errorInfo: ErrorInfo) {
        val prevState = mState.getAndSet(DISCONNECTED)
        logger.i("doDisconnect($errorInfo): $prevState")
        if (prevState != DISCONNECTED) {
            webSocket?.disconnect(1000)
            notifyListener { onTransportDisconnected(errorInfo) }
        }
    }

    private fun setupProxy(useProxy: Boolean) {
        val proxySettings = webSocketFactory.proxySettings
        proxySettings.reset()

        if (!useProxy) {
            return
        }

        val proxyInfo = ProxyInfo()
        if (proxyInfo.host == null) {
            logger.i("Proxy info is not set")
            return
        }

        logger.i("Using proxy: " + proxyInfo.host + ":" + proxyInfo.port)
        proxySettings
            .setHost(proxyInfo.host)
            .setPort(proxyInfo.port)
            .setCredentials(proxyInfo.user, proxyInfo.password)
    }

    private inline fun notifyListener(crossinline block: suspend TwilsockTransportListener.() -> Unit) {
        coroutineScope.launch {
            listener.block()
        }
    }

    private enum class State {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    init {
        webSocketFactory.connectionTimeout = connectTimeout.inWholeMilliseconds.toInt()
        webSocketFactory.sslContext = SslContext(certificates)
        logger.i("constructed connectionTimeout: ${webSocketFactory.connectionTimeout}")
    }
}
