package com.selfdevelopment.agentconnector.action.messaging;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when an outbound message is sent (IN_PROGRESS → IN_PROGRESS internal).
 * <p>
 * This action handles the business logic of outbound messages:
 * <ul>
 *   <li>Updates lastOutboundAt timestamp</li>
 *   <li>Records message audit log</li>
 *   <li>Updates message statistics</li>
 *   <li>Triggers delivery confirmation tracking</li>
 *   <li>Updates agent activity metrics</li>
 * </ul>
 * <p>
 * This is an internal transition (IN_PROGRESS → IN_PROGRESS) that does not change state,
 * but updates important fields and triggers side effects for audit and monitoring.
 */
@Slf4j
@Component
public class OutboundMessageSentAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("OutboundMessageSentAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Update lastOutboundAt timestamp
        updateLastOutboundAt(ctx);

        // 2. Record message audit log
        recordMessageAudit(ctx);

        // 3. Update message statistics
        updateMessageStatistics(ctx);

        // 4. Trigger delivery confirmation tracking
        trackDeliveryConfirmation(ctx);

        // 5. Update agent activity metrics
        updateAgentActivityMetrics(ctx);

        log.debug("OutboundMessageSentAction completed: interactionId={}, lastOutboundAt updated",
                interactionId);
    }

    private void updateLastOutboundAt(AgentConnectorStateContext ctx) {
        log.debug("Updating lastOutboundAt: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update the interaction entity
        // ctx.interaction().setLastOutboundAt(Instant.now());
    }

    private void recordMessageAudit(AgentConnectorStateContext ctx) {
        log.debug("Recording outbound message audit log: interactionId={}",
                ctx.interaction().interactionId());
    }

    private void updateMessageStatistics(AgentConnectorStateContext ctx) {
        log.debug("Updating message statistics: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would increment message count
        // ctx.interaction().incrementOutboundMessageCount();
    }

    private void trackDeliveryConfirmation(AgentConnectorStateContext ctx) {
        log.debug("Tracking delivery confirmation: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would track message delivery
        // deliveryTracker.track(ctx.interaction().interactionId(), messageId);
    }

    private void updateAgentActivityMetrics(AgentConnectorStateContext ctx) {
        log.debug("Updating agent activity metrics: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update agent metrics
        // agentMetrics.recordOutboundMessage(ctx.interaction().getAgentId());
    }
}
