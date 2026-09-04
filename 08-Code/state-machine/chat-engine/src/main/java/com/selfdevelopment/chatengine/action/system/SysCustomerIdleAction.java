package com.selfdevelopment.chatengine.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when customer idle timeout is detected (various states 鈫?ENDING).
 * <p>
 * This system-driven action handles the business logic of customer idle timeout:
 * <ul>
 *   <li>Records idle timeout in the conversation history</li>
 *   <li>Notifies the customer that the conversation is ending due to inactivity</li>
 *   <li>Releases any held resources (agent, channels)</li>
 *   <li>Updates conversation metadata (endReason=IDLE_TIMEOUT, idleDuration)</li>
 *   <li>Triggers cleanup procedures</li>
 * </ul>
 * <p>
 * This action is triggered by the CustomerIdleMonitor when the customer has been
 * inactive for longer than the configured customerIdleSeconds threshold.
 */
@Slf4j
public class SysCustomerIdleAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        long idleThreshold = ctx.marketConfig() != null ? ctx.marketConfig().customerIdleSeconds() : 300;

        log.info("SysCustomerIdleAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, idleThreshold={}s",
                from, event, to, conversationId, tenantId, market, idleThreshold);

        // 1. Record idle timeout in conversation history (simulated)
        recordIdleTimeout(ctx, idleThreshold);

        // 2. Notify customer that conversation is ending due to inactivity (simulated)
        notifyCustomerIdleTimeout(ctx, idleThreshold);

        // 3. Release any held resources (agent, channels) (simulated)
        releaseHeldResources(ctx);

        // 4. Update conversation metadata (endReason=IDLE_TIMEOUT, idleDuration) (simulated)
        updateConversationMetadata(ctx, idleThreshold);

        // 5. Trigger cleanup procedures (simulated)
        triggerCleanup(ctx);

        log.info("SysCustomerIdleAction completed successfully: conversationId={}", conversationId);
    }

    private void recordIdleTimeout(CbolStateContext ctx, long idleThreshold) {
        // In production: call conversationHistoryService.recordIdleTimeout(conversationId, idleThreshold)
        log.debug("Recording idle timeout: conversationId={}, idleThreshold={}s",
                ctx.conversation().conversationId(), idleThreshold);
    }

    private void notifyCustomerIdleTimeout(CbolStateContext ctx, long idleThreshold) {
        // In production: call messageService.sendSystemMessage(conversationId, "Conversation ending due to inactivity")
        String message = getIdleTimeoutMessage(ctx.conversation().market(), idleThreshold);
        log.debug("Notifying customer idle timeout: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void releaseHeldResources(CbolStateContext ctx) {
        // In production: call resourceService.releaseAll(conversationId)
        log.debug("Releasing held resources: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx, long idleThreshold) {
        // In production: call conversationRepository.updateMetadata(conversationId, endReason, idleDuration)
        log.debug("Updating conversation metadata: conversationId={}, endReason=IDLE_TIMEOUT",
                ctx.conversation().conversationId());
    }

    private void triggerCleanup(CbolStateContext ctx) {
        // In production: call cleanupService.triggerCleanup(conversationId)
        log.debug("Triggering cleanup: conversationId={}", ctx.conversation().conversationId());
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
