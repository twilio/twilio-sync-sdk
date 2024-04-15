//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.unit.states

import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.twilioException
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorReason.TransportDisconnected
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ExcludeFromInstrumentedTests
class DisconnectedTwilsockTest : BaseTwilsockTest() {

    override fun setUp() = runTest {
        super.setUp()
        assertIs<Disconnected>(twilsock.state)
    }

    @Test
    fun connect() = runTest {
        twilsock.connect()

        waitAndVerify { twilsockObserver.onConnecting() }
        assertIs<Connecting>(twilsock.state)
    }

    @Test
    fun updateToken() = runTest {
        val newToken = "newToken"

        val result = runCatching { twilsock.updateToken(newToken) }

        waitAndVerify { twilsockObserver.onConnecting() }
        assertIs<Connecting>(twilsock.state)

        assertTrue { result.isSuccess } // token updated locally
        assertEquals(newToken, twilsock.token)
    }

    @Test
    fun sendRequest() = runTest {
        val httpRequest = HttpRequest("http://www.example.com")
        val result = runCatching { twilsock.sendRequest(httpRequest) }

        assertIs<Disconnected>(twilsock.state)

        assertTrue { result.isFailure }
        assertEquals(TransportDisconnected, result.twilioException.errorInfo.reason)
    }
}
