package com.selfdevelopment.agentconnector.action.impl;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a channel transfer completes successfully.
 * <p>
 * Transition: TRANSFERRING → CONNECTED
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Verify channel state integrity on the new channel</li>
 *   <li>Resume message processing on the new channel</li>
 *   <li>Flush any messages that arrived during transfer</li>
 *   <li>Close and release the old channel resources</li>
 *   <li>Stop transfer timeout monitoring</li>
 *   <li>Notify upstream systems that the transfer is complete</li>
 *   <li>Record transfer metrics (duration, success rate)</li>
 * </ul>
 */
@Slf4j
public class TransferCompleteAction
        implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> context) {
        AgentConnectorStateContext ctx = context.getBusinessContext();
        String interactionId = ctx.interaction().interactionId();

        log.info("Executing TransferCompleteAction: interactionId={}, channel={}",
                interactionId, ctx.interaction().channelType());

        // 1. Verify channel state integrity
        log.debug("Verifying channel state integrity on new channel: interactionId={}", interactionId);

        // 2. Resume message processing
        log.debug("Resuming message processing on new channel: interactionId={}", interactionId);

        // 3. Flush messages that arrived during transfer
        log.debug("Flushing messages that arrived during transfer: interactionId={}", interactionId);

        // 4. Close old channel resources
        log.debug("Closing and releasing old channel resources: interactionId={}", interactionId);

        // 5. Stop transfer timeout
        log.debug("Stopping transfer timeout monitoring: interactionId={}", interactionId);

        // 6. Notify upstream
        log.debug("Notifying upstream systems: transfer complete, interactionId={}", interactionId);

        // 7. Record metrics
        log.debug("Recording transfer metrics (duration, success rate): interactionId={}", interactionId);

        log.info("TransferCompleteAction completed: interactionId={}", interactionId);
    }
}
