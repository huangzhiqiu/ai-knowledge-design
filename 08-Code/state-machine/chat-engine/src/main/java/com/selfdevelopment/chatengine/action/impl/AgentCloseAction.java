package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when an agent closes the conversation (IN_PROGRESS → ENDING).
 * <p>
 * This action handles the business logic of an agent-initiated conversation closure:
 * <ul>
 *   <li>Records agent closure in the conversation history</li>
 *   <li>Notifies the customer that the agent has ended the session</li>
 *   <li>Triggers post-conversation survey (if enabled for this market)</li>
 *   <li>Releases agent resources and updates agent availability</li>
 *   <li>Updates conversation metadata (endReason, endedBy, endedAt)</li>
 * </ul>
 */
@Slf4j
public class AgentCloseAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("AgentCloseAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Record agent closure in conversation history (simulated)
        recordAgentClosure(ctx);

        // 2. Notify customer that agent has ended the session (simulated)
        notifyCustomerAgentClosed(ctx);

        // 3. Trigger post-conversation survey if enabled (simulated)
        if (ctx.marketConfig() != null && ctx.marketConfig().surveyEnabled()) {
            triggerPostConversationSurvey(ctx);
        }

        // 4. Release agent resources and update agent availability (simulated)
        releaseAgentResources(ctx);

        // 5. Update conversation metadata (endReason, endedBy, endedAt) (simulated)
        updateConversationMetadata(ctx);

        log.info("AgentCloseAction completed successfully: conversationId={}", conversationId);
    }

    private void recordAgentClosure(CbolStateContext ctx) {
        // In production: call conversationHistoryService.recordAgentClosure(conversationId, agentId)
        log.debug("Recording agent closure: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerAgentClosed(CbolStateContext ctx) {
        // In production: call messageService.sendSystemMessage(conversationId, "Agent has ended the session")
        String message = getAgentClosedMessage(ctx.conversation().market());
        log.debug("Notifying customer agent closed: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void triggerPostConversationSurvey(CbolStateContext ctx) {
        // In production: call surveyService.triggerSurvey(conversationId, surveyType)
        log.debug("Triggering post-conversation survey: conversationId={}", ctx.conversation().conversationId());
    }

    private void releaseAgentResources(CbolStateContext ctx) {
        // In production: call agentService.releaseResources(agentId) and update availability
        log.debug("Releasing agent resources: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx) {
        // In production: call conversationRepository.updateMetadata(conversationId, endReason, endedBy, endedAt)
        log.debug("Updating conversation metadata: conversationId={}", ctx.conversation().conversationId());
    }

    private String getAgentClosedMessage(String market) {
        return switch (market) {
            case "HK" -> "客服人員已結束本次對話";
            case "SG" -> "The agent has ended this session";
            case "UK" -> "The agent has ended this session";
            default -> "The agent has ended this session";
        };
    }
}
