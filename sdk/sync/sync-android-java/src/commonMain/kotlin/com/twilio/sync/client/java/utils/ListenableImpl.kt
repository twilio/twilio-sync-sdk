//
//  Twilio Sync Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.sync.client.java.utils

import com.twilio.util.CopyOnWriteMap
import com.twilio.util.newChildCoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Thread-safe implementation of [Listenable]. So methods could be exposed to customer without any synchronisation. */
internal open class ListenableImpl<Listener : Any>(
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) : Listenable<Listener> {

    private val handlers = CopyOnWriteMap<Flow<*>, (Listener) -> Flow<*>>()

    private val scopes = CopyOnWriteMap<Listener, CoroutineScope>()

    override fun addListener(listener: Listener) {
        val scope = scopes.putIfAbsent(listener) { coroutineScope.newChildCoroutineScope(dispatcher) }

        if (scope != null) {
            handlers.values.forEach { it(listener).launchIn(scope) }
        }
    }

    override fun removeListener(listener: Listener) {
        scopes.remove(listener)?.cancel()
    }

    override fun removeAllListeners() {
        val old = scopes.clear()
        old.values.forEach { it.cancel() }
    }

    protected inline fun <T, F : SharedFlow<T>> F.connectListeners(
        crossinline handler: Listener.(T) -> Unit
    ): F = apply {
        handlers[this] = { listener: Listener ->
            onEach { listener.handler(it) }
        }
    }
}
