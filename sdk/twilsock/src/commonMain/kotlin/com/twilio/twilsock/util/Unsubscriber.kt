//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.util

class Unsubscriber(
    private val block: () -> Unit
) {
    fun unsubscribe() = block()
}
