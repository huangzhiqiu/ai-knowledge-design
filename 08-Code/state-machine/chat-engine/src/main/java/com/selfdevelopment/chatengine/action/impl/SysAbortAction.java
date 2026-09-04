package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a conversation is aborted after unrecoverable failure (ERROR → CLOSED).
 * <p>
 * This terminal recovery action handles the business logic of aborting a failed conversation:
 * <ul>
 *   <li>Records final abort with error context and retry history</li>
 *   <li>Notifies the customer that the conversation has been terminated due to technical issues</li>
 *   <li>Performs emergency cleanup of all resources</li>
 *   <li>Archives conversation data with error status for post-mortem analysis</li>
 *   <li>Triggers high-priority alerts for engineering review</li>
 *   <li>Updates final metadata (endReason=ABORTED, abortAt, errorCode, retryCount)</li>
 *   <li>Releases all held resources (agent, channels, sessions)</li>
 * </ul>
 * <p>
 * This action is triggered when:
 * <ul>
 *   <li>Maximum retry attempts have been exhausted</li>
 *   <li>The error is determined to be non-retryable (validation, business rule violation)</li>
 *   <li>Manual intervention requests abort</li>
 *   <li>The conversation has been in ERROR state for too long</li>
 * </ul>
 * <p>
 * Design principle: When recovery is not possible, fail gracefully with clear communication
 * and complete cleanup. Never leave resources in a leaked or inconsistent state.
 */
@Slf4j
public class SysAbortAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        String errorMessage = ctx.errorMessage() != null ? ctx.errorMessage() : "Unknown error";
        int retryCount = ctx.retryCount() != null ? ctx.retryCount() : 0;

        log.error("SysAbortAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, retryCount={}, error={}",
                from, event, to, conversationId, tenantId, market, retryCount, errorMessage);

        // 1. Record final abort with error context and retry history (simulated)
        recordFinalAbort(ctx, retryCount, errorMessage);

        // 2. Notify customer that conversation has been terminated (simulated)
        notifyCustomerAborted(ctx);

        // 3. Perform emergency cleanup of all resources (simulated)
        performEmergencyCleanup(ctx);

        // 4. Archive conversation data with error status for post-mortem analysis (simulated)
        archiveForPostMortem(ctx, retryCount, errorMessage);

        // 5. Trigger high-priority alerts for engineering review (simulated)
        triggerHighPriorityAlerts(ctx, errorMessage);

        // 6. Update final metadata (endReason=ABORTED, abortAt, errorCode, retryCount) (simulated)
        updateFinalMetadata(ctx, retryCount, errorMessage);

        // 7. Release all held resources (agent, channels, sessions) (simulated)
        releaseAllResources(ctx);

        log.error("SysAbortAction completed: conversationId={}, conversation is now CLOSED (ABORTED), retryCount={}",
                conversationId, retryCount);
    }

    private void recordFinalAbort(CbolStateContext ctx, int retryCount, String errorMessage) {
        // In production: call abortLogService.recordFinalAbort(conversationId, retryCount, errorMessage, stackTrace)
        log.error("Recording final abort: conversationId={}, retryCount={}, error={}",
                ctx.conversation().conversationId(), retryCount, errorMessage);
    }

    private void notifyCustomerAborted(CbolStateContext ctx) {
        // In production: call messageService.sendSystemMessage(conversationId, "Conversation terminated due to technical issues")
        String message = getAbortedMessage(ctx.conversation().market());
        log.debug("Notifying customer aborted: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private void performEmergencyCleanup(CbolStateContext ctx) {
        // In production: call cleanupService.performEmergencyCleanup(conversationId)
        // This includes: clearing temporary data, canceling pending operations, closing connections
        log.debug("Performing emergency cleanup: conversationId={}", ctx.conversation().conversationId());
    }

    private void archiveForPostMortem(CbolStateContext ctx, int retryCount, String errorMessage) {
        // In production: call archiveService.archiveForPostMortem(conversationId, errorContext, retryHistory)
        // This data is used for root cause analysis and system improvement
        log.debug("Archiving for post-mortem: conversationId={}, retryCount={}",
                ctx.conversation().conversationId(), retryCount);
    }

    private void triggerHighPriorityAlerts(CbolStateContext ctx, String errorMessage) {
        // In production: call alertService.triggerHighPriorityAlert(conversationId, errorMessage)
        // This may include: PagerDuty critical alert, on-call engineer notification, incident creation
        log.error("Triggering high-priority alerts: conversationId={}, error={}",
                ctx.conversation().conversationId(), errorMessage);
    }

    private void updateFinalMetadata(CbolStateContext ctx, int retryCount, String errorMessage) {
        // In production: call conversationRepository.updateFinalMetadata(conversationId, endReason, abortAt, errorCode, retryCount)
        log.debug("Updating final metadata: conversationId={}, endReason=ABORTED, retryCount={}",
                ctx.conversation().conversationId(), retryCount);
    }

    private void releaseAllResources(CbolStateContext ctx) {
        // In production: call resourceService.releaseAll(conversationId)
        // This includes: agent release, channel closure, session termination, lock release
        log.debug("Releasing all resources: conversationId={}", ctx.conversation().conversationId());
    }

    private String getAbortedMessage(String market) {
        return switch (market) {
            case "HK" -> "由於技術問題，本次對話已被終止，敬請諒解";
            case "SG" -> "This conversation has been terminated due to technical issues. We apologize for the inconvenience.";
            case "UK" -> "This conversation has been terminated due to technical issues. We apologize for the inconvenience.";
            default -> "Conversation terminated due to technical issues. We apologize.";
        };
    }
}
