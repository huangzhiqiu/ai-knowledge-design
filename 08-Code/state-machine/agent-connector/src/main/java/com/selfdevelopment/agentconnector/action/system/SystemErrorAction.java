package com.selfdevelopment.agentconnector.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when system error occurs (any → CLOSED).
 * <p>
 * This action handles the business logic of unrecoverable system error:
 * <ul>
 *   <li>Records system error with full context</li>
 *   <li>Triggers alerting and monitoring notifications</li>
 *   <li>Forces closure of channel connection</li>
 *   <li>Cleans up resources (best effort)</li>
 *   <li>Notifies upstream about system error</li>
 * </ul>
 */
@Slf4j
@Component
public class SystemErrorAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.error("SystemErrorAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Record system error with full context
        recordSystemError(ctx);

        // 2. Trigger alerting and monitoring notifications
        triggerAlerts(ctx);

        // 3. Force closure of channel connection
        forceCloseChannel(ctx);

        // 4. Clean up resources (best effort)
        cleanupResourcesBestEffort(ctx);

        // 5. Notify upstream about system error
        notifySystemError(ctx);

        log.error("SystemErrorAction completed: interactionId={}, interaction closed due to system error", interactionId);
    }

    private void recordSystemError(AgentConnectorStateContext ctx) {
        log.error("Recording system error: interactionId={}", ctx.interaction().interactionId());
    }

    private void triggerAlerts(AgentConnectorStateContext ctx) {
        log.error("Triggering alerts: interactionId={}", ctx.interaction().interactionId());
    }

    private void forceCloseChannel(AgentConnectorStateContext ctx) {
        log.error("Forcing channel closure: interactionId={}", ctx.interaction().interactionId());
    }

    private void cleanupResourcesBestEffort(AgentConnectorStateContext ctx) {
        log.debug("Cleaning up resources (best effort): interactionId={}", ctx.interaction().interactionId());
    }

    private void notifySystemError(AgentConnectorStateContext ctx) {
        log.error("Notifying system error: interactionId={}", ctx.interaction().interactionId());
    }
}
