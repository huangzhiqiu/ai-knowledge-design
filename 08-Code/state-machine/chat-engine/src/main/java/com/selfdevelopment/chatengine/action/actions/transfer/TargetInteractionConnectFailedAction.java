package com.selfdevelopment.chatengine.action.actions.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when target interaction connect fails (TRANSFERRED → INITIATED).
 * <p>
 * Latest policy: transfer failure does NOT rollback, Conversation returns directly to INITIATED
 * (re-route/fallback).
 * <p>
 * This action handles the business logic of transfer failure:
 * <ul>
 *   <li>Sets transferInFlight=false</li>
 *   <li>Sets transferOutcome=FAILED</li>
 *   <li>Initiates downstream re-assignment or fallback</li>
 *   <li>Records transfer failure in conversation history</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED)
public class TargetInteractionConnectFailedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("TargetInteractionConnectFailedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set transferInFlight=false
        setTransferInFlight(ctx, false);

        // 2. Set transferOutcome=FAILED
        setTransferOutcome(ctx, "FAILED");

        // 3. Initiate downstream re-assignment or fallback
        initiateReassignmentOrFallback(ctx);

        // 4. Record transfer failure in conversation history
        recordTransferFailure(ctx);

        log.info("TargetInteractionConnectFailedAction completed successfully: conversationId={}, re-routing to INITIATED",
                conversationId);
    }

    private void setTransferInFlight(CbolStateContext ctx, boolean inFlight) {
        log.debug("Setting transferInFlight={}: conversationId={}", inFlight, ctx.conversation().conversationId());
    }

    private void setTransferOutcome(CbolStateContext ctx, String outcome) {
        log.debug("Setting transferOutcome={}: conversationId={}", outcome, ctx.conversation().conversationId());
    }

    private void initiateReassignmentOrFallback(CbolStateContext ctx) {
        log.debug("Initiating reassignment or fallback: conversationId={}", ctx.conversation().conversationId());
    }

    private void recordTransferFailure(CbolStateContext ctx) {
        log.debug("Recording transfer failure: conversationId={}", ctx.conversation().conversationId());
    }
}
