package com.selfdevelopment.chatengine.action.impl;

import com.selfdevelopment.chatengine.action.CbolAction;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a transfer fails (TRANSFERRED → INITIATED).
 * <p>
 * This action handles the v6 design: transfer failure resets the conversation
 * to INITIATED state for re-routing (no rollback to ACTIVE).
 * <ul>
 *   <li>Records the failure reason</li>
 *   <li>Cleans up transfer-related state</li>
 *   <li>Notifies customer about the failure</li>
 *   <li>Triggers re-routing logic (AI bot fallback or queue)</li>
 * </ul>
 */
@Slf4j
public class TransferFailedAction implements CbolAction {

    @Override
    public void execute(CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String market = ctx.conversation().market();

        log.info("Executing TransferFailedAction: conversationId={}, market={}", conversationId, market);

        // 1. Record the failure reason (simulated)
        recordFailureReason(ctx);

        // 2. Clean up transfer-related state (simulated)
        cleanupTransferState(ctx);

        // 3. Notify customer about the failure (simulated)
        notifyCustomerFailure(ctx);

        // 4. Trigger re-routing logic (simulated)
        triggerRerouting(ctx);

        log.info("TransferFailedAction completed: conversationId={}, conversation reset for re-routing",
                conversationId);
    }

    private void recordFailureReason(CbolStateContext ctx) {
        // In production: conversationRepository.updateTransferOutcome(conversationId, FAILED, reason)
        log.debug("Recording transfer failure: conversationId={}", ctx.conversation().conversationId());
    }

    private void cleanupTransferState(CbolStateContext ctx) {
        // In production: clear transferStartTs, agentId, etc.
        log.debug("Cleaning up transfer state: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerFailure(CbolStateContext ctx) {
        // In production: messageService.sendTransferFailedMessage(tenantId)
        log.debug("Notifying customer about transfer failure: tenantId={}", ctx.conversation().tenantId());
    }

    private void triggerRerouting(CbolStateContext ctx) {
        // In production: based on fallbackRoutingStrategy, route to AI bot or queue
        String strategy = ctx.marketConfig() != null
                ? ctx.marketConfig().fallbackRoutingStrategy()
                : "AI_BOT";
        log.debug("Triggering re-routing with strategy: strategy={}, conversationId={}",
                strategy, ctx.conversation().conversationId());
    }
}
