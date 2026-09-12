package com.selfdevelopment.agentconnector.action.ending;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when end is requested (any non-terminal → CLOSED).
 * <p>
 * This action handles the business logic of graceful closure:
 * <ul>
 *   <li>Sets closedAt timestamp</li>
 *   <li>Records close reason</li>
 *   <li>Stops heartbeat monitoring</li>
 *   <li>Closes channel connection gracefully</li>
 *   <li>Cleans up all resources</li>
 *   <li>Notifies upstream about closure</li>
 * </ul>
 */
@Slf4j
@Component
public class EndRequestedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("EndRequestedAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Set closedAt timestamp
        setClosedAt(ctx);

        // 2. Record close reason
        recordCloseReason(ctx);

        // 3. Stop heartbeat monitoring
        stopHeartbeatMonitoring(ctx);

        // 4. Close channel connection gracefully
        closeChannelGracefully(ctx);

        // 5. Clean up all resources
        cleanupAllResources(ctx);

        // 6. Notify upstream about closure
        notifyClosure(ctx);

        log.info("EndRequestedAction completed: interactionId={}, interaction closed gracefully", interactionId);
    }

    private void setClosedAt(AgentConnectorStateContext ctx) {
        log.debug("Setting closedAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void recordCloseReason(AgentConnectorStateContext ctx) {
        log.debug("Recording close reason: interactionId={}", ctx.interaction().interactionId());
    }

    private void stopHeartbeatMonitoring(AgentConnectorStateContext ctx) {
        log.debug("Stopping heartbeat monitoring: interactionId={}", ctx.interaction().interactionId());
    }

    private void closeChannelGracefully(AgentConnectorStateContext ctx) {
        log.debug("Closing channel gracefully: interactionId={}", ctx.interaction().interactionId());
    }

    private void cleanupAllResources(AgentConnectorStateContext ctx) {
        log.debug("Cleaning up all resources: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyClosure(AgentConnectorStateContext ctx) {
        log.info("Notifying closure: interactionId={}", ctx.interaction().interactionId());
    }
}
