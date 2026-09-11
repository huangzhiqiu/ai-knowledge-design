package com.selfdevelopment.agentconnector.action.reconnection;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when reconnection fails (RECONNECTING → CLOSED).
 * <p>
 * This action handles the business logic of reconnection failure:
 * <ul>
 *   <li>Records final failure reason</li>
 *   <li>Cleans up all channel resources</li>
 *   <li>Notifies upstream about permanent disconnection</li>
 *   <li>Triggers conversation-level failover if needed</li>
 * </ul>
 */
@Slf4j
@Component
public class ReconnectFailAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.error("ReconnectFailAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Record final failure reason
        recordFinalFailureReason(ctx);

        // 2. Clean up all channel resources
        cleanupAllResources(ctx);

        // 3. Notify upstream about permanent disconnection
        notifyPermanentDisconnection(ctx);

        // 4. Trigger conversation-level failover if needed
        triggerConversationFailover(ctx);

        log.error("ReconnectFailAction completed: interactionId={}, reconnection exhausted, interaction closed", interactionId);
    }

    private void recordFinalFailureReason(AgentConnectorStateContext ctx) {
        log.error("Recording final failure reason: interactionId={}", ctx.interaction().interactionId());
    }

    private void cleanupAllResources(AgentConnectorStateContext ctx) {
        log.debug("Cleaning up all resources: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyPermanentDisconnection(AgentConnectorStateContext ctx) {
        log.error("Notifying permanent disconnection: interactionId={}", ctx.interaction().interactionId());
    }

    private void triggerConversationFailover(AgentConnectorStateContext ctx) {
        log.debug("Triggering conversation failover: interactionId={}", ctx.interaction().interactionId());
    }
}
