//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.integration

import com.twilio.test.util.joinLines
import com.twilio.test.util.runTest
import com.twilio.twilsock.client.TwilsockMessage
import com.twilio.twilsock.client.TwilsockMessage.Method.NOTIFICATION
import com.twilio.twilsock.client.TwilsockMessage.Method.REPLY
import com.twilio.twilsock.client.TwilsockMessage.Method.UPSTREAM_REQUEST
import com.twilio.twilsock.client.encode
import com.twilio.twilsock.client.parse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TwilsockMessageTest {

    @Test
    fun encodeWithoutPayload() {
        val message = TwilsockMessage(
            requestId = "TMf0f85835be6f4111999fd3154378b7a5",
            method = REPLY,
            headers = buildJsonObject {
                putJsonObject("status") {
                    put("code", 200)
                    put("status", "ok")
                }
            }
        )

        val expectedHeaders =
            """{"method":"reply","id":"TMf0f85835be6f4111999fd3154378b7a5","status":{"code":200,"status":"ok"}}"""
        val expectedMessage = "TWILSOCK V3.0 96\r\n$expectedHeaders\r\n"

        val encodedMessage = message.encode()
        assertEquals(expectedMessage, encodedMessage)
    }

    @Test
    fun encodeWithPayload() {
        val expectedHeader = """
            {
               "method":"message",
               "id":"RQd79c5fac3f0141d3aafa9faf8d5f30bf",
               "payload_size":302,
               "payload_type":"application/json; charset=utf-8",
               "active_grant":"ip_messaging",
               "http_request":{
                  "headers":{
                     "Content-Type":"application/json; charset=utf-8",
                     "Twilio-Request-Id":"RQ846e54a4729d4bcbad7ad5ee71efe097"
                  },
                  "host":"cds.us1.twilio.com",
                  "method":"POST",
                  "path":"/v4/Subscriptions"
               }
            }
        """.joinLines()

        val expectedPayload = """
            {
               "action":"establish",
               "correlation_id":379312,
               "event_protocol_version":4,
               "requests":[
                  {
                     "last_event_id":2,
                     "object_sid":"MP21275e23e81a46a884e8c53aeb86ae72",
                     "object_type":"map"
                  },
                  {
                     "last_event_id":-1,
                     "object_sid":"MPfec357a6876944f4b1c4726e8dea69b5",
                     "object_type":"map"
                  }
               ],
               "retried_requests":0,
               "ttl_in_s":-1
            }
        """.joinLines()

        val expectedMessage = "TWILSOCK V3.0 ${expectedHeader.length}\r\n$expectedHeader\r\n$expectedPayload\r\n"

        val message = TwilsockMessage(
            requestId = "RQd79c5fac3f0141d3aafa9faf8d5f30bf",
            method = UPSTREAM_REQUEST,
            headers = Json.parseToJsonElement(expectedHeader) as JsonObject,
            payloadType = "application/json; charset=utf-8",
            payload = expectedPayload,
        )

        val encodedMessage = message.encode()
        assertEquals(expectedMessage, encodedMessage)
    }

    @Test
    fun parseWithoutPayload() = runTest {
        val rawHeaders =
            """{"id":"TMf0f85835be6f4111999fd3154378b7a5","method":"reply","status":{"code":200,"status":"ok"}}"""
        val rawMessage = "TWILSOCK V3.0 96\r\n$rawHeaders\r\n"

        val message = TwilsockMessage.parse(rawMessage)

        assertEquals("TMf0f85835be6f4111999fd3154378b7a5", message.requestId)
        assertEquals(REPLY, message.method)
        assertEquals(rawHeaders, message.headers.toString())
        assertTrue(message.payloadType.isEmpty())
        assertTrue(message.payload.isEmpty())
    }

    @Test
    fun parseWithPayload() = runTest {
        val rawHeaders = """
            {
                "method":"notification",
                "id":"TMbbc7f5e48e4b469cb29d1db1c021df4f",
                "payload_size":340,
                "payload_type":"application/json",
                "message_type":"twilio.sync.event",
                "notification_ctx_id":""            
            }
        """.joinLines()

        val rawPayload = """
            {
                "event_type":"subscription_established",
                "correlation_id":667801,
                "event_protocol_version":4,
                "events":[
                    {
                        "object_sid":"MP612542215f19400d9b5c9f8fbf56d32c",
                        "object_type":"map",
                        "replay_status":"completed",
                        "last_event_id":2
                    },
                    {
                        "object_sid":"MP3a9fade749e64172ba6c7755ac9f7acb",
                        "object_type":"map",
                        "replay_status":"completed",
                        "last_event_id":-1
                    }
                ]
            }
        """.joinLines()

        val rawMessage = "TWILSOCK V3.0 ${rawHeaders.length}\r\n$rawHeaders\r\n$rawPayload\r\n"

        val message = TwilsockMessage.parse(rawMessage)

        assertEquals("TMbbc7f5e48e4b469cb29d1db1c021df4f", message.requestId)
        assertEquals(NOTIFICATION, message.method)
        assertEquals(rawHeaders, message.headers.toString())
        assertEquals("application/json", message.payloadType)
        assertEquals(rawPayload, message.payload)
    }

    @Test
    fun parseError() = runTest {
        suspend fun assertParseError(data: String) {
            val result = runCatching { TwilsockMessage.parse(data) }
            assertTrue(result.isFailure)
        }

        assertParseError("")                    // empty message
        assertParseError("TWILSOCK V3.0")       // no header size
        assertParseError("TWILSOCK V3.0 100")   // no headers

        val headersJson = buildJsonObject {
            put("id", "TMf0f85835be6f4111999fd3154378b7a5")
            put("method", "reply")
            putJsonObject("status") {
                put("code", 200)
                put("status", "ok")
            }
        }
        val headers = headersJson.toString()

        val result = runCatching {
            val data = "TWILSOCK V3.0 ${headers.length}\r\n$headers\r\n"
            TwilsockMessage.parse(data)
        }
        assertTrue(result.isSuccess) // double check that correct variant works

        assertParseError("TWILSOCK V3.0 ${headers.length}$headers")                           // no "\r\n" before headers
        assertParseError("TWILSOCK V3.0 ${headers.length + 1}\r\n$headers\r\n")               // wrong size

        val noIdHeaders = JsonObject(headersJson - "id").toString()
        assertParseError("TWILSOCK V3.0 ${noIdHeaders.length}\r\n$noIdHeaders\r\n")           // no request id

        val noMethodHeaders = JsonObject(headersJson - "method").toString()
        assertParseError("TWILSOCK V3.0 ${noMethodHeaders.length}\r\n$noMethodHeaders\r\n")   // no method
    }
}
