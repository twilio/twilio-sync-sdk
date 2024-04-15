//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents account information for a user.
 *
 * @property accountSid     Twilio account sid.
 * @property instanceSids   Map of Twilio grants (i.e. "data_sync", "ip_messaging" etc.) to Twilio service SIDs.
 * @property identity       User identity
 */
@Serializable
data class AccountDescriptor(
    @SerialName("account_sid") val accountSid: String,
    @SerialName("instance_sids") val instanceSids: Map<String, String>,
    @SerialName("identity") val identity: String,
) {
    companion object // To make it possible to add static extensions
}
