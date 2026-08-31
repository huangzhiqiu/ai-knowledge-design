package com.selfdevelopment.ai.messaging.statemachine.exception;

/**
 * Exception thrown when a state machine operation fails.
 * <p>
 * This includes:
 * <ul>
 *   <li>Invalid state transitions (no transition rule found)</li>
 *   <li>Guard condition failures (when configured to throw)</li>
 *   <li>Action execution failures (when configured to propagate)</li>
 *   <li>Configuration errors (duplicate transitions, etc.)</li>
 * </ul>
 */
public class StateMachineException extends RuntimeException {

    public StateMachineException(String message) {
        super(message);
    }

    public StateMachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
