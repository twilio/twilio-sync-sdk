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
import kotlinx.atomicfu.atomic

fun <T : Any> Delegates.atomicNotNull(): ReadWriteProperty<Any?, T> = AtomicNotNullVar()

private class AtomicNotNullVar<T : Any> : ReadWriteProperty<Any?, T> {

    private var value by atomic<T?>(null)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: error("Property ${property.name} should be initialized before get.")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}
