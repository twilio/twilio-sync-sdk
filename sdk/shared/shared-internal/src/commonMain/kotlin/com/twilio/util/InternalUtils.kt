//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.utils.io.*
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private val EmptyJsonObject = buildJsonObject {}
fun emptyJsonObject() = EmptyJsonObject

fun splitCertificates(certificatesString: String): List<String> {
    val beginCertificate = "-----BEGIN CERTIFICATE-----"
    val endCertificate = "-----END CERTIFICATE-----"

    return certificatesString.trim()
        .split(endCertificate)
        .map { it.substringAfter(beginCertificate) }
        .filter { it.isNotEmpty() }
        .map { beginCertificate + it + endCertificate }
}

expect fun generateUUID(): String

fun generateSID(prefix: String) = prefix + generateUUID().replace("-", "")

fun min(a: Duration, b: Duration) = if (a < b) a else b

fun max(a: Duration, b: Duration) = if (a > b) a else b

expect fun getCurrentThreadId(): String

@Suppress("NOTHING_TO_INLINE")
inline fun Result.Companion.success() = success(Unit)

@Suppress("NOTHING_TO_INLINE")
inline fun CompletableDeferred<Unit>.complete() = complete(Unit)

suspend fun <T> CompletableDeferred<T>.await(timeout: Duration) = runCatching {
    withTimeout(timeout) { await() }
}.getOrThrowTwilioException(ErrorReason.Timeout)

@Serializable
private data class ErrorResponse(
    val message: String = "",
    val code: Int = 0,
    val params: Params? = null,
)

@Serializable
private data class Params(
    @SerialName("auth_service_code")
    val authServiceCode: Int? = null,
)

suspend fun Throwable?.toTwilioException(defaultReason: ErrorReason) = when(this) {

    is ResponseException -> {
        logger.e("Request error with response: ${this.response.body<String>()}")

        val response = runCatching { this.response.body<ErrorResponse>() }.getOrElse { ErrorResponse() }
        val errorInfo = ErrorInfo(
            reason = defaultReason,
            status = this.response.status.value,
            code = response.params?.authServiceCode ?: response.code,
            message = response.message
        )
        TwilioException(errorInfo, cause = this)
    }

    else -> toTwilioExceptionSync(defaultReason)
}

// Sync version of toTwilioException is not suspend which means it can be used in sync code.
// On the other hand, it cannot read error codes from ktor responses.
// So don't use it with network operations.
fun Throwable?.toTwilioExceptionSync(defaultReason: ErrorReason) = when {

    this is TwilioException -> this

    this is CancellationException && cause.isClientShutdown ->
        TwilioException(ErrorInfo(ErrorReason.ClientShutdown, message = "Client is already shutdown"), cause = this)

    this is CancellationException -> TwilioException(ErrorInfo(ErrorReason.Cancelled), cause = this)

    else -> TwilioException(ErrorInfo(defaultReason), cause = this)
}

suspend inline fun <T> Result<T>.getOrThrowTwilioException(
    defaultReason: ErrorReason,
): T = getOrThrowTwilioException(defaultReason, doAlso = {})

suspend inline fun <T> Result<T>.getOrThrowTwilioException(
    defaultReason: ErrorReason,
    doAlso: (TwilioException) -> Unit
): T = getOrElse { t ->
    throw t.toTwilioException(defaultReason).also(doAlso)
}

inline fun <T> Result<T>.getOrThrowTwilioExceptionSync(
    defaultReason: ErrorReason,
    doAlso: (TwilioException) -> Unit = {},
): T = getOrElse { t ->
    throw t.toTwilioExceptionSync(defaultReason).also(doAlso)
}

val Throwable?.isClientShutdown: Boolean
    get() = this is TwilioException && this.errorInfo.reason == ErrorReason.ClientShutdown
