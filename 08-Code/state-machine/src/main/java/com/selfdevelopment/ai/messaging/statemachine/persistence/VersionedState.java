package com.selfdevelopment.ai.messaging.statemachine.persistence;

/**
 * A state value paired with its version number for optimistic locking.
 *
 * @param <S> the state type
 */
public record VersionedState<S>(
        S state,
        long version
) {
    /**
     * Creates a new versioned state with the given state and version.
     *
     * @param state   the state value
     * @param version the version number (monotonically increasing)
     */
    public VersionedState {
    }

    /**
     * Creates a versioned state with version 0 (initial state).
     *
     * @param state the initial state
     * @param <S>   the state type
     * @return a new versioned state with version 0
     */
    public static <S> VersionedState<S> initial(S state) {
        return new VersionedState<>(state, 0);
    }
}
