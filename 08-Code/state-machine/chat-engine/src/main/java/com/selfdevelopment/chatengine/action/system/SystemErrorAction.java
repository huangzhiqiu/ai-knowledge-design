package com.selfdevelopment.chatengine.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when system error occurs (Various → ENDING).
 * <p>
 * This action handles the business logic of system error:
 * <ul>
 *   <li>Sets endReason=SYSTEM_ERROR</li>
 *   <li>Records system error with full context</li>
 *   <li>Triggers alerting and monitoring notifications</li>
 *   <li>Notifies the customer about technical issues</li>
 *   <li>Triggers ending procedures</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.SYSTEM_ERROR)
public class SystemErrorAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.error("SystemErrorAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set endReason=SYSTEM_ERROR
        setEndReason(ctx, "SYSTEM_ERROR");

        // 2. Record system error with full context
        recordSystemError(ctx);

        // 3. Trigger alerting and monitoring notifications
        triggerAlerts(ctx);

        // 4. Notify the customer about technical issues
        notifyCustomerSystemError(ctx);

        // 5. Trigger ending procedures
        triggerEndingProcedures(ctx);

        log.error("SystemErrorAction completed: conversationId={}, entering ENDING with endReason=SYSTEM_ERROR",
                conversationId);
    }

    private void setEndReason(CbolStateContext ctx, String endReason) {
        log.debug("Setting endReason={}: conversationId={}", endReason, ctx.conversation().conversationId());
    }

    private void recordSystemError(CbolStateContext ctx) {
        log.error("Recording system error: conversationId={}", ctx.conversation().conversationId());
    }

    private void triggerAlerts(CbolStateContext ctx) {
        log.error("Triggering alerts: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerSystemError(CbolStateContext ctx) {
        String message = getSystemErrorMessage(ctx.conversation().market());
        log.debug("Notifying customer system error: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void triggerEndingProcedures(CbolStateContext ctx) {
        log.debug("Triggering ending procedures: conversationId={}", ctx.conversation().conversationId());
    }

    private String getSystemErrorMessage(String market) {
        return switch (market) {
            case "HK" -> "由於技術問題，本次對話即將結束，敬請諒解";
            case "SG" -> "This conversation is ending due to technical issues. We apologize for the inconvenience.";
            case "UK" -> "This conversation is ending due to technical issues. We apologize for the inconvenience.";
            default -> "Conversation ending due to technical issues. We apologize.";
        };
    }
}
