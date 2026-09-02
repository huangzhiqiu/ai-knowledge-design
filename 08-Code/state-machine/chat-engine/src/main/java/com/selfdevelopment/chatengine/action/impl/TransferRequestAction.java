package com.selfdevelopment.chatengine.action.impl;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a transfer is requested (IN_PROGRESS → TRANSFERRED).
 * <p>
 * This action handles the actual business logic of transferring to a human agent:
 * <ul>
 *   <li>Validates agent availability</li>
 *   <li>Requests routing to Genesys/agent queue</li>
 *   <li>Notifies customer about transfer</li>
 *   <li>Records transfer start time for timeout monitoring</li>
 * </ul>
 */
@Slf4j
public class TransferRequestAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(StateContext<ConversationState, ConversationFact, CbolStateContext> context) {
        CbolStateContext ctx = context.getBusinessContext();
        String conversationId = ctx.conversation().conversationId();
        String market = ctx.conversation().market();

        log.info("Executing TransferRequestAction: conversationId={}, market={}", conversationId, market);

        // 1. Validate agent availability (simulated)
        boolean agentAvailable = checkAgentAvailability(ctx);

        if (!agentAvailable) {
            log.warn("No agents available for market={}, will use fallback strategy", market);
        }

        // 2. Request routing to Genesys/agent queue (simulated)
        requestAgentRouting(ctx);

        // 3. Notify customer about transfer (simulated)
        notifyCustomerTransfer(ctx);

        // 4. Record transfer start time (simulated)
        recordTransferStartTime(ctx);

        log.info("TransferRequestAction completed: conversationId={}, agentAvailable={}",
                conversationId, agentAvailable);
    }

    private boolean checkAgentAvailability(CbolStateContext ctx) {
        // In production: call genesysService.checkQueueAvailability(market)
        log.debug("Checking agent availability: market={}", ctx.conversation().market());
        return true;
    }

    private void requestAgentRouting(CbolStateContext ctx) {
        // In production: call genesysService.routeToQueue(conversationId, market)
        log.debug("Requesting agent routing: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerTransfer(CbolStateContext ctx) {
        // In production: call messageService.sendTransferNotification(tenantId)
        log.debug("Notifying customer about transfer: tenantId={}", ctx.conversation().tenantId());
    }

    private void recordTransferStartTime(CbolStateContext ctx) {
        // In production: conversationRepository.updateTransferStartTs(conversationId, now)
        log.debug("Recording transfer start time: conversationId={}", ctx.conversation().conversationId());
    }
}
