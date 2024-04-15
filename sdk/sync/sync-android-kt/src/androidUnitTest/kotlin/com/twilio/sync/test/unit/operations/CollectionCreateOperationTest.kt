//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.CollectionCreateOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.generateRandomString
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.minutes

@ExcludeFromInstrumentedTests
internal open class CollectionCreateOperationTest : CollectionMetadataOperationTest() {

    val fakeMapsUrl = "fakeMapsUrl"

    override fun createOperation(config: CommandsConfig): BaseCommand<*> = CollectionCreateOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        collectionsUrl = fakeMapsUrl,
        uniqueName = generateRandomString("uniqueName"),
        ttl = 10.minutes,
    )

    @Test
    fun request() = runTest {
        val config = CommandsConfig(httpTimeout = generateRandomDuration())
        val operation = createOperation(config)
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        assertEquals(fakeMapsUrl, request.url)
        assertEquals(HttpMethod.POST, request.method)
        assertEquals(config.httpTimeout, request.timeout)
    }

    @Test
    fun payloadWithUniqueNameAndTtl() = runTest {
        val uniqueName = generateRandomString("uniqueName")
        val ttl = 10.minutes

        val operation = CollectionCreateOperation(
            coroutineScope = testCoroutineScope,
            config = CommandsConfig(),
            collectionsUrl = fakeMapsUrl,
            uniqueName = uniqueName,
            ttl = ttl,
        )
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        val expectedPayload = """
            {"unique_name":"$uniqueName","ttl":${ttl.inWholeSeconds}}
        """.trimIndent()

        assertEquals(expectedPayload, request.payload)
    }

    @Test
    fun payloadWithUniqueNameOnly() = runTest {
        val uniqueName = generateRandomString("uniqueName")

        val operation = CollectionCreateOperation(
            coroutineScope = testCoroutineScope,
            config = CommandsConfig(),
            collectionsUrl = fakeMapsUrl,
            uniqueName = uniqueName,
            ttl = INFINITE,
        )
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        val expectedPayload = """
            {"unique_name":"$uniqueName","ttl":0}
        """.trimIndent()

        assertEquals(expectedPayload, request.payload)
    }

    @Test
    fun payloadWithTtlOnly() = runTest {
        val ttl = 10.minutes

        val operation = CollectionCreateOperation(
            coroutineScope = testCoroutineScope,
            config = CommandsConfig(),
            collectionsUrl = fakeMapsUrl,
            uniqueName = null,
            ttl = ttl,
        )
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        val expectedPayload = """
            {"ttl":${ttl.inWholeSeconds}}
        """.trimIndent()

        assertEquals(expectedPayload, request.payload)
    }

    @Test
    fun payloadWithoutUniqueNameAndTtl() = runTest {
        val operation = CollectionCreateOperation(
            coroutineScope = testCoroutineScope,
            config = CommandsConfig(),
            collectionsUrl = fakeMapsUrl,
            uniqueName = null,
            ttl = INFINITE,
        )
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        val expectedPayload = """
            {"ttl":0}
        """.trimIndent()

        assertEquals(expectedPayload, request.payload)
    }
}
