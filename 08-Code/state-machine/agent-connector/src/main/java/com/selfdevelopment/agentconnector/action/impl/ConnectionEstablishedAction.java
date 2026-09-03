package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a connection is established.
 * <p>
 * Transition: CONNECTING → CONNECTED
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Register the channel in the connection registry</li>
 *   <li>Start heartbeat monitoring</li>
 *   <li>Notify upstream systems that the channel is ready</li>
 *   <li>Record connection metrics (latency, handshake time)</li>
 * </ul>
 */
@Slf4j
public class ConnectionEstablishedAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.info("Executing ConnectionEstablishedAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Register channel in connection registry
        log.debug("Registering channel in connection registry: interactionId={}", interactionId);

        // 2. Start heartbeat monitoring
        log.debug("Starting heartbeat monitoring: interactionId={}", interactionId);

        // 3. Notify upstream systems
        log.debug("Notifying upstream systems: channel ready, interactionId={}", interactionId);

        // 4. Record connection metrics
        log.debug("Recording connection metrics: interactionId={}", interactionId);

        log.info("ConnectionEstablishedAction completed: interactionId={}", interactionId);
    }
}
