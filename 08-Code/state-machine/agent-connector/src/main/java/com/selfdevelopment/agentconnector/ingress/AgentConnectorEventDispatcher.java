package com.selfdevelopment.agentconnector.ingress;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.statemachine.event.AbstractEventDispatcher;
import com.selfdevelopment.statemachine.event.StandardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Agent connector event dispatcher that routes standard events through the interaction state machine.
 * <p>
 * This dispatcher handles events from agent connectors (Genesys, WebSocket)
 * and routes them through the interaction state machine (channel-level).
 */
public class AgentConnectorEventDispatcher extends AbstractEventDispatcher<AgentConnectorStateContext> {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectorEventDispatcher.class);

    private final AgentConnectorStateMachineService stateMachineService;

    /**
     * Creates an agent connector event dispatcher.
     *
     * @param stateMachineService the agent connector state machine service
     * @param contextBuilder      function to build AgentConnectorStateContext from a StandardEvent
     */
    public AgentConnectorEventDispatcher(AgentConnectorStateMachineService stateMachineService,
                                          Function<StandardEvent, AgentConnectorStateContext> contextBuilder) {
        super(contextBuilder);
        this.stateMachineService = Objects.requireNonNull(stateMachineService, "stateMachineService must not be null");
    }

    /**
     * Handles a standard event by routing through the interaction state machine.
     */
    @Override
    protected void handleEvent(StandardEvent event, AgentConnectorStateContext context) {
        log.debug("Agent connector event processed: type={}, entityId={}",
                event.getEventType(), event.getEntityId());
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
