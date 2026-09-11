package com.selfdevelopment.chatengine.action.exception;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for {@link DownstreamConnectionException}.
 * <p>
 * This handler processes exceptions thrown when an Action cannot connect to a
 * downstream system. It logs detailed error information and can be extended to
 * implement custom logic such as alerting, circuit breaking, or retry scheduling.
 * <p>
 * Priority: 100 (high - checked before general handlers)
 *
 * @see DownstreamConnectionException
 * @see ActionExceptionHandler
 */
@Slf4j
@Component
public class DownstreamConnectionExceptionHandler implements ActionExceptionHandler {

    @Override
    public boolean canHandle(Throwable ex) {
        return ex instanceof DownstreamConnectionException;
    }

    @Override
    public void handle(Throwable ex, ConversationState from, ConversationState to,
                       ConversationFact fact, CbolStateContext ctx) {
        DownstreamConnectionException dcEx = (DownstreamConnectionException) ex;

        String conversationId = ctx != null && ctx.conversation() != null
                ? ctx.conversation().conversationId() : "unknown";
        String traceId = ctx != null && ctx.traceContext() != null
                ? ctx.traceContext().traceId() : "unknown";

        log.error("Downstream connection failed: conversationId={}, traceId={}, " +
                        "from={}, to={}, fact={}, system={}, operation={}, message={}",
                conversationId, traceId, from, to, fact,
                dcEx.getDownstreamSystem(), dcEx.getOperation(), dcEx.getMessage());

        // TODO: Extend with custom logic:
        // - Alert on-call teams (e.g., PagerDuty, Opsgenie)
        // - Trigger circuit breaker (e.g., Resilience4j)
        // - Schedule retry with exponential backoff
        // - Fall back to alternative downstream system
        // - Record metrics (e.g., Micrometer, Prometheus)
    }

    @Override
    public int getPriority() {
        return 100; // High priority - checked before general handlers
    }
}
