//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.test.integration

import com.twilio.sync.cache.SyncCacheCleaner
import com.twilio.sync.client.SyncClient
import com.twilio.sync.entities.SyncDocument
import com.twilio.sync.subscriptions.SubscriptionManager
import com.twilio.sync.subscriptions.SubscriptionState
import com.twilio.sync.test.util.TestData
import com.twilio.sync.test.util.TestMetadata
import com.twilio.sync.test.util.TestSyncClient
import com.twilio.sync.utils.SubscriptionsConfig
import com.twilio.sync.utils.setData
import com.twilio.test.util.IgnoreIos
import com.twilio.test.util.kTestTwilsockServiceUrl
import com.twilio.test.util.requestToken
import com.twilio.test.util.runTest
import com.twilio.test.util.setupTestAndroidContext
import com.twilio.test.util.setupTestLogging
import com.twilio.test.util.testCerts
import com.twilio.test.util.testCoroutineScope
import com.twilio.twilsock.client.AuthData
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.client.TwilsockFactory
import com.twilio.util.ErrorInfo
import com.twilio.util.InternalTwilioApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class SubscriptionManagerTest {
    lateinit var syncClient: SyncClient

    lateinit var document: SyncDocument

    lateinit var twilsock: Twilsock

    private lateinit var subscriptionManager: SubscriptionManager

    @OptIn(InternalTwilioApi::class)
    @BeforeTest
    fun setUp() = runTest {
        setupTestLogging()
        setupTestAndroidContext()
        SyncCacheCleaner.clearAllCaches()

        val identity = "SubscriptionManagerAndroidTest"
        syncClient = TestSyncClient { requestToken(identity) }

        document = syncClient.documents.create(ttl = 1.hours)

        val authData = AuthData(
            token = requestToken(identity),
            activeGrant = "data_sync",
            notificationProductId = "data_sync",
            certificates = testCerts,
        )

        twilsock = TwilsockFactory(
            kTestTwilsockServiceUrl,
            useProxy = false,
            authData,
            TestMetadata(),
            testCoroutineScope,
        )

        twilsock.connect()

        val config = SubscriptionsConfig(
            url = "https://cds.twilio.com/v4/Subscriptions",
            maxInitialBatchSize = 5,
        )
        subscriptionManager = SubscriptionManager(testCoroutineScope, twilsock, config)
    }

    @Test
    fun subscribeUnsubscribeList() = runTest {
        val list = syncClient.lists.create(ttl = 1.hours)

        val stateFlow = subscriptionManager.subscribe(list.sid, "list")
        stateFlow.first { it == SubscriptionState.Established }

        subscriptionManager.unsubscribe(list.sid)
        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun subscribeUnsubscribeMap() = runTest {
        val map = syncClient.maps.create(ttl = 1.hours)

        val stateFlow = subscriptionManager.subscribe(map.sid, "map")
        stateFlow.first { it == SubscriptionState.Established }

        subscriptionManager.unsubscribe(map.sid)
        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun subscribeUnsubscribeStream() = runTest {
        val stream = syncClient.streams.create(ttl = 1.hours)

        val stateFlow = subscriptionManager.subscribe(stream.sid, "stream")
        stateFlow.first { it == SubscriptionState.Established }

        subscriptionManager.unsubscribe(stream.sid)
        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }

    @Test
    fun subscribeUnsubscribe10Documents() = runTest {
        val documents = coroutineScope {
            List(10) {
                async { syncClient.documents.create(ttl = 1.hours) }
            }.awaitAll()
        }

        val stateFlows = documents.map {
            subscriptionManager.subscribe(it.sid, "document")
        }

        stateFlows.forEach { flow ->
            flow.first { it == SubscriptionState.Established }
        }

        documents.forEach {
            subscriptionManager.unsubscribe(it.sid)
        }

        stateFlows.forEach { flow ->
            flow.first { it == SubscriptionState.Unsubscribed }
        }
    }

    @Test
    fun subscribeUnsubscribeDocumentRace() = runTest {
        val document = syncClient.documents.create(ttl = 1.hours)

        repeat(15) {
            val flow = subscriptionManager.subscribe(document.sid, "document")
            flow.first { it == SubscriptionState.Established }
            subscriptionManager.unsubscribe(document.sid)

            // give it time to send the unsubscribe request,
            // then try to send following subscribe request before subscription_cancelled arrived.
            // 1-2 times of 15 we caught the right timing. At least it works on my machine :)
            delay(500)
        }
    }

    @Test
    fun subscribeFailed() = runTest {
        val stateFlow = subscriptionManager.subscribe("ET000", "document")
        val state = stateFlow.first { it is SubscriptionState.Failed } as SubscriptionState.Failed
        val expected = ErrorInfo(404, 54100, "Document not found")
        assertEquals(expected, state.errorInfo)
    }

    @Test
    fun remoteEventArrived() = runTest {
        val stateFlow = subscriptionManager.subscribe(document.sid, "document")
        stateFlow.first { it == SubscriptionState.Established }

        val event = async { subscriptionManager.remoteEventsFlow.first() }
        document.setData(TestData(1))

        assertEquals(document.sid, event.await().entitySid)
    }

    @Test
    fun pokeOnReconnect() = runTest {
        val stateFlow = subscriptionManager.subscribe(document.sid, "document")
        stateFlow.first { it == SubscriptionState.Established }

        twilsock.disconnect()
        stateFlow.first { it == SubscriptionState.Pending }

        twilsock.connect()
        stateFlow.first { it == SubscriptionState.Established }
    }

    @Test
    @Ignore // "How to check this? Backend doesn't send subscription cancellation if removeDocument from the same " +
            // "or other syncClient or remove permission for identity"
    fun subscriptionCancelledFromBackend() = runTest {
        val documentSid = document.sid
        val stateFlow = subscriptionManager.subscribe(documentSid, "document")
        stateFlow.first { it == SubscriptionState.Established }

        val otherSyncClient = TestSyncClient(useLastUserAccount = false) { requestToken("otherUser") }

        val otherClientDocuemnt = otherSyncClient.documents.openExisting(documentSid)
        otherClientDocuemnt.removeDocument()

//        document.removeDocument()
//        println("!!! run:\n" +
//                "twilio api:sync:v1:services:documents:permissions:remove \\\n" +
//                "    --service-sid IS291649b268d047bed173f3a70c3481c4 \\\n" +
//                "    --document-sid $documentSid \\\n" +
//                "    --identity SubscriptionManagerAndroidTest")

        stateFlow.first { it == SubscriptionState.Unsubscribed }
    }
}
