package com.selfdevelopment.agentconnector.statemachine.factory;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.selfdevelopment.agentconnector.action.connection.ConnectionFailAction;
import com.selfdevelopment.agentconnector.action.connection.ConnectionSuccessAction;
import com.selfdevelopment.agentconnector.action.ending.EndRequestedAction;
import com.selfdevelopment.agentconnector.action.genesys.ConsultTransferEndedAction;
import com.selfdevelopment.agentconnector.action.genesys.ConsultTransferStartedAction;
import com.selfdevelopment.agentconnector.action.heartbeat.HeartbeatMissAction;
import com.selfdevelopment.agentconnector.action.heartbeat.HeartbeatRestoredAction;
import com.selfdevelopment.agentconnector.action.messaging.FirstInboundMessageReceivedAction;
import com.selfdevelopment.agentconnector.action.reconnection.ReconnectAttemptAction;
import com.selfdevelopment.agentconnector.action.reconnection.ReconnectFailAction;
import com.selfdevelopment.agentconnector.action.reconnection.ReconnectSuccessAction;
import com.selfdevelopment.agentconnector.action.system.DownstreamUnavailableAction;
import com.selfdevelopment.agentconnector.action.system.SystemErrorAction;
import com.selfdevelopment.agentconnector.action.transfer.TransferSuccessAction;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;

/**
 * Factory for the Interaction (channel/connection) state machine.
 * <p>
 * Uses COLA StateMachine builder API.
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0).
 * <p>
 * Models the lifecycle of a communication channel:
 * connection establishment, active messaging, degradation, reconnection,
 * Genesys consult transfer, cross-channel transfer, and closure.
 *
 * <pre>
 * States: INITIATED, CONNECTED, IN_PROGRESS, DEGRADED, RECONNECTING,
 *         CONSULT_TRANSFER (GENESYS ONLY), TRANSFERRED, CLOSED
 * Events: 20+ channel-level events (connection, messaging, heartbeat,
 *         reconnection, genesys, transfer, ending, system, downstream)
 * </pre>
 */
public class InteractionStateMachineFactory {

    public static final String MACHINE_ID = "interaction";

    // Action instances (stateless, can be shared)
    private static final ConnectionSuccessAction CONNECTION_SUCCESS_ACTION = new ConnectionSuccessAction();
    private static final ConnectionFailAction CONNECTION_FAIL_ACTION = new ConnectionFailAction();
    private static final FirstInboundMessageReceivedAction FIRST_INBOUND_MESSAGE_RECEIVED_ACTION = new FirstInboundMessageReceivedAction();
    private static final HeartbeatMissAction HEARTBEAT_MISS_ACTION = new HeartbeatMissAction();
    private static final HeartbeatRestoredAction HEARTBEAT_RESTORED_ACTION = new HeartbeatRestoredAction();
    private static final ReconnectAttemptAction RECONNECT_ATTEMPT_ACTION = new ReconnectAttemptAction();
    private static final ReconnectSuccessAction RECONNECT_SUCCESS_ACTION = new ReconnectSuccessAction();
    private static final ReconnectFailAction RECONNECT_FAIL_ACTION = new ReconnectFailAction();
    private static final ConsultTransferStartedAction CONSULT_TRANSFER_STARTED_ACTION = new ConsultTransferStartedAction();
    private static final ConsultTransferEndedAction CONSULT_TRANSFER_ENDED_ACTION = new ConsultTransferEndedAction();
    private static final TransferSuccessAction TRANSFER_SUCCESS_ACTION = new TransferSuccessAction();
    private static final EndRequestedAction END_REQUESTED_ACTION = new EndRequestedAction();
    private static final SystemErrorAction SYSTEM_ERROR_ACTION = new SystemErrorAction();
    private static final DownstreamUnavailableAction DOWNSTREAM_UNAVAILABLE_ACTION = new DownstreamUnavailableAction();

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

        // I01: INITIATED → CONNECTED (connection success)
        builder.externalTransition()
                .from(InteractionState.INITIATED)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.CONNECTION_SUCCESS)
                .perform(CONNECTION_SUCCESS_ACTION);

        // I02: INITIATED → CLOSED (connection fail)
        builder.externalTransition()
                .from(InteractionState.INITIATED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.CONNECTION_FAIL)
                .perform(CONNECTION_FAIL_ACTION);

        // === Messaging ===

        // I03: CONNECTED → IN_PROGRESS (first inbound message received)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.IN_PROGRESS)
                .on(InteractionFact.FIRST_INBOUND_MESSAGE_RECEIVED)
                .perform(FIRST_INBOUND_MESSAGE_RECEIVED_ACTION);

        // I04: IN_PROGRESS → IN_PROGRESS (internal, subsequent inbound messages)
        builder.internalTransition()
                .within(InteractionState.IN_PROGRESS)
                .on(InteractionFact.INBOUND_MESSAGE_RECEIVED);

        // I05: IN_PROGRESS → IN_PROGRESS (internal, outbound messages)
        builder.internalTransition()
                .within(InteractionState.IN_PROGRESS)
                .on(InteractionFact.OUTBOUND_MESSAGE_SENT);

        // === Heartbeat & Degradation ===

        // I06: CONNECTED → DEGRADED (heartbeat miss)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.DEGRADED)
                .on(InteractionFact.HEARTBEAT_MISS)
                .perform(HEARTBEAT_MISS_ACTION);

        // I07: IN_PROGRESS → DEGRADED (heartbeat miss)
        builder.externalTransition()
                .from(InteractionState.IN_PROGRESS)
                .to(InteractionState.DEGRADED)
                .on(InteractionFact.HEARTBEAT_MISS)
                .perform(HEARTBEAT_MISS_ACTION);

        // I08: DEGRADED → CONNECTED (heartbeat restored)
        builder.externalTransition()
                .from(InteractionState.DEGRADED)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.HEARTBEAT_RESTORED)
                .perform(HEARTBEAT_RESTORED_ACTION);

        // I09: CONNECTED → DEGRADED (downstream unavailable)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.DEGRADED)
                .on(InteractionFact.DOWNSTREAM_UNAVAILABLE)
                .perform(DOWNSTREAM_UNAVAILABLE_ACTION);

        // I10: IN_PROGRESS → DEGRADED (downstream unavailable)
        builder.externalTransition()
                .from(InteractionState.IN_PROGRESS)
                .to(InteractionState.DEGRADED)
                .on(InteractionFact.DOWNSTREAM_UNAVAILABLE)
                .perform(DOWNSTREAM_UNAVAILABLE_ACTION);

        // === Reconnection ===

        // I11: DEGRADED → RECONNECTING (reconnect attempt)
        builder.externalTransition()
                .from(InteractionState.DEGRADED)
                .to(InteractionState.RECONNECTING)
                .on(InteractionFact.RECONNECT_ATTEMPT)
                .perform(RECONNECT_ATTEMPT_ACTION);

        // I12: RECONNECTING → CONNECTED (reconnect success)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.CONNECTED)
                .on(InteractionFact.RECONNECT_SUCCESS)
                .perform(RECONNECT_SUCCESS_ACTION);

        // I13: RECONNECTING → IN_PROGRESS (reconnect success, restore to in-progress)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.IN_PROGRESS)
                .on(InteractionFact.RECONNECT_SUCCESS)
                .perform(RECONNECT_SUCCESS_ACTION);

        // I14: RECONNECTING → CLOSED (reconnect fail, max retries exceeded)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.RECONNECT_FAIL)
                .perform(RECONNECT_FAIL_ACTION);

        // === Genesys Consult Transfer (GENESYS ONLY) ===

        // I15: IN_PROGRESS → CONSULT_TRANSFER (consult transfer started)
        builder.externalTransition()
                .from(InteractionState.IN_PROGRESS)
                .to(InteractionState.CONSULT_TRANSFER)
                .on(InteractionFact.CONSULT_TRANSFER_STARTED)
                .perform(CONSULT_TRANSFER_STARTED_ACTION);

        // I16: CONSULT_TRANSFER → IN_PROGRESS (consult transfer ended)
        builder.externalTransition()
                .from(InteractionState.CONSULT_TRANSFER)
                .to(InteractionState.IN_PROGRESS)
                .on(InteractionFact.CONSULT_TRANSFER_ENDED)
                .perform(CONSULT_TRANSFER_ENDED_ACTION);

        // === Cross-Channel Transfer (source detach marker) ===

        // I17: IN_PROGRESS → TRANSFERRED (transfer success, source detached)
        builder.externalTransition()
                .from(InteractionState.IN_PROGRESS)
                .to(InteractionState.TRANSFERRED)
                .on(InteractionFact.TRANSFER_SUCCESS)
                .perform(TRANSFER_SUCCESS_ACTION);

        // I18: IN_PROGRESS → IN_PROGRESS (internal, transfer failed, stay on original channel)
        builder.internalTransition()
                .within(InteractionState.IN_PROGRESS)
                .on(InteractionFact.TRANSFER_FAILED);

        // === Ending (graceful closure) ===

        // I19: CONNECTED → CLOSED (end requested)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.END_REQUESTED)
                .perform(END_REQUESTED_ACTION);

        // I20: IN_PROGRESS → CLOSED (end requested)
        builder.externalTransition()
                .from(InteractionState.IN_PROGRESS)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.END_REQUESTED)
                .perform(END_REQUESTED_ACTION);

        // I21: DEGRADED → CLOSED (end requested)
        builder.externalTransition()
                .from(InteractionState.DEGRADED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.END_REQUESTED)
                .perform(END_REQUESTED_ACTION);

        // I22: RECONNECTING → CLOSED (end requested)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.END_REQUESTED)
                .perform(END_REQUESTED_ACTION);

        // I23: TRANSFERRED → CLOSED (end requested)
        builder.externalTransition()
                .from(InteractionState.TRANSFERRED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.END_REQUESTED)
                .perform(END_REQUESTED_ACTION);

        // I24: CONSULT_TRANSFER → CLOSED (end requested)
        builder.externalTransition()
                .from(InteractionState.CONSULT_TRANSFER)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.END_REQUESTED)
                .perform(END_REQUESTED_ACTION);

        // I25: CLOSED → CLOSED (internal, terminal confirmation)
        builder.internalTransition()
                .within(InteractionState.CLOSED)
                .on(InteractionFact.INTERACTION_CLOSED);

        // === System Error (unrecoverable) ===

        // I26: INITIATED → CLOSED (system error)
        builder.externalTransition()
                .from(InteractionState.INITIATED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.SYSTEM_ERROR)
                .perform(SYSTEM_ERROR_ACTION);

        // I27: CONNECTED → CLOSED (system error)
        builder.externalTransition()
                .from(InteractionState.CONNECTED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.SYSTEM_ERROR)
                .perform(SYSTEM_ERROR_ACTION);

        // I28: IN_PROGRESS → CLOSED (system error)
        builder.externalTransition()
                .from(InteractionState.IN_PROGRESS)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.SYSTEM_ERROR)
                .perform(SYSTEM_ERROR_ACTION);

        // I29: DEGRADED → CLOSED (system error)
        builder.externalTransition()
                .from(InteractionState.DEGRADED)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.SYSTEM_ERROR)
                .perform(SYSTEM_ERROR_ACTION);

        // I30: RECONNECTING → CLOSED (system error)
        builder.externalTransition()
                .from(InteractionState.RECONNECTING)
                .to(InteractionState.CLOSED)
                .on(InteractionFact.SYSTEM_ERROR)
                .perform(SYSTEM_ERROR_ACTION);

        return builder.build(MACHINE_ID);
    }
}
