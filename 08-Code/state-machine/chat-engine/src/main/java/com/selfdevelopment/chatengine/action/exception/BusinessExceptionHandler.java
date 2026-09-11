package com.selfdevelopment.chatengine.action.exception;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for {@link BusinessException}.
 * <p>
 * This handler processes exceptions thrown for business rule violations or expected
 * business errors. It logs business context and can be extended to implement custom
 * logic such as notifying stakeholders, triggering business workflows, or recording
 * business metrics.
 * <p>
 * Priority: 80 (medium-high - checked after downstream connection, before system)
 *
 * @see BusinessException
 * @see ActionExceptionHandler
 */
@Slf4j
@Component
public class BusinessExceptionHandler implements ActionExceptionHandler {

    @Override
    public boolean canHandle(Throwable ex) {
        return ex instanceof BusinessException;
    }

    @Override
    public void handle(Throwable ex, ConversationState from, ConversationState to,
                       ConversationFact fact, CbolStateContext ctx) {
        BusinessException bEx = (BusinessException) ex;

        String conversationId = ctx != null && ctx.conversation() != null
                ? ctx.conversation().conversationId() : "unknown";
        String traceId = ctx != null && ctx.traceContext() != null
                ? ctx.traceContext().traceId() : "unknown";

        log.warn("Business exception occurred: conversationId={}, traceId={}, " +
                        "from={}, to={}, fact={}, code={}, context={}, message={}",
                conversationId, traceId, from, to, fact,
                bEx.getBusinessCode(), bEx.getBusinessContext(), bEx.getMessage());

        // TODO: Extend with custom logic:
        // - Notify business stakeholders (e.g., email, Slack)
        // - Trigger business workflows (e.g., compensation, manual review)
        // - Record business metrics (e.g., business error rate by code)
        // - Update business dashboards
        // - Create business incident tickets
    }

    @Override
    public int getPriority() {
        return 80; // Medium-high priority
    }
}
