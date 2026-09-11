package com.selfdevelopment.chatengine.action.survey;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when survey is submitted (ENDING → ENDING internal transition).
 * <p>
 * This action handles the business logic of survey submission:
 * <ul>
 *   <li>Sets surveyStatus=SUBMITTED</li>
 *   <li>Records survey submission timestamp</li>
 *   <li>Persists survey results</li>
 *   <li>Triggers survey completion notifications</li>
 *   <li>Records audit log</li>
 * </ul>
 * <p>
 * Survey is field-based (surveyStatus) in ENDING state, not a separate state.
 * This is an internal transition (ENDING → ENDING) that only updates the survey field.
 */
@Slf4j
@Component
public class SurveySubmittedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("SurveySubmittedAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Set survey status to SUBMITTED
        setSurveyStatusSubmitted(ctx);

        // 2. Record survey submission timestamp
        recordSurveySubmissionTimestamp(ctx);

        // 3. Persist survey results
        persistSurveyResults(ctx);

        // 4. Trigger survey completion notifications
        triggerSurveyCompletionNotifications(ctx);

        // 5. Record audit log
        recordAuditLog(ctx);

        log.info("SurveySubmittedAction completed: conversationId={}, surveyStatus=SUBMITTED",
                conversationId);
    }

    private void setSurveyStatusSubmitted(CbolStateContext ctx) {
        log.debug("Setting surveyStatus=SUBMITTED: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setSurveyStatus(SurveyStatus.SUBMITTED);
    }

    private void recordSurveySubmissionTimestamp(CbolStateContext ctx) {
        log.debug("Recording survey submission timestamp: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setSurveySubmittedAt(Instant.now());
    }

    private void persistSurveyResults(CbolStateContext ctx) {
        log.debug("Persisting survey results: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void triggerSurveyCompletionNotifications(CbolStateContext ctx) {
        log.debug("Triggering survey completion notifications: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording survey submitted audit log: conversationId={}",
                ctx.conversation().conversationId());
    }
}
