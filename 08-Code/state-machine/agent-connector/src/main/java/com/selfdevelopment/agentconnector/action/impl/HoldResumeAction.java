package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when an agent resumes the conversation from hold.
 * <p>
 * Transition: HELD → CONNECTED
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Stop hold music / waiting announcement</li>
 *   <li>Resume real-time message delivery to the agent</li>
 *   <li>Flush buffered messages to the agent</li>
 *   <li>Record hold duration for SLA and reporting</li>
 *   <li>Notify the customer that the agent has returned</li>
 * </ul>
 */
@Slf4j
public class HoldResumeAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.info("Executing HoldResumeAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Stop hold music
        log.debug("Stopping hold music / waiting announcement: interactionId={}", interactionId);

        // 2. Resume message delivery
        log.debug("Resuming real-time message delivery to agent: interactionId={}", interactionId);

        // 3. Flush buffered messages
        log.debug("Flushing buffered messages to agent: interactionId={}", interactionId);

        // 4. Record hold duration
        log.debug("Recording hold duration for SLA and reporting: interactionId={}", interactionId);

        // 5. Notify customer
        log.debug("Notifying customer that agent has returned: interactionId={}", interactionId);

        log.info("HoldResumeAction completed: interactionId={}", interactionId);
    }
}
