//
//  Twilio Sync Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.sync.utils

import com.twilio.util.TwilioException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.flow

/**
 * An iterator over a collection. Allows to sequentially access the elements.
 * Must be closed at the end to cleanup resources.
 *
 * Instances of this interface are *not thread-safe* and shall not be used
 * from concurrent coroutines.
 *
 * @see [SyncIterator.forEach]
 * @see [SyncIterator.asFlow]
 *
 * Example 1:
 *
 * ```
 *  val iterator = map.iterator()
 *  try {
 *      while (iterator.hasNext()) {
 *          println(iterator.next())
 *      }
 *  } finally {
 *      iterator.close()
 *  }
 * ```
 *
 * Example 2:
 *
 * ```
 *   map.queryItems().forEach { item ->
 *     put(item.key, item.data())
 *   }
 * ```
 *
 * Example 3:
 *
 * ```
 *   val allItems = map.queryItems().asFlow().toList()
 * ```
 */
interface SyncIterator<T> : Closeable {
    /**
     * @return the next element in the iteration.
     */
    @Throws(TwilioException::class)
    operator fun next(): T

    /**
     * This function retrieves and removes an element for the subsequent invocation of next.
     *
     * @return `true` if the iteration has more elements.
     * @throws TwilioException  When error occurred while retrieving next element.
     */
    @Throws(TwilioException::class, CancellationException::class)
    suspend operator fun hasNext(): Boolean
}

suspend inline fun <T> SyncIterator<T>.forEach(block: (T) -> Unit) {
    try {
        while (hasNext()) {
            block(next())
        }
    } finally {
        close()
    }
}

fun <T> SyncIterator<T>.asFlow() = flow {
    forEach { emit(it) }
}
