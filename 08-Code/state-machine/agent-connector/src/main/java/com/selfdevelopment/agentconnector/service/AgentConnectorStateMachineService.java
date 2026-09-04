package com.selfdevelopment.agentconnector.service;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.impl.StateMachineException;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Service for firing interaction fact events through the interaction state machine.
 * <p>
 * Uses COLA StateMachine. This service manages the channel-level state machine (Interaction),
 * handling connection lifecycle, transfer, and hold states for agent connectors (Genesys, WebSocket).
 */
@Slf4j
public class AgentConnectorStateMachineService {

    private final StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> interactionSm;

    /**
     * Creates a service using the default interaction state machine from the factory.
     * <p>
     * If the state machine is not yet registered, it will be automatically created
     * and registered via {@link InteractionStateMachineFactory#create()}.
     */
    public AgentConnectorStateMachineService() {
        this(getOrCreateStateMachine());
    }

    /**
     * Gets the interaction state machine from the global factory, creating and
     * registering it if it doesn't exist yet.
     *
     * @return the interaction state machine
     */
    private static StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> getOrCreateStateMachine() {
        try {
            return StateMachineFactory.get(InteractionStateMachineFactory.MACHINE_ID);
        } catch (StateMachineException e) {
            // Not registered yet, create and register it
            return InteractionStateMachineFactory.create();
        }
    }

    /**
     * Creates a service with an explicitly injected state machine.
     *
     * @param interactionSm the interaction state machine (must not be null)
     */
    public AgentConnectorStateMachineService(
            StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> interactionSm) {
        this.interactionSm = Objects.requireNonNull(interactionSm, "interactionSm must not be null");
    }

    /**
     * Fires an interaction fact event through the state machine.
     *
     * @param ctx  the interaction context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the target state after the transition
     * @throws NullPointerException if ctx or fact is null
     */
    public InteractionState fire(AgentConnectorStateContext ctx, InteractionFact fact) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(ctx.interaction(), "ctx.interaction must not be null");

        return fireStateless(ctx, fact);
    }

    /**
     * Fires an event without persistent state storage (stateless mode).
     */
    private InteractionState fireStateless(AgentConnectorStateContext ctx, InteractionFact fact) {
        long start = System.currentTimeMillis();
        String interactionId = ctx.interaction().interactionId();
        InteractionState from = ctx.interaction().state();
        try {
            InteractionState target = interactionSm.fireEvent(from, fact, ctx);

            log.info("Interaction state transition: interactionId={}, from={}, fact={}, to={}, durationMs={}",
                    interactionId, from, fact, target, System.currentTimeMillis() - start);
            return target;
        } catch (RuntimeException ex) {
            String message = String.format(
                    "Interaction state machine transition failed: interactionId=%s, from=%s, fact=%s: %s",
                    interactionId, from, fact, ex.getMessage());
            log.error(message, ex);
            StateMachineException smEx = new StateMachineException(message);
            smEx.initCause(ex);
            throw smEx;
        }
    }

    /**
     * Verifies if an event can be fired from the current state.
     *
     * @param currentState the current state
     * @param fact         the event to verify
     * @return true if the event can be fired, false otherwise
     */
    public boolean verify(InteractionState currentState, InteractionFact fact) {
        return interactionSm.verify(currentState, fact);
    }

    /**
     * Returns the underlying state machine.
     */
    public StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> getStateMachine() {
        return interactionSm;
    }
}
