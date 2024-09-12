//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
@file:Suppress("DEPRECATION")

package com.twilio.conversations.test.util

import com.twilio.conversations.media.createHttpClient
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.fill
import io.ktor.utils.io.bits.storeByteArray
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi

fun createTestHttpClient() = createHttpClient(
    httpConnectionTimeout = 60_000,
    certificates = "",
    useCertificates = false
)

class InfiniteInput(val value: Byte = 'A'.code.toByte()) : Input() {

    override fun closeSource() = Unit

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        destination.fill(offset, length, value)
        return length
    }
}

class InputThrowsException(val exception: Exception) : Input() {

    override fun closeSource() = Unit

    override fun fill(destination: Memory, offset: Int, length: Int): Int = throw exception
}

fun String.asInput() = object : Input() {

    override fun closeSource() = Unit

    var filled = 0

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        val count = minOf(this@asInput.length - filled, length)
        destination.storeByteArray(offset, this@asInput.toByteArray(), 0, count)

        filled += count
        return count
    }
}
