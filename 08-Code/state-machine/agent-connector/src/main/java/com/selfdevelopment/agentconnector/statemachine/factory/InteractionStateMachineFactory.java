package com.selfdevelopment.agentconnector.statemachine.factory;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.selfdevelopment.agentconnector.action.impl.CloseRequestAction;
import com.selfdevelopment.agentconnector.action.impl.ConnectionDroppedAction;
import com.selfdevelopment.agentconnector.action.impl.ConnectionEstablishedAction;
import com.selfdevelopment.agentconnector.action.impl.ConnectionFailedAction;
import com.selfdevelopment.agentconnector.action.impl.HoldRequestAction;
import com.selfdevelopment.agentconnector.action.impl.HoldResumeAction;
import com.selfdevelopment.agentconnector.action.impl.ReconnectSuccessAction;
import com.selfdevelopment.agentconnector.action.impl.TransferCompleteAction;
import com.selfdevelopment.agentconnector.action.impl.TransferStartAction;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;

/**
 * Factory for the Interaction (channel/connection) state machine.
 * <p>
 * Uses COLA StateMachine builder API.
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

    // Action instances (stateless, can be shared)
    private static final ConnectionEstablishedAction CONNECTION_ESTABLISHED_ACTION = new ConnectionEstablishedAction();
    private static final ConnectionFailedAction CONNECTION_FAILED_ACTION = new ConnectionFailedAction();
    private static final ConnectionDroppedAction CONNECTION_DROPPED_ACTION = new ConnectionDroppedAction();
    private static final CloseRequestAction CLOSE_REQUEST_ACTION = new CloseRequestAction();
    private static final ReconnectSuccessAction RECONNECT_SUCCESS_ACTION = new ReconnectSuccessAction();
    private static final HoldRequestAction HOLD_REQUEST_ACTION = new HoldRequestAction();
    private static final HoldResumeAction HOLD_RESUME_ACTION = new HoldResumeAction();
    private static final TransferStartAction TRANSFER_START_ACTION = new TransferStartAction();
    private static final TransferCompleteAction TRANSFER_COMPLETE_ACTION = new TransferCompleteAction();

    /**
     * Builds and registers the interaction state machine with all transition rules.
     *
     * @return the configured interaction state machine
     */
    public static StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> create() {
        // Try to get existing state machine first
        try {
            StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (Exception ignored) {
            // State machine not built yet
        }

        synchronized (InteractionStateMachineFactory.class) {
            // Double-check after acquiring lock
            try {
                StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> existing =
                        StateMachineFactory.get(MACHINE_ID);
                if (existing != null) {
                    return existing;
                }
            } catch (Exception ignored) {
                // State machine not built yet
            }

            // Build and register
            try {
                StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> sm = build();
                StateMachineFactory.register(sm);
                return sm;
            } catch (Exception e) {
                // State machine already built, return existing instance
                return StateMachineFactory.get(MACHINE_ID);
            }
        }
    }

    /**
     * Builds the interaction state machine without registering it.
     *
     * @return the configured interaction state machine
     */
    public static StateMachine<InteractionState, InteractionFact, AgentConnectorStateContext> build() {
        StateMachineBuilder<InteractionState, InteractionFact, AgentConnectorStateContext> builder =
                StateMachineBuilderFactory.create();

        // COLA API order: from → to → on → when → perform

        // === Connection Lifecycle ===

        // I01: CONNECTING → CONNECTED (connection established)
        builder.externalTransition()
                .from(InteractionState.CONNECTING)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.CONNECTION_ESTABLISHED)
                .perform(CONNECTION_ESTABLISHED_ACTION);

        // I02: CONNECTING → DISCONNECTED (connection failed)
        builder.externalTransition()
                .from(InteractionState.CONNECTING)
                .to(InteractionState.DISCONNECTED)
                .on(InteractionFact.CONNECTION_FAILED)
                .perform(CONNECTION_FAILED_ACTION);

        // I03: CONNECTED → RECONNECTING (connection dropped)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.RECONNECTING)
                .on(InteractionFact.CONNECTION_DROPPED)
                .perform(CONNECTION_DROPPED_ACTION);

        // I04: CONNECTED → DISCONNECTED (explicit close)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.DISCONNECTED)
                .on(InteractionFact.CLOSE_REQUEST)
                .perform(CLOSE_REQUEST_ACTION);

        // === Reconnection ===

        // I05: RECONNECTING → CONNECTED (reconnect succeeded)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.RECONNECT_SUCCESS)
                .perform(RECONNECT_SUCCESS_ACTION);

        // I06: RECONNECTING → DISCONNECTED (reconnect failed)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.DISCONNECTED)
                .on(InteractionFact.RECONNECT_FAILED);

        // I07: RECONNECTING → DISCONNECTED (max retries exhausted)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.DISCONNECTED)
                .on(InteractionFact.RECONNECT_EXHAUSTED);

        // === Hold ===

        // I08: CONNECTED → HELD (agent puts on hold)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.HELD)
                .on(InteractionFact.HOLD_REQUEST)
                .perform(HOLD_REQUEST_ACTION);

        // I09: HELD → CONNECTED (customer retrieved from hold)
        builder.externalTransition()
                .from(InteractionState.HELD)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.HOLD_RESUME)
                .perform(HOLD_RESUME_ACTION);

        // I10: HELD → DISCONNECTED (close while on hold)
        builder.externalTransition()
                .from(InteractionState.HELD)
                .to(InteractionState.DISCONNECTED)
                .on(InteractionFact.CLOSE_REQUEST);

        // === Transfer (channel-level) ===

        // I11: CONNECTED → TRANSFERRING (channel transfer initiated)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.TRANSFERRING)
                .on(InteractionFact.TRANSFER_START)
                .perform(TRANSFER_START_ACTION);

        // I12: TRANSFERRING → CONNECTED (transfer completed, now on new channel)
        builder.externalTransition()
                .from(InteractionState.TRANSFERRING)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.TRANSFER_COMPLETE)
                .perform(TRANSFER_COMPLETE_ACTION);

        // I13: TRANSFERRING → CONNECTED (transfer failed, stay on original channel)
        builder.externalTransition()
                .from(InteractionState.TRANSFERRING)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.TRANSFER_FAILED);

        // I14: TRANSFERRING → DISCONNECTED (close during transfer)
        builder.externalTransition()
                .from(InteractionState.TRANSFERRING)
                .to(InteractionState.DISCONNECTED)
                .on(InteractionFact.CLOSE_REQUEST);

        return builder.build(MACHINE_ID);
    }
}
