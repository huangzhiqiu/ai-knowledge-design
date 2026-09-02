package com.selfdevelopment.agentconnector.service;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import com.selfdevelopment.agentconnector.statemachine.registry.AgentConnectorStateMachineRegistry;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Service for firing interaction fact events through the interaction state machine.
 * <p>
 * This service manages the channel-level state machine (Interaction), handling
 * connection lifecycle, transfer, and hold states for agent connectors (Genesys, WebSocket).
 */
@Slf4j
public class AgentConnectorStateMachineService {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> interactionSm;
    private final int maxRetries;

    /**
     * Creates a service using the default interaction state machine from the registry.
     */
    public AgentConnectorStateMachineService() {
        this(AgentConnectorStateMachineRegistry.get(InteractionStateMachineFactory.MACHINE_ID), DEFAULT_MAX_RETRIES);
    }

    /**
     * Creates a service with an explicitly injected state machine.
     *
     * @param interactionSm the interaction state machine (must not be null)
     * @param maxRetries    maximum number of retries on conflict
     */
    public AgentConnectorStateMachineService(
            StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> interactionSm,
            int maxRetries) {
        this.interactionSm = Objects.requireNonNull(interactionSm, "interactionSm must not be null");
        this.maxRetries = maxRetries;
    }

    /**
     * Fires an interaction fact event through the state machine.
     *
     * @param ctx  the interaction context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the state context after the transition
     * @throws NullPointerException if ctx or fact is null
     */
    public StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> fire(
            AgentConnectorStateContext ctx, InteractionFact fact) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(ctx.interaction(), "ctx.interaction must not be null");

        return fireStateless(ctx, fact);
    }

    /**
     * Fires an event without persistent state storage (stateless mode).
     */
    private StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> fireStateless(
            AgentConnectorStateContext ctx, InteractionFact fact) {
        long start = System.currentTimeMillis();
        String interactionId = ctx.interaction().interactionId();
        InteractionState from = ctx.interaction().state();
        try {
            StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> result =
                    interactionSm.fireEvent(from, fact, ctx);

            log.info("Interaction state transition: interactionId={}, from={}, fact={}, to={}, accepted={}, durationMs={}",
                    interactionId, from, fact, result.getTargetState(), result.isTransitionAccepted(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            String message = String.format(
                    "Interaction state machine transition failed: interactionId=%s, from=%s, fact=%s: %s",
                    interactionId, from, fact, ex.getMessage());
            log.error(message, ex);
            throw new StateMachineException(message, ex);
        }
    }

    /**
     * Convenience method that returns only the target state.
     *
     * @throws IllegalStateException if the transition was rejected and target state is null
     */
    public InteractionState fireAndGetState(AgentConnectorStateContext ctx, InteractionFact fact) {
        StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> result = fire(ctx, fact);
        if (result.getTargetState() == null) {
            throw new IllegalStateException(
                    "Transition returned null target state (rejected): interactionId="
                            + ctx.interaction().interactionId() + ", fact=" + fact);
        }
        return result.getTargetState();
    }

    /**
     * Returns the underlying state machine.
     */
    public StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> getStateMachine() {
        return interactionSm;
    }
}
