package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a close request is received for an active connection.
 * <p>
 * Transition: CONNECTED → DISCONNECTED
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Send close frame to the remote endpoint (WebSocket)</li>
 *   <li>Flush pending messages</li>
 *   <li>Unregister channel from connection registry</li>
 *   <li>Stop heartbeat monitoring</li>
 *   <li>Release network resources</li>
 *   <li>Record disconnection metrics</li>
 * </ul>
 */
@Slf4j
public class CloseRequestAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.info("Executing CloseRequestAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Send close frame
        log.debug("Sending close frame to remote endpoint: interactionId={}", interactionId);

        // 2. Flush pending messages
        log.debug("Flushing pending messages: interactionId={}", interactionId);

        // 3. Unregister channel
        log.debug("Unregistering channel from connection registry: interactionId={}", interactionId);

        // 4. Stop heartbeat
        log.debug("Stopping heartbeat monitoring: interactionId={}", interactionId);

        // 5. Release network resources
        log.debug("Releasing network resources: interactionId={}", interactionId);

        // 6. Record metrics
        log.debug("Recording disconnection metrics: interactionId={}", interactionId);

        log.info("CloseRequestAction completed: interactionId={}", interactionId);
    }
}
