package com.selfdevelopment.ai.messaging.statemachine.config;

import com.selfdevelopment.ai.messaging.statemachine.core.Action;
import com.selfdevelopment.ai.messaging.statemachine.core.Guard;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;
import com.selfdevelopment.ai.messaging.statemachine.core.TransitionKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configurer for transitions.
 * <p>
 * Inspired by Spring StateMachine's {@code StateMachineTransitionConfigurer}.
 * Uses {@code withExternal()} and {@code withInternal()} to distinguish transition
 * kinds, with fluent source/target/event/guard/action configuration.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public interface TransitionConfigurer<S, E, C> {

    /**
     * Starts an external transition (source exits, target enters).
     *
     * @return this configurer
     */
    TransitionConfigurer<S, E, C> withExternal();

    /**
     * Starts an internal transition (action only, state does not change).
     *
     * @return this configurer
     */
    TransitionConfigurer<S, E, C> withInternal();

    /**
     * Sets the source state.
     */
    TransitionConfigurer<S, E, C> source(S source);

    /**
     * Sets the target state.
     */
    TransitionConfigurer<S, E, C> target(S target);

    /**
     * Sets the triggering event.
     */
    TransitionConfigurer<S, E, C> event(E event);

    /**
     * Sets the guard condition.
     */
    TransitionConfigurer<S, E, C> guard(Guard<S, E, C> guard);

    /**
     * Sets the transition action.
     */
    TransitionConfigurer<S, E, C> action(Action<S, E, C> action);

    /**
     * Completes the current transition and starts a new one.
     *
     * @return this configurer
     */
    TransitionConfigurer<S, E, C> and();

    /**
     * Returns the collected transitions.
     */
    List<Transition<S, E, C>> getTransitions();
}
