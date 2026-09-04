package com.selfdevelopment.chatengine.action.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a transfer connects successfully (TRANSFERRED 鈫?IN_PROGRESS).
 * <p>
 * This action handles the business logic of a successful human agent transfer:
 * <ul>
 *   <li>Records transfer completion in the conversation history</li>
 *   <li>Notifies the customer that transfer is complete</li>
 *   <li>Establishes connection between customer and human agent</li>
 *   <li>Updates conversation metadata (transferOutcome, transferredAt)</li>
 *   <li>Cleans up transfer-related temporary data</li>
 * </ul>
 */
@Slf4j
public class TransferConnectedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("TransferConnectedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Record transfer completion in conversation history (simulated)
        recordTransferCompletion(ctx);

        // 2. Notify customer that transfer is complete (simulated)
        notifyCustomerTransferComplete(ctx);

        // 3. Establish connection between customer and human agent (simulated)
        establishAgentConnection(ctx);

        // 4. Update conversation metadata (transferOutcome, transferredAt) (simulated)
        updateConversationMetadata(ctx);

        // 5. Clean up transfer-related temporary data (simulated)
        cleanupTransferData(ctx);

        log.info("TransferConnectedAction completed successfully: conversationId={}", conversationId);
    }

    private void recordTransferCompletion(CbolStateContext ctx) {
        // In production: call conversationHistoryService.recordTransferComplete(conversationId, agentId)
        log.debug("Recording transfer completion: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerTransferComplete(CbolStateContext ctx) {
        // In production: call messageService.sendSystemMessage(conversationId, "Transfer complete")
        String message = getTransferCompleteMessage(ctx.conversation().market());
        log.debug("Notifying customer transfer complete: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void establishAgentConnection(CbolStateContext ctx) {
        // In production: call agentService.establishConnection(conversationId, agentId)
        log.debug("Establishing agent connection: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx) {
        // In production: call conversationRepository.updateMetadata(conversationId, transferOutcome, transferredAt)
        log.debug("Updating conversation metadata: conversationId={}", ctx.conversation().conversationId());
    }

    private void cleanupTransferData(CbolStateContext ctx) {
        // In production: call transferService.cleanupTempData(conversationId)
        log.debug("Cleaning up transfer data: conversationId={}", ctx.conversation().conversationId());
    }

    private String getTransferCompleteMessage(String market) {
        return switch (market) {
            case "HK" -> "宸茬偤鎮ㄦ帴椐佸埌瀹㈡湇浜哄摗";
            case "SG" -> "You are now connected to an agent";
            case "UK" -> "You are now connected to an agent";
            default -> "Transfer complete, you are now connected";
        };
    }
}
