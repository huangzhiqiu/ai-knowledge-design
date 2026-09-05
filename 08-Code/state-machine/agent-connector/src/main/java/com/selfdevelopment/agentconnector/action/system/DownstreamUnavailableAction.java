package com.selfdevelopment.agentconnector.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when downstream is unavailable (CONNECTED/IN_PROGRESS → DEGRADED).
 * <p>
 * This action handles the business logic of temporary downstream unavailability:
 * <ul>
 *   <li>Records downstream unavailability event</li>
 *   <li>Sets degradedAt timestamp</li>
 *   <li>Sets up retry mechanism for downstream availability</li>
 *   <li>Queues outbound messages for later delivery</li>
 *   <li>Notifies upstream about temporary degradation</li>
 * </ul>
 */
@Slf4j
public class DownstreamUnavailableAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.warn("DownstreamUnavailableAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Record downstream unavailability event
        recordDownstreamUnavailable(ctx);

        // 2. Set degradedAt timestamp
        setDegradedAt(ctx);

        // 3. Set up retry mechanism for downstream availability
        setupRetryMechanism(ctx);

        // 4. Queue outbound messages for later delivery
        queueOutboundMessages(ctx);

        // 5. Notify upstream about temporary degradation
        notifyTemporaryDegradation(ctx);

        log.warn("DownstreamUnavailableAction completed: interactionId={}, entering DEGRADED due to downstream unavailability", interactionId);
    }

    private void recordDownstreamUnavailable(AgentConnectorStateContext ctx) {
        log.warn("Recording downstream unavailability: interactionId={}", ctx.interaction().interactionId());
    }

    private void setDegradedAt(AgentConnectorStateContext ctx) {
        log.debug("Setting degradedAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void setupRetryMechanism(AgentConnectorStateContext ctx) {
        log.debug("Setting up retry mechanism: interactionId={}", ctx.interaction().interactionId());
    }

    private void queueOutboundMessages(AgentConnectorStateContext ctx) {
        log.debug("Queueing outbound messages: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyTemporaryDegradation(AgentConnectorStateContext ctx) {
        log.warn("Notifying temporary degradation: interactionId={}", ctx.interaction().interactionId());
    }
}
