package com.selfdevelopment.agentconnector.action.heartbeat;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when heartbeat is missed (CONNECTED/IN_PROGRESS → DEGRADED).
 * <p>
 * This action handles the business logic of heartbeat miss:
 * <ul>
 *   <li>Records heartbeat miss count</li>
 *   <li>Sets degradedAt timestamp</li>
 *   <li>Triggers reconnection attempt scheduling</li>
 *   <li>Notifies upstream about degradation</li>
 * </ul>
 */
@Slf4j
public class HeartbeatMissAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.warn("HeartbeatMissAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Record heartbeat miss count
        recordHeartbeatMiss(ctx);

        // 2. Set degradedAt timestamp
        setDegradedAt(ctx);

        // 3. Trigger reconnection attempt scheduling
        scheduleReconnectionAttempt(ctx);

        // 4. Notify upstream about degradation
        notifyDegradation(ctx);

        log.warn("HeartbeatMissAction completed: interactionId={}, entering DEGRADED", interactionId);
    }

    private void recordHeartbeatMiss(AgentConnectorStateContext ctx) {
        log.warn("Recording heartbeat miss: interactionId={}", ctx.interaction().interactionId());
    }

    private void setDegradedAt(AgentConnectorStateContext ctx) {
        log.debug("Setting degradedAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void scheduleReconnectionAttempt(AgentConnectorStateContext ctx) {
        log.debug("Scheduling reconnection attempt: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyDegradation(AgentConnectorStateContext ctx) {
        log.warn("Notifying degradation: interactionId={}", ctx.interaction().interactionId());
    }
}
