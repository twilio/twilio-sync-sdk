//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.client.SyncClient
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.use
import com.twilio.test.util.generateRandomString
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.twilioException
import com.twilio.test.util.wait
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.InternalTwilioApi
import com.twilio.util.toTwilioException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours

open class StreamTest {
    lateinit var syncClient: SyncClient

    open val config = SyncConfig()

    @OptIn(InternalTwilioApi::class)
    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()
        SyncCacheCleaner.clearAllCaches()
        syncClient = TestSyncClient(config = config) { requestToken() }
    }

    @AfterTest
    fun tearDown() {
        syncClient.shutdown()
    }

    @Test
    fun createStreamDefault() = runTest {
        val stream = syncClient.streams.create()

        assertTrue(stream.sid.startsWith("TO"))
        assertNull(stream.uniqueName)
        assertNull(stream.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, stream.subscriptionState)
        assertTrue(stream.isFromCache)
    }

    @Test
    fun createStreamWithUniqueName() = runTest {
        val uniqueName = generateRandomString("stream")
        val stream = syncClient.streams.create(uniqueName)

        assertTrue(stream.sid.startsWith("TO"))
        assertEquals(uniqueName, stream.uniqueName)
        assertNull(stream.dateExpires)
    }

    @Test
    fun createStreamWithTtl() = runTest {
        val stream = syncClient.streams.create(ttl = 1.hours)

        assertTrue(stream.sid.startsWith("TO"))
        assertNull(stream.uniqueName)
        assertNotNull(stream.dateExpires)
    }

    @Test
    fun createStreamWithUniqueNameAndTtl() = runTest {
        val uniqueName = generateRandomString("stream")
        val stream = syncClient.streams.create(uniqueName, 1.hours)

        assertTrue(stream.sid.startsWith("TO"))
        assertEquals(uniqueName, stream.uniqueName)
        assertNotNull(stream.dateExpires)
    }

    @Test
    fun createStreamError() = runTest {
        val stream = syncClient.streams.create()
        val actualError = assertFails { syncClient.streams.create(uniqueName = stream.sid) }
            .toTwilioException(Unknown).errorInfo

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = HttpStatusCode.BadRequest.value,
            code = 54302,
            message = "Invalid unique name. " +
                    "Expected a string with length 1-320 not matching the SID pattern [A-Z]{2}[a-f0-9]{32}"
        )

        assertEquals(expectedError, actualError)
    }

    @Test
    fun openOrCreateStreamOpenExisting() = runTest {
        val uniqueName = generateRandomString("stream")
        val creatingClient = TestSyncClient { requestToken() }

        val createdStream = creatingClient.streams.create(uniqueName)
        val openedStream = syncClient.streams.openOrCreate(uniqueName)

        assertEquals(uniqueName, openedStream.uniqueName)
        assertEquals(createdStream.sid, openedStream.sid)
        assertEquals(createdStream.dateExpires, openedStream.dateExpires)
    }

    @Test
    fun openOrCreateStreamCreateNew() = runTest {
        val uniqueName = generateRandomString("stream")
        val createdStream = syncClient.streams.openOrCreate(uniqueName)

        assertEquals(uniqueName, createdStream.uniqueName)
        assertTrue(createdStream.sid.startsWith("TO"))
        assertNull(createdStream.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, createdStream.subscriptionState)
        assertTrue(createdStream.isFromCache)
    }

    @Test
    fun openOrCreateStreamError() = runTest {
        val stream = syncClient.streams.create()
        val actualError = assertFails { syncClient.streams.openOrCreate(uniqueName = stream.sid) }
            .toTwilioException(Unknown).errorInfo

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = HttpStatusCode.BadRequest.value,
            code = 54302,
            message = "Invalid unique name. " +
                    "Expected a string with length 1-320 not matching the SID pattern [A-Z]{2}[a-f0-9]{32}"
        )

        assertEquals(expectedError, actualError)
    }

    @Test
    fun openExistingStream() = runTest {
        val uniqueName = generateRandomString("stream")

        val creatingClient = TestSyncClient { requestToken() }
        val createdStream = creatingClient.streams.create(uniqueName)

        val openedBySid = syncClient.streams.openExisting(createdStream.sid)

        assertEquals(uniqueName, openedBySid.uniqueName)
        assertEquals(createdStream.sid, openedBySid.sid)
        assertEquals(createdStream.dateExpires, openedBySid.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, openedBySid.subscriptionState)
        assertTrue(openedBySid.isFromCache)

        val openedByUniqueName = syncClient.streams.openExisting(uniqueName)

        assertEquals(uniqueName, openedByUniqueName.uniqueName)
        assertEquals(createdStream.sid, openedByUniqueName.sid)
        assertEquals(createdStream.dateExpires, openedByUniqueName.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, openedByUniqueName.subscriptionState)
        assertTrue(openedByUniqueName.isFromCache)
    }

    @Test
    fun openNonExistingStream() = runTest {
        val result = runCatching { syncClient.streams.openExisting("TO111") }

        assertTrue(result.isFailure)

        val expectedError = ErrorInfo(
            reason = CommandPermanentError, status = 404, code = 54300, message = "Unique name not found")

        assertEquals(expectedError, result.twilioException.errorInfo)
    }

    @Test
    fun setStreamTtl() = runTest {
        val stream = syncClient.streams.create()
        assertNull(stream.dateExpires)

        stream.setTtl(1.hours)

        assertNotNull(stream.dateExpires)
    }

    @Test
    fun setStreamTtlDirect() = runTest {
        val uniqueName = generateRandomString("uniqueName")
        val stream = syncClient.streams.create(uniqueName)
        assertNull(stream.dateExpires)

        syncClient.streams.setTtl(stream.sid, 1.hours)
        wait { stream.dateExpires != null }

        syncClient.streams.setTtl(stream.sid, INFINITE)
        wait { stream.dateExpires == null }

        syncClient.streams.setTtl(uniqueName, 1.hours)
        wait { stream.dateExpires != null }
    }

    @Test
    fun resetStreamTtl() = runTest {
        val stream1 = syncClient.streams.create(ttl = 1.hours)
        val stream2 = syncClient.streams.create(ttl = 1.hours)

        assertNotNull(stream1.dateExpires)
        assertNotNull(stream2.dateExpires)

        stream1.setTtl(INFINITE)
        stream2.setTtl(ZERO)

        assertNull(stream1.dateExpires)
        assertNull(stream2.dateExpires)
    }

    @Test
    fun publishMessage() = runTest {
        val senderStream = syncClient.streams.create(ttl = 1.hours)

        val senderMessageAsync = async { senderStream.events.onMessagePublished.first() }

        val receiver = TestSyncClient { requestToken() }
        val receiverStream = receiver.streams.openExisting(senderStream.sid)

        val receiverMessageAsync = async { receiverStream.events.onMessagePublished.first() }

        senderStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }
        receiverStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        val messageData = buildJsonObject { put("data", "value") }
        val message = senderStream.publishMessage(messageData)

        val senderMessage = senderMessageAsync.await()
        val receiverMessage = receiverMessageAsync.await()

        assertTrue(message.sid.startsWith("TZ"))
        assertEquals(message, senderMessage)
        assertEquals(message, receiverMessage)

        assertEquals(messageData, senderMessage.data)
        assertEquals(messageData, receiverMessage.data)

        senderStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
        receiverStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun publishMessageDirect() = runTest {
        val receiver = TestSyncClient { requestToken() }
        val receiverStream = receiver.streams.create()

        val receiverMessageAsync = async { receiverStream.events.onMessagePublished.first() }
        receiverStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        val messageData = buildJsonObject { put("data", "value") }
        val message = syncClient.streams.publishMessage(receiverStream.sid, messageData)

        val receiverMessage = receiverMessageAsync.await()

        assertTrue(message.sid.startsWith("TZ"))
        assertEquals(message, receiverMessage)
        assertEquals(messageData, receiverMessage.data)

        receiverStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun removeStream() = runTest {
        val remoteClient = TestSyncClient { requestToken() }

        val localStream = syncClient.streams.create()
        val remoteStream = remoteClient.streams.openExisting(localStream.sid)

        val localEvent = async { localStream.events.onRemoved.first() }
        val remoteEvent = async { remoteStream.events.onRemoved.first() }

        localStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }
        remoteStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        assertFalse(localStream.isRemoved)
        assertFalse(remoteStream.isRemoved)

        localStream.removeStream()
        assertTrue(localStream.isRemoved)

        localEvent.await()
        remoteEvent.await()

        assertTrue(remoteStream.isRemoved)
    }

    @Test
    fun removeStreamBeforeSubscribed() = runTest {
        val stream = syncClient.streams.create()
        assertFalse(stream.isRemoved)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.streams.remove(stream.sid)
        }

        stream.events.onRemoved.first() // Here the stream subscribes and got 'subscription_failed'
        assertTrue(stream.isRemoved)
    }

    @Test
    fun removeStreamDirect() = runTest {
        val remoteClient = TestSyncClient { requestToken() }

        val localStream = syncClient.streams.create()
        val remoteStream = remoteClient.streams.openExisting(localStream.sid)

        val localEvent = async { localStream.events.onRemoved.first() }
        val remoteEvent = async { remoteStream.events.onRemoved.first() }

        localStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }
        remoteStream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        assertFalse(localStream.isRemoved)
        assertFalse(remoteStream.isRemoved)

        syncClient.streams.remove(localStream.sid)

        localEvent.await()
        remoteEvent.await()

        assertTrue(localStream.isRemoved)
        assertTrue(remoteStream.isRemoved)
    }

    @Test
    fun streamSubscriptionState() = runTest {
        val stream = syncClient.streams.create()
        assertEquals(SubscriptionState.Unsubscribed, stream.subscriptionState)
        assertTrue(stream.isFromCache)

        val listener1 = launch { stream.events.onMessagePublished.collect() }
        val listener2 = launch { stream.events.onMessagePublished.collect() }

        stream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Pending }
        stream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Subscribing }
        stream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, stream.subscriptionState)
        assertFalse(stream.isFromCache)

        wait { listener1.isActive && listener2.isActive }

        listener1.cancel()

        stream.events.onSubscriptionStateChanged
            .onEach { assertEquals(SubscriptionState.Established, it) } // No state changes here
            .first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, stream.subscriptionState)
        assertFalse(stream.isFromCache)

        listener2.cancel()

        // Once all listeners disappeared stream should unsubscribe itself
        stream.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
        assertEquals(SubscriptionState.Unsubscribed, stream.subscriptionState)
        assertTrue(stream.isFromCache)
    }
}
