//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java.utils

import com.twilio.util.json
import kotlinx.serialization.json.JsonObject

internal inline fun SyncMutator.wrap(): (JsonObject?) -> JsonObject? = { data: JsonObject? ->
    mutate(data?.toString())?.let { json.decodeFromString<JsonObject>(it) }
}
