package com.selfdevelopment.ai.messaging.statemachine.api;

import com.selfdevelopment.ai.messaging.statemachine.core.Transition;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;

/**
 * Action to execute during a state transition.
 * <p>
 * Inspired by Spring StateMachine's {@code Action}. Receives the full
 * {@link StateContext}, giving access to source/target state, event,
 * business context, extended state, and event headers.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the business context type
 */
@FunctionalInterface
public interface Action<S, E, C> {

    /**
     * Executes the action.
     *
     * @param context the current state context
     */
    void execute(StateContext<S, E, C> context);
}
