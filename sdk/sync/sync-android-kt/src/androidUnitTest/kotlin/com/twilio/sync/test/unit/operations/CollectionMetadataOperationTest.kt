//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit.operations

import com.twilio.sync.test.util.testCollectionMetadataResponse
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testHttpResponse
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.commands.BaseCommand
import com.twilio.twilsock.commands.CommandsConfig
import com.twilio.util.StateMachine.Matcher.Companion.any
import com.twilio.util.json
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.serialization.encodeToString
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal abstract class CollectionMetadataOperationTest {

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@CollectionMetadataOperationTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true
    }

    @Test
    fun makeRequest() = runTest {
        val expectedResponse = testCollectionMetadataResponse(sid = "MP000")
        val testPayload = json.encodeToString(expectedResponse)

        coEvery { twilsock.sendRequest(any()) } returns testHttpResponse(HttpStatusCode.OK, payload = testPayload)

        val operation = createOperation()
        operation.execute(twilsock)
        val actualResponse = operation.awaitResult()

        assertEquals(expectedResponse, actualResponse)
    }

    protected abstract fun createOperation(config: CommandsConfig = CommandsConfig()): BaseCommand<*>
}
