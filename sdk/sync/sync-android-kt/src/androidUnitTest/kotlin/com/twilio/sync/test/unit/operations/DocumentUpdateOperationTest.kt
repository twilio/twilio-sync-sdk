//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.sync.operations.DocumentUpdateOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.emptyJsonObject
import com.twilio.util.json
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@ExcludeFromInstrumentedTests
internal class DocumentUpdateOperationTest : DocumentMetadataOperationTest() {

    private val data: JsonObject = mockk(relaxed = true)
    private val ttl: Duration = 60.seconds

    override fun createOperation(config: CommandsConfig) = DocumentUpdateOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        documentUrl = "fakeDocumentUrl",
        data = data,
        ttl = ttl,
    )

    @Test
    fun request() = runTest {
        val operation = createOperation()
        operation.execute(twilsock)
        val request = twilsock.captureSentRequest()

        val expectedPayload = buildJsonObject {
            put("data", data)
            put("ttl", 60)
        }.toString()

        assertEquals("fakeDocumentUrl", request.url)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(expectedPayload, request.payload)
    }
}
