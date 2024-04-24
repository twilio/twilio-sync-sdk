//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client

import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.TransportDisconnected
import com.twilio.util.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

actual class TwilsockTransport actual constructor(
    private val coroutineScope: CoroutineScope,
    private val connectTimeout: Duration,
    certificates: List<String>, // ignored on iOS, always uses system certificates
    private val listener: TwilsockTransportListener
) {
    private val state = atomic(State.DISCONNECTED)

    private var ktorClient: HttpClient? = null

    private var webSocket: DefaultClientWebSocketSession? = null

    actual fun connect(url: String, useProxy: Boolean) {
        logger.i("connect: $url")
        connectWebsocket(url, useProxy)
    }

    private fun connectWebsocket(url: String, useProxy: Boolean) = coroutineScope.launch {
        if (!state.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
            logger.w { "Cannot connect in state ${state.value}. Ignored." }
            return@launch
        }

        try {
            val client = HttpClient(Darwin) {
                engine {
                    configureSession {
                        if (!useProxy) {
                            connectionProxyDictionary = emptyMap<Any?, Any>()
                        }
                    }
                }
                install(WebSockets)
            }

            ktorClient = client
            webSocket = withTimeout(connectTimeout) { client.webSocketSession(url) }

            if (!state.compareAndSet(State.CONNECTING, State.CONNECTED)) {
                logger.w { "State has been changed while connecting: ${state.value}" }
                return@launch
            }

            notifyListener { onTransportConnected() }
            runReceiveLoop()
        } catch (t: Throwable) {
            logger.e("Error in connectWebsocket: ", t)
            doDisconnect(ErrorInfo(ErrorReason.Unknown, message = "Error in connectWebsocket: ${t.stackTraceToString()}"))
        }
    }

    private suspend fun runReceiveLoop() {
        val localWebSocket = checkNotNull(webSocket) { "runReceiveLoop: webSocket is null. Should never happen." }

        logger.v { "runReceiveLoop started: ${state.value}" }

        try {
            while (state.value == State.CONNECTED) {
                val message = localWebSocket.incoming.receive() as Frame.Binary
                logger.v { "onBinaryMessage: ${message.data.decodeToString()}" }
                notifyListener { onMessageReceived(message.data) }
            }

            logger.v { "runReceiveLoop disconnected: ${state.value}" }
        } catch (t: Throwable) {
            if (state.value != State.CONNECTED) {
                logger.v { "runReceiveLoop finished: ${state.value}" }
                return
            }

            if (localWebSocket != webSocket) {
                logger.v { "runReceiveLoop finished: webSocket changed" }
                return
            }

            doDisconnect(ErrorInfo(TransportDisconnected, message = "Error in runReceiveLoop: ${t.stackTraceToString()}"))
        }
    }

    actual fun sendMessage(bytes: ByteArray) {
        coroutineScope.launch {
            if (state.value != State.CONNECTED) {
                logger.w { "sendMessage: Cannot send in state ${state.value}. Ignored." }
                return@launch
            }

            try {
                webSocket?.send(bytes) ?: return@launch
                logger.v { "sendMessage: ${bytes.decodeToString()}" }
            } catch (t: Throwable) {
                logger.e("Error in sendMessage: ", t)
                doDisconnect(ErrorInfo(TransportDisconnected, message = "Error in sendMessage: ${t.stackTraceToString()}"))
            }
        }
    }

    actual fun disconnect(reason: String) {
        doDisconnect(ErrorInfo(TransportDisconnected, message = "Disconnect called: $reason"))
    }

    private fun doDisconnect(errorInfo: ErrorInfo) {
        val prevState = state.getAndSet(State.DISCONNECTED)
        logger.i { "doDisconnect($errorInfo): $prevState" }

        if (prevState != State.DISCONNECTED) {
            ktorClient?.close()
            notifyListener { onTransportDisconnected(errorInfo) }
        }
    }

    private inline fun notifyListener(crossinline block: suspend TwilsockTransportListener.() -> Unit) {
        coroutineScope.launch {
            listener.block()
        }
    }

    private enum class State {
        DISCONNECTED, CONNECTING, CONNECTED
    }
}
