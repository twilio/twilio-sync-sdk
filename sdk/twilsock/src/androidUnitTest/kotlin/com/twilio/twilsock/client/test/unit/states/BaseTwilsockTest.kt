//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client.test.unit.states

import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.client.AuthData
import com.twilio.twilsock.client.ClientMetadata
import com.twilio.twilsock.client.ContinuationTokenStorage
import com.twilio.twilsock.client.TwilsockImpl
import com.twilio.twilsock.client.TwilsockObserver
import com.twilio.twilsock.client.TwilsockTransport
import com.twilio.twilsock.client.TwilsockTransportFactory
import com.twilio.twilsock.util.ConnectivityMonitor
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.test.BeforeTest

open class BaseTwilsockTest {

    internal lateinit var twilsock: TwilsockImpl

    @RelaxedMockK
    internal lateinit var continuationTokenStorage: ContinuationTokenStorage

    @RelaxedMockK
    internal lateinit var connectivityMonitor: ConnectivityMonitor

    @RelaxedMockK
    internal lateinit var twilsockTransportFactory: TwilsockTransportFactory

    @RelaxedMockK
    internal lateinit var twilsockTransport: TwilsockTransport

    @RelaxedMockK
    lateinit var twilsockObserver: TwilsockObserver

    var onConnectivityChanged = {}

    @BeforeTest
    open fun setUp() {
        setupTestLogging()
        MockKAnnotations.init(this@BaseTwilsockTest, relaxUnitFun = true)
        every { connectivityMonitor.isNetworkAvailable } returns true
        every { connectivityMonitor.onChanged = any() } propertyType onConnectivityChanged::class answers { onConnectivityChanged = value }
        every { twilsockTransportFactory(any(), any(), any(), any()) } returns twilsockTransport
        clearTwilsockObserverMock()

        val authData = AuthData(
            token = "testToken",
            activeGrant = "testActiveGrant",
            notificationProductId = "testNotificationProductId",
            certificates = emptyList(),
        )

        twilsock = TwilsockImpl(
            coroutineScope = testCoroutineScope,
            url = "testUrl",
            useProxy = false,
            authData,
            ClientMetadata(),
            continuationTokenStorage,
            connectivityMonitor,
            twilsockTransportFactory,
        )

        twilsock.addObserver(twilsockObserver)
    }

    fun clearTwilsockObserverMock() {
        clearMocks(twilsockObserver)
        every { twilsockObserver.onRawDataReceived } returns { _: ByteArray -> false }
    }
}
