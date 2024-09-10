//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:Suppress("DEPRECATION")

package com.twilio.util

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readAvailable

private class ListenableInput(private val input: Input, private val onProgress: (bytesRead: Long) -> Unit) : Input() {

    var bytesRead = 0L

    override fun closeSource() = input.close()

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        var count = 0

        while (count == 0) { // while at least one byte read or EOF encountered
            count = input.readAvailable(destination, offset, length)
        }

        if (count == -1) { // EOF encountered
            return 0
        }

        bytesRead += count
        onProgress(bytesRead)

        return count
    }
}

internal fun Input.toListenableInput(onProgress: (bytesRead: Long) -> Unit): Input = ListenableInput(this, onProgress)
