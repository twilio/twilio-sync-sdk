//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.operations.CollectionOpenOperation
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.generateRandomDuration
import com.twilio.test.util.runTest
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.twilsock.util.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

@ExcludeFromInstrumentedTests
internal class CollectionOpenOperationTest : CollectionMetadataOperationTest() {

    val fakeMapUrl = "fakeMapUrl"

    override fun createOperation(config: CommandsConfig) = CollectionOpenOperation(
        coroutineScope = testCoroutineScope,
        config = config,
        collectionUrl = fakeMapUrl,
    )

    @Test
    fun request() = runTest {
        val config = CommandsConfig(httpTimeout = generateRandomDuration())
        val operation = createOperation(config)
        operation.execute(twilsock)

        val request = twilsock.captureSentRequest()

        assertEquals(fakeMapUrl, request.url)
        assertEquals(HttpMethod.GET, request.method)
        assertEquals(config.httpTimeout, request.timeout)
    }
}
