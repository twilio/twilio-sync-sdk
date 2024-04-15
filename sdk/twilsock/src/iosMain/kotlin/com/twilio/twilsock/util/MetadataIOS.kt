//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.util

import com.twilio.twilsock.client.ClientMetadata
import com.twilio.util.TwilioLogger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.darwin.CPU_SUBTYPE_ARM64E
import platform.darwin.CPU_SUBTYPE_ARM64_32_V8
import platform.darwin.CPU_SUBTYPE_ARM64_V8
import platform.darwin.CPU_SUBTYPE_ARM_V6
import platform.darwin.CPU_SUBTYPE_ARM_V7
import platform.darwin.CPU_SUBTYPE_ARM_V8
import platform.darwin.CPU_TYPE_ARM
import platform.darwin.CPU_TYPE_ARM64
import platform.darwin.CPU_TYPE_ARM64_32
import platform.darwin.CPU_TYPE_X86
import platform.darwin.CPU_TYPE_X86_64
import platform.darwin.cpu_subtype_tVar
import platform.darwin.cpu_type_tVar
import platform.darwin.sysctlbyname
import platform.posix.size_tVar

private val logger = TwilioLogger.getLogger("ClientMetadataIOS")

@Suppress("FunctionName")
fun ClientMetadataIOS(sdkVersion: String, sdkType: String): ClientMetadata {
    val info = NSBundle.mainBundle.infoDictionary

    val metadata = ClientMetadata(
        appName = info?.get("CFBundleName") as? String ?: "",
        appVer = info?.get("CFBundleShortVersionString") as? String ?: "",
        os = UIDevice.currentDevice.systemName,
        osVer = UIDevice.currentDevice.systemVersion,
        osArch = getArch(),
        devModel = UIDevice.currentDevice.model,
        devVendor = "Apple",
        devType = hwMachine(),
        sdk = "iOS",
        sdkType = sdkType,
        sdkVer = sdkVersion,
    )

    logger.d { "metadata: $metadata" }

    return metadata
}

@OptIn(ExperimentalForeignApi::class)
private fun hwMachine(): String = memScoped {
    val size = alloc<size_tVar>()
    size.value = 0u

    sysctlbyname("hw.machine", null, size.ptr, null, 0u)
    if (size.value > 0u) {
        val devTypeBuf = allocArray<ByteVar>(size.value.toInt()) { value = 0 }
        sysctlbyname("hw.machine", devTypeBuf, size.ptr, null, 0u)
        return@memScoped devTypeBuf.toKString()
    }

    return ""
}

@OptIn(ExperimentalForeignApi::class)
private fun getArch(): String = memScoped {
    val type = alloc<cpu_type_tVar>()
    val subtype = alloc<cpu_subtype_tVar>()
    val size = alloc<size_tVar>()

    size.value = sizeOf<cpu_type_tVar>().toULong()
    sysctlbyname("hw.cputype", type.ptr, size.ptr, null, 0u);

    size.value = sizeOf<cpu_subtype_tVar>().toULong()
    sysctlbyname("hw.cpusubtype", subtype.ptr, size.ptr, null, 0u);

    return buildString {
        when (type.value) {
            CPU_TYPE_X86_64 -> append("x86_64")
            CPU_TYPE_X86 -> append("x86")
            CPU_TYPE_ARM -> {
                append("arm")
                when (subtype.value) {
                    CPU_SUBTYPE_ARM_V6 -> append("v6")
                    CPU_SUBTYPE_ARM_V7 -> append("v7")
                    CPU_SUBTYPE_ARM_V8 -> append("v8")
                }
            }
            CPU_TYPE_ARM64 -> {
                append("arm64")
                when (subtype.value) {
                    CPU_SUBTYPE_ARM64_V8 -> append("v8")
                    CPU_SUBTYPE_ARM64E -> append("e")
                }
            }
            CPU_TYPE_ARM64_32 -> {
                append("arm64_32")
                when (subtype.value) {
                    CPU_SUBTYPE_ARM64_32_V8 -> append("v8")
                }
            }
        }
    }
}
