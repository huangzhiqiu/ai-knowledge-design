package com.selfdevelopment.agentconnector.demo;

import com.alibaba.cola.statemachine.impl.StateMachineException;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;

import java.util.UUID;

/**
 * Demo for the Agent Connector Interaction State Machine.
 * <p>
 * Uses COLA StateMachine. Demonstrates the complete channel/connection lifecycle.
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0).
 */
public class AgentConnectorDemo {

    public static void main(String[] args) {
        DemoLogger.printTitle("Agent Connector Interaction State Machine Demo (COLA)");

        // Build and register the interaction state machine
        InteractionStateMachineFactory.create();
        DemoLogger.printInfo("State machine registered: " + InteractionStateMachineFactory.MACHINE_ID);

        runBasicConnectionFlow();
        runHeartbeatDegradationAndReconnectionFlow();
        runGenesysConsultTransferFlow();
        runCrossChannelTransferFlow();
        runSystemErrorFlow();

        DemoLogger.printTitle("All Demos Completed Successfully");
    }

    /**
     * Demo 1: Basic connection flow.
     * INITIATED → CONNECTED → IN_PROGRESS → CLOSED
     */
    public static void runBasicConnectionFlow() {
        DemoLogger.printSection("Demo 1: Basic Connection Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-001", "GENESYS");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("INITIATED", "int-001", "GENESYS");

        // INITIATED → CONNECTED
        DemoLogger.printAction("ConnectionSuccessAction", "Register channel, start heartbeat");
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_SUCCESS, service);

        // CONNECTED → IN_PROGRESS
        DemoLogger.printAction("FirstInboundMessageReceivedAction", "Set firstMessageAt, record first response");
        ctx = fireAndPrint(ctx, InteractionFact.FIRST_INBOUND_MESSAGE_RECEIVED, service);

        // IN_PROGRESS → CLOSED
        DemoLogger.printAction("EndRequestedAction", "Send close frame, release resources");
        ctx = fireAndPrint(ctx, InteractionFact.END_REQUESTED, service);

        DemoLogger.printFinalState("CLOSED", "int-001");
        DemoLogger.printDemoComplete("Basic Connection Flow", true);
    }

    /**
     * Demo 2: Heartbeat degradation and reconnection flow.
     * CONNECTED → DEGRADED → RECONNECTING → CONNECTED
     */
    public static void runHeartbeatDegradationAndReconnectionFlow() {
        DemoLogger.printSection("Demo 2: Heartbeat Degradation & Reconnection Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-002", "WEBSOCKET");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("INITIATED", "int-002", "WEBSOCKET");

        // INITIATED → CONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_SUCCESS, service);

        // CONNECTED → DEGRADED (heartbeat miss)
        DemoLogger.printAction("HeartbeatMissAction", "Record miss, schedule reconnection");
        ctx = fireAndPrint(ctx, InteractionFact.HEARTBEAT_MISS, service);

        // DEGRADED → RECONNECTING
        DemoLogger.printAction("ReconnectAttemptAction", "Increment attempt count, initiate reconnection");
        ctx = fireAndPrint(ctx, InteractionFact.RECONNECT_ATTEMPT, service);

        // RECONNECTING → CONNECTED (reconnect success)
        DemoLogger.printAction("ReconnectSuccessAction", "Reset attempt count, restore session");
        ctx = fireAndPrint(ctx, InteractionFact.RECONNECT_SUCCESS, service);

        // CONNECTED → CLOSED
        ctx = fireAndPrint(ctx, InteractionFact.END_REQUESTED, service);

        DemoLogger.printFinalState("CLOSED", "int-002");
        DemoLogger.printDemoComplete("Heartbeat Degradation & Reconnection Flow", true);
    }

    /**
     * Demo 3: Genesys consult transfer flow (GENESYS ONLY).
     * IN_PROGRESS → CONSULT_TRANSFER → IN_PROGRESS
     */
    public static void runGenesysConsultTransferFlow() {
        DemoLogger.printSection("Demo 3: Genesys Consult Transfer Flow (GENESYS ONLY)");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-003", "GENESYS");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("INITIATED", "int-003", "GENESYS");

        // INITIATED → CONNECTED → IN_PROGRESS
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_SUCCESS, service);
        ctx = fireAndPrint(ctx, InteractionFact.FIRST_INBOUND_MESSAGE_RECEIVED, service);

        // IN_PROGRESS → CONSULT_TRANSFER
        DemoLogger.printAction("ConsultTransferStartedAction", "Set consultInFlight, initiate consult via Genesys API");
        ctx = fireAndPrint(ctx, InteractionFact.CONSULT_TRANSFER_STARTED, service);

        // CONSULT_TRANSFER → IN_PROGRESS
        DemoLogger.printAction("ConsultTransferEndedAction", "Clear consultInFlight, restore original session");
        ctx = fireAndPrint(ctx, InteractionFact.CONSULT_TRANSFER_ENDED, service);

        // IN_PROGRESS → CLOSED
        ctx = fireAndPrint(ctx, InteractionFact.END_REQUESTED, service);

        DemoLogger.printFinalState("CLOSED", "int-003");
        DemoLogger.printDemoComplete("Genesys Consult Transfer Flow", true);
    }

    /**
     * Demo 4: Cross-channel transfer flow (source detach marker).
     * IN_PROGRESS → TRANSFERRED → CLOSED
     */
    public static void runCrossChannelTransferFlow() {
        DemoLogger.printSection("Demo 4: Cross-Channel Transfer Flow (Source Detach Marker)");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-004", "WEBSOCKET");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("INITIATED", "int-004", "WEBSOCKET");

        // INITIATED → CONNECTED → IN_PROGRESS
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_SUCCESS, service);
        ctx = fireAndPrint(ctx, InteractionFact.FIRST_INBOUND_MESSAGE_RECEIVED, service);

        // IN_PROGRESS → TRANSFERRED (cross-channel transfer success, source detached)
        DemoLogger.printAction("TransferSuccessAction", "Set transferredAt, detach source interaction");
        ctx = fireAndPrint(ctx, InteractionFact.TRANSFER_SUCCESS, service);

        // TRANSFERRED → CLOSED
        DemoLogger.printAction("EndRequestedAction", "Close source interaction after transfer");
        ctx = fireAndPrint(ctx, InteractionFact.END_REQUESTED, service);

        DemoLogger.printFinalState("CLOSED", "int-004");
        DemoLogger.printDemoComplete("Cross-Channel Transfer Flow", true);
    }

    /**
     * Demo 5: System error flow (unrecoverable).
     * IN_PROGRESS → CLOSED (due to system error)
     */
    public static void runSystemErrorFlow() {
        DemoLogger.printSection("Demo 5: System Error Flow (Unrecoverable)");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-005", "GENESYS");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("INITIATED", "int-005", "GENESYS");

        // INITIATED → CONNECTED → IN_PROGRESS
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_SUCCESS, service);
        ctx = fireAndPrint(ctx, InteractionFact.FIRST_INBOUND_MESSAGE_RECEIVED, service);

        // IN_PROGRESS → CLOSED (system error)
        DemoLogger.printAction("SystemErrorAction", "Record error, trigger alerts, force close");
        ctx = fireAndPrint(ctx, InteractionFact.SYSTEM_ERROR, service);

        DemoLogger.printFinalState("CLOSED", "int-005");
        DemoLogger.printInfo("Interaction closed due to unrecoverable system error");
        DemoLogger.printDemoComplete("System Error Flow", true);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates an InteractionInstance with the given ID and channel type.
     */
    private static InteractionInstance createInteraction(String interactionId, String channelType) {
        return InteractionInstance.builder()
                .interactionId(interactionId)
                .channelType(channelType)
                .state(InteractionState.INITIATED)
                .build();
    }

    /**
     * Creates an AgentConnectorStateContext with the given interaction.
     */
    private static AgentConnectorStateContext createContext(InteractionInstance interaction) {
        return AgentConnectorStateContext.builder()
                .interaction(interaction)
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Updates the interaction state in the context and returns a new context.
     */
    private static AgentConnectorStateContext updateContextState(AgentConnectorStateContext ctx, InteractionState newState) {
        InteractionInstance updatedInteraction = ctx.interaction().withState(newState);
        return AgentConnectorStateContext.builder()
                .interaction(updatedInteraction)
                .traceId(ctx.traceId())
                .build();
    }

    /**
     * Fires an event, prints the transition result, and returns the updated context.
     */
    private static AgentConnectorStateContext fireAndPrint(
            AgentConnectorStateContext ctx,
            InteractionFact fact,
            AgentConnectorStateMachineService service) {

        InteractionState from = ctx.interaction().state();
        try {
            InteractionState to = service.fire(ctx, fact);
            DemoLogger.printTransition(from.name(), fact.name(), to.name(), true);
            return updateContextState(ctx, to);
        } catch (StateMachineException e) {
            DemoLogger.printError("Transition failed: " + e.getMessage());
            DemoLogger.printTransition(from.name(), fact.name(), from.name(), false);
            return ctx;
        }
    }
}
