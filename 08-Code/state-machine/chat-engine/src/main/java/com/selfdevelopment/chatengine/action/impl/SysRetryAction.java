package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a retry is initiated after a failure (ERROR → IN_PROGRESS).
 * <p>
 * This recovery action handles the business logic of retrying a failed operation:
 * <ul>
 *   <li>Records retry attempt with attempt number and backoff duration</li>
 *   <li>Restores conversation context from before the failure</li>
 *   <li>Re-initializes any resources that may have been cleaned up</li>
 *   <li>Resets error state and clears error metadata</li>
 *   <li>Applies exponential backoff before retry (if configured)</li>
 *   <li>Updates retry metadata (retryCount, lastRetryAt, backoffApplied)</li>
 * </ul>
 * <p>
 * This action is triggered when the system determines that a failed operation is
 * retryable and initiates a retry. The conversation transitions from ERROR back to
 * IN_PROGRESS, and the failed operation is re-attempted.
 * <p>
 * Retry strategy:
 * - Exponential backoff: delay = baseDelay * 2^attempt
 * - Maximum retry attempts: configurable per market
 * - Only retryable errors are retried (network timeouts, temporary unavailability)
 * - Non-retryable errors go directly to SYS_ABORT
 */
@Slf4j
public class SysRetryAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();
        int retryCount = ctx.retryCount() != null ? ctx.retryCount() : 0;
        int newRetryCount = retryCount + 1;
        int maxRetries = ctx.marketConfig() != null ? ctx.marketConfig().maxRetries() : 3;
        long backoffMs = calculateBackoff(newRetryCount, ctx);

        log.warn("SysRetryAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}, attempt={}/{}, backoff={}ms",
                from, event, to, conversationId, tenantId, market, newRetryCount, maxRetries, backoffMs);

        // 1. Record retry attempt with attempt number and backoff duration (simulated)
        recordRetryAttempt(ctx, newRetryCount, backoffMs);

        // 2. Restore conversation context from before the failure (simulated)
        restoreConversationContext(ctx);

        // 3. Re-initialize any resources that may have been cleaned up (simulated)
        reinitializeResources(ctx);

        // 4. Reset error state and clear error metadata (simulated)
        resetErrorState(ctx);

        // 5. Apply exponential backoff before retry (simulated - in production this would be a scheduled delay)
        applyBackoff(backoffMs);

        // 6. Update retry metadata (retryCount, lastRetryAt, backoffApplied) (simulated)
        updateRetryMetadata(ctx, newRetryCount, backoffMs);

        log.warn("SysRetryAction completed: conversationId={}, retryCount={}, transitioning back to IN_PROGRESS",
                conversationId, newRetryCount);
    }

    private void recordRetryAttempt(CbolStateContext ctx, int attempt, long backoffMs) {
        // In production: call retryLogService.recordAttempt(conversationId, attempt, backoffMs)
        log.debug("Recording retry attempt: conversationId={}, attempt={}, backoff={}ms",
                ctx.conversation().conversationId(), attempt, backoffMs);
    }

    private void restoreConversationContext(CbolStateContext ctx) {
        // In production: restore context from error snapshot taken during SysActionFailedAction
        log.debug("Restoring conversation context: conversationId={}", ctx.conversation().conversationId());
    }

    private void reinitializeResources(CbolStateContext ctx) {
        // In production: re-initialize any resources that may have been cleaned up during error handling
        log.debug("Reinitializing resources: conversationId={}", ctx.conversation().conversationId());
    }

    private void resetErrorState(CbolStateContext ctx) {
        // In production: call conversationRepository.resetErrorState(conversationId)
        log.debug("Resetting error state: conversationId={}", ctx.conversation().conversationId());
    }

    private void applyBackoff(long backoffMs) {
        // In production: this would be handled by a scheduler or delayed queue
        // For synchronous execution, we apply a sleep (with a cap to prevent long blocking)
        if (backoffMs > 0 && backoffMs <= 5000) {
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Backoff sleep interrupted", e);
            }
        }
    }

    private void updateRetryMetadata(CbolStateContext ctx, int retryCount, long backoffMs) {
        // In production: call conversationRepository.updateRetryMetadata(conversationId, retryCount, lastRetryAt, backoffApplied)
        log.debug("Updating retry metadata: conversationId={}, retryCount={}, backoff={}ms",
                ctx.conversation().conversationId(), retryCount, backoffMs);
    }

    private long calculateBackoff(int attempt, CbolStateContext ctx) {
        // Exponential backoff: delay = baseDelay * 2^(attempt-1)
        // With jitter to prevent thundering herd
        long baseDelayMs = ctx.marketConfig() != null ? ctx.marketConfig().retryBaseDelayMs() : 1000;
        long maxDelayMs = ctx.marketConfig() != null ? ctx.marketConfig().retryMaxDelayMs() : 30000;
        long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), maxDelayMs);
        // Add jitter: ±20%
        long jitter = (long) (delay * 0.2 * (Math.random() - 0.5));
        return Math.max(0, delay + jitter);
    }
}
