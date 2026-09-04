package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when an unhandled exception occurs during action execution (various states → ERROR).
 * <p>
 * This failover action handles the business logic of action failure:
 * <ul>
 *   <li>Records the failure with full context (failed action, exception, stack trace)</li>
 *   <li>Preserves the original state before failure for potential recovery</li>
 *   <li>Triggers alerting and monitoring notifications</li>
 *   <li>Sets error metadata (errorCode, errorMessage, failedAt, failedAction)</li>
 *   <li>Evaluates retry eligibility based on error type and retry count</li>
 *   <li>Prepares for either SYS_RETRY or SYS_ABORT recovery path</li>
 * </ul>
 * <p>
 * This action is triggered when a business action throws an unhandled exception.
 * The exception is caught at the service layer, which then fires the SYS_ACTION_FAILED
 * event to route the conversation to the ERROR state. This ensures that unexpected
 * failures do not leave the conversation in an inconsistent state.
 * <p>
 * Design principle: Fail fast, fail safe, and provide clear recovery paths.
 */
@Slf4j
public class SysActionFailedAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        String failedAction = ctx.failedAction() != null ? ctx.failedAction() : "UNKNOWN";
        String errorMessage = ctx.errorMessage() != null ? ctx.errorMessage() : "Unknown error";

        log.error("SysActionFailedAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, failedAction={}, error={}",
                from, event, to, conversationId, tenantId, market, failedAction, errorMessage);

        // 1. Record the failure with full context (simulated)
        recordFailure(ctx, from, failedAction, errorMessage);

        // 2. Preserve the original state before failure for potential recovery (simulated)
        preserveOriginalState(ctx, from);

        // 3. Trigger alerting and monitoring notifications (simulated)
        triggerAlerts(ctx, failedAction, errorMessage);

        // 4. Set error metadata (errorCode, errorMessage, failedAt, failedAction) (simulated)
        setErrorMetadata(ctx, failedAction, errorMessage);

        // 5. Evaluate retry eligibility based on error type and retry count (simulated)
        boolean retryEligible = evaluateRetryEligibility(ctx, errorMessage);

        // 6. Prepare for either SYS_RETRY or SYS_ABORT recovery path (simulated)
        prepareRecoveryPath(ctx, retryEligible);

        log.error("SysActionFailedAction completed: conversationId={}, retryEligible={}, conversation is now in ERROR state",
                conversationId, retryEligible);
    }

    private void recordFailure(CbolStateContext ctx, ConversationState fromState, String failedAction, String errorMessage) {
        // In production: call errorLogService.recordFailure(conversationId, fromState, failedAction, errorMessage, stackTrace)
        log.error("Recording failure: conversationId={}, fromState={}, failedAction={}, error={}",
                ctx.conversation().conversationId(), fromState, failedAction, errorMessage);
    }

    private void preserveOriginalState(CbolStateContext ctx, ConversationState originalState) {
        // In production: store original state in error context for potential rollback or retry
        log.debug("Preserving original state: conversationId={}, originalState={}",
                ctx.conversation().conversationId(), originalState);
    }

    private void triggerAlerts(CbolStateContext ctx, String failedAction, String errorMessage) {
        // In production: call alertService.triggerAlert(conversationId, failedAction, errorMessage)
        // This may include: PagerDuty, Slack notifications, email alerts, monitoring dashboards
        log.warn("Triggering alerts: conversationId={}, failedAction={}",
                ctx.conversation().conversationId(), failedAction);
    }

    private void setErrorMetadata(CbolStateContext ctx, String failedAction, String errorMessage) {
        // In production: call conversationRepository.setErrorMetadata(conversationId, errorCode, errorMessage, failedAt, failedAction)
        log.debug("Setting error metadata: conversationId={}, failedAction={}",
                ctx.conversation().conversationId(), failedAction);
    }

    private boolean evaluateRetryEligibility(CbolStateContext ctx, String errorMessage) {
        // In production: evaluate based on error type, retry count, and business rules
        // Retryable errors: network timeouts, temporary service unavailability, rate limiting
        // Non-retryable errors: validation errors, business rule violations, data integrity issues
        int retryCount = ctx.retryCount() != null ? ctx.retryCount() : 0;
        int maxRetries = ctx.marketConfig() != null ? ctx.marketConfig().maxRetries() : 3;
        boolean retryEligible = retryCount < maxRetries && isRetryableError(errorMessage);
        log.debug("Evaluating retry eligibility: conversationId={}, retryCount={}, maxRetries={}, retryEligible={}",
                ctx.conversation().conversationId(), retryCount, maxRetries, retryEligible);
        return retryEligible;
    }

    private boolean isRetryableError(String errorMessage) {
        // Simple heuristic: timeout and connection errors are typically retryable
        String lower = errorMessage.toLowerCase();
        return lower.contains("timeout") || lower.contains("connection") ||
               lower.contains("unavailable") || lower.contains("temporarily");
    }

    private void prepareRecoveryPath(CbolStateContext ctx, boolean retryEligible) {
        // In production: set recovery strategy in context for next step
        // If retryEligible: system will fire SYS_RETRY after backoff
        // If not retryEligible: system will fire SYS_ABORT to close conversation
        log.debug("Preparing recovery path: conversationId={}, retryEligible={}",
                ctx.conversation().conversationId(), retryEligible);
    }
}
