package com.selfdevelopment.chatengine.action.survey;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a survey starts (IN_PROGRESS 鈫?IN_PROGRESS, internal transition).
 * <p>
 * The survey is NOT a separate state 鈥?it is a sub-phase within IN_PROGRESS.
 * This action handles the actual business logic of starting a post-conversation survey:
 * <ul>
 *   <li>Creates a survey record</li>
 *   <li>Sends survey invitation to customer</li>
 *   <li>Sets survey timeout for monitoring</li>
 * </ul>
 * <p>
 * After this action completes, the conversation remains in IN_PROGRESS state,
 * but enters the survey sub-phase. When the survey completes (SURVEY_COMPLETE)
 * or times out (SYS_SURVEY_TIMEOUT), the conversation transitions to ENDING.
 */
@Slf4j
public class SurveyStartAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();

        log.info("SurveyStartAction: {} --({})--> {}, conversationId={}",
                from, event, to, conversationId);

        // 1. Create a survey record (simulated)
        createSurveyRecord(ctx);

        // 2. Send survey invitation to customer (simulated)
        sendSurveyInvitation(ctx);

        // 3. Set survey timeout for monitoring (simulated)
        setSurveyTimeout(ctx);

        log.info("SurveyStartAction completed: conversationId={}", conversationId);
    }

    private void createSurveyRecord(CbolStateContext ctx) {
        // In production: surveyRepository.create(conversationId, tenantId)
        log.debug("Creating survey record: conversationId={}", ctx.conversation().conversationId());
    }

    private void sendSurveyInvitation(CbolStateContext ctx) {
        // In production: messageService.sendSurveyInvitation(tenantId, surveyUrl)
        log.debug("Sending survey invitation: tenantId={}", ctx.conversation().tenantId());
    }

    private void setSurveyTimeout(CbolStateContext ctx) {
        // In production: schedule SYS_SURVEY_TIMEOUT event after surveyTimeoutSeconds
        long timeoutSeconds = ctx.marketConfig() != null
                ? ctx.marketConfig().customerIdleSeconds()
                : 300;
        log.debug("Setting survey timeout: conversationId={}, timeoutSeconds={}",
                ctx.conversation().conversationId(), timeoutSeconds);
    }
}
