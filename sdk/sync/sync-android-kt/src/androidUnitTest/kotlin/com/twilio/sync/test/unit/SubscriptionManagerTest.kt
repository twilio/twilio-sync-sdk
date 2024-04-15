//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.unit

import com.twilio.sync.subscriptions.SubscriptionAction
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionRequestBody
import com.twilio.sync.subscriptions.SubscriptionResponse
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.utils.SubscriptionsConfig
import com.twilio.test.util.ExcludeFromInstrumentedTests
import com.twilio.test.util.captureAddObserver
import com.twilio.test.util.captureSentRequest
import com.twilio.test.util.joinLines
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCoroutineScope
import com.twilio.test.util.waitAndVerify
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.client.TwilsockObserver
import com.twilio.twilsock.util.HttpRequest
import com.twilio.twilsock.util.HttpResponse
import com.twilio.twilsock.util.MultiMap
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CannotParse
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.RetrierConfig
import com.twilio.util.TwilioException
import com.twilio.util.json
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.RelaxedMockK
import kotlin.properties.Delegates
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString

@ExcludeFromInstrumentedTests
class SubscriptionManagerTest {

    private lateinit var subscriptionManager: SubscriptionManager

    @RelaxedMockK
    lateinit var twilsock: Twilsock

    lateinit var twilsockObserver: TwilsockObserver

    private val config = SubscriptionsConfig(
        url = "fakeSubscriptionsUrl",
        maxInitialBatchSize = 10,
        retrierConfig = RetrierConfig(
            startDelay = 100.milliseconds,
            minDelay = 1.milliseconds,
            maxDelay = 1.seconds,
            maxAttemptsCount = null, // keep retrying
            maxAttemptsTime = INFINITE
        )
    )

    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        MockKAnnotations.init(this@SubscriptionManagerTest, relaxUnitFun = true)
        every { twilsock.isConnected } returns true
        excludeRecords { twilsock.isConnected }

        subscriptionManager = SubscriptionManager(testCoroutineScope, twilsock, config)
        twilsockObserver = twilsock.captureAddObserver()
    }

    @Test
    fun subscribeUnsubscribeNotificationAfterReply() = runTest {
        /**
         * In this test we send `subscription_established` notification after reply to the `establish` action,
         * and send `subscription_cancelled` notification after reply to the `cancel` action.
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }

        clearMocks(twilsock, answers = false)
        subscriptionManager.unsubscribe(documentSid)

        val unsubscribeRequest = twilsock.captureSentRequest()
        val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)
        val subscriptionCancelledPayload = subscriptionCancelledPayload(unsubscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionCancelledPayload)
        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun subscribeUnsubscribeNotificationBeforeReply() = runTest {
        /**
         * In this test we send `subscription_established` notification before reply to the `establish` action,
         * and send `subscription_cancelled` notification before reply to the `cancel` action.
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))

        var stateFlow: Flow<SubscriptionState> by Delegates.notNull()

        coEvery { twilsock.sendRequest(any()) } coAnswers { call ->
            val subscribeRequest = call.invocation.args.first() as HttpRequest
            val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
            val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

            // Send `subscription_established` notification
            twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
            stateFlow.first { it == SubscriptionState.Established }

            // Now send reply to the `establish` message
            return@coAnswers response
        }

        stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Established }

        clearMocks(twilsock, answers = false)
        coEvery { twilsock.sendRequest(any()) } coAnswers { call ->
            val unsubscribeRequest = call.invocation.args.first() as HttpRequest
            val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)
            val subscriptionCancelledPayload = subscriptionCancelledPayload(unsubscribeRequestBody)

            // Send `subscription_cancelled` notification
            twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionCancelledPayload)
            stateFlow.first { it == SubscriptionState.Unsubscribed }

            // Now send reply to the `cancel` message
            return@coAnswers response
        }

        subscriptionManager.unsubscribe(documentSid)

        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun subscribeBatches() = runTest {
        val newBatchSize = 3
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = newBatchSize)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val documentSids1 = Array(config.maxInitialBatchSize * 3 /2) { "ET000$it" }
        val stateFlows1 = documentSids1.map {  subscriptionManager.subscribe(it, "document") }

        val sentRequests = mutableListOf<HttpRequest>()
        waitAndVerify(exactly = 2) { twilsock.sendRequest(capture(sentRequests)) }

        // until we got first response we use initialBatchSize == 10. So it should be 2 batches
        assertEquals(2, sentRequests.size)
        sentRequests.forEach { request ->
            val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(request.payload)
            val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)
            twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        }

        stateFlows1.forEach { flow ->
            flow.first { it == SubscriptionState.Established }
        }

        clearMocks(twilsock, answers = false)

        val documentSids2 = Array(10) { "ET111$it" }
        val stateFlows2 = documentSids2.map {  subscriptionManager.subscribe(it, "document") }

        sentRequests.clear()
        waitAndVerify(exactly = 4) { twilsock.sendRequest(capture(sentRequests)) }

        // now newBatchSize == 3 should be applied. So we expect 4 requests
        assertEquals(4, sentRequests.size)
        sentRequests.forEach { request ->
            val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(request.payload)
            val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)
            twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        }

        stateFlows2.forEach { flow ->
            flow.first { it == SubscriptionState.Established }
        }
    }

    @Test
    fun timeoutRetried() = runTest {
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 500, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))

        coEvery { twilsock.sendRequest(any()) }
            .throws(TwilioException(ErrorInfo(Timeout)))
            .andThen(response)

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")

        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }
        stateFlow.first { it == SubscriptionState.Pending } // twilsock.sendRequest() throws timeout exception
        stateFlow.first { it == SubscriptionState.Subscribing }
        stateFlow.first { it == SubscriptionState.Pending } // estimatedDeliveryInMilliseconds = 500 timeout occurred
        stateFlow.first { it == SubscriptionState.Subscribing }

        val requests = mutableListOf<HttpRequest>()
        waitAndVerify(exactly = 3) { twilsock.sendRequest(capture(requests)) }

        val subscribeRequest = requests.last()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }
    }

    @Test
    fun parseError() = runTest {
        val documentSid = "ET000"
        val response = HttpResponse(200, "OK", "", MultiMap(), "invalid payload")

        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")

        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }
        stateFlow.first { it is SubscriptionState.Failed && it.errorInfo.reason == CannotParse }
    }

    @Test
    fun cancelledByBackend() = runTest {
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }

        val subscriptionCancelledPayload = """
            {
               "event_type":"subscription_canceled",
               "event_protocol_version":4,
               "events":[
                   {
                       "object_sid":"$documentSid",
                       "object_type":"document"
                   } 
               ]
            }
        """.joinLines()

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionCancelledPayload)
        stateFlow.first { it == SubscriptionState.Unsubscribed }


        // Now try to subscribe again
        clearMocks(twilsock, answers = false)
        val stateFlow1 = subscriptionManager.subscribe(documentSid, "document")

        val subscribeRequest1 = twilsock.captureSentRequest()
        val subscribeRequestBody1 = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest1.payload)
        val subscriptionEstablishedPayload1 = subscriptionEstablishedPayload(subscribeRequestBody1)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload1)
        stateFlow1.first { it == SubscriptionState.Established }
    }

    @Test
    fun remoteEventArrived() = runTest {
        val documentSid = "ET000"

        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }

        val remoteEvent = """
            {
                "document_data":{
                    "data":1
                },
                "date_created":"2022-12-18T09:07:03.443Z",
                "document_revision":"1",
                "date_expires":"2022-12-18T10:07:00.000Z",
                "id":1,
                "document_sid":"$documentSid"
            }
        """.joinLines()

        val remoteEventPayload = """
            {
                "event_type":"document_updated",
                "event_protocol_version":4,
                "event":$remoteEvent
            }
        """.joinLines()

        twilsockObserver.onMessageReceived("com.twilio.rtd.cds.document", remoteEventPayload)
        val event = subscriptionManager.remoteEventsFlow.first()

        assertEquals(documentSid, event.entitySid)
        assertEquals("document_updated", event.eventType)
        assertEquals(json.parseToJsonElement(remoteEvent), event.event)
    }

    @Test
    fun subscribeUnsubscribeRace1() = runTest {
        /**
         * Calls unsubscribe() before subscription request is sent
         */
        val documentSid = "ET000"
        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        subscriptionManager.unsubscribe(documentSid)

        stateFlow.first { it == SubscriptionState.Unsubscribed }

        // check subscribe() still works. So internal counters logic works correctly
        subscriptionManager.subscribe(documentSid, "document")
        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)

        assertEquals(1, subscribeRequestBody.requests.size)
        assertEquals(documentSid, subscribeRequestBody.requests.first().entitySid)
    }

    @Test
    fun subscribeUnsubscribeRace2() = runTest {
        /**
         * call unsubscribe() after the subscribe request is sent, but response isn't received yet
         * in this test expected that cancel request will be sent after establish request is completed
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        clearMocks(twilsock, answers = false)

        // The unsubscribe() call here
        subscriptionManager.unsubscribe(documentSid)

        delay(1.seconds)
        confirmVerified(twilsock) // sendRequest() is not called yet

        coEvery { twilsock.sendRequest(any()) } returns response
        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }

        val unsubscribeRequest = twilsock.captureSentRequest()
        val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)
        val subscriptionCancelledPayload = subscriptionCancelledPayload(unsubscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionCancelledPayload)
        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun subscribeUnsubscribeRace3() = runTest {
        /**
         * call unsubscribe() after the subscribe request is sent, but response isn't received yet
         * in this test we never send response, so the subscription establish attempt should fail by timeout and then
         * new unsubscribe request should be send.
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 500, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        assertEquals(SubscriptionAction.Subscribe, subscribeRequestBody.action)

        clearMocks(twilsock, answers = false)

        // The unsubscribe() call here
        subscriptionManager.unsubscribe(documentSid)

        val unsubscribeRequest = twilsock.captureSentRequest()
        val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)
        assertEquals(SubscriptionAction.Unsubscribe, unsubscribeRequestBody.action)
        val subscriptionCancelledPayload = subscriptionCancelledPayload(unsubscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionCancelledPayload)

        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun unsubscribeSubscribeRace1() = runTest {
        /**
         * Calls subscribe() before previous unsubscribe() is sent and subscription is cancelled.
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 10000, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }

        subscriptionManager.unsubscribe(documentSid)

        // The subscribe() call here
        subscriptionManager.subscribe(documentSid, "document")

        stateFlow.first { it == SubscriptionState.Established }
        confirmVerified(twilsock)

        // check unsubscribe() still works. So internal counters logic works correctly
        subscriptionManager.unsubscribe(documentSid)
        val unsubscribeRequest = twilsock.captureSentRequest()
        val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)

        assertEquals(1, unsubscribeRequestBody.requests.size)
        assertEquals(documentSid, unsubscribeRequestBody.requests.first().entitySid)
    }

    @Test
    fun unsubscribeSubscribeRace2() = runTest {
        /**
         * call subscribe() after the unsubscribe request is sent, but response isn't received yet
         * in this test expected that establish request will be sent after cancel request is completed
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 500, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow1 = subscriptionManager.subscribe(documentSid, "document")

        stateFlow1.first { it == SubscriptionState.Pending }
        stateFlow1.first { it == SubscriptionState.Subscribing }

        val subscribeRequest1 = twilsock.captureSentRequest()
        val subscribeRequestBody1 = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest1.payload)
        val subscriptionEstablishedPayload1 = subscriptionEstablishedPayload(subscribeRequestBody1)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload1)
        stateFlow1.first { it == SubscriptionState.Established }

        clearMocks(twilsock, answers = false)
        subscriptionManager.unsubscribe(documentSid)

        val unsubscribeRequest = twilsock.captureSentRequest()
        val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)
        val subscriptionCancelledPayload = subscriptionCancelledPayload(unsubscribeRequestBody)

        clearMocks(twilsock, answers = false)

        // The subscribe() call here
        subscriptionManager.subscribe(documentSid, "document")

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionCancelledPayload)

        // Unsubscribed is not delivered, because the StateFlow delivers is always conflated.
        // So it delivers only last state.
        //
        // stateFlow1.first { it == SubscriptionState.Unsubscribed }
        stateFlow1.first { it == SubscriptionState.Pending }
        stateFlow1.first { it == SubscriptionState.Subscribing }

        val subscribeRequest2 = twilsock.captureSentRequest()
        val subscribeRequestBody2 = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest2.payload)
        val subscriptionEstablishedPayload2 = subscriptionEstablishedPayload(subscribeRequestBody2)
        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload2)

        stateFlow1.first { it == SubscriptionState.Established }
    }

    @Test
    fun unsubscribeSubscribeRace3() = runTest {
        /**
         * call subscribe() after the unsubscribe request is sent, but response isn't received yet
         * in this test we never send response, so the subscription cancellation attempt should fail by timeout and then
         * new subscribe request should be send.
         */
        val documentSid = "ET000"
        val body = SubscriptionResponse(estimatedDeliveryInMilliseconds = 500, maxBatchSize = 1000)
        val response = HttpResponse(200, "OK", "", MultiMap(), json.encodeToString(body))
        coEvery { twilsock.sendRequest(any()) } returns response

        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Pending }
        stateFlow.first { it == SubscriptionState.Subscribing }

        val subscribeRequest = twilsock.captureSentRequest()
        val subscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(subscribeRequest.payload)
        val subscriptionEstablishedPayload = subscriptionEstablishedPayload(subscribeRequestBody)

        twilsockObserver.onMessageReceived("twilio.sync.event", subscriptionEstablishedPayload)
        stateFlow.first { it == SubscriptionState.Established }

        clearMocks(twilsock, answers = false)
        subscriptionManager.unsubscribe(documentSid)
        val unsubscribeRequest = twilsock.captureSentRequest()
        val unsubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(unsubscribeRequest.payload)
        assertEquals(SubscriptionAction.Unsubscribe, unsubscribeRequestBody.action)

        clearMocks(twilsock, answers = false)

        // The subscribe() call here
        val stateFlow1 = subscriptionManager.subscribe(documentSid, "document")
        val newSubscribeRequest = twilsock.captureSentRequest()
        val newSubscribeRequestBody = json.decodeFromString<SubscriptionRequestBody>(newSubscribeRequest.payload)
        assertEquals(SubscriptionAction.Subscribe, newSubscribeRequestBody.action)
        val newSubscriptionEstablishedPayload = subscriptionEstablishedPayload(newSubscribeRequestBody)

        stateFlow1.first { it == SubscriptionState.Subscribing }
        twilsockObserver.onMessageReceived("twilio.sync.event", newSubscriptionEstablishedPayload)
        stateFlow1.first { it == SubscriptionState.Established }
    }

    private fun subscriptionEstablishedPayload(request: SubscriptionRequestBody): String {
        val events = request.requests.map {
            """
                {
                     "object_sid":"${it.entitySid}",
                     "object_type":"document",
                     "replay_status":"completed",
                     "last_event_id":0
                }            
            """.joinLines()
        }

        return """
            {
               "event_type":"subscription_established",
               "correlation_id":${request.correlationId},
               "event_protocol_version":4,
               "events":[${events.joinToString(",")}]
            }
        """.joinLines()
    }

    private fun subscriptionCancelledPayload(request: SubscriptionRequestBody): String {
        val events = request.requests.map {
            """
                {
                   "object_sid":"${it.entitySid}",
                   "object_type":"document"
                }            
            """.joinLines()
        }

        return """
            {
               "event_type":"subscription_canceled",
               "correlation_id":${request.correlationId},
               "event_protocol_version":4,
               "events":[${events.joinToString(",")}]
            }
        """.joinLines()
    }
}
