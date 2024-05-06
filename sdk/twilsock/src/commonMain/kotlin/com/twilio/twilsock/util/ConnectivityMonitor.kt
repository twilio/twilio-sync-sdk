//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.util

import kotlinx.coroutines.CoroutineScope

interface ConnectivityMonitor {
    val isNetworkAvailable: Boolean
    var onChanged: () -> Unit

    fun start()
    fun stop()
}

internal expect class ConnectivityMonitorImpl(coroutineScope: CoroutineScope) : ConnectivityMonitor
