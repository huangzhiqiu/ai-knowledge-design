package com.selfdevelopment.ai.messaging.statemachine.core;

/**
 * Core state machine interface.
 * <p>
 * The state machine is stateless: it does not store the current state.
 * Instead, the caller injects the current state on each {@link #fireEvent} call.
 * This design allows the same state machine instance to be safely shared across
 * threads and conversations.
 *
 * @param <S> the state type (typically an enum)
 * @param <E> the event type (typically an enum)
 * @param <C> the context type carrying business data
 */
public interface StateMachine<S, E, C> {

    /**
     * Fires an event and attempts to transition from the given source state.
     * <p>
     * The method:
     * <ol>
     *   <li>Looks up the transition rule for (sourceState, event)</li>
     *   <li>Evaluates the guard condition (if any)</li>
     *   <li>Executes the action (if any) on success</li>
     *   <li>Returns the target state</li>
     * </ol>
     *
     * @param sourceState the current state before the event
     * @param event       the event to fire
     * @param context     the business context
     * @return the new state after the transition
     * @throws com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException
     *         if no transition exists or the guard condition fails
     */
    S fireEvent(S sourceState, E event, C context);

    /**
     * Checks whether a transition exists for the given source state and event.
     * <p>
     * This does not evaluate the guard condition — use {@link #canFire} for that.
     *
     * @param sourceState the current state
     * @param event       the event to check
     * @return {@code true} if a transition rule exists
     */
    boolean hasTransition(S sourceState, E event);

    /**
     * Checks whether the event can be fired from the given source state,
     * including guard condition evaluation.
     *
     * @param sourceState the current state
     * @param event       the event to check
     * @param context     the business context
     * @return {@code true} if the transition exists and the condition is satisfied
     */
    boolean canFire(S sourceState, E event, C context);

    /**
     * Returns the number of transition rules in this state machine.
     *
     * @return the transition count
     */
    int getTransitionCount();
}
