//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION")

package com.twilio.util

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.storeUByteArray
import io.ktor.utils.io.core.Input
import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import platform.Foundation.NSInputStream
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_t

internal val ApplicationDispatcher: CoroutineDispatcher = NsQueueDispatcher(dispatch_get_main_queue())

internal class NsQueueDispatcher(
    private val dispatchQueue: dispatch_queue_t
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch_async(dispatchQueue) {
            block.run()
        }
    }
}

internal class NSInputStreamAsInput(
    private val stream: NSInputStream
) : Input() {

    private var buffer = UByteArray(10240)

    init {
        stream.open()
    }

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        if (buffer.size < length) {
            buffer = UByteArray(length)
        }

        buffer.usePinned { pinned ->
            val rc = stream.read(pinned.addressOf(0), length.convert()).toInt()
            if (rc <= 0) return 0
            destination.storeUByteArray(offset, buffer, 0, rc)
            return rc
        }
    }

    override fun closeSource() {
        stream.close()
    }
}

fun NSInputStream.asInput(): Input = NSInputStreamAsInput(this)
