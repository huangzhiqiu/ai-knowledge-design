package com.selfdevelopment.chatengine.action.exception.handler;

import com.selfdevelopment.chatengine.action.exception.SystemException;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for {@link SystemException}.
 * <p>
 * This handler processes exceptions thrown for unexpected system errors. It logs
 * detailed error information with stack traces and can be extended to implement
 * custom logic such as alerting engineering teams, triggering incident management,
 * or collecting system metrics.
 * <p>
 * Priority: 50 (medium - checked after downstream and business, before fallback)
 *
 * @see SystemException
 * @see ActionExceptionHandler
 */
@Slf4j
@Component
public class SystemExceptionHandler implements ActionExceptionHandler {

    @Override
    public boolean canHandle(Throwable ex) {
        return ex instanceof SystemException;
    }

    @Override
    public void handle(Throwable ex, ConversationState from, ConversationState to,
                       ConversationFact fact, CbolStateContext ctx) {
        SystemException sEx = (SystemException) ex;

        String conversationId = ctx != null && ctx.conversation() != null
                ? ctx.conversation().conversationId() : "unknown";
        String traceId = ctx != null && ctx.traceContext() != null
                ? ctx.traceContext().traceId() : "unknown";

        log.error("System error occurred: conversationId={}, traceId={}, " +
                        "from={}, to={}, fact={}, component={}, category={}, message={}",
                conversationId, traceId, from, to, fact,
                sEx.getSystemComponent(), sEx.getErrorCategory(), sEx.getMessage(), ex);

        // TODO: Extend with custom logic:
        // - Alert on-call engineering teams (e.g., PagerDuty, Opsgenie)
        // - Trigger incident management workflow (e.g., create incident ticket)
        // - Collect system metrics (e.g., error rate by component/category)
        // - Dump diagnostic information (e.g., thread dump, heap dump)
        // - Update system health dashboards
    }

    @Override
    public int getPriority() {
        return 50; // Medium priority
    }
}
