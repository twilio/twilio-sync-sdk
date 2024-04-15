//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope

fun <T : Any> Delegates.expirableValue(scope: CoroutineScope, ttl: Duration): ReadWriteProperty<Any?, T?> =
    object : ReadWriteProperty<Any?, T?> {
        private val timer = Timer(scope)

        private var value: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>) = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            this.value = value
            timer.schedule(ttl) { this.value = null }
        }
    }
