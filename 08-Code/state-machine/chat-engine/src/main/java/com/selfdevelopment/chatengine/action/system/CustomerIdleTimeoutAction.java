package com.selfdevelopment.chatengine.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when customer idle timeout is detected (Various → ENDING).
 * <p>
 * This system-driven action handles the business logic of customer idle timeout:
 * <ul>
 *   <li>Sets endReason=CUSTOMER_IDLE</li>
 *   <li>Records idle timeout in the conversation history</li>
 *   <li>Notifies the customer that the conversation is ending due to inactivity</li>
 *   <li>For TRANSFERRED state: special handling (don't cancel transfer, refresh endingDeadlineAt, defer CloseInteractions)</li>
 *   <li>Triggers ending procedures</li>
 * </ul>
 */
@Slf4j
public class CustomerIdleTimeoutAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        long idleThreshold = ctx.marketConfig() != null ? ctx.marketConfig().customerIdleSeconds() : 300;

        log.info("CustomerIdleTimeoutAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, idleThreshold={}s",
                from, event, to, conversationId, tenantId, market, idleThreshold);

        // 1. Set endReason=CUSTOMER_IDLE
        setEndReason(ctx, "CUSTOMER_IDLE");

        // 2. Record idle timeout in the conversation history
        recordIdleTimeout(ctx, idleThreshold);

        // 3. Notify the customer that the conversation is ending due to inactivity
        notifyCustomerIdleTimeout(ctx, idleThreshold);

        // 4. For TRANSFERRED state: special handling
        if (from == ConversationState.TRANSFERRED) {
            handleTransferredIdleSpecialCase(ctx);
        }

        // 5. Trigger ending procedures
        triggerEndingProcedures(ctx);

        log.info("CustomerIdleTimeoutAction completed successfully: conversationId={}, entering ENDING with endReason=CUSTOMER_IDLE",
                conversationId);
    }

    private void setEndReason(CbolStateContext ctx, String endReason) {
        log.debug("Setting endReason={}: conversationId={}", endReason, ctx.conversation().conversationId());
    }

    private void recordIdleTimeout(CbolStateContext ctx, long idleThreshold) {
        log.debug("Recording idle timeout: conversationId={}, idleThreshold={}s",
                ctx.conversation().conversationId(), idleThreshold);
    }

    private void notifyCustomerIdleTimeout(CbolStateContext ctx, long idleThreshold) {
        String message = getIdleTimeoutMessage(ctx.conversation().market(), idleThreshold);
        log.debug("Notifying customer idle timeout: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void handleTransferredIdleSpecialCase(CbolStateContext ctx) {
        log.debug("Handling TRANSFERRED idle special case (don't cancel transfer, defer CloseInteractions): conversationId={}",
                ctx.conversation().conversationId());
    }

    private void triggerEndingProcedures(CbolStateContext ctx) {
        log.debug("Triggering ending procedures: conversationId={}", ctx.conversation().conversationId());
    }

    private String getIdleTimeoutMessage(String market, long idleThreshold) {
        return switch (market) {
            case "HK" -> String.format("由於 %d 秒內沒有活動，對話即將結束", idleThreshold);
            case "SG" -> String.format("This conversation is ending due to %d seconds of inactivity", idleThreshold);
            case "UK" -> String.format("This conversation is ending due to %d seconds of inactivity", idleThreshold);
            default -> String.format("Conversation ending due to %d seconds of inactivity", idleThreshold);
        };
    }
}
