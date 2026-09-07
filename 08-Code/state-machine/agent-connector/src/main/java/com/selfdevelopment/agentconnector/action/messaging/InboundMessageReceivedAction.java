package com.selfdevelopment.agentconnector.action.messaging;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a subsequent inbound message is received (IN_PROGRESS → IN_PROGRESS internal).
 * <p>
 * This action handles the business logic of subsequent inbound messages:
 * <ul>
 *   <li>Updates lastInboundAt timestamp</li>
 *   <li>Resets customer idle timer</li>
 *   <li>Records message audit log</li>
 *   <li>Updates message statistics</li>
 *   <li>Triggers message processing pipeline</li>
 * </ul>
 * <p>
 * This is an internal transition (IN_PROGRESS → IN_PROGRESS) that does not change state,
 * but updates important fields and triggers side effects.
 * <p>
 * Distinction from FIRST_INBOUND_MESSAGE_RECEIVED:
 * - FIRST_INBOUND_MESSAGE_RECEIVED: CONNECTED → IN_PROGRESS (state change, initial message)
 * - INBOUND_MESSAGE_RECEIVED: IN_PROGRESS → IN_PROGRESS (internal, subsequent messages)
 */
@Slf4j
public class InboundMessageReceivedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("InboundMessageReceivedAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Update lastInboundAt timestamp
        updateLastInboundAt(ctx);

        // 2. Reset customer idle timer
        resetCustomerIdleTimer(ctx);

        // 3. Record message audit log
        recordMessageAudit(ctx);

        // 4. Update message statistics
        updateMessageStatistics(ctx);

        // 5. Trigger message processing pipeline
        triggerMessageProcessing(ctx);

        log.debug("InboundMessageReceivedAction completed: interactionId={}, lastInboundAt updated",
                interactionId);
    }

    private void updateLastInboundAt(AgentConnectorStateContext ctx) {
        log.debug("Updating lastInboundAt: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would update the interaction entity
        // ctx.interaction().setLastInboundAt(Instant.now());
    }

    private void resetCustomerIdleTimer(AgentConnectorStateContext ctx) {
        log.debug("Resetting customer idle timer: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would reset the idle monitor
        // CustomerIdleMonitor.resetTimer(ctx.interaction().interactionId());
    }

    private void recordMessageAudit(AgentConnectorStateContext ctx) {
        log.debug("Recording inbound message audit log: interactionId={}",
                ctx.interaction().interactionId());
    }

    private void updateMessageStatistics(AgentConnectorStateContext ctx) {
        log.debug("Updating message statistics: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would increment message count
        // ctx.interaction().incrementInboundMessageCount();
    }

    private void triggerMessageProcessing(AgentConnectorStateContext ctx) {
        log.debug("Triggering message processing pipeline: interactionId={}",
                ctx.interaction().interactionId());
        // In a real implementation, this would trigger message processing
        // messageProcessor.process(ctx.interaction(), message);
    }
}
