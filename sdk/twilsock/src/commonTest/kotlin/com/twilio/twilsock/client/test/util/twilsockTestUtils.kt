//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.util

import com.twilio.twilsock.client.TwilsockTransportListener
import com.twilio.util.ErrorInfo

fun TwilsockTransportListener(
    onTransportConnected: () -> Unit = {},
    onTransportDisconnected: (errorInfo: ErrorInfo) -> Unit = {},
    onMessageReceived: (ByteArray) -> Unit = {},
) = object : TwilsockTransportListener {
    override fun onTransportConnected() = onTransportConnected()

    override fun onTransportDisconnected(errorInfo: ErrorInfo) = onTransportDisconnected(errorInfo)

    override fun onMessageReceived(data: ByteArray) = onMessageReceived(data)
}
