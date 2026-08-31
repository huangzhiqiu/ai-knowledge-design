package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.registry.StateMachineRegistry;

/**
 * CBOL business layer state machine registry holder.
 * <p>
 * Holds a shared {@link StateMachineRegistry} instance for use by factories and services.
 * This is a singleton holder — use static methods to access.
 */
public final class CbolStateMachineRegistry {

    private static final StateMachineRegistry INSTANCE = new StateMachineRegistry();

    private CbolStateMachineRegistry() {
        // Singleton holder, prevent instantiation
    }

    public static StateMachineRegistry getInstance() {
        return INSTANCE;
    }

    public static <S, E, C> void register(StateMachine<S, E, C> machine) {
        INSTANCE.register(machine);
    }

    public static <S, E, C> StateMachine<S, E, C> get(String machineId) {
        return INSTANCE.get(machineId);
    }

    /**
     * Clears all registered state machines.
     * Primarily intended for test isolation.
     */
    public static void clear() {
        INSTANCE.clear();
    }
}
