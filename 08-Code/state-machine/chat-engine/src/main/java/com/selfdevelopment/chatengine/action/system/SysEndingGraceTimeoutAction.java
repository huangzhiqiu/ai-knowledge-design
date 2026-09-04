package com.selfdevelopment.chatengine.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when ending grace period timeout is detected (ENDING 鈫?CLOSED).
 * <p>
 * This system-driven action handles the final closure of the conversation:
 * <ul>
 *   <li>Records final closure in the conversation history</li>
 *   <li>Performs final cleanup of all conversation resources</li>
 *   <li>Archives conversation data for compliance and analytics</li>
 *   <li>Releases session, channel, and agent resources</li>
 *   <li>Updates conversation metadata (finalState=CLOSED, closedAt)</li>
 *   <li>Triggers post-closure workflows (surveys, reports, notifications)</li>
 * </ul>
 * <p>
 * This action is triggered by the EndingGraceMonitor when the conversation has been
 * in the ENDING state for longer than the configured endingGraceSeconds threshold.
 * The ENDING state provides a grace period for any final messages or cleanup before
 * the conversation is permanently closed.
 */
@Slf4j
public class SysEndingGraceTimeoutAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        long gracePeriod = ctx.marketConfig() != null ? ctx.marketConfig().endingGraceSeconds() : 30;

        log.info("SysEndingGraceTimeoutAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, gracePeriod={}s",
                from, event, to, conversationId, tenantId, market, gracePeriod);

        // 1. Record final closure in conversation history (simulated)
        recordFinalClosure(ctx, gracePeriod);

        // 2. Perform final cleanup of all conversation resources (simulated)
        performFinalCleanup(ctx);

        // 3. Archive conversation data for compliance and analytics (simulated)
        archiveConversationData(ctx);

        // 4. Release session, channel, and agent resources (simulated)
        releaseAllResources(ctx);

        // 5. Update conversation metadata (finalState=CLOSED, closedAt) (simulated)
        updateConversationMetadata(ctx);

        // 6. Trigger post-closure workflows (surveys, reports, notifications) (simulated)
        triggerPostClosureWorkflows(ctx);

        log.info("SysEndingGraceTimeoutAction completed successfully: conversationId={}, conversation is now CLOSED",
                conversationId);
    }

    private void recordFinalClosure(CbolStateContext ctx, long gracePeriod) {
        // In production: call conversationHistoryService.recordFinalClosure(conversationId, gracePeriod)
        log.debug("Recording final closure: conversationId={}, gracePeriod={}s",
                ctx.conversation().conversationId(), gracePeriod);
    }

    private void performFinalCleanup(CbolStateContext ctx) {
        // In production: call cleanupService.performFinalCleanup(conversationId)
        log.debug("Performing final cleanup: conversationId={}", ctx.conversation().conversationId());
    }

    private void archiveConversationData(CbolStateContext ctx) {
        // In production: call archiveService.archiveConversation(conversationId)
        log.debug("Archiving conversation data: conversationId={}", ctx.conversation().conversationId());
    }

    private void releaseAllResources(CbolStateContext ctx) {
        // In production: call resourceService.releaseAll(conversationId)
        log.debug("Releasing all resources: conversationId={}", ctx.conversation().conversationId());
    }

    private void updateConversationMetadata(CbolStateContext ctx) {
        // In production: call conversationRepository.updateMetadata(conversationId, finalState, closedAt)
        log.debug("Updating conversation metadata: conversationId={}, finalState=CLOSED",
                ctx.conversation().conversationId());
    }

    private void triggerPostClosureWorkflows(CbolStateContext ctx) {
        // In production: call workflowService.triggerPostClosureWorkflows(conversationId)
        // This may include: satisfaction surveys, quality assurance reviews, analytics reporting
        log.debug("Triggering post-closure workflows: conversationId={}", ctx.conversation().conversationId());
    }
}
