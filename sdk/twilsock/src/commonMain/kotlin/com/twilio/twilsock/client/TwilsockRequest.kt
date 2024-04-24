//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.client

import com.twilio.util.ErrorInfo
import com.twilio.util.ErrorReason.Cancelled
import com.twilio.util.ErrorReason.Timeout
import com.twilio.util.Timer
import com.twilio.util.TwilioException
import com.twilio.util.logger
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

internal class TwilsockRequest(
    coroutineScope: CoroutineScope,
    val message: TwilsockMessage,
    val timeout: Duration,
    val onFinished: (request: TwilsockRequest) -> Unit = {},
) {
    private val deferredResponse = CompletableDeferred<TwilsockMessage>()

    private val timer = Timer(coroutineScope)

    init {
        timer.schedule(timeout) {
            if (!deferredResponse.isActive) return@schedule

            val errorInfo = ErrorInfo(Timeout, message = "TwilsockRequest timeout: ${message.requestId}")
            deferredResponse.completeExceptionally(TwilioException(errorInfo))
            onFinished(this)
        }
    }

    fun complete(response: TwilsockMessage) {
        if (!deferredResponse.isActive) return

        timer.cancel()
        deferredResponse.complete(response)
        onFinished(this)
    }

    fun cancel(
        errorInfo: ErrorInfo = ErrorInfo(Cancelled, message = "TwilsockRequest has been cancelled")
    ) {
        if (!deferredResponse.isActive) return

        logger.d { "cancel[${message.requestId}] $errorInfo" }
        timer.cancel()
        deferredResponse.completeExceptionally(TwilioException(errorInfo))
        onFinished(this)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : TwilsockMessage> awaitResponse() = deferredResponse.await() as T

    inline fun onReply(
        crossinline onSuccess: () -> Unit = {},
        crossinline onTimeout: () -> Unit = {},
        crossinline onFailure: (Throwable) -> Unit = {},
    ) {
        deferredResponse.invokeOnCompletion { cause ->
            when {
                cause == null -> onSuccess()

                cause is TwilioException && cause.errorInfo.reason == Timeout -> onTimeout()

                else -> onFailure(cause)
            }
        }
    }
}
