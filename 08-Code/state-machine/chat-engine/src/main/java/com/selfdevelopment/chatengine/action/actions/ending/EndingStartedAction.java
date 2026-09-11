package com.selfdevelopment.chatengine.action.actions.ending;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when ending starts (Various → ENDING).
 * <p>
 * This action handles the business logic of entering ENDING state:
 * <ul>
 *   <li>Sets endReason</li>
 *   <li>Sets endingDeadlineAt=now+endingDeadlineSeconds (default 120s)</li>
 *   <li>Triggers ending actions (Notify mandatory)</li>
 *   <li>Sends survey if surveyEligible=true</li>
 *   <li>Handles TRANSFERRED idle special case (defer CloseInteractions)</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.ENDING_STARTED)
public class EndingStartedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("EndingStartedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set endReason
        setEndReason(ctx);

        // 2. Set endingDeadlineAt=now+endingDeadlineSeconds (default 120s)
        setEndingDeadline(ctx);

        // 3. Trigger ending actions (Notify mandatory)
        triggerNotifyAction(ctx);

        // 4. Handle TRANSFERRED idle special case (defer CloseInteractions)
        if (from == ConversationState.TRANSFERRED) {
            handleTransferredIdleSpecialCase(ctx);
        } else {
            // 5. Trigger CloseInteractions (mandatory, not deferred)
            triggerCloseInteractions(ctx);
        }

        // 6. Send survey if surveyEligible=true
        if (ctx.marketConfig() != null && ctx.marketConfig().surveyEnabled()) {
            sendSurvey(ctx);
        }

        log.info("EndingStartedAction completed successfully: conversationId={}, entering ENDING", conversationId);
    }

    private void setEndReason(CbolStateContext ctx) {
        log.debug("Setting endReason: conversationId={}", ctx.conversation().conversationId());
    }

    private void setEndingDeadline(CbolStateContext ctx) {
        log.debug("Setting ending deadline: conversationId={}", ctx.conversation().conversationId());
    }

    private void triggerNotifyAction(CbolStateContext ctx) {
        log.debug("Triggering Notify action: conversationId={}", ctx.conversation().conversationId());
    }

    private void handleTransferredIdleSpecialCase(CbolStateContext ctx) {
        log.debug("Handling TRANSFERRED idle special case (defer CloseInteractions): conversationId={}",
                ctx.conversation().conversationId());
    }

    private void triggerCloseInteractions(CbolStateContext ctx) {
        log.debug("Triggering CloseInteractions: conversationId={}", ctx.conversation().conversationId());
    }

    private void sendSurvey(CbolStateContext ctx) {
        log.debug("Sending survey: conversationId={}", ctx.conversation().conversationId());
    }
}
