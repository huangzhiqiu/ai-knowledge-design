package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when an agent is attached to the conversation (IN_PROGRESS → IN_PROGRESS, internal transition).
 * <p>
 * This is an internal transition - the state does not change, but the action executes
 * to handle the agent attachment business logic:
 * <ul>
 *   <li>Records agent attachment in the conversation history</li>
 *   <li>Notifies the customer that an agent is now available</li>
 *   <li>Transfers conversation context from AI bot to human agent</li>
 *   <li>Updates conversation metadata (agentId, attachedAt)</li>
 * </ul>
 */
@Slf4j
public class AgentAttachedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("AgentAttachedAction: {} --({})--> {} (internal), conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Record agent attachment in conversation history (simulated)
        recordAgentAttachment(ctx);

        // 2. Notify customer that an agent is now available (simulated)
        notifyCustomerAgentAttached(ctx);

        // 3. Transfer conversation context from AI bot to human agent (simulated)
        transferContextToAgent(ctx);

        // 4. Update conversation metadata (agentId, attachedAt) (simulated)
        updateConversationMetadata(ctx);

        log.info("AgentAttachedAction completed successfully: conversationId={}", conversationId);
    }

    private void recordAgentAttachment(CbolStateContext ctx) {
        // In production: call conversationHistoryService.recordAgentAttachment(conversationId, agentId)
        log.debug("Recording agent attachment: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerAgentAttached(CbolStateContext ctx) {
        // In production: call messageService.sendSystemMessage(conversationId, "Agent is now connected")
        String message = getAgentAttachedMessage(ctx.conversation().market());
        log.debug("Notifying customer agent attached: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void transferContextToAgent(CbolStateContext ctx) {
        // In production: call agentService.transferContext(conversationId, agentId, context)
        log.debug("Transferring context to agent: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx) {
        // In production: call conversationRepository.updateMetadata(conversationId, agentId, attachedAt)
        log.debug("Updating conversation metadata: conversationId={}", ctx.conversation().conversationId());
    }

    private String getAgentAttachedMessage(String market) {
        return switch (market) {
            case "HK" -> "客服人員已為您服務";
            case "SG" -> "An agent is now connected to assist you";
            case "UK" -> "An agent is now connected to assist you";
            default -> "An agent is now connected";
        };
    }
}
