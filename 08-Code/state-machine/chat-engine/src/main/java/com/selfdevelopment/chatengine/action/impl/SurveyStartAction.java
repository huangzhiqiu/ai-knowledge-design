package com.selfdevelopment.chatengine.action.impl;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a survey starts (ACTIVE → SURVEY_IN_PROGRESS).
 * <p>
 * This action handles the actual business logic of starting a post-conversation survey:
 * <ul>
 *   <li>Creates a survey record</li>
 *   <li>Sends survey invitation to customer</li>
 *   <li>Sets survey timeout for monitoring</li>
 * </ul>
 */
@Slf4j
public class SurveyStartAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(StateContext<ConversationState, ConversationFact, CbolStateContext> context) {
        CbolStateContext ctx = context.getBusinessContext();
        String conversationId = ctx.conversation().conversationId();

        log.info("Executing SurveyStartAction: conversationId={}", conversationId);

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
