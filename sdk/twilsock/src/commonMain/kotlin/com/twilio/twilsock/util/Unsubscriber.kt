//
//  Twilio Twilsock Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.twilsock.util

class Unsubscriber(
    private val block: () -> Unit
) {
    fun unsubscribe() = block()
}
