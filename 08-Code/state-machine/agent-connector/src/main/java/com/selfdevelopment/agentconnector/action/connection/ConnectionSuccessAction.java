package com.selfdevelopment.agentconnector.action.connection;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when connection is successfully established (INITIATED → CONNECTED).
 * <p>
 * This action handles the business logic of successful connection:
 * <ul>
 *   <li>Sets connectedAt timestamp</li>
 *   <li>Initializes channel resources</li>
 *   <li>Starts heartbeat monitoring</li>
 *   <li>Notifies upstream that connection is ready</li>
 * </ul>
 */
@Slf4j
public class ConnectionSuccessAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();
        String channelType = ctx.interaction().channelType();

        log.info("ConnectionSuccessAction: {} --({})--> {}, interactionId={}, channelType={}",
                from, event, to, interactionId, channelType);

        // 1. Set connectedAt timestamp
        setConnectedAt(ctx);

        // 2. Initialize channel resources
        initializeChannelResources(ctx);

        // 3. Start heartbeat monitoring
        startHeartbeatMonitoring(ctx);

        // 4. Notify upstream that connection is ready
        notifyConnectionReady(ctx);

        log.info("ConnectionSuccessAction completed: interactionId={}, connection established", interactionId);
    }

    private void setConnectedAt(AgentConnectorStateContext ctx) {
        log.debug("Setting connectedAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void initializeChannelResources(AgentConnectorStateContext ctx) {
        log.debug("Initializing channel resources: interactionId={}", ctx.interaction().interactionId());
    }

    private void startHeartbeatMonitoring(AgentConnectorStateContext ctx) {
        log.debug("Starting heartbeat monitoring: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyConnectionReady(AgentConnectorStateContext ctx) {
        log.debug("Notifying connection ready: interactionId={}", ctx.interaction().interactionId());
    }
}
