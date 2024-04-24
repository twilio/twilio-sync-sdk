//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.DocumentMetadataResponse
import com.twilio.sync.operations.DocumentOpenOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.generateSID
import com.twilio.util.json
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

@ExcludeFromInstrumentedTests
internal class DocumentOpenOperationTest : DocumentMetadataOperationTest() {

    private val documentUrl = "fakeDocumentUrl"

    override fun createOperation(config: CommandsConfig) = DocumentOpenOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        documentUrl = documentUrl,
    )

    @Test
    fun request() = runTest {
        val operation = createOperation()
        operation.execute(twilsock)
        val request = twilsock.captureSentRequest()

        assertEquals(documentUrl, request.url)
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("", request.payload)
    }
}
