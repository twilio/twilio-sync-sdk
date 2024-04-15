//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.StreamPublishMessageOperation
import com.twilio.sync.operations.StreamPublishMessageResponse
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.util.json
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@ExcludeFromInstrumentedTests
internal class StreamPublishMessageOperationTest {

    val fakeMessagesUrl = "fakeMessagesUrl"

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@StreamPublishMessageOperationTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true
    }

    fun createOperation(config: CommandsConfig): BaseCommand<*> = StreamPublishMessageOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        messagesUrl = fakeMessagesUrl,
        data = buildJsonObject { put("data", "value") },
    )

    @Test
    fun makeRequest() = runTest {
        val expectedResponse = StreamPublishMessageResponse(sid = "TZ000")
        val testPayload = json.encodeToString(expectedResponse)

        coEvery { twilsock.sendRequest(any()) } returns testHttpResponse(HttpStatusCode.OK, payload = testPayload)

        val config = CommandsConfig(httpTimeout = generateRandomDuration())
        val operation = createOperation(config)
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        assertEquals(fakeMessagesUrl, request.url)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(config.httpTimeout, request.timeout)

        val actualResponse = operation.awaitResult()
        assertEquals(expectedResponse, actualResponse)
    }
}
