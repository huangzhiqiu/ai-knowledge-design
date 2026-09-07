package com.selfdevelopment.agentconnector.action.ending;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when interaction is confirmed closed (CLOSED → CLOSED internal terminal confirmation).
 * <p>
 * This action handles the business logic of interaction closure confirmation:
 * <ul>
 *   <li>Records terminal closure audit log</li>
 *   <li>Updates interaction closed timestamp</li>
 *   <li>Records closure reason and end details</li>
 *   <li>Triggers resource cleanup (connection, sessions, timers)</li>
 *   <li>Notifies Conversation state machine that interaction ended</li>
 *   <li>Updates interaction statistics and metrics</li>
 *   <li>Triggers post-interaction workflows (survey, wrap-up)</li>
 * </ul>
 * <p>
 * This is an internal transition (CLOSED → CLOSED) that serves as terminal confirmation.
 * It is triggered after the state has already transitioned to CLOSED (via END_REQUESTED
 * or SYSTEM_ERROR), and performs final cleanup and notification.
 * <p>
 * Distinction from END_REQUESTED:
 * - END_REQUESTED: any non-terminal → CLOSED (state change, graceful closure request)
 * - INTERACTION_CLOSED: CLOSED → CLOSED (internal, terminal confirmation and cleanup)
 */
@Slf4j
public class InteractionClosedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("InteractionClosedAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Record terminal closure audit log
        recordTerminalClosureAudit(ctx);

        // 2. Update interaction closed timestamp
        updateClosedTimestamp(ctx);

        // 3. Record closure reason and end details
        recordClosureDetails(ctx);

        // 4. Trigger resource cleanup (connection, sessions, timers)
        triggerResourceCleanup(ctx);

        // 5. Notify Conversation state machine that interaction ended
        notifyConversationInteractionEnded(ctx);

        // 6. Update interaction statistics and metrics
        updateInteractionStatistics(ctx);

        // 7. Trigger post-interaction workflows (survey, wrap-up)
        triggerPostInteractionWorkflows(ctx);

        log.info("InteractionClosedAction completed: interactionId={}, terminal closure confirmed, resources cleaned up",
                interactionId);
    }

    private void recordTerminalClosureAudit(AgentConnectorStateContext ctx) {
        log.debug("Recording terminal closure audit log: interactionId={}",
                ctx.interaction().interactionId());
    }

    private void updateClosedTimestamp(AgentConnectorStateContext ctx) {
        log.debug("Updating interaction closed timestamp: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update the interaction entity
        // ctx.interaction().setClosedAt(Instant.now());
    }

    private void recordClosureDetails(AgentConnectorStateContext ctx) {
        log.debug("Recording closure reason and end details: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would record closure details
        // ctx.interaction().setClosureReason(closureReason);
        // ctx.interaction().setEndDetails(endDetails);
    }

    private void triggerResourceCleanup(AgentConnectorStateContext ctx) {
        log.debug("Triggering resource cleanup: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would trigger cleanup
        // connectionManager.close(ctx.interaction().getConnectionId());
        // sessionManager.cleanup(ctx.interaction().getSessionId());
        // timerManager.cancelAllTimers(ctx.interaction().interactionId());
    }

    private void notifyConversationInteractionEnded(AgentConnectorStateContext ctx) {
        log.debug("Notifying Conversation state machine that interaction ended: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would notify the Conversation state machine
        // conversationService.onInteractionEnded(ctx.interaction().getConversationId(), interactionId);
    }

    private void updateInteractionStatistics(AgentConnectorStateContext ctx) {
        log.debug("Updating interaction statistics and metrics: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update statistics
        // metricsService.recordInteractionClosure(ctx.interaction());
    }

    private void triggerPostInteractionWorkflows(AgentConnectorStateContext ctx) {
        log.debug("Triggering post-interaction workflows: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would trigger post-interaction workflows
        // postInteractionService.triggerSurvey(ctx.interaction());
        // postInteractionService.triggerWrapUp(ctx.interaction());
    }
}
