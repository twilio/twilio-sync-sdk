//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client

import com.twilio.util.ErrorInfo
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

expect class TwilsockTransport(
    coroutineScope: CoroutineScope,
    connectTimeout: Duration,
    certificates: List<String>,
    listener: TwilsockTransportListener,
) {
    fun connect(url: String, useProxy: Boolean = false)
    fun sendMessage(bytes: ByteArray)
    fun disconnect(reason: String)
}

typealias TwilsockTransportFactory = (
    coroutineScope: CoroutineScope,
    connectTimeout: Duration,
    certificates: List<String>,
    listener: TwilsockTransportListener
) -> TwilsockTransport

@Suppress("FunctionName")
internal fun TwilsockTransportFactory(
    coroutineScope: CoroutineScope,
    connectTimeout: Duration,
    certificates: List<String>,
    listener: TwilsockTransportListener
) = TwilsockTransport(coroutineScope, connectTimeout, certificates, listener)

interface TwilsockTransportListener {
    fun onTransportConnected()
    fun onTransportDisconnected(errorInfo: ErrorInfo)
    fun onMessageReceived(data: ByteArray)
}
