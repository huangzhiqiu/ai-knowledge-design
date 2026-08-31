package com.selfdevelopment.ai.messaging.statemachine.config;

import com.selfdevelopment.ai.messaging.statemachine.core.Action;
import com.selfdevelopment.ai.messaging.statemachine.core.StateDef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of {@link StateConfigurer}.
 */
public final class DefaultStateConfigurer<S, E, C> implements StateConfigurer<S, E, C> {

    private final Map<S, StateDef<S, E, C>> states = new LinkedHashMap<>();
    private S initialState;

    @Override
    public StateConfigurer<S, E, C> withStates() {
        return this;
    }

    @Override
    public StateConfigurer<S, E, C> initial(S state) {
        this.initialState = Objects.requireNonNull(state, "initial state must not be null");
        states.putIfAbsent(state, StateDef.<S, E, C>builder(state).initial().build());
        return this;
    }

    @Override
    public StateConfigurer<S, E, C> state(S id) {
        states.putIfAbsent(id, StateDef.<S, E, C>builder(id).build());
        return this;
    }

    @Override
    public StateConfigurer<S, E, C> state(S id, Action<S, E, C> entryAction, Action<S, E, C> exitAction) {
        states.put(id, StateDef.<S, E, C>builder(id)
                .entryAction(entryAction)
                .exitAction(exitAction)
                .build());
        return this;
    }

    @Override
    public StateConfigurer<S, E, C> stateWithEntry(S id, Action<S, E, C> entryAction) {
        states.put(id, StateDef.<S, E, C>builder(id).entryAction(entryAction).build());
        return this;
    }

    @Override
    public StateConfigurer<S, E, C> stateWithExit(S id, Action<S, E, C> exitAction) {
        states.put(id, StateDef.<S, E, C>builder(id).exitAction(exitAction).build());
        return this;
    }

    @Override
    public StateConfigurer<S, E, C> end(S state) {
        states.put(state, StateDef.<S, E, C>builder(state).end().build());
        return this;
    }

    @Override
    public Map<S, StateDef<S, E, C>> getStates() {
        return Collections.unmodifiableMap(states);
    }

    @Override
    public S getInitialState() {
        return initialState;
    }
}
