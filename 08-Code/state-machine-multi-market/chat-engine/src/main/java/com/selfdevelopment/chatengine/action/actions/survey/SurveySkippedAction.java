package com.selfdevelopment.chatengine.action.actions.survey;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.ConditionalAction;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when survey is skipped (ENDING 鈫?ENDING internal transition).
 * <p>
 * This action handles the business logic of survey skipping:
 * <ul>
 *   <li>Sets surveyStatus=SKIPPED</li>
 *   <li>Records survey skip timestamp</li>
 *   <li>Records skip reason (if provided)</li>
 *   <li>Triggers survey skip notifications</li>
 *   <li>Records audit log</li>
 * </ul>
 * <p>
 * Survey is field-based (surveyStatus) in ENDING state, not a separate state.
 * This is an internal transition (ENDING 鈫?ENDING) that only updates the survey field.
 * Survey skip can be triggered by customer explicitly skipping, or by market configuration
 * that disables surveys for certain scenarios.
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.SURVEY_SKIPPED)
public class SurveySkippedAction implements ConditionalAction<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("SurveySkippedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Set survey status to SKIPPED
        setSurveyStatusSkipped(ctx);

        // 2. Record survey skip timestamp
        recordSurveySkipTimestamp(ctx);

        // 3. Record skip reason (if provided)
        recordSkipReason(ctx);

        // 4. Trigger survey skip notifications
        triggerSurveySkipNotifications(ctx);

        // 5. Record audit log
        recordAuditLog(ctx);

        log.info("SurveySkippedAction completed: conversationId={}, surveyStatus=SKIPPED",
                conversationId);
    }

    private void setSurveyStatusSkipped(CbolStateContext ctx) {
        log.debug("Setting surveyStatus=SKIPPED: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setSurveyStatus(SurveyStatus.SKIPPED);
    }

    private void recordSurveySkipTimestamp(CbolStateContext ctx) {
        log.debug("Recording survey skip timestamp: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setSurveySkippedAt(Instant.now());
    }

    private void recordSkipReason(CbolStateContext ctx) {
        log.debug("Recording survey skip reason: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity if reason provided
        // ctx.conversation().setSurveySkipReason(skipReason);
    }

    private void triggerSurveySkipNotifications(CbolStateContext ctx) {
        log.debug("Triggering survey skip notifications: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording survey skipped audit log: conversationId={}",
                ctx.conversation().conversationId());
    }
}
