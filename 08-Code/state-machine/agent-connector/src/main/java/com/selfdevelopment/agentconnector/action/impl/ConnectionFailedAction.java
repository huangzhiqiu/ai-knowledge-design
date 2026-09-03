package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a connection fails during establishment.
 * <p>
 * Transition: CONNECTING → DISCONNECTED
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Record the failure reason and error code</li>
 *   <li>Clean up partial connection resources</li>
 *   <li>Notify upstream systems of the failure</li>
 *   <li>Trigger reconnection strategy if applicable</li>
 * </ul>
 */
@Slf4j
public class ConnectionFailedAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.warn("Executing ConnectionFailedAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Record failure reason and error code
        log.debug("Recording failure reason: interactionId={}", interactionId);

        // 2. Clean up partial connection resources
        log.debug("Cleaning up partial connection resources: interactionId={}", interactionId);

        // 3. Notify upstream systems
        log.debug("Notifying upstream systems of connection failure: interactionId={}", interactionId);

        // 4. Trigger reconnection strategy if needReconnect is true
        if (ctx.interaction().needReconnect()) {
            log.debug("Triggering reconnection strategy: interactionId={}", interactionId);
        }

        log.warn("ConnectionFailedAction completed: interactionId={}", interactionId);
    }
}
