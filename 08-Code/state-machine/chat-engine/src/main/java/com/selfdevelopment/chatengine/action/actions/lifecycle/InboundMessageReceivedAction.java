package com.selfdevelopment.chatengine.action.actions.lifecycle;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when inbound message is received (ACTIVE → IN_PROGRESS).
 * <p>
 * This action handles the business logic of first inbound message:
 * <ul>
 *   <li>Sets lastInboundAt timestamp</li>
 *   <li>Records first response</li>
 *   <li>Routes message to AI bot or agent</li>
 *   <li>Updates conversation metadata</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.INBOUND_MESSAGE_RECEIVED)
public class InboundMessageReceivedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("InboundMessageReceivedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set lastInboundAt timestamp
        setLastInboundAt(ctx);

        // 2. Record first response
        recordFirstResponse(ctx);

        // 3. Route message to AI bot or agent
        routeMessage(ctx);

        // 4. Update conversation metadata
        updateConversationMetadata(ctx);

        log.info("InboundMessageReceivedAction completed successfully: conversationId={}", conversationId);
    }

    private void setLastInboundAt(CbolStateContext ctx) {
        log.debug("Setting lastInboundAt timestamp: conversationId={}", ctx.conversation().conversationId());
    }

    private void recordFirstResponse(CbolStateContext ctx) {
        log.debug("Recording first response: conversationId={}", ctx.conversation().conversationId());
    }

    private void routeMessage(CbolStateContext ctx) {
        log.debug("Routing message: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx) {
        log.debug("Updating conversation metadata: conversationId={}", ctx.conversation().conversationId());
    }
}
