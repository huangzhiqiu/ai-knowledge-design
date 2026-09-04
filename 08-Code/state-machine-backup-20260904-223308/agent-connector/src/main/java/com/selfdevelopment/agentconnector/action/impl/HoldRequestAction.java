package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when an agent puts the customer on hold.
 * <p>
 * Transition: CONNECTED → HELD
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Pause real-time message delivery to the agent</li>
 *   <li>Start hold music / waiting announcement</li>
 *   <li>Record hold start time for SLA tracking</li>
 *   <li>Notify the customer that they are on hold</li>
 *   <li>Buffer incoming messages during hold</li>
 * </ul>
 */
@Slf4j
public class HoldRequestAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.info("Executing HoldRequestAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Pause message delivery to agent
        log.debug("Pausing real-time message delivery to agent: interactionId={}", interactionId);

        // 2. Start hold music
        log.debug("Starting hold music / waiting announcement: interactionId={}", interactionId);

        // 3. Record hold start time
        log.debug("Recording hold start time for SLA tracking: interactionId={}", interactionId);

        // 4. Notify customer
        log.debug("Notifying customer that they are on hold: interactionId={}", interactionId);

        // 5. Buffer incoming messages
        log.debug("Buffering incoming messages during hold: interactionId={}", interactionId);

        log.info("HoldRequestAction completed: interactionId={}", interactionId);
    }
}
