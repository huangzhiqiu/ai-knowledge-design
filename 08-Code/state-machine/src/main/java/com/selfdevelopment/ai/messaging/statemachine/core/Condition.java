package com.selfdevelopment.ai.messaging.statemachine.core;

/**
 * Guard condition for a state transition.
 * <p>
 * A transition is only allowed when this condition evaluates to {@code true}.
 *
 * @param <C> the context type carrying business data
 */
@FunctionalInterface
public interface Condition<C> {

    /**
     * Evaluates whether the transition is allowed given the current context.
     *
     * @param context the current business context
     * @return {@code true} if the transition is allowed, {@code false} otherwise
     */
    boolean isSatisfied(C context);
}
