package com.selfdevelopment.agentconnector.statemachine.registry;

import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.api.StateMachineRegistry;
import com.selfdevelopment.statemachine.exception.StateMachineException;

/**
 * Registry for agent connector state machines.
 * <p>
 * Provides static access to the interaction state machine instance.
 */
public final class AgentConnectorStateMachineRegistry {

    private static final StateMachineRegistry registry = new StateMachineRegistry();
    private static volatile boolean initialized = false;

    private AgentConnectorStateMachineRegistry() {
    }

    /**
     * Initializes the registry with all agent connector state machines.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        registry.register(InteractionStateMachineFactory.create());
        initialized = true;
    }

    /**
     * Retrieves a state machine by ID.
     *
     * @param machineId the machine identifier
     * @param <S>       the state type
     * @param <E>       the event type
     * @param <C>       the context type
     * @return the state machine
     * @throws StateMachineException if no machine is registered with the given ID
     */
    @SuppressWarnings("unchecked")
    public static <S, E, C> StateMachine<S, E, C> get(String machineId) {
        if (!initialized) {
            initialize();
        }
        return registry.get(machineId);
    }

    /**
     * Returns the underlying registry for advanced operations.
     */
    public static StateMachineRegistry getRegistry() {
        if (!initialized) {
            initialize();
        }
        return registry;
    }

    /**
     * Clears all registered state machines (primarily for testing).
     */
    public static synchronized void clear() {
        registry.clear();
        initialized = false;
    }
}
