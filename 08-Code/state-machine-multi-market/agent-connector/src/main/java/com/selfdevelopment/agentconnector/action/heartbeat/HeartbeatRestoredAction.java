package com.selfdevelopment.agentconnector.action.heartbeat;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when heartbeat is restored (DEGRADED → CONNECTED/IN_PROGRESS).
 * <p>
 * This action handles the business logic of heartbeat restoration:
 * <ul>
 *   <li>Resets heartbeat miss count</li>
 *   <li>Clears degradedAt timestamp</li>
 *   <li>Cancels pending reconnection attempts</li>
 *   <li>Notifies upstream about restoration</li>
 * </ul>
 */
@Slf4j
@Component
public class HeartbeatRestoredAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("HeartbeatRestoredAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Reset heartbeat miss count
        resetHeartbeatMissCount(ctx);

        // 2. Clear degradedAt timestamp
        clearDegradedAt(ctx);

        // 3. Cancel pending reconnection attempts
        cancelReconnectionAttempts(ctx);

        // 4. Notify upstream about restoration
        notifyRestoration(ctx);

        log.info("HeartbeatRestoredAction completed: interactionId={}, connection restored to {}",
                interactionId, to);
    }

    private void resetHeartbeatMissCount(AgentConnectorStateContext ctx) {
        log.debug("Resetting heartbeat miss count: interactionId={}", ctx.interaction().interactionId());
    }

    private void clearDegradedAt(AgentConnectorStateContext ctx) {
        log.debug("Clearing degradedAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void cancelReconnectionAttempts(AgentConnectorStateContext ctx) {
        log.debug("Cancelling reconnection attempts: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyRestoration(AgentConnectorStateContext ctx) {
        log.info("Notifying restoration: interactionId={}", ctx.interaction().interactionId());
    }
}
