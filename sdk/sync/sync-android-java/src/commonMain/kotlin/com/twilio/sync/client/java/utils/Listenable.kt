//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java.utils

interface Listenable<Listener : Any> {
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
    fun removeAllListeners()
}
