//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.CollectionUpdateMetadataOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@ExcludeFromInstrumentedTests
internal open class CollectionUpdateMetadataOperationTest : CollectionMetadataOperationTest() {

    val fakeMapUrl = "fakeMapUrl"

    val ttl = 30.seconds

    override fun createOperation(config: CommandsConfig): BaseCommand<*> = CollectionUpdateMetadataOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        collectionUrl = fakeMapUrl,
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

        assertEquals(fakeMapUrl, request.url)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(config.httpTimeout, request.timeout)
        assertEquals(expectedPayload, request.payload)
    }
}
