package com.twilio.util

import com.twilio.util.StateMachine.Graph.State
import com.twilio.util.StateMachine.Graph.State.TransitionTo
import com.twilio.util.StateMachine.Transition.DontTransition
import com.twilio.util.StateMachine.Transition.Invalid
import com.twilio.util.StateMachine.Transition.Valid
import kotlin.reflect.KClass
import kotlinx.atomicfu.atomic

class StateMachine<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> private constructor(
    private val graph: Graph<STATE, EVENT, SIDE_EFFECT>
) {

    var state by atomic(graph.initialState)
        private set

    fun transition(event: EVENT): Transition<STATE, EVENT, SIDE_EFFECT> {
        val fromState = state
        val transition = fromState.getTransition(event)
        if (transition is Valid) {
            state = transition.toState
        }
        transition.notifyOnTransition()
        if (transition is Valid) {
            with(transition) {
                with(fromState) {
                    notifyOnExit(event)
                }
                with(toState) {
                    notifyOnEnter(event)
                }
            }
        }
        return transition
    }

    fun with(init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit): StateMachine<STATE, EVENT, SIDE_EFFECT> {
        return create(graph.copy(initialState = state), init)
    }

    private fun STATE.getTransition(event: EVENT): Transition<STATE, EVENT, SIDE_EFFECT> {
        for ((eventMatcher, createTransitionTo) in getDefinition().transitions) {
            if (eventMatcher.matches(event)) {
                val (toState, sideEffect, isDontTransition) = createTransitionTo(this, event)
                return if (isDontTransition) {
                    DontTransition(this, event, sideEffect)
                } else {
                    Valid(this, event, toState, sideEffect)
                }
            }
        }
        return Invalid(this, event)
    }

    private fun STATE.getDefinition() = graph.stateDefinitions
        .filter { it.key.matches(this) }
        .map { it.value }
        .firstOrNull() ?: error("Missing definition for state ${this::class.simpleName}!")

    private fun STATE.notifyOnEnter(cause: EVENT) {
        getDefinition().onEnterListeners.forEach { it(this, cause) }
    }

    private fun STATE.notifyOnExit(cause: EVENT) {
        getDefinition().onExitListeners.forEach { it(this, cause) }
    }

    private fun Transition<STATE, EVENT, SIDE_EFFECT>.notifyOnTransition() {
        graph.onTransitionListeners.forEach { it(this) }
    }

    @Suppress("UNUSED")
    sealed class Transition<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> {
        abstract val fromState: STATE
        abstract val event: EVENT

        data class Valid<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT,
            val toState: STATE,
            val sideEffect: SIDE_EFFECT?,
        ) : Transition<STATE, EVENT, SIDE_EFFECT>()

        data class DontTransition<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT,
            val sideEffect: SIDE_EFFECT?,
        ) : Transition<STATE, EVENT, SIDE_EFFECT>()

        data class Invalid<out STATE : Any, out EVENT : Any, out SIDE_EFFECT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT
        ) : Transition<STATE, EVENT, SIDE_EFFECT>()
    }

    data class Graph<STATE : Any, EVENT : Any, SIDE_EFFECT : Any>(
        val initialState: STATE,
        val stateDefinitions: Map<Matcher<STATE, STATE>, State<STATE, EVENT, SIDE_EFFECT>>,
        val onTransitionListeners: List<(Transition<STATE, EVENT, SIDE_EFFECT>) -> Unit>
    ) {

        class State<STATE : Any, EVENT : Any, SIDE_EFFECT : Any> internal constructor() {
            val onEnterListeners = mutableListOf<(STATE, EVENT) -> Unit>()
            val onExitListeners = mutableListOf<(STATE, EVENT) -> Unit>()
            val transitions = linkedMapOf<Matcher<EVENT, EVENT>, (STATE, EVENT) -> TransitionTo<STATE, SIDE_EFFECT>>()

            data class TransitionTo<out STATE : Any, out SIDE_EFFECT : Any> internal constructor(
                val toState: STATE,
                val sideEffect: SIDE_EFFECT?,
                val isDontTransition: Boolean,
            )
        }
    }

    class Matcher<T : Any, out R : T> private constructor(private val clazz: KClass<R>) {

        private val predicates = mutableListOf<(T) -> Boolean>({ clazz.isInstance(it) })

        fun where(predicate: R.() -> Boolean): Matcher<T, R> = apply {
            predicates.add {
                @Suppress("UNCHECKED_CAST")
                (it as R).predicate()
            }
        }

        fun matches(value: T) = predicates.all { it(value) }

        companion object {
            fun <T : Any, R : T> any(clazz: KClass<R>): Matcher<T, R> = Matcher(clazz)

            inline fun <T : Any, reified R : T> any(): Matcher<T, R> = any(R::class)

            inline fun <T : Any, reified R : T> eq(value: R): Matcher<T, R> = any<T, R>().where { this == value }
        }
    }

    class GraphBuilder<STATE : Any, EVENT : Any, SIDE_EFFECT : Any>(
        graph: Graph<STATE, EVENT, SIDE_EFFECT>? = null
    ) {
        private var initialState = graph?.initialState
        private val stateDefinitions = LinkedHashMap(graph?.stateDefinitions ?: emptyMap())
        private val onTransitionListeners = ArrayList(graph?.onTransitionListeners ?: emptyList())

        fun initialState(initialState: STATE) {
            this.initialState = initialState
        }

        fun <S : STATE> state(
            stateMatcher: Matcher<STATE, S>,
            init: StateDefinitionBuilder<S>.() -> Unit
        ) {
            stateDefinitions[stateMatcher] = StateDefinitionBuilder<S>().apply(init).build()
        }

        inline fun <reified S : STATE> state(noinline init: StateDefinitionBuilder<S>.() -> Unit) {
            state(Matcher.any(), init)
        }

        inline fun <reified S : STATE> state(state: S, noinline init: StateDefinitionBuilder<S>.() -> Unit) {
            state(Matcher.eq<STATE, S>(state), init)
        }

        fun onTransition(listener: (Transition<STATE, EVENT, SIDE_EFFECT>) -> Unit) {
            onTransitionListeners.add(listener)
        }

        fun build(): Graph<STATE, EVENT, SIDE_EFFECT> {
            return Graph(requireNotNull(initialState), stateDefinitions.toMap(), onTransitionListeners.toList())
        }

        inner class StateDefinitionBuilder<S : STATE> {

            private val stateDefinition = State<STATE, EVENT, SIDE_EFFECT>()

            inline fun <reified E : EVENT> any(): Matcher<EVENT, E> =
                Matcher.any()

            inline fun <reified R : EVENT> eq(value: R): Matcher<EVENT, R> =
                Matcher.eq(value)

            fun <E : EVENT> on(
                eventMatcher: Matcher<EVENT, E>,
                createTransitionTo: S.(E) -> TransitionTo<STATE, SIDE_EFFECT>
            ) {
                stateDefinition.transitions[eventMatcher] = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    createTransitionTo((state as S), event as E)
                }
            }

            inline fun <reified E : EVENT> on(
                noinline createTransitionTo: S.(E) -> TransitionTo<STATE, SIDE_EFFECT>
            ) {
                return on(any(), createTransitionTo)
            }

            inline fun <reified E : EVENT> on(
                event: E,
                noinline createTransitionTo: S.(E) -> TransitionTo<STATE, SIDE_EFFECT>
            ) {
                return on(eq(event), createTransitionTo)
            }

            fun onEnter(listener: S.(EVENT) -> Unit) = with(stateDefinition) {
                onEnterListeners.add { state, cause ->
                    @Suppress("UNCHECKED_CAST")
                    listener(state as S, cause)
                }
            }

            fun onExit(listener: S.(EVENT) -> Unit) = with(stateDefinition) {
                onExitListeners.add { state, cause ->
                    @Suppress("UNCHECKED_CAST")
                    listener(state as S, cause)
                }
            }

            fun build() = stateDefinition

            @Suppress("UNUSED") // The unused warning is probably a compiler bug.
            fun S.transitionTo(state: STATE, sideEffect: SIDE_EFFECT? = null, dontTransition: Boolean = false) =
                TransitionTo(state, sideEffect, dontTransition)

            @Suppress("UNUSED") // The unused warning is probably a compiler bug.
            fun S.dontTransition(sideEffect: SIDE_EFFECT? = null) = transitionTo(this, sideEffect, dontTransition = true)
        }
    }

    companion object {
        fun <STATE : Any, EVENT : Any, SIDE_EFFECT : Any> create(
            init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit
        ): StateMachine<STATE, EVENT, SIDE_EFFECT> {
            return create(null, init)
        }

        private fun <STATE : Any, EVENT : Any, SIDE_EFFECT : Any> create(
            graph: Graph<STATE, EVENT, SIDE_EFFECT>?,
            init: GraphBuilder<STATE, EVENT, SIDE_EFFECT>.() -> Unit
        ): StateMachine<STATE, EVENT, SIDE_EFFECT> {
            return StateMachine(GraphBuilder(graph).apply(init).build())
        }
    }
}
