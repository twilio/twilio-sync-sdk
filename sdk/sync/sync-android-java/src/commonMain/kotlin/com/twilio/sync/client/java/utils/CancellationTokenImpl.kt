//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java.utils

internal fun CancellationToken(onCancel: () -> Unit): CancellationToken = CancellationTokenImpl(onCancel)

private class CancellationTokenImpl(private val onCancel: () -> Unit) : CancellationToken {
    override fun cancel() = onCancel()
}
