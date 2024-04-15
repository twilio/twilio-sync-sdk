//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.RemoveOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExcludeFromInstrumentedTests
internal class RemoveOperationTest {

    val fakeUrl = "fakeUrl"

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@RemoveOperationTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true
    }

    fun createOperation(config: CommandsConfig) = RemoveOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        url = fakeUrl,
    )

    @Test
    fun makeRequest() = runTest {
        val config = CommandsConfig(httpTimeout = generateRandomDuration())
        val operation = createOperation(config)

        coEvery { twilsock.sendRequest(any()) } returns testHttpResponse(HttpStatusCode.OK)

        operation.execute(twilsock)
        val request = twilsock.captureSentRequest()

        assertEquals(fakeUrl, request.url)
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals(config.httpTimeout, request.timeout)

        val result = runCatching { operation.awaitResult() }
        assertTrue { result.isSuccess }
    }
}
