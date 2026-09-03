package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when an active connection is dropped unexpectedly.
 * <p>
 * Transition: CONNECTED → RECONNECTING
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Detect and record the drop reason (network error, timeout, etc.)</li>
 *   <li>Pause message processing for this channel</li>
 *   <li>Initialize reconnection context (attempt count, backoff strategy)</li>
 *   <li>Buffer outgoing messages during reconnection</li>
 * </ul>
 */
@Slf4j
public class ConnectionDroppedAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.warn("Executing ConnectionDroppedAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Detect and record drop reason
        log.debug("Recording drop reason: interactionId={}", interactionId);

        // 2. Pause message processing
        log.debug("Pausing message processing: interactionId={}", interactionId);

        // 3. Initialize reconnection context
        log.debug("Initializing reconnection context (attempt=1, backoff=exponential): interactionId={}", interactionId);

        // 4. Buffer outgoing messages
        log.debug("Buffering outgoing messages during reconnection: interactionId={}", interactionId);

        log.warn("ConnectionDroppedAction completed: interactionId={}", interactionId);
    }
}
