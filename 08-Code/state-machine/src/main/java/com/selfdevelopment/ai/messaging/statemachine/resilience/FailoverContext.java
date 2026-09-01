package com.selfdevelopment.ai.messaging.statemachine.resilience;

/**
 * Context object passed to the fail event provider when an action fails.
 * <p>
 * Contains all information needed to generate an appropriate fail event,
 * including the original state, event, context, and the exception that caused the failure.
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public record FailoverContext<S, E, C>(
        S sourceState,
        E originalEvent,
        C context,
        RuntimeException cause
) {
    /**
     * Returns the exception message, or "unknown" if the cause is null.
     */
    public String causeMessage() {
        return cause != null && cause.getMessage() != null ? cause.getMessage() : "unknown";
    }

    /**
     * Returns the simple class name of the exception, or "UnknownException" if the cause is null.
     */
    public String causeType() {
        return cause != null ? cause.getClass().getSimpleName() : "UnknownException";
    }
}
