package com.selfdevelopment.ai.messaging.statemachine.config;

import com.selfdevelopment.ai.messaging.statemachine.core.Action;
import com.selfdevelopment.ai.messaging.statemachine.core.StateDef;

import java.util.Map;

/**
 * Configurer for state definitions.
 * <p>
 * Inspired by Spring StateMachine's {@code StateMachineStateConfigurer}.
 * Allows defining states with entry/exit actions, initial state, and end states.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public interface StateConfigurer<S, E, C> {

    /**
     * Starts defining states.
     *
     * @return this configurer for fluent chaining
     */
    StateConfigurer<S, E, C> withStates();

    /**
     * Sets the initial state.
     *
     * @param state the initial state
     * @return this configurer
     */
    StateConfigurer<S, E, C> initial(S state);

    /**
     * Adds a simple state (no entry/exit actions).
     *
     * @param id the state identifier
     * @return this configurer
     */
    StateConfigurer<S, E, C> state(S id);

    /**
     * Adds a state with entry and exit actions.
     *
     * @param id          the state identifier
     * @param entryAction the action to execute on entry (may be null)
     * @param exitAction  the action to execute on exit (may be null)
     * @return this configurer
     */
    StateConfigurer<S, E, C> state(S id, Action<S, E, C> entryAction, Action<S, E, C> exitAction);

    /**
     * Adds a state with only an entry action.
     */
    StateConfigurer<S, E, C> stateWithEntry(S id, Action<S, E, C> entryAction);

    /**
     * Adds a state with only an exit action.
     */
    StateConfigurer<S, E, C> stateWithExit(S id, Action<S, E, C> exitAction);

    /**
     * Adds an end (terminal) state.
     *
     * @param state the end state
     * @return this configurer
     */
    StateConfigurer<S, E, C> end(S state);

    /**
     * Returns the collected state definitions.
     *
     * @return map of state id to StateDef
     */
    Map<S, StateDef<S, E, C>> getStates();

    /**
     * Returns the initial state.
     */
    S getInitialState();
}
