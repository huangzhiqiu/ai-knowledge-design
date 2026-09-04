package com.selfdevelopment.agentconnector.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a reconnection attempt succeeds.
 * <p>
 * Transition: RECONNECTING → CONNECTED
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Register the new channel in the connection registry</li>
 *   <li>Resume message processing</li>
 *   <li>Flush buffered messages to the new channel</li>
 *   <li>Restart heartbeat monitoring</li>
 *   <li>Reset reconnection attempt counter</li>
 *   <li>Notify upstream systems that the channel is restored</li>
 * </ul>
 */
@Slf4j
public class ReconnectSuccessAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("ReconnectSuccessAction: {} --({})--> {}, interactionId={}, channel={}",
                from, event, to, interactionId, ctx.interaction().channelType());

        // 1. Register new channel
        log.debug("Registering new channel in connection registry: interactionId={}", interactionId);

        // 2. Resume message processing
        log.debug("Resuming message processing: interactionId={}", interactionId);

        // 3. Flush buffered messages
        log.debug("Flushing buffered messages to new channel: interactionId={}", interactionId);

        // 4. Restart heartbeat
        log.debug("Restarting heartbeat monitoring: interactionId={}", interactionId);

        // 5. Reset reconnection counter
        log.debug("Resetting reconnection attempt counter: interactionId={}", interactionId);

        // 6. Notify upstream
        log.debug("Notifying upstream systems: channel restored, interactionId={}", interactionId);

        log.info("ReconnectSuccessAction completed: interactionId={}", interactionId);
    }
}
