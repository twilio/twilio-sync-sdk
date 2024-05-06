//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.util

import kotlinx.coroutines.CoroutineScope

internal actual class ConnectivityMonitorImpl actual constructor(coroutineScope: CoroutineScope):
    ConnectivityMonitor {
    override val isNetworkAvailable = true
    override var onChanged: () -> Unit = {}
    override fun start() = Unit
    override fun stop() = Unit
}
