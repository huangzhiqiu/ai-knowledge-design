package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when survey timeout is detected (IN_PROGRESS → ENDING).
 * <p>
 * This system-driven action handles the business logic of survey timeout:
 * <ul>
 *   <li>Records survey timeout in the conversation history</li>
 *   <li>Notifies the customer that the survey period has ended</li>
 *   <li>Finalizes survey results with partial data (if any)</li>
 *   <li>Marks survey as TIMEOUT status</li>
 *   <li>Updates conversation metadata (surveyStatus, surveyTimeoutAt)</li>
 *   <li>Triggers transition to ENDING state</li>
 * </ul>
 * <p>
 * This action is triggered when the customer does not complete the survey within
 * the configured survey timeout period. The survey is a sub-phase within the
 * IN_PROGRESS state (not a separate state), and timeout causes the conversation
 * to transition to ENDING.
 */
@Slf4j
public class SysSurveyTimeoutAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        long surveyTimeout = ctx.marketConfig() != null ? ctx.marketConfig().surveyTimeoutSeconds() : 120;

        log.info("SysSurveyTimeoutAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, surveyTimeout={}s",
                from, event, to, conversationId, tenantId, market, surveyTimeout);

        // 1. Record survey timeout in conversation history (simulated)
        recordSurveyTimeout(ctx, surveyTimeout);

        // 2. Notify customer that survey period has ended (simulated)
        notifyCustomerSurveyTimeout(ctx, surveyTimeout);

        // 3. Finalize survey results with partial data (if any) (simulated)
        finalizeSurveyResults(ctx);

        // 4. Mark survey as TIMEOUT status (simulated)
        markSurveyTimeout(ctx);

        // 5. Update conversation metadata (surveyStatus, surveyTimeoutAt) (simulated)
        updateConversationMetadata(ctx);

        // 6. Trigger transition to ENDING state (handled by state machine)
        log.info("SysSurveyTimeoutAction completed: conversationId={}, transitioning to ENDING", conversationId);
    }

    private void recordSurveyTimeout(CbolStateContext ctx, long surveyTimeout) {
        // In production: call conversationHistoryService.recordSurveyTimeout(conversationId, surveyTimeout)
        log.debug("Recording survey timeout: conversationId={}, surveyTimeout={}s",
                ctx.conversation().conversationId(), surveyTimeout);
    }

    private void notifyCustomerSurveyTimeout(CbolStateContext ctx, long surveyTimeout) {
        // In production: call messageService.sendSystemMessage(conversationId, "Survey period has ended")
        String message = getSurveyTimeoutMessage(ctx.conversation().market(), surveyTimeout);
        log.debug("Notifying customer survey timeout: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void finalizeSurveyResults(CbolStateContext ctx) {
        // In production: call surveyService.finalizeResults(conversationId, partialData)
        log.debug("Finalizing survey results: conversationId={}", ctx.conversation().conversationId());
    }

    private void markSurveyTimeout(CbolStateContext ctx) {
        // In production: call surveyService.markTimeout(conversationId)
        log.debug("Marking survey as TIMEOUT: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx) {
        // In production: call conversationRepository.updateMetadata(conversationId, surveyStatus, surveyTimeoutAt)
        log.debug("Updating conversation metadata: conversationId={}, surveyStatus=TIMEOUT",
                ctx.conversation().conversationId());
    }

    private String getSurveyTimeoutMessage(String market, long surveyTimeout) {
        return switch (market) {
            case "HK" -> String.format("問卷已於 %d 秒後超時，感謝您的參與", surveyTimeout);
            case "SG" -> String.format("The survey has timed out after %d seconds. Thank you for your participation.", surveyTimeout);
            case "UK" -> String.format("The survey has timed out after %d seconds. Thank you for your participation.", surveyTimeout);
            default -> String.format("Survey timed out after %d seconds. Thank you.", surveyTimeout);
        };
    }
}
