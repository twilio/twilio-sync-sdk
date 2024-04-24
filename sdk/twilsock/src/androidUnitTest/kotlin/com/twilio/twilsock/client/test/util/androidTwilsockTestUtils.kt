//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.util

import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.TwilsockMessage
import com.twilio.twilsock.client.TwilsockObserver
import com.twilio.twilsock.client.TwilsockTransport
import com.twilio.twilsock.client.parse
import com.twilio.util.ErrorInfo
import io.mockk.slot

internal suspend fun TwilsockTransport.captureSentString(): String {
    val sentBytes = slot<ByteArray>()
    waitAndVerify { sendMessage(capture(sentBytes)) }
    return sentBytes.captured.decodeToString()
}

internal suspend fun TwilsockTransport.captureSentMessage(): TwilsockMessage {
    return TwilsockMessage.parse(captureSentString())
}

internal suspend fun TwilsockObserver.captureNonFatalError(): ErrorInfo {
    val errorInfoSlot = slot<ErrorInfo>()
    waitAndVerify { onNonFatalError(capture(errorInfoSlot)) }
    return errorInfoSlot.captured
}

internal suspend fun TwilsockObserver.captureFatalError(): ErrorInfo {
    val errorInfoSlot = slot<ErrorInfo>()
    waitAndVerify { onFatalError(capture(errorInfoSlot)) }
    return errorInfoSlot.captured
}
