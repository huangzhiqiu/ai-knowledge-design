package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a channel transfer is initiated.
 * <p>
 * Transition: CONNECTED → TRANSFERRING
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Pause message processing on the current channel</li>
 *   <li>Establish connection to the target node/channel</li>
 *   <li>Serialize and transfer channel state (session, buffers)</li>
 *   <li>Start transfer timeout monitoring</li>
 *   <li>Notify upstream systems of the transfer in progress</li>
 * </ul>
 */
@Slf4j
public class TransferStartAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.info("Executing TransferStartAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Pause message processing on current channel
        log.debug("Pausing message processing on current channel: interactionId={}", interactionId);

        // 2. Establish connection to target node
        log.debug("Establishing connection to target node: interactionId={}", interactionId);

        // 3. Serialize and transfer channel state
        log.debug("Serializing and transferring channel state (session, buffers): interactionId={}", interactionId);

        // 4. Start transfer timeout monitoring
        log.debug("Starting transfer timeout monitoring: interactionId={}", interactionId);

        // 5. Notify upstream
        log.debug("Notifying upstream systems: transfer in progress, interactionId={}", interactionId);

        log.info("TransferStartAction completed: interactionId={}", interactionId);
    }
}
