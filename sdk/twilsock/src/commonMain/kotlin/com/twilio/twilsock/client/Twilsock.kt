//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client

import com.twilio.twilsock.client.SideEffect.HandleMessageReceived
import com.twilio.twilsock.client.SideEffect.NotifyObservers
import com.twilio.twilsock.client.Status.Companion.BadRequest
import com.twilio.twilsock.client.Status.Companion.Ok
import com.twilio.twilsock.client.TwilsockEvent.OnConnect
import com.twilio.twilsock.client.TwilsockEvent.OnDisconnect
import com.twilio.twilsock.client.TwilsockEvent.OnFatalError
import com.twilio.twilsock.client.TwilsockEvent.OnInitMessageReceived
import com.twilio.twilsock.client.TwilsockEvent.OnMessageReceived
import com.twilio.twilsock.client.TwilsockEvent.OnNetworkBecameReachable
import com.twilio.twilsock.client.TwilsockEvent.OnNetworkBecameUnreachable
import com.twilio.twilsock.client.TwilsockEvent.OnNonFatalError
import com.twilio.twilsock.client.TwilsockEvent.OnSendRequest
import com.twilio.twilsock.client.TwilsockEvent.OnTimeout
import com.twilio.twilsock.client.TwilsockEvent.OnTooManyRequests
import com.twilio.twilsock.client.TwilsockEvent.OnTransportConnected
import com.twilio.twilsock.client.TwilsockEvent.OnUpdateToken
import com.twilio.twilsock.client.TwilsockMessage.Method.INIT
import com.twilio.twilsock.client.TwilsockMessage.Method.REPLY
import com.twilio.twilsock.client.TwilsockMessage.Method.UPDATE
import com.twilio.twilsock.client.TwilsockMessage.Method.UPSTREAM_REQUEST
import com.twilio.twilsock.client.TwilsockState.Connected
import com.twilio.twilsock.client.TwilsockState.Connecting
import com.twilio.twilsock.client.TwilsockState.Disconnected
import com.twilio.twilsock.client.TwilsockState.Initializing
import com.twilio.twilsock.client.TwilsockState.Throttling
import com.twilio.twilsock.client.TwilsockState.WaitAndReconnect
import com.twilio.twilsock.util.ConnectivityMonitor
import com.twilio.twilsock.util.ConnectivityMonitorImpl
import com.twilio.twilsock.util.HandledInCppException
import com.twilio.twilsock.util.HttpRequest
import com.twilio.twilsock.util.HttpResponse
import com.twilio.twilsock.util.Unsubscriber
import com.twilio.twilsock.util.toErrorInfo
import com.twilio.twilsock.util.toMultiMap
import com.twilio.util.AccountDescriptor
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.CannotParse
import com.twilio.util.ErrorReason.CloseMessageReceived
import com.twilio.util.ErrorReason.HostnameUnverified
import com.twilio.util.ErrorReason.NetworkBecameUnreachable
import com.twilio.util.ErrorReason.SslHandshakeError
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.ErrorReason.TokenExpired
import com.twilio.util.ErrorReason.TokenUpdatedLocally
import com.twilio.util.ErrorReason.TooManyRequests
import com.twilio.util.ErrorReason.TransportDisconnected
import com.twilio.util.ErrorReason.Unauthorized
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.StateMachine
import com.twilio.util.StateMachine.Transition.DontTransition
import com.twilio.util.StateMachine.Transition.Valid
import com.twilio.util.Timer
import com.twilio.util.TwilioException
import com.twilio.util.fibonacci
import com.twilio.util.json
import com.twilio.util.logger
import io.ktor.http.HttpStatusCode
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val kTokenExpiredCode = 20104;

interface Twilsock {
    val isConnected: Boolean
    val accountDescriptor: AccountDescriptor?

    fun connect()
    fun disconnect()

    suspend fun sendRequest(httpRequest: HttpRequest): HttpResponse
    suspend fun sendRequest(requestId: String, timeout: Duration, rawMessage: ByteArray): HttpResponse
    suspend fun updateToken(newToken: String)

    fun populateInitRegistrations(messageTypes: Set<String>)
    fun handleMessageReceived(data: ByteArray)

    fun addObserver(block: TwilsockObserver.() -> Unit): Unsubscriber
}

data class AuthData(
    val token: String,
    val activeGrant: String,
    val notificationProductId: String,
    val certificates: List<String>,
    val tweaks: JsonObject = buildJsonObject {},
) {
    constructor( // for easy use from JNI
        token: String,
        activeGrant: String,
        notificationProductId: String,
        certificates: List<String>,
        tweaksJson: String,
    ) : this(
        token,
        activeGrant,
        notificationProductId,
        certificates,
        json.parseToJsonElement(tweaksJson) as? JsonObject ?: buildJsonObject {}
    )
}

internal sealed class TwilsockState {
    data class Disconnected(val errorInfo: ErrorInfo) : TwilsockState()
    object Connecting : TwilsockState()
    object Initializing : TwilsockState()
    object Connected : TwilsockState()
    data class WaitAndReconnect(val waitTime: Duration? = null) : TwilsockState()
    data class Throttling(val waitTime: Duration) : TwilsockState()
}

private sealed class TwilsockEvent {
    object OnConnect : TwilsockEvent()
    object OnDisconnect : TwilsockEvent()
    data class OnUpdateToken(val token: String, val request: TwilsockRequest) : TwilsockEvent()
    data class OnSendRequest(val request: TwilsockRequest) : TwilsockEvent()
    object OnTransportConnected : TwilsockEvent()
    object OnInitMessageReceived : TwilsockEvent()
    data class OnTooManyRequests(val waitTime: Duration) : TwilsockEvent()
    object OnNetworkBecameReachable : TwilsockEvent()
    object OnNetworkBecameUnreachable : TwilsockEvent()
    object OnTimeout : TwilsockEvent()
    data class OnNonFatalError(val errorInfo: ErrorInfo) : TwilsockEvent()
    data class OnFatalError(val errorInfo: ErrorInfo) : TwilsockEvent()
    data class OnMessageReceived(val message: TwilsockMessage) : TwilsockEvent()
}

private sealed class SideEffect {
    class NotifyObservers(val block: TwilsockObserver.() -> Unit) : SideEffect()
    class HandleMessageReceived(val message: TwilsockMessage) : SideEffect()
}

class TwilsockObserver(
    var onConnecting: () -> Unit = {},
    var onConnected: () -> Unit = {},
    var onDisconnected: (reason: String) -> Unit = {},
    var onFatalError: (errorInfo: ErrorInfo) -> Unit = {},
    var onNonFatalError: (errorInfo: ErrorInfo) -> Unit = {},

    var onTokenAboutToExpire: () -> Unit = {},
    var onTokenExpired: () -> Unit = {},

    var onMessageReceived: (messageType: String, message: String) -> Unit = { _, _ -> },

    /*
     * This method is used in performance optimisation tricks. See JniFuture.onHandledInCpp().
     * Should be removed together with JniFuture when Sync will be implemented in kotlin.
     */
    var onRawDataReceived: (data: ByteArray) -> Boolean = { false },
)

internal class TwilsockImpl(
    private val coroutineScope: CoroutineScope,
    private val url: String,
    private val useProxy: Boolean,
    private val authData: AuthData,
    private val clientMetadata: ClientMetadata,
    private val continuationTokenStorage: ContinuationTokenStorage = ContinuationTokenStorageImpl(),
    private val connectivityMonitor: ConnectivityMonitor = ConnectivityMonitorImpl(coroutineScope),
    private val twilsockTransportFactory: TwilsockTransportFactory = ::TwilsockTransportFactory,
) : Twilsock, TwilsockTransportListener {

    override val isConnected: Boolean get() = state is Connected

    override var accountDescriptor: AccountDescriptor? = null

    val state: TwilsockState get() = stateMachine.state

    var token: String = authData.token

    val initRegistrations = mutableSetOf<String>()

    val pendingRequests = mutableMapOf<String, TwilsockRequest>()

    val sentRequests = mutableMapOf<String, TwilsockRequest>()

    var failedReconnectionAttempts = 0

    private val observers = mutableSetOf<TwilsockObserver>()

    private var websocket: TwilsockTransport? = null

    private val isNetworkAvailable get() = connectivityMonitor.isNetworkAvailable

    private val watchdogTimer = Timer(coroutineScope)

    private val stateMachine: StateMachine<TwilsockState, TwilsockEvent, SideEffect> = StateMachine.create {
        initialState(Disconnected(ErrorInfo(message = "twilsock created")))

        state<Disconnected> {
            onEnter {
                failedReconnectionAttempts = 0
                connectivityMonitor.stop()
                failAllSentRequests(errorInfo)
                failAllPendingRequests(errorInfo)
                shutdownWebSocket()
                notifyObservers { onDisconnected(errorInfo.message) }
            }
            onExit {
                connectivityMonitor.start()
            }
            on<OnConnect> { transitionTo(Connecting) }
            on<OnUpdateToken> { event ->
                token = event.token
                event.request.cancel(ErrorInfo(TokenUpdatedLocally))
                transitionTo(Connecting)
            }
            on<OnSendRequest> { event ->
                val errorInfo = ErrorInfo(TransportDisconnected, message = "Cannot send request in disconnected state")
                event.request.cancel(errorInfo)
                dontTransition()
            }
        }

        state<Connecting> {
            onEnter {
                connectWebSocket()
                notifyObservers { onConnecting() }
            }
            defaultOnDisconnect()
            on<OnUpdateToken> { event ->
                token = event.token
                event.request.cancel(ErrorInfo(TokenUpdatedLocally))
                dontTransition()
            }
            on<OnSendRequest> { event ->
                addPendingRequest(event.request)
                dontTransition()
            }
            on<OnTransportConnected> { transitionTo(Initializing) }
            defaultOnNetworkBecameUnreachable()
            defaultOnNonFatalError()
            defaultOnFatalError()
        }

        state<Initializing> {
            lateinit var request: TwilsockRequest

            onEnter {
                // 2 seconds timeout by design.
                // See https://curly-parakeet-d60caa01.pages.github.io/content/6_Shared/2_Retrier_Timeouts.html
                request = createInitRequest(timeout = 2.seconds).apply {
                    send()
                    onReply(
                        onSuccess = { onInitMessageReceived() },
                        onTimeout = { onTimeout() }
                    )
                }
            }
            onExit {
                request.cancel()
            }
            on<OnTimeout> {
                transitionTo(WaitAndReconnect(), NotifyObservers { onNonFatalError(ErrorInfo(Timeout)) })
            }
            defaultOnMessageReceived()
            defaultOnDisconnect()
            on<OnUpdateToken> { event ->
                token = event.token
                addPendingRequest(event.request)
                dontTransition()
            }
            on<OnSendRequest> { event ->
                addPendingRequest(event.request)
                dontTransition()
            }
            on<OnInitMessageReceived> { transitionTo(Connected) }
            on<OnTooManyRequests> { event ->
                val errorInfo = ErrorInfo(TooManyRequests)
                transitionTo(WaitAndReconnect(event.waitTime), NotifyObservers { onNonFatalError(errorInfo) })
            }
            defaultOnNetworkBecameUnreachable()
            defaultOnNonFatalError()
            defaultOnFatalError()
        }

        state<Connected> {
            onEnter {
                failedReconnectionAttempts = 0
                startWatchdogTimer()
                sendAllPendingRequests()
                notifyObservers { onConnected() }
            }
            onExit {
                cancelWatchdogTimer()
            }
            defaultOnMessageReceived()
            defaultOnDisconnect()
            on<OnUpdateToken> { event ->
                token = event.token
                event.request.send()
                dontTransition()
            }
            on<OnSendRequest> { event ->
                event.request.send()
                dontTransition()
            }
            on<OnTooManyRequests> { event ->
                val errorInfo = ErrorInfo(TooManyRequests)
                transitionTo(Throttling(event.waitTime), NotifyObservers { onNonFatalError(errorInfo) })
            }
            defaultOnNetworkBecameUnreachable()
            defaultOnNonFatalError()
            defaultOnFatalError()
        }

        state<WaitAndReconnect> {
            val timer = Timer(coroutineScope)
            onEnter {
                val finalWaitTime = waitTime ?: calcDefaultWaitTime()
                logger.d { "failedReconnectionAttempts: $failedReconnectionAttempts; finalWaitTime: $finalWaitTime" }

                failAllSentRequests(
                    ErrorInfo(TransportDisconnected,
                        message = "Transport disconnected, will try to reconnect after $finalWaitTime")
                )

                shutdownWebSocket()
                if (isNetworkAvailable) {
                    timer.schedule(finalWaitTime) { onTimeout() }
                }
                failedReconnectionAttempts++
                notifyObservers { onDisconnected("wait and reconnect") }
            }
            onExit {
                timer.cancel()
            }
            on<OnTimeout> { transitionTo(Connecting) }
            on<OnConnect> { transitionTo(Connecting) }
            defaultOnDisconnect()
            on<OnUpdateToken> { event ->
                token = event.token
                event.request.cancel(ErrorInfo(TokenUpdatedLocally))
                dontTransition()
            }
            on<OnSendRequest> { event ->
                addPendingRequest(event.request)
                dontTransition()
            }
            on<OnNetworkBecameReachable> {
                failedReconnectionAttempts = 0
                transitionTo(Connecting)
            }
            on<OnNetworkBecameUnreachable> {
                timer.cancel()
                dontTransition()
            }
            // Fatal/NonFatalError are ignored:
            // 1. Websocket is disconnected in this state. So no errors can happen.
            // 2. shutdownWebSocket() can lead to onTransportDisconnected() callback which
            //    is handled as NonFatalError in other states, but in this state we shouldn't notify observers
            //    about NonFatalError in this case.
        }

        state<Throttling> {
            val timer = Timer(coroutineScope)
            onEnter {
                timer.schedule(waitTime) { onTimeout() }
            }
            onExit {
                timer.cancel()
            }
            on<OnTimeout> { transitionTo(Connected) }
            defaultOnMessageReceived()
            defaultOnDisconnect()
            on<OnUpdateToken> { event ->
                token = event.token
                addPendingRequest(event.request)
                dontTransition()
            }
            on<OnSendRequest> { event ->
                addPendingRequest(event.request)
                dontTransition()
            }
            defaultOnNetworkBecameUnreachable()
            defaultOnNonFatalError()
            defaultOnFatalError()
        }

        onTransition { transition ->
            val sideEffect = when(transition) {
                is Valid -> {
                    logger.d {
                        "onTransition: " +
                                "${transition.fromState::class.simpleName} -> " +
                                "${transition.toState::class.simpleName} " +
                                "[${transition.event}]"
                    }
                    transition.sideEffect
                }

                is DontTransition -> {
                    logger.d {
                        "dontTransition: " +
                                "${transition.fromState::class.simpleName} " +
                                "[${transition.event}]"
                    }
                    transition.sideEffect
                }

                else -> return@onTransition
            }

            when (sideEffect) {
                is NotifyObservers -> notifyObservers(sideEffect.block)

                is HandleMessageReceived -> handleMessageReceived(sideEffect.message)

                null -> Unit
            }
        }
    }

    init {
        connectivityMonitor.onChanged = this::onConnectivityChanged
    }

    private fun failAllPendingRequests(errorInfo: ErrorInfo) {
        val requests = buildList { addAll(pendingRequests.values) } // to avoid ConcurrentModificationException
        pendingRequests.clear()
        requests.forEach { it.cancel(errorInfo) }
    }

    private fun failAllSentRequests(errorInfo: ErrorInfo) {
        val requests = buildList { addAll(sentRequests.values) } // to avoid ConcurrentModificationException
        sentRequests.clear()
        requests.forEach { it.cancel(errorInfo) }
    }

    private fun sendAllPendingRequests() {
        pendingRequests.values.forEach { it.send() }
        pendingRequests.clear()
    }

    private fun addPendingRequest(request: TwilsockRequest) = pendingRequests.put(request.message.requestId, request)

    private fun calcDefaultWaitTime(): Duration {
        // Wait time is calculated according to the requirements.
        // See requirements: https://curly-parakeet-d60caa01.pages.github.io/content/6_Shared/2_Retrier_Timeouts.html
        val waitTime = min(fibonacci(failedReconnectionAttempts), 45.0)
        val randomDelayToAdd = waitTime * 0.2 * Random.nextDouble(0.0, 1.0)

        return (waitTime + randomDelayToAdd).seconds
    }

    private fun startWatchdogTimer() {
        // 45 seconds timeout by design
        // See requirements: https://curly-parakeet-d60caa01.pages.github.io/content/6_Shared/2_Retrier_Timeouts.html
        watchdogTimer.schedule(45.seconds) {
            logger.w("watchdog timeout")
            stateMachine.transition(OnNonFatalError(ErrorInfo(Timeout, message = "watchdog timeout")))
        }
    }

    private fun cancelWatchdogTimer() {
        watchdogTimer.cancel()
    }

    private fun restartWatchdogTimer() {
        if (watchdogTimer.isScheduled) {
            cancelWatchdogTimer()
            startWatchdogTimer()
        }
    }

    override fun connect() {
        logger.d("connect")
        stateMachine.transition(OnConnect)
    }

    override fun disconnect() {
        logger.d("disconnect")
        stateMachine.transition(OnDisconnect)
    }

    override suspend fun sendRequest(httpRequest: HttpRequest): HttpResponse {
        logger.d("sendRequest")

        val request = createUpstreamRequest(httpRequest)
        stateMachine.transition(OnSendRequest(request))

        val result = runCatching { request.awaitResponse<TwilsockReplyMessage>() }
        if (result.exceptionOrNull() is CancellationException) {
            logger.d("the request ${request.message.requestId} has been cancelled by the user")
            request.cancel() // have to cancel request if coroutine has been cancelled
        }

        val message = result.getOrThrow()

        return HttpResponse(
            statusCode = message.replyHeaders.httpStatus.code,
            status = message.replyHeaders.httpStatus.status,
            rawMessageHeaders = message.rawHeaders,
            headers = message.replyHeaders.httpHeaders.toMultiMap(),
            payload = message.payload
        )
    }

    /*
     * This method is used in performance optimisation tricks. See JniFuture.onHandledInCpp().
     * Used only from TwilsockWrapper and should be removed together with TwilsockWrapper
     * when Sync will be implemented in kotlin.
     */
    override suspend fun sendRequest(requestId: String, timeout: Duration, rawMessage: ByteArray): HttpResponse {
        logger.d("sendRequest(raw)")

        val request = createUpstreamRequest(requestId, timeout, rawMessage)

        // This is because the stateMachine.transition() is suddenly expensive it terms of performance.
        // So we save time and call request.send() directly when we are in the Connected state (which are 90%+ of cases)
        // TODO: optimise stateMachine.transition() and drop this condition
        if (stateMachine.state === Connected) {
            request.send()
        } else {
            stateMachine.transition(OnSendRequest(request))
        }

        val result = runCatching { request.awaitResponse<TwilsockReplyMessage>() }

        when (result.exceptionOrNull()) {
            is HandledInCppException -> request.cancel(ErrorInfo(message = "Reply has been handled on CPP level"))

            is CancellationException -> {
                logger.d("the request ${request.message.requestId} has been cancelled by the user")
                request.cancel() // have to cancel request if coroutine has been cancelled
            }
        }

        val message = result.getOrThrow()

        return HttpResponse(
            statusCode = message.replyHeaders.httpStatus.code,
            status = message.replyHeaders.httpStatus.status,
            rawMessageHeaders = message.rawHeaders,
            headers = message.replyHeaders.httpHeaders.toMultiMap(),
            payload = message.payload
        )
    }

    override suspend fun updateToken(newToken: String) {
        logger.d("updateToken")

        if (newToken == token) {
            logger.i { "token is the same, skipping update" }
            return
        }

        val request = createUpdateTokenRequest(newToken)
        stateMachine.transition(OnUpdateToken(newToken, request))

        val result = runCatching { request.awaitResponse<TwilsockMessage>() }
        when (val e = result.exceptionOrNull()) {
            is TwilioException -> {
                if (e.errorInfo.reason == TokenUpdatedLocally) {
                    logger.d("token updated locally")
                    return
                }
            }

            is CancellationException -> {
                logger.w("updateToken cancelled", e)
                request.cancel() // have to cancel request if coroutine has been cancelled
            }

            null -> logger.i("token updated remotely")
        }

        result.getOrThrow()
    }

    override fun populateInitRegistrations(messageTypes: Set<String>) {
        logger.d("populateInitRegistrations: $messageTypes")
        initRegistrations.addAll(messageTypes)
    }

    override fun addObserver(block: TwilsockObserver.() -> Unit) = addObserver(TwilsockObserver().apply(block))

    fun addObserver(observer: TwilsockObserver): Unsubscriber {
        observers += observer
        return Unsubscriber { observers -= observer }
    }

    private inline fun notifyObservers(crossinline block: TwilsockObserver.() -> Unit) = coroutineScope.launch {
        observers.forEach { it.block() }
    }

    private inline fun notifyObserversSync(block: TwilsockObserver.() -> Unit) {
        observers.forEach { it.block() }
    }

    private fun connectWebSocket() {
        logger.d("connectWebSocket")
        check(websocket == null)

        val connectionTimeout = 60.seconds
        val transportListener = this
        websocket = twilsockTransportFactory(coroutineScope, connectionTimeout, authData.certificates, transportListener).apply {
            connect(url, useProxy)
        }
    }

    private fun shutdownWebSocket() {
        logger.d("shutdownWebSocket")

        websocket?.disconnect("shutdownWebSocket")
        websocket = null
    }

    private fun createInitRequest(timeout: Duration): TwilsockRequest {
        val registrations = InitRegistration(
            productId = authData.notificationProductId,
            messageTypes = initRegistrations,
        )

        val tweakKey = buildJsonObject {
            put("tweak_key", "TweakKey-2019")
        }

        val headers = InitMessageHeaders(
            capabilities = listOf("client_update", "account_descriptor"/*, "telemetry.v1"*/),
            token = token,
            continuationToken = continuationTokenStorage.continuationToken.takeIf { it.isNotEmpty() },
            registrations = listOf(registrations).takeIf { registrations.messageTypes.isNotEmpty() },
            tweaks = JsonObject(tweakKey + authData.tweaks).takeIf { authData.tweaks.isNotEmpty() },
            metadata = clientMetadata
        )

        val message = TwilsockMessage(
            method = INIT,
            headers = json.encodeToJsonElement(headers).jsonObject,
        )

        return TwilsockRequest(
            coroutineScope = coroutineScope,
            message = message,
            timeout = timeout,
            onFinished = this::onRequestFinished
        )
    }

    private fun createUpdateTokenRequest(newToken: String): TwilsockRequest {
        val message = TwilsockMessage(
            method = UPDATE,
            headers = buildJsonObject {
                put("token", newToken)
            },
        )

        // 60 seconds default timeout by design
        // See requirements: https://curly-parakeet-d60caa01.pages.github.io/#/Advanced%20Messaging%20SDKs/Twilsock/HOME
        return TwilsockRequest(
            coroutineScope = coroutineScope,
            message = message,
            timeout = 60.seconds,
            onFinished = this::onRequestFinished
        )
    }

    private fun createUpstreamRequest(httpRequest: HttpRequest): TwilsockRequest {
        val headers = UpstreamRequestMessageHeaders(
            activeGrant = authData.activeGrant,
            httpRequest = httpRequest,
        )

        val message = TwilsockMessage(
            method = UPSTREAM_REQUEST,
            headers = json.encodeToJsonElement(headers).jsonObject,
            payloadType = httpRequest.headers["Content-Type"]?.first() ?: "application/json",
            payload = httpRequest.payload,
        )

        return TwilsockRequest(
            coroutineScope = coroutineScope,
            message = message,
            timeout = httpRequest.timeout,
            onFinished = this::onRequestFinished
        )
    }

    private fun createUpstreamRequest(requestId: String, timeout: Duration, request: ByteArray): TwilsockRequest {
        val message = TwilsockMessage(
            requestId = requestId,
            method = UPSTREAM_REQUEST,
            rawMessage = request
        )

        return TwilsockRequest(
            coroutineScope = coroutineScope,
            message = message,
            timeout = timeout,
            onFinished = this::onRequestFinished
        )
    }

    private fun onRequestFinished(request: TwilsockRequest) {
        pendingRequests.remove(request.message.requestId)
        sentRequests.remove(request.message.requestId)
    }

    private fun onTimeout() {
        logger.d("onTimeout")
        stateMachine.transition(OnTimeout)
    }

    private fun onInitMessageReceived() {
        logger.d("onInitMessageReceived")
        stateMachine.transition(OnInitMessageReceived)
    }

    override fun onTransportConnected() {
        logger.d("onTransportConnected")
        stateMachine.transition(OnTransportConnected)
    }

    override fun onTransportDisconnected(errorInfo: ErrorInfo) {
        logger.d("onTransportDisconnected: $errorInfo")

        when (errorInfo.reason) {
            SslHandshakeError,
            HostnameUnverified -> stateMachine.transition(OnFatalError(errorInfo))

            else -> stateMachine.transition(OnNonFatalError(errorInfo))
        }
    }

    override fun onMessageReceived(data: ByteArray) {
        var handled = false
        notifyObserversSync {
            /*
            * In order to improve performance we pass all incoming twilsock packets to CPP level, parse them there
            * (because parsing in CPP works faster than in JVM) and handle "reply" packages there (which are 90%+ packets).
            *
            * If packet is not handled in CPP level it passed back to JVM and handled in TwilsockWrapper.handleMessageReceived().
            */
            handled = handled or onRawDataReceived(data)
        }

        coroutineScope.launch { restartWatchdogTimer() }

        if (!handled) {
            coroutineScope.launch { handleMessageReceived(data) }
        }
    }


    /*
     * This method is used in performance optimisation tricks. See JniFuture.onHandledInCpp().
     * Should be merged into onMessageReceived() when Sync will be implemented in kotlin.
     */
    override fun handleMessageReceived(data: ByteArray) {
        val rawMessage = data.decodeToString()

        val message = runCatching { TwilsockMessage.parse(rawMessage) }.getOrElse { t ->
            logger.w("Error parsing incoming message: $rawMessage", t)
            stateMachine.transition(OnFatalError(ErrorInfo(CannotParse)))
            return
        }

        // This is because the stateMachine.transition() is suddenly expensive it terms of performance.
        // So we save time and call handleMessageReceived() directly when we are in
        // the Connected state (which are 90%+ of cases)
        // TODO: optimise stateMachine.transition() and drop this condition
        if (stateMachine.state === Connected) {
            handleMessageReceived(message)
        } else {
            stateMachine.transition(OnMessageReceived(message))
        }
    }

    private fun handleMessageReceived(message: TwilsockMessage) {
        logger.d { "handleMessageReceived: ${message.requestId}" }

        when (message) {
            is TwilsockReplyMessage -> handleReplyMessage(message)

            is TwilsockCloseMessage -> handleCloseMessage(message)

            is TwilsockClientUpdateMessage -> handleClientUpdateMessage(message)

            is TwilsockNotificationMessage -> handleNotificationMessage(message)

            is TwilsockPingMessage -> message.sendReply(status = Ok)

            else -> logger.w("Skipped message with unexpected method ${message.method}")
        }
    }

    private fun handleReplyMessage(message: TwilsockReplyMessage) {
        val request = sentRequests[message.requestId] ?: run {
            logger.w("Skipped reply for unknown request: ${message.requestId}")
            return
        }

        val status = message.replyHeaders.status
        val isError = status.code / 100 != 2

        if (isError) {
            val httpStatus = HttpStatusCode.fromValue(status.code)

            val errorInfo = when {
                status.errorCode == kTokenExpiredCode -> status.toErrorInfo(TokenExpired)

                httpStatus == HttpStatusCode.Unauthorized -> status.toErrorInfo(Unauthorized)

                httpStatus == HttpStatusCode.TooManyRequests -> status.toErrorInfo(TooManyRequests)

                else -> status.toErrorInfo(Unknown)
            }

            request.cancel(errorInfo)

            if (status.errorCode == kTokenExpiredCode) {
                logger.w("Token expired reply received")
                notifyObservers { onTokenExpired() }
            }

            when (httpStatus) {
                HttpStatusCode.Unauthorized -> stateMachine.transition(OnFatalError(errorInfo))

                HttpStatusCode.TooManyRequests -> {
                    val backoffPolicy = message.replyPayload.backoffPolicy

                    val waitTime = Random.nextInt(
                        backoffPolicy.reconnectMinMilliseconds,
                        backoffPolicy.reconnectMaxMilliseconds,
                    )

                    stateMachine.transition(OnTooManyRequests(waitTime.milliseconds))
                }

                else -> stateMachine.transition(OnNonFatalError(errorInfo))
            }

            return
        }

        val continuationToken = message.replyHeaders.continuationToken
        if (continuationToken.isNotEmpty()) {
            continuationTokenStorage.continuationToken = continuationToken
        }

        message.replyHeaders.accountDescriptor?.let { accountDescriptor = it }

        request.complete(message)
    }

    private fun handleCloseMessage(message: TwilsockCloseMessage) {
        logger.d { "Server has just initiated process of closing connection: ${message.payload}" }

        message.sendReply(status = Ok)

        var errorInfo = message.status.toErrorInfo(CloseMessageReceived)

        if (message.status.code == 308 && message.status.errorCode == 51232) {
            logger.d { "Token with different instanceSid" } // see RTDSDK-3448
            stateMachine.transition(OnFatalError(errorInfo))
            return
        }

        when (val code = message.status.code) {
            308 -> {
                logger.d { "Offloading to another instance" }
                stateMachine.transition(OnNonFatalError(errorInfo))
            }

            401, // Authentication fail
            406, // Not acceptable message
            410, // Expired token
            417, // Protocol error
            -> {
                logger.d { "Server closed connection because of fatal error: $errorInfo" }
                if (code == 410 || message.status.errorCode == kTokenExpiredCode) {
                    errorInfo = errorInfo.copy(reason = TokenExpired)
                    notifyObservers { onTokenExpired() }
                } else if (code == 401) {
                    errorInfo = errorInfo.copy(reason = Unauthorized)
                }
                stateMachine.transition(OnFatalError(errorInfo))
            }

            else -> {
                logger.w { "Unexpected close message: $errorInfo" }
                stateMachine.transition(OnNonFatalError(errorInfo))
            }
        }
    }

    private fun handleClientUpdateMessage(message: TwilsockClientUpdateMessage) {
        message.sendReply(status = Ok)

        when (message.clientUpdateType) {
            "token_about_to_expire" -> notifyObservers { onTokenAboutToExpire() }

            else -> logger.w("Ignoring unknown client update: ${message.clientUpdateType}")
        }
    }

    private fun handleNotificationMessage(message: TwilsockNotificationMessage) {
        if (message.payload.isEmpty()) {
            logger.w("Notification message is skipped. Payload is empty: ${message.requestId}")
            message.sendReply(status = BadRequest, description = "Notification message must carry data")
            return
        }

        message.sendReply(status = Ok)
        notifyObservers { onMessageReceived(message.messageType, message.payload) }
    }

    private fun onConnectivityChanged() {
        logger.d { "onConnectivityChanged: $isNetworkAvailable" }
        val event = if (isNetworkAvailable) OnNetworkBecameReachable else OnNetworkBecameUnreachable
        stateMachine.transition(event)
    }

    private fun TwilsockMessage.sendReply(status: Status, description: String = "") {
        val websocket = websocket ?: return

        val message = TwilsockMessage(
            requestId = requestId,
            method = REPLY,
            headers = buildJsonObject {
                put("status", json.encodeToJsonElement(status))
            },
            payloadType = if (description.isNotEmpty()) "text/plain" else "",
            payload = description
        )

        val rawMessage = message.encode()
        websocket.sendMessage(rawMessage.encodeToByteArray())
    }

    private fun TwilsockRequest.send() {
        val rawMessage = message.encodeToByteArray()
        checkNotNull(websocket).sendMessage(rawMessage)
        sentRequests[message.requestId] = this
    }
}

private typealias TwilsockStateDefinitionBuilder<STATE> =
        StateMachine.GraphBuilder<TwilsockState, TwilsockEvent, SideEffect>.StateDefinitionBuilder<STATE>

private fun <S : TwilsockState> TwilsockStateDefinitionBuilder<S>.defaultOnMessageReceived() =
    on<OnMessageReceived> { event ->
        dontTransition(HandleMessageReceived(event.message))
    }

private fun <S : TwilsockState> TwilsockStateDefinitionBuilder<S>.defaultOnDisconnect() =
    on<OnDisconnect> { transitionTo(Disconnected(ErrorInfo(message = "disconnect called"))) }

private fun <S : TwilsockState> TwilsockStateDefinitionBuilder<S>.defaultOnNonFatalError() =
    on<OnNonFatalError> { event ->
        transitionTo(WaitAndReconnect(), NotifyObservers { onNonFatalError(event.errorInfo) })
    }

private fun <S : TwilsockState> TwilsockStateDefinitionBuilder<S>.defaultOnNetworkBecameUnreachable() =
    on<OnNetworkBecameUnreachable> {
        val errorInfo = ErrorInfo(NetworkBecameUnreachable, message = "Network became unreachable")
        transitionTo(WaitAndReconnect(), NotifyObservers { onNonFatalError(errorInfo) })
    }

private fun <S : TwilsockState> TwilsockStateDefinitionBuilder<S>.defaultOnFatalError() =
    on<OnFatalError> { event ->
        transitionTo(Disconnected(event.errorInfo), NotifyObservers { onFatalError(event.errorInfo) })
    }
