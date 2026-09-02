package com.selfdevelopment.ai.messaging.statemachine.api;

import com.selfdevelopment.ai.messaging.statemachine.core.Transition;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;

/**
 * Guard condition for a state transition.
 * <p>
 * Inspired by Spring StateMachine's {@code Guard}. Evaluates whether a transition
 * is allowed, with access to the full {@link StateContext}.
 * <p>
 * This replaces the simpler {@code Condition<C>} interface, providing access to
 * source/target state, event, extended state, and event headers in addition to
 * the business context.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the business context type
 */
@FunctionalInterface
public interface Guard<S, E, C> {

    /**
     * Evaluates whether the transition is allowed.
     *
     * @param context the current state context
     * @return {@code true} if the transition is allowed
     */
    boolean evaluate(StateContext<S, E, C> context);
}
