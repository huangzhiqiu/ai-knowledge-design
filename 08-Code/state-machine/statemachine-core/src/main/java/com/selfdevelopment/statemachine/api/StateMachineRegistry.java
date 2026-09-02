package com.selfdevelopment.statemachine.api;

import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.exception.StateMachineException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe registry for managing multiple named {@link StateMachine} instances.
 * <p>
 * This is useful when an application needs several state machines (e.g., one per
 * business domain) and wants to look them up by identifier.
 * <p>
 * The registry is optional — state machines can be used directly without registration.
 */
public final class StateMachineRegistry {

    /**
     * Global singleton instance for application-wide state machine management.
     * <p>
     * Use this instance when you want a shared registry across the entire application.
     * For isolated registries (e.g., in tests), create a new instance via the constructor.
     */
    private static final StateMachineRegistry GLOBAL_INSTANCE = new StateMachineRegistry();

    private final Map<String, StateMachine<?, ?, ?>> machines = new ConcurrentHashMap<>();

    /**
     * Returns the global singleton registry instance.
     * <p>
     * This instance is shared across the entire application. State machines from
     * different modules (e.g., chat-engine, agent-connector) can be registered here
     * as long as they have unique machine IDs.
     *
     * @return the global StateMachineRegistry instance
     */
    public static StateMachineRegistry getInstance() {
        return GLOBAL_INSTANCE;
    }

    /**
     * Registers a state machine under its machine ID.
     *
     * @param machine the state machine to register
     * @throws NullPointerException     if machine is null
     * @throws StateMachineException    if a machine with the same ID is already registered
     */
    public <S, E, C> void register(StateMachine<S, E, C> machine) {
        Objects.requireNonNull(machine, "machine must not be null");
        String id = extractId(machine);
        StateMachine<?, ?, ?> existing = machines.putIfAbsent(id, machine);
        if (existing != null) {
            throw new StateMachineException("State machine already registered with ID: " + id);
        }
    }

    /**
     * Retrieves a registered state machine by ID.
     *
     * @param machineId the machine identifier
     * @param <S>       the state type
     * @param <E>       the event type
     * @param <C>       the context type
     * @return the registered state machine
     * @throws StateMachineException if no machine is registered with the given ID
     */
    @SuppressWarnings("unchecked")
    public <S, E, C> StateMachine<S, E, C> get(String machineId) {
        Objects.requireNonNull(machineId, "machineId must not be null");
        StateMachine<?, ?, ?> machine = machines.get(machineId);
        if (machine == null) {
            throw new StateMachineException("No state machine registered with ID: " + machineId);
        }
        return (StateMachine<S, E, C>) machine;
    }

    /**
     * Checks whether a state machine is registered with the given ID.
     *
     * @param machineId the machine identifier
     * @return {@code true} if a machine is registered
     */
    public boolean contains(String machineId) {
        return machines.containsKey(machineId);
    }

    /**
     * Removes a state machine from the registry.
     *
     * @param machineId the machine identifier
     * @return {@code true} if a machine was removed
     */
    public boolean unregister(String machineId) {
        return machines.remove(machineId) != null;
    }

    /**
     * Returns the number of registered state machines.
     *
     * @return the count
     */
    public int size() {
        return machines.size();
    }

    /**
     * Removes all registered state machines.
     * Primarily intended for test isolation.
     */
    public void clear() {
        machines.clear();
    }

    private String extractId(StateMachine<?, ?, ?> machine) {
        // SimpleStateMachine exposes getMachineId(); for other implementations,
        // fall back to toString-based extraction or require explicit ID registration.
        try {
            var method = machine.getClass().getMethod("getMachineId");
            Object result = method.invoke(machine);
            if (result instanceof String id) {
                return id;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return machine.toString();
    }
}
