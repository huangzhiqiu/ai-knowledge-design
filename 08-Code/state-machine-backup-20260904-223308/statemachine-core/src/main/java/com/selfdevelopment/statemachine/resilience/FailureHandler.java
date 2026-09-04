package com.selfdevelopment.statemachine.resilience;

import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.core.Transition;

/**
 * Strategy interface for handling state machine transition failures.
 * <p>
 * Implementations define what happens when a transition fails due to:
 * <ul>
 *   <li>No matching transition rule found</li>
 *   <li>Guard condition evaluation failure</li>
 *   <li>Action execution exception</li>
 * </ul>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public interface FailureHandler<S, E, C> {

    /**
     * The type of failure that occurred.
     */
    enum FailureType {
        /** No transition rule found for (sourceState, event). */
        NO_TRANSITION,
        /** All guard conditions failed. */
        GUARD_FAILED,
        /** Transition action threw an exception. */
        ACTION_ERROR
    }

    /**
     * Handles a transition failure.
     *
     * @param type          the type of failure
     * @param sourceState   the source state before the event
     * @param event         the event that was fired
     * @param context       the business context
     * @param transition    the transition that failed (may be null for NO_TRANSITION)
     * @param exception     the exception that caused the failure (may be null)
     * @return a state context representing the outcome (may indicate acceptance or denial)
     */
    StateContext<S, E, C> handleFailure(FailureType type,
                                          S sourceState,
                                          E event,
                                          C context,
                                          Transition<S, E, C> transition,
                                          Exception exception);
}
