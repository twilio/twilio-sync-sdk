//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.StreamOpenOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.generateRandomString
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlin.test.Test
import kotlin.test.assertEquals

@ExcludeFromInstrumentedTests
internal class StreamOpenOperationTest : StreamMetadataOperationTest() {

    val fakeStreamUrl = "fakeStreamUrl"

    override fun createOperation(config: CommandsConfig) = StreamOpenOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        streamUrl = fakeStreamUrl,
    )

    @Test
    fun request() = runTest {
        val config = CommandsConfig(httpTimeout = generateRandomDuration())
        val operation = createOperation(config)
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        assertEquals(fakeStreamUrl, request.url)
        assertEquals(HttpMethod.GET, request.method)
        assertEquals(config.httpTimeout, request.timeout)
    }
}
