package com.twilio.twilsock.util

import com.twilio.twilsock.util.HttpMethod.GET
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias Payload = String

// 10s by design. See: https://curly-parakeet-d60caa01.pages.github.io/content/6_Shared/2_Retrier_Timeouts.html#twilsock
val kDefaultRequestTimeout = 10.seconds

data class HttpRequest(
    val url: String,
    val method: HttpMethod = GET,
    val headers: MultiMap<String, String> = MultiMap(),
    val timeout: Duration = kDefaultRequestTimeout,
    val payload: Payload = "",
)

data class HttpResponse(
    val statusCode: Int,
    val status: String,
    val rawMessageHeaders: String,
    val headers: MultiMap<String, String>,
    val payload: Payload
)

// Must be synchronized with c++ rtd::HttpMethod
enum class HttpMethod(val value: Int) {
    POST(0),
    GET(1),
    PUT(2),
    DELETE(3);

    companion object {
        private val map = values().associateBy { it.value }

        fun fromInt(value: Int) = map[value] ?: error("Unknown HttpMethod value: $value")
    }
}
