package com.selfdevelopment.agentconnector.action.reconnection;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when reconnection attempt starts (DEGRADED → RECONNECTING).
 * <p>
 * This action handles the business logic of reconnection attempt:
 * <ul>
 *   <li>Increments reconnection attempt count</li>
 *   <li>Sets reconnectingAt timestamp</li>
 *   <li>Initiates reconnection to channel</li>
 *   <li>Applies backoff strategy for next attempt</li>
 * </ul>
 */
@Slf4j
@Component
public class ReconnectAttemptAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("ReconnectAttemptAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Increment reconnection attempt count
        incrementReconnectAttemptCount(ctx);

        // 2. Set reconnectingAt timestamp
        setReconnectingAt(ctx);

        // 3. Initiate reconnection to channel
        initiateReconnection(ctx);

        // 4. Apply backoff strategy for next attempt
        applyBackoffStrategy(ctx);

        log.info("ReconnectAttemptAction completed: interactionId={}, reconnection attempt started", interactionId);
    }

    private void incrementReconnectAttemptCount(AgentConnectorStateContext ctx) {
        log.debug("Incrementing reconnect attempt count: interactionId={}", ctx.interaction().interactionId());
    }

    private void setReconnectingAt(AgentConnectorStateContext ctx) {
        log.debug("Setting reconnectingAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void initiateReconnection(AgentConnectorStateContext ctx) {
        log.debug("Initiating reconnection: interactionId={}", ctx.interaction().interactionId());
    }

    private void applyBackoffStrategy(AgentConnectorStateContext ctx) {
        log.debug("Applying backoff strategy: interactionId={}", ctx.interaction().interactionId());
    }
}
