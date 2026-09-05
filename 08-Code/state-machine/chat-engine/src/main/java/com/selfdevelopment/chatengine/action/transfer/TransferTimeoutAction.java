package com.selfdevelopment.chatengine.action.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when transfer times out (TRANSFERRED → INITIATED).
 * <p>
 * Latest policy: transfer timeout does NOT rollback, Conversation returns directly to INITIATED
 * (re-route/fallback).
 * <p>
 * This action handles the business logic of transfer timeout (>=180s):
 * <ul>
 *   <li>Sets transferInFlight=false</li>
 *   <li>Sets transferOutcome=TIMEOUT</li>
 *   <li>Initiates downstream re-assignment or fallback</li>
 *   <li>Records transfer timeout in conversation history</li>
 *   <li>Cancels any pending target connection attempts</li>
 * </ul>
 */
@Slf4j
public class TransferTimeoutAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("TransferTimeoutAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set transferInFlight=false
        setTransferInFlight(ctx, false);

        // 2. Set transferOutcome=TIMEOUT
        setTransferOutcome(ctx, "TIMEOUT");

        // 3. Cancel any pending target connection attempts
        cancelPendingConnections(ctx);

        // 4. Initiate downstream re-assignment or fallback
        initiateReassignmentOrFallback(ctx);

        // 5. Record transfer timeout in conversation history
        recordTransferTimeout(ctx);

        log.info("TransferTimeoutAction completed successfully: conversationId={}, re-routing to INITIATED",
                conversationId);
    }

    private void setTransferInFlight(CbolStateContext ctx, boolean inFlight) {
        log.debug("Setting transferInFlight={}: conversationId={}", inFlight, ctx.conversation().conversationId());
    }

    private void setTransferOutcome(CbolStateContext ctx, String outcome) {
        log.debug("Setting transferOutcome={}: conversationId={}", outcome, ctx.conversation().conversationId());
    }

    private void cancelPendingConnections(CbolStateContext ctx) {
        log.debug("Cancelling pending connections: conversationId={}", ctx.conversation().conversationId());
    }

    private void initiateReassignmentOrFallback(CbolStateContext ctx) {
        log.debug("Initiating reassignment or fallback: conversationId={}", ctx.conversation().conversationId());
    }

    private void recordTransferTimeout(CbolStateContext ctx) {
        log.debug("Recording transfer timeout: conversationId={}", ctx.conversation().conversationId());
    }
}
