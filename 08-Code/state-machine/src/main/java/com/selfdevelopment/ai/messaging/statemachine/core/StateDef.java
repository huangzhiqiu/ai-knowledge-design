package com.selfdevelopment.ai.messaging.statemachine.core;

import lombok.Getter;

import java.util.Objects;

/**
 * Definition of a state in the state machine.
 * <p>
 * Inspired by Spring StateMachine's {@code State} concept. A state definition
 * carries optional entry and exit actions that execute automatically when the
 * state machine enters or leaves the state.
 * <p>
 * A state can be marked as initial (the starting state) or end (a terminal state).
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
@Getter
public final class StateDef<S, E, C> {

    private final S id;
    private final Action<S, E, C> entryAction;
    private final Action<S, E, C> exitAction;
    private final boolean initial;
    private final boolean end;

    private StateDef(Builder<S, E, C> builder) {
        this.id = Objects.requireNonNull(builder.id, "state id must not be null");
        this.entryAction = builder.entryAction;
        this.exitAction = builder.exitAction;
        this.initial = builder.initial;
        this.end = builder.end;
    }

    public boolean hasEntryAction() { return entryAction != null; }
    public boolean hasExitAction() { return exitAction != null; }

    /**
     * Executes the entry action if present.
     */
    public void enter(StateContext<S, E, C> context) {
        if (entryAction != null) {
            entryAction.execute(context);
        }
    }

    /**
     * Executes the exit action if present.
     */
    public void exit(StateContext<S, E, C> context) {
        if (exitAction != null) {
            exitAction.execute(context);
        }
    }

    public static <S, E, C> Builder<S, E, C> builder(S id) {
        return new Builder<>(id);
    }

    public static final class Builder<S, E, C> {
        private final S id;
        private Action<S, E, C> entryAction;
        private Action<S, E, C> exitAction;
        private boolean initial;
        private boolean end;

        private Builder(S id) {
            this.id = id;
        }

        public Builder<S, E, C> entryAction(Action<S, E, C> action) {
            this.entryAction = action;
            return this;
        }

        public Builder<S, E, C> exitAction(Action<S, E, C> action) {
            this.exitAction = action;
            return this;
        }

        public Builder<S, E, C> initial() {
            this.initial = true;
            return this;
        }

        public Builder<S, E, C> end() {
            this.end = true;
            return this;
        }

        public StateDef<S, E, C> build() {
            return new StateDef<>(this);
        }
    }

    @Override
    public String toString() {
        return "StateDef{" + id
                + (initial ? ", initial" : "")
                + (end ? ", end" : "")
                + (entryAction != null ? ", entry" : "")
                + (exitAction != null ? ", exit" : "")
                + "}";
    }
}
