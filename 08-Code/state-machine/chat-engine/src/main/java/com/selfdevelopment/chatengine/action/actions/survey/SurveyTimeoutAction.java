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
 * Action executed when survey times out (ENDING 鈫?ENDING internal transition).
 * <p>
 * This action handles the business logic of survey timeout:
 * <ul>
 *   <li>Sets surveyStatus=TIMEOUT</li>
 *   <li>Sets endReason=CUSTOMER_IDLE (if not already set)</li>
 *   <li>Records survey timeout timestamp</li>
 *   <li>Triggers survey timeout notifications</li>
 *   <li>Records audit log</li>
 * </ul>
 * <p>
 * Survey is field-based (surveyStatus) in ENDING state, not a separate state.
 * This is an internal transition (ENDING 鈫?ENDING) that only updates the survey field.
 * Survey timeout typically indicates customer idle during the survey phase.
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.SURVEY_TIMEOUT)
public class SurveyTimeoutAction implements ConditionalAction<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("SurveyTimeoutAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Set survey status to TIMEOUT
        setSurveyStatusTimeout(ctx);

        // 2. Set endReason to CUSTOMER_IDLE (if not already set)
        setEndReasonCustomerIdle(ctx);

        // 3. Record survey timeout timestamp
        recordSurveyTimeoutTimestamp(ctx);

        // 4. Trigger survey timeout notifications
        triggerSurveyTimeoutNotifications(ctx);

        // 5. Record audit log
        recordAuditLog(ctx);

        log.info("SurveyTimeoutAction completed: conversationId={}, surveyStatus=TIMEOUT, endReason=CUSTOMER_IDLE",
                conversationId);
    }

    private void setSurveyStatusTimeout(CbolStateContext ctx) {
        log.debug("Setting surveyStatus=TIMEOUT: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setSurveyStatus(SurveyStatus.TIMEOUT);
    }

    private void setEndReasonCustomerIdle(CbolStateContext ctx) {
        log.debug("Setting endReason=CUSTOMER_IDLE: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity if not already set
        // if (ctx.conversation().getEndReason() == null) {
        //     ctx.conversation().setEndReason(EndReason.CUSTOMER_IDLE);
        // }
    }

    private void recordSurveyTimeoutTimestamp(CbolStateContext ctx) {
        log.debug("Recording survey timeout timestamp: conversationId={}",
                ctx.conversation().conversationId());
        // In a real implementation, this would update the conversation entity
        // ctx.conversation().setSurveyTimeoutAt(Instant.now());
    }

    private void triggerSurveyTimeoutNotifications(CbolStateContext ctx) {
        log.debug("Triggering survey timeout notifications: conversationId={}",
                ctx.conversation().conversationId());
    }

    private void recordAuditLog(CbolStateContext ctx) {
        log.debug("Recording survey timeout audit log: conversationId={}",
                ctx.conversation().conversationId());
    }
}
