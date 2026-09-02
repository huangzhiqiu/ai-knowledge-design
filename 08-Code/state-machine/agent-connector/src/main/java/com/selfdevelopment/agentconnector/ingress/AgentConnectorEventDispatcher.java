package com.selfdevelopment.agentconnector.ingress;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.statemachine.event.EventDispatcher;
import com.selfdevelopment.statemachine.event.StandardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Agent connector event dispatcher that routes standard events through the
 * interaction state machine pipeline.
 * <p>
 * This dispatcher handles events from agent connectors (Genesys, WebSocket)
 * and routes them through the interaction state machine (channel-level).
 */
public class AgentConnectorEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectorEventDispatcher.class);

    private final EventDispatcher dispatcher;
    private final AgentConnectorStateMachineService stateMachineService;
    private final Function<StandardEvent, AgentConnectorStateContext> contextBuilder;

    /**
     * Creates an agent connector event dispatcher.
     *
     * @param stateMachineService the agent connector state machine service
     * @param contextBuilder      function to build AgentConnectorStateContext from a StandardEvent
     */
    public AgentConnectorEventDispatcher(AgentConnectorStateMachineService stateMachineService,
                                          Function<StandardEvent, AgentConnectorStateContext> contextBuilder) {
        this.stateMachineService = Objects.requireNonNull(stateMachineService, "stateMachineService must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.dispatcher = new EventDispatcher();
        this.dispatcher.setDefaultHandler(this::handleEvent);
    }

    /**
     * Dispatches a standard event through the interaction state machine pipeline.
     *
     * @param event the standard event to dispatch
     */
    public void dispatch(StandardEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        log.info("Dispatching agent connector event: type={}, source={}, entityId={}",
                event.getEventType(), event.getSource(), event.getEntityId());

        try {
            dispatcher.dispatch(event);
        } catch (Exception e) {
            log.error("Failed to dispatch agent connector event: type={}, eventId={}, error={}",
                    event.getEventType(), event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handles a standard event by routing through the interaction state machine.
     */
    private void handleEvent(StandardEvent event) {
        AgentConnectorStateContext context = contextBuilder.apply(event);
        log.debug("Processing agent connector event through state machine: type={}, entityId={}",
                event.getEventType(), event.getEntityId());
    }

    /**
     * Returns the underlying event dispatcher for advanced configuration.
     */
    public EventDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Registers an interceptor for cross-cutting concerns (logging, metrics, tracing).
     */
    public void registerInterceptor(String name, EventDispatcher.EventInterceptor interceptor) {
        dispatcher.registerInterceptor(name, interceptor);
    }
}
