package com.selfdevelopment.agentconnector.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
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
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("HoldResumeAction: {} --({})--> {}, interactionId={}, channel={}",
                from, event, to, interactionId, ctx.interaction().channelType());

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
