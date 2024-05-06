//
//  Twilio Twilsock Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
@file:JvmName("PreferencesKt")
package com.twilio.util

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val sharedPreferences = sharedPreferencesFactory()

private fun sharedPreferencesFactory(): SharedPreferences {
    val context = ApplicationContextHolder.applicationContext

    val regularPreferences by lazy {
        context.getSharedPreferences("TwilioPreferencesPre23", MODE_PRIVATE)
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return regularPreferences
    }

    try {
        return EncryptedSharedPreferences.create(
            "TwilioPreferences",
            MasterKeys.getOrCreate(AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) { // Possible fix for RTDSDK-4218
        val logger = TwilioLogger.getLogger("sharedPreferencesFactory")
        logger.w("Error creating EncryptedSharedPreferences, falling back to regular SharedPreferences", t)
        return regularPreferences
    }
}

actual fun stringPreference() = object : ReadWriteProperty<Any?, String> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return sharedPreferences.getString(property.name, "")!!
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        sharedPreferences.edit()
            .putString(property.name, value)
            .apply()
    }
}
