package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a survey is completed (IN_PROGRESS → ENDING).
 * <p>
 * The survey is a sub-phase within IN_PROGRESS, not a separate state.
 * This action handles the actual business logic of completing a survey:
 * <ul>
 *   <li>Saves survey results</li>
 *   <li>Calculates satisfaction score (NPS/CSAT)</li>
 *   <li>Cancels survey timeout</li>
 *   <li>Triggers ending grace period</li>
 * </ul>
 */
@Slf4j
public class SurveyCompleteAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("SurveyCompleteAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Save survey results (simulated)
        saveSurveyResults(ctx);

        // 2. Calculate satisfaction score (simulated)
        calculateSatisfactionScore(ctx);

        // 3. Cancel survey timeout (simulated)
        cancelSurveyTimeout(ctx);

        // 4. Trigger ending grace period (simulated)
        triggerEndingGracePeriod(ctx);

        log.info("SurveyCompleteAction completed: conversationId={}", conversationId);
    }

    private void saveSurveyResults(CbolStateContext ctx) {
        // In production: surveyRepository.saveResults(conversationId, answers)
        log.debug("Saving survey results: conversationId={}", ctx.conversation().conversationId());
    }

    private void calculateSatisfactionScore(CbolStateContext ctx) {
        // In production: calculate NPS/CSAT score from survey answers
        log.debug("Calculating satisfaction score: conversationId={}", ctx.conversation().conversationId());
    }

    private void cancelSurveyTimeout(CbolStateContext ctx) {
        // In production: cancel scheduled SYS_SURVEY_TIMEOUT event
        log.debug("Cancelling survey timeout: conversationId={}", ctx.conversation().conversationId());
    }

    private void triggerEndingGracePeriod(CbolStateContext ctx) {
        // In production: schedule SYS_ENDING_GRACE_TIMEOUT after endingGraceSeconds
        long graceSeconds = ctx.marketConfig() != null
                ? ctx.marketConfig().endingGraceSeconds()
                : 120;
        log.debug("Triggering ending grace period: conversationId={}, graceSeconds={}",
                ctx.conversation().conversationId(), graceSeconds);
    }
}
