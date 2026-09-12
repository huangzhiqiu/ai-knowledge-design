package com.selfdevelopment.agentconnector.ingress;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Agent connector event dispatcher that routes events through the interaction state machine.
 * <p>
 * <b>RESERVED CODE - Currently not used in production flow.</b>
 * <p>
 * This dispatcher handles events from agent connectors (Genesys, WebSocket)
 * and routes them through the interaction state machine (channel-level).
 */
public class AgentConnectorEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectorEventDispatcher.class);

    private final AgentConnectorStateMachineService stateMachineService;
    private final Function<GenesysEventNormalizer.NormalizedEvent, AgentConnectorStateContext> contextBuilder;

    /**
     * Creates an agent connector event dispatcher.
     *
     * @param stateMachineService the agent connector state machine service
     * @param contextBuilder      function to build AgentConnectorStateContext from a normalized event
     */
    public AgentConnectorEventDispatcher(AgentConnectorStateMachineService stateMachineService,
                                          Function<GenesysEventNormalizer.NormalizedEvent, AgentConnectorStateContext> contextBuilder) {
        this.stateMachineService = Objects.requireNonNull(stateMachineService, "stateMachineService must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
    }

    /**
     * Dispatches a normalized event through the interaction state machine.
     *
     * @param event the normalized event to dispatch
     */
    public void dispatch(GenesysEventNormalizer.NormalizedEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        AgentConnectorStateContext context = contextBuilder.apply(event);
        handleEvent(event.fact(), context);
    }

    /**
     * Handles an event by routing through the interaction state machine.
     *
     * @param fact    the interaction fact to fire
     * @param context the interaction context
     */
    protected void handleEvent(InteractionFact fact, AgentConnectorStateContext context) {
        log.debug("Agent connector event processed: fact={}, interactionId={}",
                fact, context.interaction() != null ? context.interaction().interactionId() : "unknown");

        // Fire event through state machine
        stateMachineService.fire(context, fact);
    }

    /**
     * Returns the agent connector state machine service.
     *
     * @return the state machine service
     */
    public AgentConnectorStateMachineService getStateMachineService() {
        return stateMachineService;
    }
}
