package com.selfdevelopment.chatengine.action.exception.handler;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback handler for exceptions that don't match any specific handler.
 * <p>
 * This handler is used when no specific {@link ActionExceptionHandler} can handle
 * the exception. It provides a default logging behavior and can be extended to
 * implement custom fallback logic.
 * <p>
 * Priority: -100 (lowest - always checked last, used as fallback)
 * <p>
 * This handler is automatically detected by
 * {@link com.selfdevelopment.chatengine.action.exception.registry.ActionExceptionHandlerRegistry}
 * and used as the fallback when no other handler matches.
 *
 * @see ActionExceptionHandler
 * @see com.selfdevelopment.chatengine.action.exception.registry.ActionExceptionHandlerRegistry
 */
@Slf4j
@Component
public class FallbackActionExceptionHandler implements ActionExceptionHandler {

    @Override
    public boolean canHandle(Throwable ex) {
        // Fallback handler can handle any exception
        return true;
    }

    @Override
    public void handle(Throwable ex, ConversationState from, ConversationState to,
                       ConversationFact fact, CbolStateContext ctx) {
        String conversationId = ctx != null && ctx.conversation() != null
                ? ctx.conversation().conversationId() : "unknown";
        String traceId = ctx != null && ctx.traceContext() != null
                ? ctx.traceContext().traceId() : "unknown";

        log.error("Unhandled exception during Action execution (fallback): " +
                        "conversationId={}, traceId={}, from={}, to={}, fact={}, " +
                        "exceptionType={}, message={}",
                conversationId, traceId, from, to, fact,
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        // TODO: Extend with custom fallback logic:
        // - Log to error tracking system (e.g., Sentry, Rollbar, Datadog)
        // - Create generic incident ticket
        // - Notify engineering team of unhandled exception
        // - Collect metrics for unhandled exception rate
        // - Trigger circuit breaker if error rate exceeds threshold
    }

    @Override
    public int getPriority() {
        return -100; // Lowest priority - always checked last
    }
}
