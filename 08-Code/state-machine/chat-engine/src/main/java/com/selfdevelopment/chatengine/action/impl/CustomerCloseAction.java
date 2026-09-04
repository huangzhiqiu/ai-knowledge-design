package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a customer closes the conversation (IN_PROGRESS → ENDING).
 * <p>
 * This action handles the actual business logic of closing a conversation:
 * <ul>
 *   <li>Marks conversation as ending</li>
 *   <li>Sends closing confirmation to customer</li>
 *   <li>Releases agent resources (if any)</li>
 *   <li>Records ending start time for grace period monitoring</li>
 * </ul>
 */
@Slf4j
public class CustomerCloseAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("CustomerCloseAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Mark conversation as ending (simulated)
        markConversationEnding(ctx);

        // 2. Send closing confirmation to customer (simulated)
        sendClosingConfirmation(ctx);

        // 3. Release agent resources (simulated)
        releaseAgentResources(ctx);

        // 4. Record ending start time (simulated)
        recordEndingStartTime(ctx);

        log.info("CustomerCloseAction completed: conversationId={}", conversationId);
    }

    private void markConversationEnding(CbolStateContext ctx) {
        // In production: conversationRepository.updateState(conversationId, ENDING)
        log.debug("Marking conversation as ending: conversationId={}", ctx.conversation().conversationId());
    }

    private void sendClosingConfirmation(CbolStateContext ctx) {
        // In production: messageService.sendClosingMessage(tenantId)
        log.debug("Sending closing confirmation: tenantId={}", ctx.conversation().tenantId());
    }

    private void releaseAgentResources(CbolStateContext ctx) {
        // In production: if agentId != null, release agent from Genesys
        log.debug("Releasing agent resources: conversationId={}", ctx.conversation().conversationId());
    }

    private void recordEndingStartTime(CbolStateContext ctx) {
        // In production: conversationRepository.updateEndingStartTs(conversationId, now)
        log.debug("Recording ending start time: conversationId={}", ctx.conversation().conversationId());
    }
}
