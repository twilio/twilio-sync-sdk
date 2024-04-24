//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

import com.twilio.util.ErrorReason
import com.twilio.util.TwilioException
import com.twilio.util.TwilioLogger
import com.twilio.util.getOrThrowTwilioExceptionSync
import com.twilio.util.json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

private val logger = TwilioLogger.getLogger("KotlinJson")

@Throws(TwilioException::class, CancellationException::class)
internal fun jsonFromString(jsonString: String): JsonObject {
    // Converts parsing exceptions to TwilioExceptions in order to pass to swift
    return runCatching { json.parseToJsonElement(jsonString).jsonObject }
        .getOrThrowTwilioExceptionSync(ErrorReason.CannotParse) { logger.e(it) { "Cannot parse json: $jsonString" } }
}

@Throws(TwilioException::class, CancellationException::class)
fun jsonToString(jsonMap: Map<String, JsonElement>): String {
    // Converts encoding exceptions to TwilioExceptions in order to pass to swift
    return runCatching { json.encodeToString(JsonObject(jsonMap)) }
        .getOrThrowTwilioExceptionSync(ErrorReason.CannotParse) { logger.e(it) { "Cannot encode json: $jsonMap" } }
}
