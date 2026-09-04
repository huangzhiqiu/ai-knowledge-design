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
 */
public class AgentConnectorDemo {

    public static void main(String[] args) {
        DemoLogger.printTitle("Agent Connector Interaction State Machine Demo (COLA)");

        // Build and register the interaction state machine
        InteractionStateMachineFactory.create();
        DemoLogger.printInfo("State machine registered: " + InteractionStateMachineFactory.MACHINE_ID);

        runBasicConnectionFlow();
        runHoldFlow();
        runReconnectionFlow();
        runTransferFlow();

        DemoLogger.printTitle("All Demos Completed Successfully");
    }

    /**
     * Demo 1: Basic connection flow.
     * CONNECTING → CONNECTED → DISCONNECTED
     */
    public static void runBasicConnectionFlow() {
        DemoLogger.printSection("Demo 1: Basic Connection Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-001", "GENESYS");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("CONNECTING", "int-001", "GENESYS");

        // CONNECTING → CONNECTED
        DemoLogger.printAction("ConnectionEstablishedAction", "Register channel, start heartbeat");
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_ESTABLISHED, service);

        // CONNECTED → DISCONNECTED
        DemoLogger.printAction("CloseRequestAction", "Send close frame, release resources");
        ctx = fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-001");
        DemoLogger.printDemoComplete("Basic Connection Flow", true);
    }

    /**
     * Demo 2: Hold flow.
     * CONNECTING → CONNECTED → HELD → CONNECTED → DISCONNECTED
     */
    public static void runHoldFlow() {
        DemoLogger.printSection("Demo 2: Hold Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-002", "WEBSOCKET");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("CONNECTING", "int-002", "WEBSOCKET");

        // CONNECTING → CONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_ESTABLISHED, service);

        // CONNECTED → HELD
        DemoLogger.printAction("HoldRequestAction", "Pause delivery, start hold music");
        ctx = fireAndPrint(ctx, InteractionFact.HOLD_REQUEST, service);

        // HELD → CONNECTED
        DemoLogger.printAction("HoldResumeAction", "Stop hold music, resume delivery, flush messages");
        ctx = fireAndPrint(ctx, InteractionFact.HOLD_RESUME, service);

        // CONNECTED → DISCONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-002");
        DemoLogger.printDemoComplete("Hold Flow", true);
    }

    /**
     * Demo 3: Reconnection flow.
     * CONNECTING → CONNECTED → RECONNECTING → CONNECTED → DISCONNECTED
     */
    public static void runReconnectionFlow() {
        DemoLogger.printSection("Demo 3: Reconnection Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-003", "GENESYS");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("CONNECTING", "int-003", "GENESYS");

        // CONNECTING → CONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_ESTABLISHED, service);

        // CONNECTED → RECONNECTING
        DemoLogger.printAction("ConnectionDroppedAction", "Pause processing, init reconnection");
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_DROPPED, service);

        // RECONNECTING → CONNECTED
        DemoLogger.printAction("ReconnectSuccessAction", "Resume processing, flush buffered messages");
        ctx = fireAndPrint(ctx, InteractionFact.RECONNECT_SUCCESS, service);

        // CONNECTED → DISCONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-003");
        DemoLogger.printDemoComplete("Reconnection Flow", true);
    }

    /**
     * Demo 4: Transfer flow (channel-level).
     * CONNECTING → CONNECTED → TRANSFERRING → CONNECTED → DISCONNECTED
     */
    public static void runTransferFlow() {
        DemoLogger.printSection("Demo 4: Transfer Flow (Channel-level)");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-004", "GENESYS");
        AgentConnectorStateContext ctx = createContext(interaction);
        DemoLogger.printInitialState("CONNECTING", "int-004", "GENESYS");

        // CONNECTING → CONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_ESTABLISHED, service);

        // CONNECTED → TRANSFERRING
        DemoLogger.printAction("TransferStartAction", "Pause processing, establish target connection");
        ctx = fireAndPrint(ctx, InteractionFact.TRANSFER_START, service);

        // TRANSFERRING → CONNECTED
        DemoLogger.printAction("TransferCompleteAction", "Verify integrity, resume processing, close old channel");
        ctx = fireAndPrint(ctx, InteractionFact.TRANSFER_COMPLETE, service);

        // CONNECTED → DISCONNECTED
        ctx = fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-004");
        DemoLogger.printDemoComplete("Transfer Flow", true);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private static InteractionInstance createInteraction(String interactionId, String channelType) {
        return InteractionInstance.builder()
                .interactionId(interactionId)
                .channelType(channelType)
                .state(InteractionState.CONNECTING)
                .build();
    }

    private static AgentConnectorStateContext createContext(InteractionInstance interaction) {
        String traceId = UUID.randomUUID().toString();
        return AgentConnectorStateContext.builder()
                .interaction(interaction)
                .traceId(traceId)
                .build();
    }

    private static AgentConnectorStateContext updateContextState(
            AgentConnectorStateContext ctx, InteractionState newState) {
        InteractionInstance updated = new InteractionInstance(
                ctx.interaction().interactionId(),
                ctx.interaction().conversationId(),
                ctx.interaction().channelType(),
                newState,
                ctx.interaction().needReconnect()
        );
        return AgentConnectorStateContext.builder()
                .interaction(updated)
                .traceId(ctx.traceId())
                .market(ctx.market())
                .build();
    }

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
