package com.selfdevelopment.ai.messaging.statemachine.api;

import com.selfdevelopment.ai.messaging.statemachine.core.ExtendedState;

import com.selfdevelopment.ai.messaging.statemachine.core.Transition;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachineListener;

import java.util.Collection;

/**
 * Core state machine interface.
 * <p>
 * The state machine is stateless: it does not store the current state.
 * Instead, the caller injects the current state on each {@link #fireEvent} call.
 * This design allows the same state machine instance to be safely shared across
 * threads and conversations.
 * <p>
 * Inspired by Spring StateMachine's {@code StateMachine} interface, with
 * lifecycle management, listener support, and rich {@link StateContext} return values.
 *
 * @param <S> the state type (typically an enum)
 * @param <E> the event type (typically an enum)
 * @param <C> the context type carrying business data
 */
public interface StateMachine<S, E, C> {

    /**
     * Starts the state machine. Notifies listeners and sets the started flag.
     * For stateless machines this is optional but useful for lifecycle hooks.
     */
    void start();

    /**
     * Stops the state machine. Notifies listeners.
     */
    void stop();

    /**
     * Returns whether the state machine has been started.
     *
     * @return true if started
     */
    boolean isStarted();

    /**
     * Fires an event and attempts to transition from the given source state.
     * <p>
     * The method:
     * <ol>
     *   <li>Looks up the transition rule for (sourceState, event)</li>
     *   <li>Notifies listeners: transitionStarted</li>
     *   <li>Evaluates the guard condition (if any)</li>
     *   <li>Executes the action (if any) on success</li>
     *   <li>Notifies listeners: transitionEnded / stateChanged</li>
     *   <li>Returns the state context (containing target state)</li>
     * </ol>
     *
     * @param sourceState the current state before the event
     * @param event       the event to fire
     * @param context     the business context
     * @return the state context after the transition (contains target state)
     * @throws com.selfdevelopment.ai.messaging.statemachine.exception.StateMachineException
     *         if no transition exists or the guard condition fails
     */
    StateContext<S, E, C> fireEvent(S sourceState, E event, C context);

    /**
     * Fires an event with an existing extended state. Useful for chaining
     * multiple transitions that share extended state variables.
     *
     * @param sourceState   the current state
     * @param event         the event
     * @param context       the business context
     * @param extendedState the extended state to use (created fresh if null)
     * @return the state context
     */
    StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState);

    /**
     * Checks whether a transition exists for the given source state and event.
     * This does not evaluate the guard condition — use {@link #canFire} for that.
     *
     * @param sourceState the current state
     * @param event       the event to check
     * @return true if a transition rule exists
     */
    boolean hasTransition(S sourceState, E event);

    /**
     * Checks whether the event can be fired from the given source state,
     * including guard condition evaluation.
     *
     * @param sourceState the current state
     * @param event       the event to check
     * @param context     the business context
     * @return true if the transition exists and the guard is satisfied
     */
    boolean canFire(S sourceState, E event, C context);

    /**
     * Returns the number of transition rules in this state machine.
     *
     * @return the transition count
     */
    int getTransitionCount();

    /**
     * Returns all transition rules in this state machine.
     * Useful for diagram generation, validation, and introspection.
     *
     * @return an unmodifiable collection of all transitions
     */
    Collection<Transition<S, E, C>> getAllTransitions();

    /**
     * Returns the machine identifier.
     *
     * @return the machine ID
     */
    String getMachineId();

    /**
     * Returns the initial state, if configured.
     *
     * @return the initial state, or null if not configured
     */
    S getInitialState();

    /**
     * Returns the end states, if configured.
     *
     * @return the end states (empty if none configured)
     */
    Collection<S> getEndStates();

    /**
     * Adds a listener.
     *
     * @param listener the listener to add
     */
    void addListener(StateMachineListener<S, E, C> listener);

    /**
     * Removes a listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(StateMachineListener<S, E, C> listener);
}
