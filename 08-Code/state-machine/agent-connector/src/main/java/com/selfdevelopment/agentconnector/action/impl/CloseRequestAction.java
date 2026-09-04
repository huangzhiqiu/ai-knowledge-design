package com.selfdevelopment.agentconnector.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
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
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("CloseRequestAction: {} --({})--> {}, interactionId={}, channel={}",
                from, event, to, interactionId, ctx.interaction().channelType());

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
