package com.selfdevelopment.agentconnector.action.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when a cross-channel transfer fails (IN_PROGRESS → IN_PROGRESS internal).
 * <p>
 * This action handles the business logic of transfer failure:
 * <ul>
 *   <li>Records transfer failure reason and error details</li>
 *   <li>Clears transfer in-progress flag</li>
 *   <li>Notifies current agent that transfer was rejected</li>
 *   <li>Offers retry options for transfer</li>
 *   <li>Records failure for analytics and improvement</li>
 *   <li>Updates transfer statistics</li>
 * </ul>
 * <p>
 * This is an internal transition (IN_PROGRESS → IN_PROGRESS) - when transfer is rejected,
 * the interaction stays in IN_PROGRESS with the current agent. This is different from
 * Conversation-level transfer failure which returns to INITIATED.
 * <p>
 * Distinction from TRANSFER_SUCCESS:
 * - TRANSFER_SUCCESS: IN_PROGRESS → TRANSFERRED (state change, source detached)
 * - TRANSFER_FAILED: IN_PROGRESS → IN_PROGRESS (internal, transfer rejected, stay)
 */
@Slf4j
@Component
public class TransferFailedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("TransferFailedAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Record transfer failure reason and error details
        recordFailureDetails(ctx);

        // 2. Clear transfer in-progress flag
        clearTransferInProgress(ctx);

        // 3. Notify current agent that transfer was rejected
        notifyAgentTransferRejected(ctx);

        // 4. Offer retry options for transfer
        offerTransferRetry(ctx);

        // 5. Record failure for analytics and improvement
        recordFailureForAnalytics(ctx);

        // 6. Update transfer statistics
        updateTransferStatistics(ctx);

        log.info("TransferFailedAction completed: interactionId={}, transfer rejected, staying in IN_PROGRESS",
                interactionId);
    }

    private void recordFailureDetails(AgentConnectorStateContext ctx) {
        log.debug("Recording transfer failure details: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would record failure details
        // ctx.interaction().setTransferFailedAt(Instant.now());
        // ctx.interaction().setTransferFailureReason(failureReason);
        // ctx.interaction().setTransferErrorDetails(errorDetails);
    }

    private void clearTransferInProgress(AgentConnectorStateContext ctx) {
        log.debug("Clearing transfer in-progress flag: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update the interaction entity
        // ctx.interaction().setTransferInProgress(false);
    }

    private void notifyAgentTransferRejected(AgentConnectorStateContext ctx) {
        log.debug("Notifying agent that transfer was rejected: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would notify the agent
        // agentNotifier.notifyTransferRejected(ctx.interaction().getAgentId(), failureReason);
    }

    private void offerTransferRetry(AgentConnectorStateContext ctx) {
        log.debug("Offering transfer retry options: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would offer retry options
        // transferService.offerRetry(ctx.interaction());
    }

    private void recordFailureForAnalytics(AgentConnectorStateContext ctx) {
        log.debug("Recording transfer failure for analytics: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would record analytics
        // analyticsService.recordTransferFailure(ctx.interaction());
    }

    private void updateTransferStatistics(AgentConnectorStateContext ctx) {
        log.debug("Updating transfer statistics: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update statistics
        // ctx.interaction().incrementTransferFailureCount();
    }
}
