package com.selfdevelopment.ai.messaging.statemachine.core;

import lombok.Getter;

import java.util.Objects;

/**
 * Represents a single state transition rule.
 * <p>
 * A transition defines: source state → event → target state, with an optional
 * guard condition, an optional action, and a transition kind.
 * <p>
 * Inspired by Spring StateMachine's {@code Transition}.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
@Getter
public final class Transition<S, E, C> {

    private final S sourceState;
    private final E event;
    private final S targetState;
    private final Guard<S, E, C> guard;
    private final Action<S, E, C> action;
    private final TransitionKind kind;

    /**
     * Creates a new transition with default EXTERNAL kind.
     */
    public Transition(S sourceState, E event, S targetState,
                      Guard<S, E, C> guard, Action<S, E, C> action) {
        this(sourceState, event, targetState, guard, action, TransitionKind.EXTERNAL);
    }

    /**
     * Creates a new transition with explicit kind.
     *
     * @param sourceState the state before the transition
     * @param event       the event that triggers the transition
     * @param targetState the state after the transition (for INTERNAL, should equal sourceState)
     * @param guard       the guard condition (may be null, meaning always allowed)
     * @param action      the action to execute on success (may be null)
     * @param kind        the transition kind (EXTERNAL or INTERNAL)
     */
    public Transition(S sourceState, E event, S targetState,
                      Guard<S, E, C> guard, Action<S, E, C> action,
                      TransitionKind kind) {
        this.sourceState = Objects.requireNonNull(sourceState, "sourceState must not be null");
        this.event = Objects.requireNonNull(event, "event must not be null");
        this.targetState = Objects.requireNonNull(targetState, "targetState must not be null");
        this.guard = guard;
        this.action = action;
        this.kind = kind != null ? kind : TransitionKind.EXTERNAL;
    }

    /**
     * Checks whether this transition is applicable for the given source state and event.
     */
    public boolean matches(S sourceState, E event) {
        return this.sourceState.equals(sourceState) && this.event.equals(event);
    }

    /**
     * Evaluates the guard condition.
     *
     * @param context the current state context
     * @return true if the guard is satisfied (or no guard exists)
     */
    public boolean isGuardSatisfied(StateContext<S, E, C> context) {
        return guard == null || guard.evaluate(context);
    }

    /**
     * Executes the transition action, if any.
     *
     * @param context the current state context
     */
    public void executeAction(StateContext<S, E, C> context) {
        if (action != null) {
            action.execute(context);
        }
    }

    /**
     * Returns true if this is an internal transition (state does not change).
     */
    public boolean isInternal() {
        return kind == TransitionKind.INTERNAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transition<?, ?, ?> that)) return false;
        return sourceState.equals(that.sourceState)
                && event.equals(that.event)
                && targetState.equals(that.targetState)
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceState, event, targetState, kind);
    }

    @Override
    public String toString() {
        return sourceState + " --[" + event + ", " + kind + "]--> " + targetState;
    }
}
