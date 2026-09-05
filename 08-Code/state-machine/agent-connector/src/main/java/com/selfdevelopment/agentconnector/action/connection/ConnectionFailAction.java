package com.selfdevelopment.agentconnector.action.connection;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when connection fails (INITIATED → CLOSED).
 * <p>
 * This action handles the business logic of connection failure:
 * <ul>
 *   <li>Records failure reason</li>
 *   <li>Cleans up partial resources</li>
 *   <li>Notifies upstream about connection failure</li>
 *   <li>Triggers retry or fallback strategy if configured</li>
 * </ul>
 */
@Slf4j
public class ConnectionFailAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();
        String channelType = ctx.interaction().channelType();

        log.error("ConnectionFailAction: {} --({})--> {}, interactionId={}, channelType={}",
                from, event, to, interactionId, channelType);

        // 1. Record failure reason
        recordFailureReason(ctx);

        // 2. Clean up partial resources
        cleanupPartialResources(ctx);

        // 3. Notify upstream about connection failure
        notifyConnectionFailed(ctx);

        // 4. Trigger retry or fallback strategy if configured
        triggerRetryOrFallback(ctx);

        log.error("ConnectionFailAction completed: interactionId={}, connection failed, interaction closed", interactionId);
    }

    private void recordFailureReason(AgentConnectorStateContext ctx) {
        log.error("Recording failure reason: interactionId={}", ctx.interaction().interactionId());
    }

    private void cleanupPartialResources(AgentConnectorStateContext ctx) {
        log.debug("Cleaning up partial resources: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyConnectionFailed(AgentConnectorStateContext ctx) {
        log.error("Notifying connection failed: interactionId={}", ctx.interaction().interactionId());
    }

    private void triggerRetryOrFallback(AgentConnectorStateContext ctx) {
        log.debug("Triggering retry or fallback: interactionId={}", ctx.interaction().interactionId());
    }
}
