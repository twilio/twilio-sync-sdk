//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.DocumentCreateOperation
import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.emptyJsonObject
import com.twilio.util.generateSID
import com.twilio.util.json
import io.ktor.http.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@ExcludeFromInstrumentedTests
internal class DocumentCreateOperationTest : DocumentMetadataOperationTest() {

    private var uniqueName: String? = null
    private val sid = "TO000"
    private val documentUrl = "fakeDocumentUrl"
    private val revision = "0"
    private val generatedSid = "TOGENERATED"
    private val expectedDocumentMetadataResponse = DocumentMetadataResponse(
        sid = sid,
        dateCreated = Instant.DISTANT_PAST,
        dateUpdated = Instant.DISTANT_PAST,
        revision = revision,
        lastEventId = 0,
    )
    private val httpRequestSlot = slot<HttpRequest>()

    override fun createOperation(config: CommandsConfig) = DocumentCreateOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        documentsUrl = documentUrl,
        uniqueName = uniqueName,
        ttl = 10.minutes,
    )

    @BeforeTest
    override fun setUp() {
        super.setUp()

        mockkStatic("com.twilio.util.InternalUtilsKt")
        uniqueName = null

        val testPayload = json.encodeToString(expectedDocumentMetadataResponse)
        coEvery { twilsock.sendRequest(capture(httpRequestSlot)) } returns testHttpResponse(
            HttpStatusCode.OK,
            payload = testPayload
        )
        every { generateSID(any()) } returns generatedSid
    }

    @Test
    fun makeRequestWithoutUniqueName() = runTest {
        val operation = createOperation()
        operation.execute(twilsock)
        val actualDocumentMetadataResponse = operation.awaitResult()
        val expectedPayload = buildJsonObject {
            put("ttl", 600)
        }.toString()

        assert(expectedPayload, actualDocumentMetadataResponse)
    }

    @Test
    fun makeRequestWithUniqueName() = runTest {
        uniqueName = "uniqueName"
        val operation = createOperation()
        operation.execute(twilsock)
        val actualDocumentMetadataResponse = operation.awaitResult()
        val expectedPayload = buildJsonObject {
            put("unique_name", uniqueName)
            put("ttl", 600)
        }.toString()

        assert(expectedPayload, actualDocumentMetadataResponse)
    }

    private fun assert(
        expectedPayload: String,
        actualDocumentMetadataResponse: DocumentMetadataResponse
    ) {
        verify { generateSID("RQ") }
        assertEquals(documentUrl, httpRequestSlot.captured.url)
        assertEquals(HttpMethod.POST, httpRequestSlot.captured.method)
        assertEquals(expectedPayload, httpRequestSlot.captured.payload)
        assertEquals(expectedDocumentMetadataResponse, actualDocumentMetadataResponse)
    }
}
