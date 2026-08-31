package com.selfdevelopment.ai.messaging.statemachine.listener;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.Transition;

/**
 * Listener for state machine lifecycle events.
 * <p>
 * Inspired by Spring StateMachine's {@code StateMachineListener}. Provides hooks
 * for auditing, monitoring, logging, and side effects without polluting transition
 * actions.
 * <p>
 * All methods have default no-op implementations, so subclasses only override what they need.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the business context type
 */
public interface StateMachineListener<S, E, C> {

    /**
     * Called when a state changes (after a successful transition).
     *
     * @param context the state context containing source/target states
     */
    default void stateChanged(StateContext<S, E, C> context) {
    }

    /**
     * Called when a transition starts (before guard evaluation and action execution).
     *
     * @param transition the transition being attempted
     * @param context    the state context
     */
    default void transitionStarted(Transition<S, E, C> transition, StateContext<S, E, C> context) {
    }

    /**
     * Called when a transition completes successfully (after action execution).
     *
     * @param transition the transition that completed
     * @param context    the state context
     */
    default void transitionEnded(Transition<S, E, C> transition, StateContext<S, E, C> context) {
    }

    /**
     * Called when a transition is denied (guard condition failed or no transition found).
     *
     * @param context the state context (targetState may be null)
     * @param reason  the reason for denial
     */
    default void transitionDenied(StateContext<S, E, C> context, String reason) {
    }

    /**
     * Called when an error occurs during a transition.
     *
     * @param context the state context (contains the exception)
     */
    default void transitionError(StateContext<S, E, C> context) {
    }

    /**
     * Called when the state machine is started.
     */
    default void stateMachineStarted() {
    }

    /**
     * Called when the state machine is stopped.
     */
    default void stateMachineStopped() {
    }
}
