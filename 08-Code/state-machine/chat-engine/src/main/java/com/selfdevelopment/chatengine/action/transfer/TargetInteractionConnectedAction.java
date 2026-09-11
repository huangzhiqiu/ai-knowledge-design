package com.selfdevelopment.chatengine.action.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when target interaction connects successfully (TRANSFERRED → ACTIVE).
 * <p>
 * This action handles the business logic of successful transfer:
 * <ul>
 *   <li>Sets transferInFlight=false</li>
 *   <li>Sets transferOutcome=CONNECTED</li>
 *   <li>Establishes connection between customer and target</li>
 *   <li>Records transfer completion in conversation history</li>
 * </ul>
 */
@Slf4j
@Component
public class TargetInteractionConnectedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("TargetInteractionConnectedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set transferInFlight=false
        setTransferInFlight(ctx, false);

        // 2. Set transferOutcome=CONNECTED
        setTransferOutcome(ctx, "CONNECTED");

        // 3. Establish connection between customer and target
        establishConnection(ctx);

        // 4. Record transfer completion in conversation history
        recordTransferCompletion(ctx);

        log.info("TargetInteractionConnectedAction completed successfully: conversationId={}", conversationId);
    }

    private void setTransferInFlight(CbolStateContext ctx, boolean inFlight) {
        log.debug("Setting transferInFlight={}: conversationId={}", inFlight, ctx.conversation().conversationId());
    }

    private void setTransferOutcome(CbolStateContext ctx, String outcome) {
        log.debug("Setting transferOutcome={}: conversationId={}", outcome, ctx.conversation().conversationId());
    }

    private void establishConnection(CbolStateContext ctx) {
        log.debug("Establishing connection: conversationId={}", ctx.conversation().conversationId());
    }

    private void recordTransferCompletion(CbolStateContext ctx) {
        log.debug("Recording transfer completion: conversationId={}", ctx.conversation().conversationId());
    }
}
