package com.selfdevelopment.agentconnector.statemachine.factory;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.api.StateMachineRegistry;
import com.selfdevelopment.statemachine.builder.StateMachineBuilder;

/**
 * Factory for the Interaction (channel/connection) state machine.
 * <p>
 * Models the lifecycle of a communication channel: connection establishment,
 * active communication, hold, transfer, reconnection, and disconnection.
 *
 * <pre>
 * States: CONNECTING → CONNECTED → RECONNECTING / HELD / TRANSFERRING → DISCONNECTED
 * Events: 14 channel-level events (connection lifecycle, hold, transfer)
 * </pre>
 */
public class InteractionStateMachineFactory {

    public static final String MACHINE_ID = "interaction";

    /**
     * Builds and registers the interaction state machine with all transition rules.
     *
     * @return the configured interaction state machine
     */
    public static StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> create() {
        StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> sm = build();
        // Register to the global registry
        StateMachineRegistry.getInstance().register(sm);
        return sm;
    }

    /**
     * Builds the interaction state machine without registering it.
     *
     * @return the configured interaction state machine
     */
    public static StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> build() {
        StateMachineBuilder<InteractionState, InteractionFact, AgentConnectorStateContext> builder =
                StateMachineBuilder.builder(MACHINE_ID);

        builder.initialState(InteractionState.CONNECTING)
                .endStates(InteractionState.DISCONNECTED);

        // === Connection Lifecycle ===

        // I01: CONNECTING → CONNECTED (connection established)
        builder.transition()
                .from(InteractionState.CONNECTING)
                .on(InteractionFact.CONNECTION_ESTABLISHED)
                .to(InteractionState.CONNECTED)
                .and();

        // I02: CONNECTING → DISCONNECTED (connection failed)
        builder.transition()
                .from(InteractionState.CONNECTING)
                .on(InteractionFact.CONNECTION_FAILED)
                .to(InteractionState.DISCONNECTED)
                .and();

        // I03: CONNECTED → RECONNECTING (connection dropped)
        builder.transition()
                .from(InteractionState.CONNECTED)
                .on(InteractionFact.CONNECTION_DROPPED)
                .to(InteractionState.RECONNECTING)
                .and();

        // I04: CONNECTED → DISCONNECTED (explicit close)
        builder.transition()
                .from(InteractionState.CONNECTED)
                .on(InteractionFact.CLOSE_REQUEST)
                .to(InteractionState.DISCONNECTED)
                .and();

        // === Reconnection ===

        // I05: RECONNECTING → CONNECTED (reconnect succeeded)
        builder.transition()
                .from(InteractionState.RECONNECTING)
                .on(InteractionFact.RECONNECT_SUCCESS)
                .to(InteractionState.CONNECTED)
                .and();

        // I06: RECONNECTING → DISCONNECTED (reconnect failed)
        builder.transition()
                .from(InteractionState.RECONNECTING)
                .on(InteractionFact.RECONNECT_FAILED)
                .to(InteractionState.DISCONNECTED)
                .and();

        // I07: RECONNECTING → DISCONNECTED (max retries exhausted)
        builder.transition()
                .from(InteractionState.RECONNECTING)
                .on(InteractionFact.RECONNECT_EXHAUSTED)
                .to(InteractionState.DISCONNECTED)
                .and();

        // === Hold ===

        // I08: CONNECTED → HELD (agent puts on hold)
        builder.transition()
                .from(InteractionState.CONNECTED)
                .on(InteractionFact.HOLD_REQUEST)
                .to(InteractionState.HELD)
                .and();

        // I09: HELD → CONNECTED (customer retrieved from hold)
        builder.transition()
                .from(InteractionState.HELD)
                .on(InteractionFact.HOLD_RESUME)
                .to(InteractionState.CONNECTED)
                .and();

        // I10: HELD → DISCONNECTED (close while on hold)
        builder.transition()
                .from(InteractionState.HELD)
                .on(InteractionFact.CLOSE_REQUEST)
                .to(InteractionState.DISCONNECTED)
                .and();

        // === Transfer (channel-level) ===

        // I11: CONNECTED → TRANSFERRING (channel transfer initiated)
        builder.transition()
                .from(InteractionState.CONNECTED)
                .on(InteractionFact.TRANSFER_START)
                .to(InteractionState.TRANSFERRING)
                .and();

        // I12: TRANSFERRING → CONNECTED (transfer completed, now on new channel)
        builder.transition()
                .from(InteractionState.TRANSFERRING)
                .on(InteractionFact.TRANSFER_COMPLETE)
                .to(InteractionState.CONNECTED)
                .and();

        // I13: TRANSFERRING → CONNECTED (transfer failed, stay on original channel)
        builder.transition()
                .from(InteractionState.TRANSFERRING)
                .on(InteractionFact.TRANSFER_FAILED)
                .to(InteractionState.CONNECTED)
                .and();

        // I14: TRANSFERRING → DISCONNECTED (close during transfer)
        builder.transition()
                .from(InteractionState.TRANSFERRING)
                .on(InteractionFact.CLOSE_REQUEST)
                .to(InteractionState.DISCONNECTED)
                .and();

        return builder.build();
    }
}
