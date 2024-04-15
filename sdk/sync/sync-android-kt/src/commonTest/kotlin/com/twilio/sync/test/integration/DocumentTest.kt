//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.client.SyncClient
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.SyncClientConfig
import com.twilio.sync.utils.SyncConfig
import com.twilio.sync.utils.data
import com.twilio.sync.utils.mutateData
import com.twilio.sync.utils.use
import com.twilio.test.util.IgnoreIos
import com.twilio.test.util.generateRandomString
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.twilioException
import com.twilio.test.util.wait
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.ErrorReason.MutateOperationAborted
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.InternalTwilioApi
import com.twilio.util.TwilioException
import com.twilio.util.emptyJsonObject
import com.twilio.util.logger
import com.twilio.util.toTwilioException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

open class DocumentTest {

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
    fun createDocumentDefault() = runTest {
        val document = syncClient.documents.create()

        assertTrue(document.sid.startsWith("ET"))
        assertNull(document.uniqueName)
        assertNotNull(document.dateCreated)
        assertNotNull(document.dateUpdated)
        assertNull(document.dateExpires)
        assertEquals(emptyJsonObject(), document.data)

        assertEquals(SubscriptionState.Unsubscribed, document.subscriptionState)
        assertTrue(document.isFromCache)

        // First onUpdated event should be emitted immediately to each subscriber
        document.events.onUpdated.first()
    }

    @Test
    fun createDocumentWithUniqueName() = runTest {
        val uniqueName = generateRandomString("document")
        val document = syncClient.documents.create(uniqueName)

        assertTrue(document.sid.startsWith("ET"))
        assertEquals(uniqueName, document.uniqueName)
        assertNotNull(document.dateCreated)
        assertNotNull(document.dateUpdated)
        assertNull(document.dateExpires)
        assertEquals(emptyJsonObject(), document.data)
    }

    @Test
    fun createDocumentWithTtl() = runTest {
        val document = syncClient.documents.create(ttl = 1.hours)

        assertTrue(document.sid.startsWith("ET"))
        assertNull(document.uniqueName)
        assertNotNull(document.dateCreated)
        assertNotNull(document.dateUpdated)
        assertNotNull(document.dateExpires)
        assertEquals(emptyJsonObject(), document.data)
    }

    @Test
    fun createDocumentWithUniqueNameAndTtl() = runTest {
        val uniqueName = generateRandomString("document")
        val document = syncClient.documents.create(uniqueName, 1.hours)

        assertTrue(document.sid.startsWith("ET"))
        assertEquals(uniqueName, document.uniqueName)
        assertNotNull(document.dateExpires)
        assertEquals(emptyJsonObject(), document.data)
    }

    @Test
    fun createDocumentError() = runTest {
        val document = syncClient.documents.create()
        val actualError = assertFails { syncClient.documents.create(uniqueName = document.sid) }
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
    fun openOrCreateDocumentOpenExisting() = runTest {
        val uniqueName = generateRandomString("document")
        val creatingClient = TestSyncClient { requestToken() }

        val createdDocument = creatingClient.documents.create(uniqueName)
        val openedDocument = syncClient.documents.openOrCreate(uniqueName)

        assertEquals(uniqueName, openedDocument.uniqueName)
        assertEquals(createdDocument.sid, openedDocument.sid)
        assertEquals(createdDocument.dateExpires, openedDocument.dateExpires)
        assertEquals(createdDocument.data, openedDocument.data)
    }

    @Test
    fun openOrCreateDocumentOpenExistingWithData() = runTest {
        val uniqueName = generateRandomString("document")
        val creatingClient = TestSyncClient { requestToken() }

        val createdDocument = creatingClient.documents.create(uniqueName)
        val data = buildJsonObject { put("data", "value") }
        createdDocument.setData(data)

        val openedDocument = syncClient.documents.openOrCreate(uniqueName)

        assertEquals(uniqueName, openedDocument.uniqueName)
        assertEquals(createdDocument.sid, openedDocument.sid)
        assertEquals(createdDocument.dateExpires, openedDocument.dateExpires)
        assertEquals(createdDocument.data, openedDocument.data)
    }

    @Test
    fun openOrCreateDocumentCreateNew() = runTest {
        val uniqueName = generateRandomString("document")
        val createdDocument = syncClient.documents.openOrCreate(uniqueName)

        assertEquals(uniqueName, createdDocument.uniqueName)
        assertTrue(createdDocument.sid.startsWith("ET"))
        assertNull(createdDocument.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, createdDocument.subscriptionState)
        assertTrue(createdDocument.isFromCache)
    }

    @Test
    fun openOrCreateDocumentError() = runTest {
        val document = syncClient.documents.create()
        val actualError = assertFails { syncClient.documents.openOrCreate(uniqueName = document.sid) }
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
    fun openExistingDocument() = runTest {
        val uniqueName = generateRandomString("document")

        val creatingClient = TestSyncClient { requestToken() }
        val createdDocument = creatingClient.documents.create(uniqueName)

        val openedBySid = syncClient.documents.openExisting(createdDocument.sid)

        assertEquals(uniqueName, openedBySid.uniqueName)
        assertEquals(createdDocument.sid, openedBySid.sid)
        assertEquals(createdDocument.dateExpires, openedBySid.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, openedBySid.subscriptionState)
        assertTrue(openedBySid.isFromCache)

        val openedByUniqueName = syncClient.documents.openExisting(uniqueName)

        assertEquals(uniqueName, openedByUniqueName.uniqueName)
        assertEquals(createdDocument.sid, openedByUniqueName.sid)
        assertEquals(createdDocument.dateExpires, openedByUniqueName.dateExpires)

        assertEquals(SubscriptionState.Unsubscribed, openedByUniqueName.subscriptionState)
        assertTrue(openedByUniqueName.isFromCache)
    }

    @Test
    fun openNonExistingDocument() = runTest {
        val result = runCatching { syncClient.documents.openExisting("TO111") }

        assertTrue(result.isFailure)

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            status = 404,
            code = 54300,
            message = "Unique name not found"
        )

        assertEquals(expectedError, result.twilioException.errorInfo)
    }

    @Test
    fun setDocumentTtlLocal() = runTest {
        val document = syncClient.documents.create()
        val onUpdated = async { document.events.onUpdated.first { it.dateExpires != null } }

        assertNull(document.dateExpires)

        document.setTtl(1.hours)

        assertNotNull(document.dateExpires)
        onUpdated.await()
    }

    @Test
    fun setDocumentTtlRemote() = runTest {
        val document = syncClient.documents.create()

        assertNull(document.dateExpires)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.documents.setTtl(document.sid, 1.hours)
        }

        document.events.onUpdated.first { it.dateExpires != null }
        assertNotNull(document.dateExpires)
    }

    @Test
    fun resetDocumentTtl() = runTest {
        val document1 = syncClient.documents.create(ttl = 1.hours)
        val document2 = syncClient.documents.create(ttl = 1.hours)

        assertNotNull(document1.dateExpires)
        assertNotNull(document2.dateExpires)

        document1.setTtl(Duration.INFINITE)
        document2.setTtl(Duration.ZERO)

        assertNull(document1.dateExpires)
        assertNull(document2.dateExpires)
    }

    @Test
    fun mutateDataConflict() = runTest {
        val client2 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }

        val document1 = syncClient.documents.create(ttl = 1.hours)
        val document2 = client2.documents.openExisting(document1.sid)

        var conflictsCounter = 0

        repeat(10) { counter ->
            val oldData = buildJsonObject { put("data", "value$counter") }
            document1.setData(oldData)

            val newData = buildJsonObject { put("data", "mutatedValue$counter") }
            var mutateCounter = 0

            document2.mutateData { data ->
                if (mutateCounter++ > 0) {
                    logger.d("conflict: $data")
                    assertEquals(oldData, data)
                }
                return@mutateData newData
            }

            document1.events.onUpdated.first { it.data == newData }
            document2.events.onUpdated.first { it.data == newData }

            assertTrue(mutateCounter <= 2)

            if (mutateCounter == 2) conflictsCounter++
        }

        logger.d("conflictsCounter: $conflictsCounter")
    }

    @Test
    fun resetDocumentTtlRemote() = runTest {
        val document = syncClient.documents.create(ttl = 1.hours)
        assertNotNull(document.dateExpires)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.documents.setTtl(document.sid, Duration.INFINITE)
        }

        document.events.onUpdated.first { it.dateExpires == null }
        assertNull(document.dateExpires)
    }

    @Test
    fun setDataLocal() = runTest {
        val document = syncClient.documents.create(ttl = 1.hours)

        val newData = buildJsonObject { put("data", "value") }
        val onUpdated = async { document.events.onUpdated.first { it.data == newData } }

        document.setData(newData)

        assertEquals(newData, document.data)
        onUpdated.await()
    }

    @Test
    fun setDataRemote() = runTest {
        val document = syncClient.documents.create()
        val data1 = buildJsonObject { put("data", "value1") }
        val data2 = buildJsonObject { put("data", "value2") }

        val client2 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }
        client2.documents.updateDocument(document.sid, data1)

        document.events.onUpdated.first { it.data == data1 }
        assertEquals(data1, document.data)
        assertNull(document.dateExpires)

        client2.documents.updateDocumentWithTtl(document.sid, data2, 1.hours)

        document.events.onUpdated.first { it.data == data2 }
        assertEquals(data2, document.data)
        assertNotNull(document.dateExpires)
    }

    @Test
    fun onUpdatedEmitsOnce() = runTest {
        val client2 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }

        val document1 = syncClient.documents.create()
        val document2 = client2.documents.openExisting(document1.sid)

        val updatedList1 = mutableListOf<JsonObject>()
        val updatedList2 = mutableListOf<JsonObject>()

        val listener1 = launch { document1.events.onUpdated.map { it.data }.toList(updatedList1) }
        val listener2 = launch { document2.events.onUpdated.map { it.data }.toList(updatedList2) }

        val data = buildJsonObject { put("data", "value") }
        document1.setData(data)

        wait { updatedList1.size >= 2 && updatedList2.size >= 2 }

        delay(2.seconds) // to be sure no more events came

        val expectedList = listOf(emptyJsonObject(), data)

        assertEquals(expectedList, updatedList1)
        assertEquals(expectedList, updatedList2)

        listener1.cancel()
        listener2.cancel()
    }

    @Test
    fun mutateDataDirect() = runTest {
        val document = syncClient.documents.create()

        val data1 = buildJsonObject { put("data", "value1") }
        val data2 = buildJsonObject { put("data", "value2") }

        val client2 = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }
        client2.documents.mutateDocument(document.sid) { data1 }

        document.events.onUpdated.first { it.data == data1 }
        assertEquals(data1, document.data)
        assertNull(document.dateExpires)

        client2.documents.mutateDocumentWithTtl(document.sid, 1.hours) { data2 }

        document.events.onUpdated.first { it.data == data2 }
        assertEquals(data2, document.data)
        assertNotNull(document.dateExpires)
    }

    @Test
    fun mutateCollision() = runTest {
        val config = SyncConfig(
            syncClientConfig = SyncClientConfig(commandTimeout = 60.seconds)
        )
        val client = TestSyncClient(config = config, useLastUserAccount = false) { requestToken("otherUser") }
        val document = client.documents.create(ttl = 1.hours)

        @Serializable
        data class Counter(val value: Long = 0) {
            operator fun plus(x: Long) = Counter(value + x)
        }

        List(10) {
            async {
                document.mutateData<Counter> { counter -> counter + 1 }
            }
        }.awaitAll()

        assertEquals(Counter(10), document.data())
    }

    @Test
    fun mutateData() = runTest {
        val senderDocument = syncClient.documents.create(ttl = 1.hours)

        val receiver = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }
        val receiverDocument = receiver.documents.openExisting(senderDocument.sid)

        val newData = buildJsonObject { put("data", "value") }
        senderDocument.mutateData { newData }

        assertEquals(newData, senderDocument.data)
        senderDocument.events.onUpdated.first { it.data == newData }
        receiverDocument.events.onUpdated.first { it.data == newData }
    }

    @Test
    fun mutateDataAbort() = runTest {
        val document = syncClient.documents.create(ttl = 1.hours)

        val result = runCatching {
            document.mutateData { null }
        }

        val expectedError = ErrorInfo(
            reason = CommandPermanentError,
            message = "Mutate operation aborted: Mutator function has returned null as new data"
        )
        val expectedCause = ErrorInfo(reason = MutateOperationAborted)

        assertEquals(expectedError, result.twilioException.errorInfo)
        assertEquals(expectedCause, (result.twilioException.cause as TwilioException).errorInfo)
    }

    @Test
    fun mutateDataWithExistingValue() = runTest {
        val receiver = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }

        val senderDocument = syncClient.documents.create(ttl = 1.hours)
        val receiverDocument = receiver.documents.openExisting(senderDocument.sid)

        val oldData = buildJsonObject { put("data", "oldValue") }
        val newData = buildJsonObject { put("data", "newValue") }

        receiverDocument.setData(oldData)
        senderDocument.mutateData { newData }

        assertEquals(newData, senderDocument.data)
        senderDocument.events.onUpdated.first { it.data == newData }
        receiverDocument.events.onUpdated.first { it.data == newData }
    }

    @Test
    fun removeDocumentLocal() = runTest {
        val document = syncClient.documents.create()
        assertFalse(document.isRemoved)

        document.removeDocument()

        document.events.onRemoved.first()
        assertTrue(document.isRemoved)
    }

    @Test
    fun removeDocumentRemote() = runTest {
        val uniqueName = generateRandomString("document")
        val document = syncClient.documents.create(uniqueName)
        assertFalse(document.isRemoved)

        TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }.use { client ->
            client.documents.remove(uniqueName)
        }

        document.events.onRemoved.first()
        assertTrue(document.isRemoved)
    }

    @Test
    fun documentSubscriptionState() = runTest {
        val document = syncClient.documents.create(ttl = 1.hours)
        assertEquals(SubscriptionState.Unsubscribed, document.subscriptionState)
        assertTrue(document.isFromCache)

        val listener1 = launch { document.events.onUpdated.collect() }
        val listener2 = launch { document.events.onUpdated.collect() }

        document.events.onSubscriptionStateChanged.first { it == SubscriptionState.Pending }
        document.events.onSubscriptionStateChanged.first { it == SubscriptionState.Subscribing }
        document.events.onSubscriptionStateChanged.first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, document.subscriptionState)
        assertFalse(document.isFromCache)

        wait { listener1.isActive && listener2.isActive }

        listener1.cancel()

        document.events.onSubscriptionStateChanged
            .onEach { assertEquals(SubscriptionState.Established, it) } // No state changes here
            .first { it == SubscriptionState.Established }

        assertEquals(SubscriptionState.Established, document.subscriptionState)
        assertFalse(document.isFromCache)

        listener2.cancel()

        // Once all listeners disappeared document should unsubscribe itself
        document.events.onSubscriptionStateChanged.first { it == SubscriptionState.Unsubscribed }
        assertEquals(SubscriptionState.Unsubscribed, document.subscriptionState)
        assertTrue(document.isFromCache)
    }

    @Test
    fun cachedData() = runTest {
        val data1 = buildJsonObject { put("data", "value1") }
        val data2 = buildJsonObject { put("data", "value2") }
        val data3 = buildJsonObject { put("data", "value3") }

        val documentSid = syncClient.documents.create(ttl = 1.hours).use { document ->
            document.setData(data1)
            return@use document.sid
        }

        TestSyncClient(useLastUserAccount = false) { requestToken(identity = "otherUser") }.use { client ->
            client.documents.updateDocument(documentSid, data2)
            client.documents.updateDocument(documentSid, data3)
        }

        syncClient.documents.openExisting(documentSid).use { document ->
            assertTrue(document.isFromCache)
            assertEquals(data1, document.data) // document is not subscribed. So it returns cached data.

            // Here the document subscribes and receives the last update
            document.events.onUpdated.first { it.data == data3 }
            assertEquals(data3, document.data)
        }
    }

    @Test
    fun onUpdatedWhenCreateAnotherDocument() = runTest { // RTDSDK-4266
        val document1 = syncClient.documents.create(ttl = 1.hours)

        val updatedList1 = mutableListOf<JsonObject>()
        val listener1 = launch { document1.events.onUpdated
            .map { it.data }
            .onEach { println("!!! listener1 onUpdated: $it") }
            .toList(updatedList1)
        }

        wait { updatedList1.size == 1 }
        assertEquals(emptyJsonObject(), updatedList1[0])

        val document2 = syncClient.documents.create(ttl = 1.hours)

        val updatedList2 = mutableListOf<JsonObject>()
        val listener2 = launch { document2.events.onUpdated
            .map { it.data }
            .onEach { println("!!! listener2 onUpdated: $it") }
            .toList(updatedList2)
        }

        delay(2.seconds) // to be sure no more events came

        val expectedList = listOf(emptyJsonObject())

        assertEquals(expectedList, updatedList1)
        assertEquals(expectedList, updatedList2)

        listener1.cancel()
        listener2.cancel()
    }
}
