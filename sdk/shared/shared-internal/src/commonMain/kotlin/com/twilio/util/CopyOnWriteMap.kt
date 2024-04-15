//
//  Twilio Utils
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.util

import kotlinx.atomicfu.atomic

class CopyOnWriteMap<K: Any, V: Any>(initWith: Map<K, V> = emptyMap()) : Map<K, V> {

    @PublishedApi
    internal val map = atomic(initWith.toMap())

    override val entries: Set<Map.Entry<K, V>>
        get() = map.value.entries

    override val keys: Set<K>
        get() = map.value.keys

    override val size: Int
        get() = map.value.size

    override val values: Collection<V>
        get() = map.value.values

    fun clear(): Map<K, V> = map.getAndSet(emptyMap())

    override fun isEmpty(): Boolean  = map.value.isEmpty()

    fun remove(key: K): V? {

        do {
            val old = map.value
            if (key !in old ) return null

            val copy = old.toMutableMap()
            val result = copy.remove(key)
            if (map.compareAndSet(old, copy)) {
                return result
            }
        } while (true)
    }

    fun put(key: K, value: V): V? {
        do {
            val old = map.value
            val copy = old.toMutableMap()
            val result = copy.put(key, value)
            if (map.compareAndSet(old, copy)) {
                return result
            }
        } while (true)
    }


    inline fun putIfAbsent(key: K, getValue: () -> V): V? {
        var value: V? = null

        do {
            val old = map.value
            if (key in old) {
                return null
            }

            if (value == null) {
                value = getValue()
            }

            val copy = old.toMutableMap()
            copy[key] = value

            if (map.compareAndSet(old, copy)) {
                return value
            }
        } while (true)
    }

    operator fun set(key: K, value: V) = put(key, value)

    override fun get(key: K): V? = map.value.get(key)

    override fun containsValue(value: V): Boolean = map.value.containsValue(value)

    override fun containsKey(key: K): Boolean = map.value.containsKey(key)
}
