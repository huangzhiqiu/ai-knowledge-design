package com.selfdevelopment.chatengine.action.exception;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;

/**
 * Handler for exceptions thrown during Action execution.
 * <p>
 * Implementations can define custom handling logic for specific exception types.
 * The state machine transition continues regardless of exception handling result —
 * exceptions do not block state changes.
 * <p>
 * Usage:
 * <pre>{@code
 * public class DownstreamConnectionExceptionHandler implements ActionExceptionHandler {
 *     @Override
 *     public boolean canHandle(Throwable ex) {
 *         return ex instanceof DownstreamConnectionException;
 *     }
 *
 *     @Override
 *     public void handle(Throwable ex, ConversationState from, ConversationState to,
 *                        ConversationFact fact, CbolStateContext ctx) {
 *         // Custom handling logic: alert, retry, fallback, etc.
 *         log.error("Downstream connection failed: {}", ex.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @see ActionExceptionHandlerRegistry
 * @see ExceptionHandlingAction
 */
public interface ActionExceptionHandler {

    /**
     * Determines if this handler can handle the given exception.
     *
     * @param ex the exception thrown during Action execution
     * @return true if this handler can handle the exception, false otherwise
     */
    boolean canHandle(Throwable ex);

    /**
     * Handles the exception thrown during Action execution.
     * <p>
     * This method is called after the exception is caught, before the state machine
     * transition completes. The state change will proceed regardless of how this
     * method handles the exception.
     *
     * @param ex   the exception thrown during Action execution
     * @param from the source state of the transition
     * @param to   the target state of the transition
     * @param fact the event/fact that triggered the transition
     * @param ctx  the state machine context
     */
    void handle(Throwable ex, ConversationState from, ConversationState to,
                ConversationFact fact, CbolStateContext ctx);

    /**
     * Returns the priority of this handler.
     * <p>
     * Handlers with higher priority are checked first. Default priority is 0.
     * Use higher values for more specific handlers, lower values for general handlers.
     *
     * @return the priority of this handler (higher = checked first)
     */
    default int getPriority() {
        return 0;
    }
}
