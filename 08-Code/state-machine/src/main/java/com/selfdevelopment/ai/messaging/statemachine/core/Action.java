package com.selfdevelopment.ai.messaging.statemachine.core;

/**
 * Action executed on a successful state transition.
 * <p>
 * Actions are side-effecting operations (e.g., sending notifications,
 * persisting data). The state machine engine itself does not perform
 * any I/O; actions encapsulate all side effects.
 *
 * @param <C> the context type carrying business data
 */
@FunctionalInterface
public interface Action<C> {

    /**
     * Executes the action with the given context.
     *
     * @param context the current business context
     */
    void execute(C context);
}
