//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.twilio.twilsock.client.ClientMetadata
import com.twilio.util.logger
import java.util.*

@Deprecated("Use toClientMetadata(sdkVersion, sdkType) instead", ReplaceWith("toClientMetadata(sdkVersion, \"sync\")"))
fun Context.toClientMetadata(sdkVersion: String) = toClientMetadata(sdkVersion, sdkType = "sync")

fun Context.toClientMetadata(sdkVersion: String, sdkType: String) = ClientMetadata(
    appName = packageName,
    appVer = packageInfo.versionName,
    os = "Android",
    osVer = Build.VERSION.RELEASE,
    osArch = System.getProperty("os.arch"),
    devModel = deviceName,
    devVendor = deviceManufacturer,
    devType = deviceType,
    sdk = "Android",
    sdkType = sdkType,
    sdkVer = sdkVersion,
).also {
    logger.d("BOARD " + Build.BOARD)
    logger.d("BRAND " + Build.BRAND)
    logger.d("DEVICE " + Build.DEVICE)
    logger.d("DISPLAY " + Build.DISPLAY)
    logger.d("FINGERPRINT " + Build.FINGERPRINT)
    logger.d("HARDWARE " + Build.HARDWARE)
    logger.d("MANUFACTURER " + Build.MANUFACTURER)
    logger.d("MODEL " + Build.MODEL)
    logger.d("PRODUCT " + Build.PRODUCT)
}

private val Context.packageInfo: PackageInfo get() = if (Build.VERSION.SDK_INT >= 33)
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    else
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)

private val Context.deviceName: String get() {
        if (isProbablyRunningOnEmulator) return "emulator"

        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        return if (model.startsWith(manufacturer)) capitalize(model) else "${capitalize(manufacturer)} $model"
}

private val Context.deviceManufacturer: String
    get() = if (isProbablyRunningOnEmulator) "emulator" else capitalize(Build.MANUFACTURER)

private val Context.deviceType: String
    get() = if (isProbablyRunningOnEmulator) "emulator" else Build.DEVICE

private fun capitalize(s: String) =
    s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

private val isProbablyRunningOnEmulator: Boolean by lazy {
    // https://stackoverflow.com/a/40310535
    return@lazy Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(Build.PRODUCT);
}
