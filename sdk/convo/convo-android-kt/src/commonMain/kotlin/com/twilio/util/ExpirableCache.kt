//
//  Twilio Conversations Client
//
//  Copyright © Twilio, Inc. All rights reserved.
//
package com.twilio.util

import io.ktor.util.date.getTimeMillis

/**
 * [ExpirableCache] flushes all items after [flushInterval] from the last flush.
 */
internal class ExpirableCache<K, V>(
    private val flushInterval: Long = 60_000
) {
    private val map = mutableMapOf<K, V>()
    private var lastFlushTime = getTimeMillis()

    val size: Int
        get() {
            recycle()
            return map.size
        }

    fun remove(key: K): V? {
        recycle()
        return map.remove(key)
    }

    operator fun get(key: K): V? {
        recycle()
        return map[key]
    }

    operator fun set(key: K, value: V) {
        recycle()
        map[key] = value
    }

    fun putAll(items: Map<K, V>) {
        recycle()
        map.putAll(items)
    }

    inline fun getOrPut(key: K, getValue: () -> V): V {
        get(key)?.let { return it }
        val value = getValue()
        map[key] = value
        return value
    }

    private fun recycle() {
        val shouldRecycle = getTimeMillis() - lastFlushTime >= flushInterval
        if (shouldRecycle) {
            map.clear()
            lastFlushTime = getTimeMillis()
        }
    }
}
