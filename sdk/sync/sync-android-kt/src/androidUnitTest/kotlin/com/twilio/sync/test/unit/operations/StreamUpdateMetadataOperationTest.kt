//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.StreamUpdateMetadataOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@ExcludeFromInstrumentedTests
internal open class StreamUpdateMetadataOperationTest : StreamMetadataOperationTest() {

    val fakeStreamUrl = "fakeStreamUrl"

    val ttl = 30.seconds

    override fun createOperation(config: CommandsConfig): BaseCommand<*> = StreamUpdateMetadataOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        streamUrl = fakeStreamUrl,
        ttl = ttl,
    )

    @Test
    fun request() = runTest {
        val config = CommandsConfig(httpTimeout = generateRandomDuration())
        val operation = createOperation(config)
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        val expectedPayload = """
            {"ttl":${ttl.inWholeSeconds}}
        """.trimIndent()

        assertEquals(fakeStreamUrl, request.url)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(config.httpTimeout, request.timeout)
        assertEquals(expectedPayload, request.payload)
    }
}
