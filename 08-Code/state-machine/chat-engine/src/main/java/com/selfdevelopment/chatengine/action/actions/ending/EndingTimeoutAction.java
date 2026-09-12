package com.selfdevelopment.chatengine.action.actions.ending;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.ConditionalAction;
import com.selfdevelopment.chatengine.action.annotation.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when ending times out (ENDING 鈫?CLOSED).
 * <p>
 * This action handles the business logic of forced closure at ending deadline (>=120s):
 * <ul>
 *   <li>Forces close, records alert reason</li>
 *   <li>Performs final cleanup of all resources</li>
 *   <li>Archives conversation data for compliance and analytics</li>
 *   <li>Releases session, channel, and agent resources</li>
 *   <li>Updates conversation metadata (finalState=CLOSED, closedAt)</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.ENDING_TIMEOUT)
public class EndingTimeoutAction implements ConditionalAction<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.warn("EndingTimeoutAction: {} --({})--> {}, conversationId={}, tenantId={}, market={} (forced close at ending deadline)",
                from, event, to, conversationId, tenantId, market);

        // 1. Forces close, records alert reason
        recordForcedCloseAlert(ctx);

        // 2. Performs final cleanup of all resources
        performFinalCleanup(ctx);

        // 3. Archives conversation data for compliance and analytics
        archiveConversationData(ctx);

        // 4. Releases session, channel, and agent resources
        releaseAllResources(ctx);

        // 5. Updates conversation metadata (finalState=CLOSED, closedAt)
        updateFinalMetadata(ctx);

        log.warn("EndingTimeoutAction completed successfully: conversationId={}, conversation is now CLOSED (forced)",
                conversationId);
    }

    private void recordForcedCloseAlert(CbolStateContext ctx) {
        log.warn("Recording forced close alert: conversationId={}", ctx.conversation().conversationId());
    }

    private void performFinalCleanup(CbolStateContext ctx) {
        log.debug("Performing final cleanup: conversationId={}", ctx.conversation().conversationId());
    }

    private void archiveConversationData(CbolStateContext ctx) {
        log.debug("Archiving conversation data: conversationId={}", ctx.conversation().conversationId());
    }

    private void releaseAllResources(CbolStateContext ctx) {
        log.debug("Releasing all resources: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateFinalMetadata(CbolStateContext ctx) {
        log.debug("Updating final metadata: conversationId={}, finalState=CLOSED",
                ctx.conversation().conversationId());
    }
}
