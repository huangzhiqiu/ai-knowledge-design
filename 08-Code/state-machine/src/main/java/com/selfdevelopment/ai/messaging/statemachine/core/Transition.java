package com.selfdevelopment.ai.messaging.statemachine.core;

import java.util.Objects;

/**
 * Represents a single state transition rule.
 * <p>
 * A transition defines: source state → event → target state, with an optional
 * guard condition and an optional action to execute on success.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public final class Transition<S, E, C> {

    private final S sourceState;
    private final E event;
    private final S targetState;
    private final Condition<C> condition;
    private final Action<C> action;

    /**
     * Creates a new transition.
     *
     * @param sourceState the state before the transition
     * @param event       the event that triggers the transition
     * @param targetState the state after the transition
     * @param condition   the guard condition (may be {@code null}, meaning always allowed)
     * @param action      the action to execute on success (may be {@code null})
     */
    public Transition(S sourceState, E event, S targetState,
                      Condition<C> condition, Action<C> action) {
        this.sourceState = Objects.requireNonNull(sourceState, "sourceState must not be null");
        this.event = Objects.requireNonNull(event, "event must not be null");
        this.targetState = Objects.requireNonNull(targetState, "targetState must not be null");
        this.condition = condition;
        this.action = action;
    }

    /**
     * Checks whether this transition is applicable for the given source state and event.
     *
     * @param sourceState the current state
     * @param event       the triggered event
     * @return {@code true} if this transition matches the source state and event
     */
    public boolean matches(S sourceState, E event) {
        return this.sourceState.equals(sourceState) && this.event.equals(event);
    }

    /**
     * Evaluates the guard condition.
     *
     * @param context the current business context
     * @return {@code true} if the condition is satisfied (or no condition exists)
     */
    public boolean isConditionSatisfied(C context) {
        return condition == null || condition.isSatisfied(context);
    }

    /**
     * Executes the transition action, if any.
     *
     * @param context the current business context
     */
    public void executeAction(C context) {
        if (action != null) {
            action.execute(context);
        }
    }

    public S getSourceState() {
        return sourceState;
    }

    public E getEvent() {
        return event;
    }

    public S getTargetState() {
        return targetState;
    }

    public Condition<C> getCondition() {
        return condition;
    }

    public Action<C> getAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transition<?, ?, ?> that)) return false;
        return sourceState.equals(that.sourceState)
                && event.equals(that.event)
                && targetState.equals(that.targetState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceState, event, targetState);
    }

    @Override
    public String toString() {
        return sourceState + " --[" + event + "]--> " + targetState;
    }
}
