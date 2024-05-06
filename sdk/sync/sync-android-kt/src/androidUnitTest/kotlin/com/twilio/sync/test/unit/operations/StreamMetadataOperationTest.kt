//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.StreamMetadataResponse
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
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

internal abstract class StreamMetadataOperationTest {

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@StreamMetadataOperationTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true
    }

    @Test
    fun makeRequest() = runTest {
        val expectedResponse = StreamMetadataResponse(sid = "TO000")
        val testPayload = json.encodeToString(expectedResponse)

        coEvery { twilsock.sendRequest(any()) } returns testHttpResponse(HttpStatusCode.OK, payload = testPayload)

        val operation = createOperation()
        operation.execute(twilsock)
        val actualResponse = operation.awaitResult()

        assertEquals(expectedResponse, actualResponse)
    }

    protected abstract fun createOperation(config: CommandsConfig = CommandsConfig()): BaseCommand<*>
}
