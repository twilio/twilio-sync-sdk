//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.utils

import com.twilio.util.AccountDescriptor

/** Twilio grant value for the sync product. */
val AccountDescriptor.Companion.SYNC_GRANT: String get() = "data_sync"

/**
 * Creates [AccountDescriptor] with single instance SID for the sync grant.
 *
 * @property accountSid         Twilio account sid.
 * @property syncInstanceSid    Instance SID for the sync grant.
 * @property identity           User identity
 */
fun AccountDescriptor.Companion.createAccountDescriptor(accountSid: String, syncInstanceSid: String, identity: String) =
    AccountDescriptor(
        accountSid = accountSid,
        instanceSids = mapOf(AccountDescriptor.SYNC_GRANT to syncInstanceSid),
        identity = identity,
    )

/**
 * Instance SID for the sync grant.
 *
 * @throws IllegalStateException if instance SID for the sync grant is not found in [AccountDescriptor].
 */
val AccountDescriptor.syncInstanceSid: String get() = instanceSids[AccountDescriptor.SYNC_GRANT]
    ?: error("Instance SID for the ${AccountDescriptor.SYNC_GRANT} grant is not found in AccountDescriptor: $this")
