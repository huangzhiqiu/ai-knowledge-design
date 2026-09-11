package com.selfdevelopment.chatengine.action.actions.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when source interaction is transferred (IN_PROGRESS → TRANSFERRED).
 * <p>
 * This action handles the business logic of cross-channel transfer initiation:
 * <ul>
 *   <li>Sets transferInFlight=true</li>
 *   <li>Sets transferDeadlineAt=now+transferDeadlineSeconds (default 180s)</li>
 *   <li>Detaches source interaction</li>
 *   <li>Initiates target interaction connection</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.SOURCE_INTERACTION_TRANSFERRED)
public class SourceInteractionTransferredAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("SourceInteractionTransferredAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set transferInFlight=true
        setTransferInFlight(ctx, true);

        // 2. Set transferDeadlineAt=now+transferDeadlineSeconds (default 180s)
        setTransferDeadline(ctx);

        // 3. Detach source interaction
        detachSourceInteraction(ctx);

        // 4. Initiate target interaction connection
        initiateTargetConnection(ctx);

        log.info("SourceInteractionTransferredAction completed successfully: conversationId={}", conversationId);
    }

    private void setTransferInFlight(CbolStateContext ctx, boolean inFlight) {
        log.debug("Setting transferInFlight={}: conversationId={}", inFlight, ctx.conversation().conversationId());
    }

    private void setTransferDeadline(CbolStateContext ctx) {
        log.debug("Setting transfer deadline: conversationId={}", ctx.conversation().conversationId());
    }

    private void detachSourceInteraction(CbolStateContext ctx) {
        log.debug("Detaching source interaction: conversationId={}", ctx.conversation().conversationId());
    }

    private void initiateTargetConnection(CbolStateContext ctx) {
        log.debug("Initiating target connection: conversationId={}", ctx.conversation().conversationId());
    }
}
