//
//  Twilio Twilsock Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import platform.Foundation.NSUserDefaults
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val preferences = NSUserDefaults.standardUserDefaults

actual fun stringPreference() = object : ReadWriteProperty<Any?, String> {

    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        preferences.objectForKey(property.name)?.let { it as? String } ?: ""

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        preferences.setObject(value, property.name)
    }
}
