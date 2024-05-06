//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.BaseMutateOperation
import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.sync.operations.DocumentMutateDataOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.json
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@ExcludeFromInstrumentedTests
internal class DocumentMutateOperationTest {

    private val sid = "TO000"
    private val currentRevision = "0"
    private val newRevision = "1"
    private val documentUrl = "fakeDocumentUrl"
    private val ttl: Duration = 60.seconds
    private val mutator: (JsonObject) -> JsonObject = mockk(relaxed = true)
    private val currentData: JsonObject = mockk(relaxed = true)
    private val mutatorSlot = slot<JsonObject>()
    private val httpRequestSlot = slot<HttpRequest>()

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    fun createOperation(config: CommandsConfig = CommandsConfig()) = DocumentMutateDataOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        documentUrl = documentUrl,
        currentData = BaseMutateOperation.CurrentData(currentRevision, currentData),
        ttl = ttl,
        mutateData = mutator,
    )

    @BeforeTest
    fun setUp() {
        setupTestLogging()
        MockKAnnotations.init(this@DocumentMutateOperationTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true

        every { mutator.invoke(capture(mutatorSlot)) } returns mockk()
    }

    @Test
    fun makeRequest() = runTest {
        val newData: JsonObject = mockk(relaxed = true)
        every { mutator.invoke(capture(mutatorSlot)) } returns newData
        coEvery { twilsock.sendRequest(capture(httpRequestSlot)) } returns testHttpResponse(
            HttpStatusCode.OK,
            payload = json.encodeToString(
                DocumentMetadataResponse(
                    sid = sid,
                    revision = newRevision,
                    lastEventId = 0,
                    dateCreated = Instant.DISTANT_PAST,
                    dateUpdated = Instant.DISTANT_PAST,
                )
            )
        )

        val operation = createOperation()
        operation.execute(twilsock)
        val actualDocumentMutateDataResult = operation.awaitResult()

        val expectedDocumentMutateDataResult = DocumentMetadataResponse(
            sid = sid,
            revision = newRevision,
            lastEventId = 0,
            dateCreated = Instant.DISTANT_PAST,
            dateUpdated = Instant.DISTANT_PAST,
            data = newData
        )
        val expectedPayload = buildJsonObject {
            put("data", newData)
            put("ttl", 60)
        }.toString()

        assertEquals(expectedDocumentMutateDataResult, actualDocumentMutateDataResult)
        assertEquals(mutatorSlot.captured, currentData)
        assertEquals("fakeDocumentUrl", httpRequestSlot.captured.url)
        assertEquals(HttpMethod.POST, httpRequestSlot.captured.method)
        assertEquals("0", httpRequestSlot.captured.headers["If-Match"]?.first())
        assertEquals(expectedPayload, httpRequestSlot.captured.payload)
    }

    @Test
    fun makeRequestWithRetryWhenPreConditionFailed() = runTest {
        val mutatedData: JsonObject = mockk(relaxed = true)
        val currentDataFromRemote: JsonObject = mockk(relaxed = true)
        val mutatorSlot = slot<JsonObject>()
        val retryMutatorSlot = slot<JsonObject>()
        val mutateRequestSlot = slot<HttpRequest>()
        val fetchCurrentDataRequestSlot = slot<HttpRequest>()
        val retryMutateRequestSlot = slot<HttpRequest>()

        every { mutator.invoke(any()) } returns mutatedData
        coEvery { twilsock.sendRequest(any()) } returnsMany
                listOf(
                    testHttpResponse(
                        HttpStatusCode.PreconditionFailed
                    ),
                    testHttpResponse(
                        httpStatus = HttpStatusCode.OK,
                        payload = json.encodeToString(
                            DocumentMetadataResponse(
                                sid = sid,
                                revision = newRevision,
                                lastEventId = 0,
                                dateCreated = Instant.DISTANT_PAST,
                                dateUpdated = Instant.DISTANT_PAST,
                                data = currentDataFromRemote
                            )
                        )
                    ),
                    testHttpResponse(
                        httpStatus = HttpStatusCode.OK,
                        payload = json.encodeToString(
                            DocumentMetadataResponse(
                                sid = sid,
                                revision = newRevision,
                                lastEventId = 0,
                                dateCreated = Instant.DISTANT_PAST,
                                dateUpdated = Instant.DISTANT_PAST,
                            )
                        )
                    )
                )

        val operation = createOperation()
        operation.execute(twilsock)
        val actualDocumentMutateDataResult = operation.awaitResult()

        val expectedDocumentMutateDataResult = DocumentMetadataResponse(
            sid = sid,
            revision = newRevision,
            lastEventId = 0,
            dateCreated = Instant.DISTANT_PAST,
            dateUpdated = Instant.DISTANT_PAST,
            data = mutatedData
        )
        val expectedMutateRequestPayload = buildJsonObject {
            put("data", mutatedData)
            put("ttl", 60)
        }.toString()
        val expectedRetryMutateRequestPayload = buildJsonObject {
            put("data", mutatedData)
            put("ttl", 60)
        }.toString()

        verifySequence {
            mutator.invoke(capture(mutatorSlot))
            mutator.invoke(capture(retryMutatorSlot))
        }
        coVerifySequence {
            twilsock.sendRequest(capture(mutateRequestSlot))
            twilsock.sendRequest(capture(fetchCurrentDataRequestSlot))
            twilsock.sendRequest(capture(retryMutateRequestSlot))
        }
        assertEquals(expectedDocumentMutateDataResult, actualDocumentMutateDataResult)

        assertEquals(mutatorSlot.captured, currentData)
        assertEquals(retryMutatorSlot.captured, currentDataFromRemote)

        assertEquals("fakeDocumentUrl", mutateRequestSlot.captured.url)
        assertEquals(HttpMethod.POST, mutateRequestSlot.captured.method)
        assertEquals("0", mutateRequestSlot.captured.headers["If-Match"]?.first())
        assertEquals(expectedMutateRequestPayload, mutateRequestSlot.captured.payload)

        assertEquals("fakeDocumentUrl", fetchCurrentDataRequestSlot.captured.url)
        assertEquals(HttpMethod.GET, fetchCurrentDataRequestSlot.captured.method)
        assertEquals("", fetchCurrentDataRequestSlot.captured.payload)

        assertEquals("fakeDocumentUrl", retryMutateRequestSlot.captured.url)
        assertEquals(HttpMethod.POST, retryMutateRequestSlot.captured.method)
        assertEquals("1", retryMutateRequestSlot.captured.headers["If-Match"]?.first())
        assertEquals(expectedRetryMutateRequestPayload, retryMutateRequestSlot.captured.payload)
    }

    @Test
    fun makeRequestShouldThrowError() = runTest {
        val newData: JsonObject = mockk(relaxed = true)
        every { mutator.invoke(capture(mutatorSlot)) } returns newData
        coEvery { twilsock.sendRequest(capture(httpRequestSlot)) } returns testHttpResponse(
            HttpStatusCode.Unauthorized
        )

        val operation = createOperation()
        operation.execute(twilsock)

        assertFails(message = "", block = { operation.awaitResult() })
    }
}
