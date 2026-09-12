package com.selfdevelopment.agentconnector.action.messaging;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when first inbound message is received (CONNECTED → IN_PROGRESS).
 * <p>
 * This action handles the business logic of first message reception:
 * <ul>
 *   <li>Sets firstMessageAt timestamp</li>
 *   <li>Sets lastInboundAt timestamp</li>
 *   <li>Records first response time metric</li>
 *   <li>Notifies upstream that messaging has started</li>
 * </ul>
 */
@Slf4j
@Component
public class FirstInboundMessageReceivedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("FirstInboundMessageReceivedAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Set firstMessageAt timestamp
        setFirstMessageAt(ctx);

        // 2. Set lastInboundAt timestamp
        setLastInboundAt(ctx);

        // 3. Record first response time metric
        recordFirstResponseTime(ctx);

        // 4. Notify upstream that messaging has started
        notifyMessagingStarted(ctx);

        log.info("FirstInboundMessageReceivedAction completed: interactionId={}, entering IN_PROGRESS", interactionId);
    }

    private void setFirstMessageAt(AgentConnectorStateContext ctx) {
        log.debug("Setting firstMessageAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void setLastInboundAt(AgentConnectorStateContext ctx) {
        log.debug("Setting lastInboundAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void recordFirstResponseTime(AgentConnectorStateContext ctx) {
        log.debug("Recording first response time: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyMessagingStarted(AgentConnectorStateContext ctx) {
        log.debug("Notifying messaging started: interactionId={}", ctx.interaction().interactionId());
    }
}
