//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.subscriptions

import com.twilio.sync.subscriptions.ReplayStatus.Completed
import com.twilio.sync.subscriptions.ReplayStatus.Interrupted
import com.twilio.sync.subscriptions.SubscriptionAction.*
import com.twilio.sync.subscriptions.SubscriptionState.*
import com.twilio.sync.subscriptions.TerminalEventType.SubscriptionCancelled
import com.twilio.sync.subscriptions.TerminalEventType.SubscriptionEstablished
import com.twilio.sync.subscriptions.TerminalEventType.SubscriptionFailed
import com.twilio.sync.utils.CorrelationId
import com.twilio.sync.utils.EntitySid
import com.twilio.sync.utils.EventType
import com.twilio.sync.utils.SubscriptionsConfig
import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.util.HttpMethod.POST
import com.twilio.twilsock.util.HttpRequest
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.*
import com.twilio.util.NextLong
import com.twilio.util.TwilioException
import com.twilio.util.complete
import com.twilio.util.getOrThrowTwilioException
import com.twilio.util.isClientShutdown
import com.twilio.util.json
import com.twilio.util.logger
import com.twilio.util.retry
import com.twilio.util.success
import com.twilio.util.toTwilioException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration

/**
 * Represents the state of a subscription for sync entities,
 * i.e. [SyncDocument]s, [SyncList]s, [SyncMap]s and [SyncStream]s.
 */
sealed class SubscriptionState {

    /** The initial state, when no one listens for events from sync entity.*/
    object Unsubscribed : SubscriptionState()

    /** Sync entity has subscribers, but a subscription request hasn't been made yet.*/
    object Pending : SubscriptionState()

    /** The subscription request has been made but not yet acknowledged by the server.*/
    object Subscribing : SubscriptionState()

    /** The subscription has been successfully established. */
    object Established : SubscriptionState()

    /** The subscription request has failed.*/
    data class Failed(val errorInfo: ErrorInfo) : SubscriptionState()
}

internal val SubscriptionState.isFailedWithNotFound: Boolean
    get() = this is Failed && HttpStatusCode.fromValue(errorInfo.status) == HttpStatusCode.NotFound

internal data class RemoteEvent(val entitySid: EntitySid, val eventType: EventType, val event: JsonObject)

internal enum class TerminalEventType(val value: String) {
    SubscriptionEstablished("subscription_established"),
    SubscriptionCancelled("subscription_canceled"),
    SubscriptionFailed("subscription_failed");

    companion object {
        val valuesSet = values().map { it.value }.toSet()
    }
}

@Serializable
internal sealed interface Subscription {
    val entitySid: EntitySid
}

@Serializable
internal data class Subscribe(
    @SerialName("object_sid") override val entitySid: EntitySid,
    @SerialName("object_type") val entityType: String,
    @SerialName("last_event_id") var lastEventId: Long? = null,
) : Subscription

@Serializable
internal data class Unsubscribe(
    @SerialName("object_sid") override val entitySid: EntitySid,
    @SerialName("object_type") val entityType: String,
) : Subscription

internal enum class SubscriptionAction {
    @SerialName("establish") Subscribe,
    @SerialName("cancel") Unsubscribe,
}

private class PendingSubscription(
    val correlationId: CorrelationId,
    val action: SubscriptionAction,
    val subscriptions: MutableMap<EntitySid, Subscription>,
)

@Serializable
internal data class SubscriptionResponse(
    @SerialName("estimated_delivery_in_ms") val estimatedDeliveryInMilliseconds: Long,
    @SerialName("max_batch_size") val maxBatchSize: Int,
)

@Serializable
private data class SubscriptionMessage(
    @SerialName("correlation_id") val correlationId: CorrelationId? = null,
    @SerialName("event_type") val eventType: EventType,
    @SerialName("events") val events: List<SubscriptionEvent>? = null,
)

@Serializable
private data class SubscriptionEvent(
    @SerialName("object_sid") val entitySid: EntitySid,
    @SerialName("replay_status") val replayStatus: ReplayStatus? = null,
    @SerialName("error") val error: ErrorInfo? = null,
)

private enum class ReplayStatus {
    @SerialName("completed") Completed,
    @SerialName("interrupted") Interrupted,
}

@Serializable
internal data class SubscriptionRequestBody(
    @SerialName("event_protocol_version") val eventProtocolVersion: Int,
    @SerialName("action") val action: SubscriptionAction,
    @SerialName("correlation_id") val correlationId: CorrelationId,
    @SerialName("requests") val requests: Collection<Subscription>,
)

internal class SubscriptionManager(
    private val coroutineScope: CoroutineScope,
    private val twilsock: Twilsock,
    private val config: SubscriptionsConfig,
) {
    private val _remoteEventsFlow = MutableSharedFlow<RemoteEvent>()

    val remoteEventsFlow: SharedFlow<RemoteEvent> = _remoteEventsFlow.asSharedFlow()

    private var maxBatchSize = config.maxInitialBatchSize

    private val subscriptionStates = mutableMapOf<EntitySid, MutableStateFlow<SubscriptionState>>()
    private val subscriptionCounters = mutableMapOf<EntitySid, Int>()

    private val desiredSubscriptions = mutableMapOf<EntitySid, Subscription>()
    private val pendingSubscriptions = mutableMapOf<CorrelationId, PendingSubscription>()
    private val committedSubscriptions = mutableMapOf<EntitySid, Subscription>()

    private var retrier: Job? = null

    private val messagesFlow = MutableSharedFlow<JsonObject>()
    private val onTerminalMessageProcessed = MutableSharedFlow<SubscriptionMessage>()

    init {
        // Retrier must be infinite by design
        check(config.retrierConfig.maxAttemptsCount == null)
        check(config.retrierConfig.maxAttemptsTime == Duration.INFINITE)

        twilsock.addObserver {
            onConnected = { startRetrier() }
            onDisconnected = { onTwilsockDisconnected() }
            onMessageReceived = ::onMessageReceived
        }

        coroutineScope.launch { messagesFlow.collect(::handleMessageReceived) }
    }

    fun subscribe(entitySid: EntitySid, entityType: String, lastEventId: Long? = null): Flow<SubscriptionState> {
        val counter = subscriptionCounters[entitySid] ?: 0
        logger.d { "subscribe: $entitySid; counter: $counter" }

        val stateFlow = subscriptionStates.getOrPut(entitySid) { MutableStateFlow(Unsubscribed) }
        val flow = stateFlow.onEach { logger.d { "$entitySid --> ${it::class.simpleName}" } }

        subscriptionCounters[entitySid] = counter + 1

        if (counter > 0) {
            return flow
        }

        if (desiredSubscriptions[entitySid] is Unsubscribe) {
            logger.d { "subscribe() skipped for $entitySid: unsubscribe request for it is not sent yet, removed it instead" }
            desiredSubscriptions.remove(entitySid)
        } else {
            stateFlow.compareAndSet(Unsubscribed, Pending) // could be in process of unsubscribing
            desiredSubscriptions[entitySid] = Subscribe(entitySid, entityType, lastEventId)
            startRetrier()
        }

        return flow
    }

    fun unsubscribe(entitySid: EntitySid) {
        val counter = subscriptionCounters[entitySid] ?: 0
        logger.d { "unsubscribe: $entitySid; counter: $counter" }


        if (counter == 0) {
            logger.d { "unsubscribe() skipped for $entitySid: no active subscriptions found" }
            return
        }
        if (counter > 1) {
            logger.d { "unsubscribe() skipped for $entitySid: subscriptionCounters == $counter" }
            subscriptionCounters[entitySid] = counter - 1
            return
        }

        subscriptionCounters.remove(entitySid)

        val entityType = (committedSubscriptions[entitySid] as? Subscribe)?.entityType
            ?: pendingSubscriptions.values.firstNotNullOfOrNull { it.subscriptions[entitySid] as? Subscribe }?.entityType
            ?: (desiredSubscriptions[entitySid] as? Subscribe)?.entityType
            ?: run {
                logger.d { "unsubscribe() skipped for $entitySid: entity not found" }
                return
            }

        if (desiredSubscriptions[entitySid] is Subscribe) {
            logger.d { "unsubscribe() skipped for $entitySid: single subscription is not sent yet, removed it instead" }
            desiredSubscriptions.remove(entitySid)
            subscriptionStates.remove(entitySid)?.value = Unsubscribed
        } else {
            desiredSubscriptions[entitySid] = Unsubscribe(entitySid, entityType)
            startRetrier()
        }
    }

    private fun startRetrier() {
        logger.d("startRetrier")

        if (retrier != null) {
            logger.w("Retrier already started")
            return
        }
        if (desiredSubscriptions.isEmpty()) {
            logger.w("No desired subscriptions")
            return
        }
        if (!twilsock.isConnected) {
            logger.w("Twilsock is not connected")
            return
        }

        retrier = coroutineScope.launch {
            runCatching {
                retry(config.retrierConfig, ::onRetrierAttempt)
            }
            .onSuccess {
                retrier = null

                // Check if some new desiredSubscriptions arrived
                startRetrier()
            }
            .onFailure { t ->
                retrier = null

                if (t.cause.isClientShutdown) {
                    logger.i { "SubscriptionManager: Retrier has been cancelled because the client has been shutdown" }
                } else {
                    logger.e(t) {
                        "Retrier has failed because it has reached the maxAttemptsCount or maxAttemptsTime " +
                                "limit. This should never happen by design as SubscriptionManager should " +
                                "keep retrying infinitely. Now not committed subscriptions won't be retried until next " +
                                "subscribe() call or reconnect. Pass correct config.retrierConfig with infinite limits " +
                                "to fix it."
                    }
                }
            }
        }
    }

    private fun stopRetrier() {
        logger.d("stopRetrier")
        retrier?.cancel()
        retrier = null
    }

    private suspend fun onRetrierAttempt(): Result<Unit> {
        logger.d("onRetrierAttempt")

        if (!twilsock.isConnected) {
            logger.w("Twilsock is not connected")
            return Result.failure(TwilioException(ErrorInfo(TransportDisconnected)))
        }

        val subscriptionsToCommit = mutableListOf<PendingSubscription>()

        subscriptionsToCommit += desiredSubscriptions.values
            .filterIsInstance<Subscribe>()
            .onEach { subscriptionStates[it.entitySid]?.value = Subscribing }
            .chunked(maxBatchSize)
            .map { it.toPendingSubscription(SubscriptionAction.Subscribe) }

        subscriptionsToCommit += desiredSubscriptions.values
            .filterIsInstance<Unsubscribe>()
            .chunked(maxBatchSize)
            .map { it.toPendingSubscription(SubscriptionAction.Unsubscribe) }

        subscriptionsToCommit.forEach { pendingSubscription ->
            desiredSubscriptions -= pendingSubscription.subscriptions.keys
            pendingSubscriptions[pendingSubscription.correlationId] = pendingSubscription
        }

        subscriptionsToCommit
            .map { commitSubscriptionAsync(it) }
            .map { it.await() }
            .firstOrNull { it.isFailure }
            ?.let { return it }

        return Result.success()
    }

    private fun List<Subscription>.toPendingSubscription(action: SubscriptionAction) = PendingSubscription(
        correlationId = NextLong(),
        action = action,
        subscriptions = associateBy { it.entitySid }.toMutableMap()
    )

    private fun commitSubscriptionAsync(pendingSubscription: PendingSubscription) = coroutineScope.async {
        val result = runCatching { processRequest(pendingSubscription) }

        pendingSubscriptions.remove(pendingSubscription.correlationId)

        val exception = result.exceptionOrNull()?.toTwilioException(Unknown) ?: return@async Result.success()

        when (exception.errorInfo.reason) {
            CannotParse -> {
                logger.w(exception) { "Subscription response parsing error for correlationId " +
                        "${pendingSubscription.correlationId}, request will not be retried." }

                pendingSubscription.subscriptions.keys.forEach { entitySid ->
                    clearSubscriptionState(entitySid, Failed(exception.errorInfo))
                }
            }
            else -> {
                logger.w(exception) { "Subscription request with correlationId ${pendingSubscription.correlationId} " +
                        "has failed and will be retried." }

                when (pendingSubscription.action) {
                    SubscriptionAction.Subscribe -> handleSubscribeRequestFailure(pendingSubscription)

                    SubscriptionAction.Unsubscribe -> handleUnsubscribeRequestFailure(pendingSubscription)
                }
            }
        }

        return@async Result.failure(exception)
    }

    private fun handleSubscribeRequestFailure(pendingSubscription: PendingSubscription) {
        require(pendingSubscription.action == SubscriptionAction.Subscribe)

        val unsubscribed = pendingSubscription.subscriptions.filterKeys { desiredSubscriptions[it] is Unsubscribe }

        if (unsubscribed.isNotEmpty()) {
            logger.d {
                "The subscription request failed, but the user has cancelled the following subscriptions: " +
                        "${unsubscribed.keys}. Therefore, the subscription request will not be retried for " +
                        "these subscriptions, but a new unsubscription request will still be sent, because it " +
                        "is unclear whether the subscription was established by the backend or not."
            }
        }

        // keep only subscriptions which still should be established
        pendingSubscription.subscriptions -= unsubscribed.keys
        desiredSubscriptions += pendingSubscription.subscriptions

        pendingSubscription.subscriptions.keys.forEach { subscriptionStates[it]?.value = Pending }
    }

    private fun handleUnsubscribeRequestFailure(pendingSubscription: PendingSubscription) {
        require(pendingSubscription.action == SubscriptionAction.Unsubscribe)

        val subscribed = pendingSubscription.subscriptions.filterKeys { desiredSubscriptions[it] is Subscribe }

        if (subscribed.isNotEmpty()) {
            logger.d {
                "The unsubscription request failed, but the user has resubscribed to the following subscriptions: " +
                        "${subscribed.keys}. Therefore, the unsubscription request will not be retried for these " +
                        "subscriptions, but a new subscription request will still be sent, because it is unclear " +
                        "whether the unsubscription was cancelled by the backend or not."
            }
        }
        pendingSubscription.subscriptions -= subscribed.keys // keep only subscriptions which still should be cancelled
        desiredSubscriptions += pendingSubscription.subscriptions
    }

    private fun clearSubscriptionState(entitySid: EntitySid, lastState: SubscriptionState) {
        val stateFlow = subscriptionStates[entitySid]
        val counter = subscriptionCounters[entitySid] ?: 0

        stateFlow?.value = lastState

        if (counter == 0) {
            subscriptionStates.remove(entitySid)
        } else {
            // While the unsubscribe request was processing - user called subscribe() for the same entity again
            check(desiredSubscriptions[entitySid] is Subscribe) // To be sure this is the case
            stateFlow?.value = Pending
        }
    }

    private suspend fun processRequest(pendingSubscription: PendingSubscription) {
        val onFlowSubscribed = CompletableDeferred<Unit>()
        val waitCorrelationMessagesJob = coroutineScope.launch {
            onTerminalMessageProcessed
                .onSubscription { onFlowSubscribed.complete() }
                .takeWhile { pendingSubscription.subscriptions.isNotEmpty() }
                .collect()

            logger.d {
                "All subscriptions with correlationId ${pendingSubscription.correlationId} have been handled"
            }
        }

        // subscription_established or subscription_cancelled notification can arrive before a
        // reply to the corresponding establish/cancel message. So we have to start monitoring messages
        // with our correlationId before sending the establish/cancel message.
        onFlowSubscribed.await()

        val body = SubscriptionRequestBody(
            eventProtocolVersion = config.eventProtocolVersion,
            action = pendingSubscription.action,
            correlationId = pendingSubscription.correlationId,
            requests = pendingSubscription.subscriptions.values
        )

        val request = HttpRequest(
            url = config.url,
            method = POST,
            timeout = config.httpTimeout,
            payload = json.encodeToString(body),
        )

        logger.d { "request: $request" }

        try {
            val httpResponse = twilsock.sendRequest(request)
            val parseResult = runCatching { json.decodeFromString<SubscriptionResponse>(httpResponse.payload) }
            val subscriptionResponse = parseResult.getOrThrowTwilioException(CannotParse)

            maxBatchSize = subscriptionResponse.maxBatchSize

            withTimeout(subscriptionResponse.estimatedDeliveryInMilliseconds) { waitCorrelationMessagesJob.join() }
        } finally {
            // if error occurred we have to cancel the job, if no - the job has finished. So this line does nothing.
            waitCorrelationMessagesJob.cancel()
        }
    }

    private suspend fun handleTerminalMessage(message: SubscriptionMessage) {
        logger.d { "handleTerminalMessage: $message" }

        when (message.eventType) {
            SubscriptionEstablished.value -> handleSubscriptionEstablished(message)
            SubscriptionCancelled.value -> handleSubscriptionCancelled(message)
            SubscriptionFailed.value -> handleSubscriptionFailed(message)
        }

        onTerminalMessageProcessed.emit(message)
    }

    private fun handleSubscriptionEstablished(message: SubscriptionMessage) {
        val pendingSubscription = pendingSubscriptions[message.correlationId] ?: run {
            logger.w("handleSubscriptionEstablished: cannot find pending subscription for " +
                    "correlationId: {message.correlationId}, message skipped.")
            return
        }

        message.events?.forEach { event ->
            val entitySid = event.entitySid
            val subscription = pendingSubscription.subscriptions.remove(entitySid) ?: return@forEach

            when (event.replayStatus) {
                Completed -> {
                    subscriptionStates[entitySid]?.value = Established
                    committedSubscriptions.put(entitySid, subscription)
                }

                Interrupted -> {
                    subscriptionStates[entitySid]?.value = Pending
                    desiredSubscriptions.put(entitySid, subscription)
                }

                else -> logger.w { "No replay_status field in the subscription_established notification. " +
                        "This should never happen. $message" }
            }
        }
    }

    private fun handleSubscriptionCancelled(message: SubscriptionMessage) {
        message.events?.forEach { event ->
            val entitySid = event.entitySid
            committedSubscriptions -= entitySid
            pendingSubscriptions[message.correlationId]?.subscriptions?.remove(entitySid)

            if (message.correlationId == null) { // cancelled by backend
                subscriptionStates.remove(entitySid)?.value = Unsubscribed
                subscriptionCounters.remove(entitySid)
            } else { // cancel request completed
                clearSubscriptionState(entitySid, Unsubscribed)
            }
        }
    }

    private fun handleSubscriptionFailed(message: SubscriptionMessage) {
        val pendingSubscription = pendingSubscriptions[message.correlationId] ?: return

        message.events?.forEach { event ->
            val entitySid = event.entitySid
            pendingSubscription.subscriptions.remove(entitySid) ?: return@forEach

            val error = event.error ?: ErrorInfo(message = "Unknown error while subscribing: $entitySid")

            // If subscription has failed, we shouldn't retry it as we wouldn't be able to subscribe to it anyway.
            // So we don't put it back into desiredSubscriptions

            subscriptionStates.remove(entitySid)?.value = Failed(error)
            subscriptionCounters.remove(entitySid)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onMessageReceived(messageType: String, message: String) {
        coroutineScope.launch {
            runCatching { json.parseToJsonElement(message).jsonObject }
                .onSuccess { messagesFlow.emit(it) }
                .onFailure { logger.w(it) { "Error parsing message to json object: $message" } }
        }
    }

    private suspend fun handleMessageReceived(messageJson: JsonObject) {
        logger.d { "handleMessageReceived: $messageJson" }

        val result = runCatching { json.decodeFromJsonElement<SubscriptionMessage>(messageJson) }
        val message = result.getOrElse {
            logger.w(it) { "Error parsing message: $messageJson" }
            return
        }

        if (message.isTerminal) {
            handleTerminalMessage(message)
        } else {
            messageJson["events"]?.jsonArray?.forEach { event ->
                dispatchRemoteEvent(message.correlationId, message.eventType, event.jsonObject)
            }
            messageJson["event"]?.let { event ->
                dispatchRemoteEvent(message.correlationId, message.eventType, event.jsonObject)
            }
        }
    }

    private val SubscriptionMessage.isTerminal get() = (eventType in TerminalEventType.valuesSet)

    private fun dispatchRemoteEvent(correlationId: CorrelationId?, eventType: EventType, event: JsonObject) {
        logger.d { "dispatchRemoteEvent: correlationId: $correlationId; eventType: $eventType; event: $event" }

        val entitySid = event["document_sid"]?.jsonPrimitive?.content
            ?: event["list_sid"]?.jsonPrimitive?.content
            ?: event["map_sid"]?.jsonPrimitive?.content
            ?: event["stream_sid"]?.jsonPrimitive?.content
            ?: run {
                logger.w("Event skipped: cannot find entitySid for event: $event")
                return
            }

        val isSubscriptionCommitted = committedSubscriptions.containsKey(entitySid)

        val subscription = if (isSubscriptionCommitted) {
            committedSubscriptions[entitySid] as? Subscribe
        } else {
            pendingSubscriptions[correlationId]?.subscriptions?.get(entitySid) as? Subscribe
        }

        val eventId = event["id"]?.jsonPrimitive?.content?.toLong()

        if (subscription == null) {
            // Subscription is not fully established yet.
            // This event should be replayed with correct ordering during establishing the subscription.
            //
            // But in fact it's not always true. Sometimes we subscribe, for instance with correlationId == 8 and
            // last_event_id == 399, then we receive eventId 431 with correlationId == null, then events 400-430 are
            // replayed with correlationId == 8, but event 431 is never come with correlationId == 8.
            //
            // So we cannot ignore events here.
            //
            // See the log below for details:

            // 11-07 03:57:44.490: I/System.out(26586): 4490 [1860] SubscriptionManager MPe69886bd4d3cf05b9664d2be282018e8 --> Subscribing
            //
            // 11-07 03:57:44.491: I/System.out(26586): 4491 [1860] SubscriptionManager request: HttpRequest(url=https://cds.us1.twilio.com/v4/Subscriptions, method=POST, headers={}, timeout=10s, payload={"event_protocol_version":4,"action":"establish","correlation_id":8,"requests":[{"type":"com.twilio.sync.subscriptions.Subscribe","object_sid":"MPe69886bd4d3cf05b9664d2be282018e8","object_type":"map","last_event_id":399}]})
            //
            // 11-07 03:57:44.591: I/System.out(26586): 4591 [1860] SubscriptionManager dispatchRemoteEvent: correlationId: null; eventType: map_item_removed; event: {"item_revision":"1af","date_created":"2023-11-07T11:57:43.715Z","item_key":"key30","map_revision":"1b0","id":431,"map_sid":"MPe69886bd4d3cf05b9664d2be282018e8","item_data":{"data2":"value30"}}
            // 11-07 03:57:44.591: I/System.out(26586): 4591 [1860] SubscriptionManager Subscription for sid MPe69886bd4d3cf05b9664d2be282018e8 not found, eventId 431 has been ignored
            //
            //
            // 11-07 03:57:44.604: I/System.out(26586): 4604 [1860] SubscriptionManager dispatchRemoteEvent: correlationId: 8; eventType: map_item_removed; event: {"item_revision":"1ae","date_created":"2023-11-07T11:57:43.713Z","item_key":"key31","map_revision":"1af","id":430,"map_sid":"MPe69886bd4d3cf05b9664d2be282018e8","item_data":{"data2":"value31"}}
            // 11-07 03:57:44.604: I/System.out(26586): 4604 [1860] SubscriptionManager dispatchRemoteEvent emit: RemoteEvent(entitySid=MPe69886bd4d3cf05b9664d2be282018e8, eventType=map_item_removed, event={"item_revision":"1ae","date_created":"2023-11-07T11:57:43.713Z","item_key":"key31","map_revision":"1af","id":430,"map_sid":"MPe69886bd4d3cf05b9664d2be282018e8","item_data":{"data2":"value31"}})
            //
            // !!! NO EVENT WITH ID 431 HERE !!!
            //
            // 11-07 03:57:44.604: I/System.out(26586): 4604 [1860] SubscriptionManager handleTerminalMessage: SubscriptionMessage(correlationId=8, eventType=subscription_established, events=[SubscriptionEvent(entitySid=MPe69886bd4d3cf05b9664d2be282018e8, replayStatus=Completed, error=null)])

            logger.w { "Subscription for sid $entitySid not found, eventId $eventId will be emitted anyway..." }

            // no return here, emit the event anyway...
        }

        val lastEventId = subscription?.lastEventId

        if (lastEventId == null || (eventId != null && eventId > lastEventId)) {
            subscription?.lastEventId = eventId
        } else {
            logger.w { "Got wrong ordered event: eventId: $eventId received after $lastEventId" }

            // This is often reproduces with the MapAndroidTest.setItemDataRemoteStress() test. According to logs
            // we receive disordered events right from websocket (in the onBinaryMessage() callback
            // in TwilsockTransport)
            //
            // We can receive order like:
            // - eventId: 25
            // - eventId: 27
            // - eventId: 26
            // In this example if connection drops right after eventId==27 - the eventId==26 will not be replayed
            // after poke-on-reconnect procedure, because after reconnect lastEventId==27 will be sent while
            // re-establishing subscription.
            //
            // Even worse: lastEventId==27 will be stored in the persistent cache. So eventId==26 will not be received
            // even after sync client recreation. Only cache cleanup by customer's code can help to finally receive
            // actual data (or one more update on the same data, e.g. map item etc).
            //
            // It looks we can do nothing with it on the client side :(
            //
            // Hopefully backend folks will help: https://twilio.slack.com/archives/C0XM22HFF/p1692778630856589
        }

        val remoteEvent = RemoteEvent(entitySid, eventType, event)
        logger.d { "dispatchRemoteEvent emit: $remoteEvent" }
        coroutineScope.launch { _remoteEventsFlow.emit(remoteEvent) }
    }

    private fun onTwilsockDisconnected() {
        committedSubscriptions.values.forEach { subscription ->
            subscriptionStates[subscription.entitySid]?.value = Pending
        }

        desiredSubscriptions += committedSubscriptions // POKE all previously established subscriptions
        committedSubscriptions.clear()
        stopRetrier()
    }
}
