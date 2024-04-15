//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.commands

import com.twilio.twilsock.client.Twilsock
import com.twilio.twilsock.util.HttpResponse
import com.twilio.twilsock.util.MultiMap
import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason
import com.twilio.util.ErrorReason.Cancelled
import com.twilio.util.ErrorReason.CannotParse
import com.twilio.util.ErrorReason.CommandPermanentError
import com.twilio.util.ErrorReason.CommandRecoverableError
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.ErrorReason.Unknown
import com.twilio.util.Timer
import com.twilio.util.TwilioException
import com.twilio.util.generateSID
import com.twilio.util.getOrThrowTwilioException
import com.twilio.util.json
import com.twilio.util.logger
import com.twilio.util.retry
import com.twilio.util.success
import com.twilio.util.toTwilioException
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadGateway
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.GatewayTimeout
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.ServiceUnavailable
import io.ktor.http.HttpStatusCode.Companion.TooManyRequests
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

abstract class BaseCommand<T>(
    private val coroutineScope: CoroutineScope,
    private val config: CommandsConfig,
) {

    var isCancelled: Boolean = false
        private set

    private var isExecuted = false

    private val timer = Timer(coroutineScope)

    private val deferredResult = CompletableDeferred<T>()

    private var job: Job? = null

    fun startTimer() {
        logger.d { "startTimer commandTimeout: ${config.commandTimeout}" }
        timer.schedule(config.commandTimeout) { cancel(ErrorInfo(Timeout)) }
    }

    fun execute(twilsock: Twilsock) {
        check(!isExecuted) // commands are designed to be executed only once
        isExecuted = true

        if (isCancelled) return

        job = coroutineScope.launch {
            suspend fun retrierAttempt() = onRetrierAttempt(twilsock)

            runCatching { retry(config.retrierConfig, ::retrierAttempt) }
                .onFailure { deferredResult.completeExceptionally(it) }

            timer.cancel()
        }
    }

    suspend fun awaitResult(): T = deferredResult.await()

    fun cancel(errorInfo: ErrorInfo = ErrorInfo(Cancelled)) {
        isCancelled = true
        job?.cancel()
        timer.cancel()
        deferredResult.completeExceptionally(TwilioException(errorInfo))
    }

    protected abstract suspend fun makeRequest(twilsock: Twilsock): T

    private suspend fun onRetrierAttempt(twilsock: Twilsock): Result<Unit> {
        val requestResult = runCatching { makeRequest(twilsock) }

        val commandResult = requestResult.getOrElse { t ->
            val exception = t.toTwilioException(Unknown)
            when (exception.errorInfo.reason) {
                CannotParse,
                CommandPermanentError -> throw t // stop retrying

                else -> return Result.failure(t) // keep retrying
            }
        }

        deferredResult.complete(commandResult)
        return Result.success()
    }

    companion object {

        @JvmStatic
        protected fun generateHeaders(): MultiMap<String, String> {
            val headers = MultiMap<String, String>()

            headers["Twilio-Request-Id"] = generateSID("RQ")
            headers["Content-Type"] = "application/json; charset=utf-8"

            return headers
        }

        @JvmStatic
        protected fun JsonObjectBuilder.putTtl(ttl: Duration) {
            put("ttl", if (ttl != Duration.INFINITE) ttl.inWholeSeconds else 0)
        }

        @JvmStatic
        protected fun MultiMap<String, String>.putRevision(revision: String): MultiMap<String, String> {
            this["If-Match"] = revision
            return this
        }

        @JvmStatic
        protected suspend inline fun <reified T> HttpResponse.parseResponse(): T =
            when (HttpStatusCode.fromValue(statusCode)) {
                OK,
                Created,
                NoContent-> when (T::class) {
                    Unit::class -> Unit as T

                    else -> runCatching { json.decodeFromString<T>(payload) }.getOrThrowTwilioException(CannotParse)
                }

                ServiceUnavailable,
                GatewayTimeout,
                BadGateway,
                TooManyRequests -> throw TwilioException(parseError(CommandRecoverableError))

                else -> throw TwilioException(parseError(CommandPermanentError))
            }

        protected fun HttpResponse.parseError(reason: ErrorReason): ErrorInfo {
            val result = runCatching { json.decodeFromString<ErrorInfo>(payload) }
            val errorInfo = result.getOrElse { ErrorInfo(status = statusCode, message = status) }
            return errorInfo.copy(reason = reason)
        }
    }
}
