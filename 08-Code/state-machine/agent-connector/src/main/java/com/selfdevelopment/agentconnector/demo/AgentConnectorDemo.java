package com.selfdevelopment.agentconnector.demo;

import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import com.selfdevelopment.agentconnector.model.InteractionInstance;
import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.exception.StateMachineException;

import java.util.UUID;

/**
 * Demo for the Agent Connector Interaction State Machine.
 * <p>
 * Demonstrates the complete channel/connection lifecycle with action execution design:
 * <ul>
 *   <li>Basic connection flow: CONNECTING → CONNECTED → DISCONNECTED</li>
 *   <li>Hold flow: CONNECTED → HELD → CONNECTED → DISCONNECTED</li>
 *   <li>Reconnection flow: CONNECTED → RECONNECTING → CONNECTED → DISCONNECTED</li>
 *   <li>Reconnection exhausted flow: CONNECTED → RECONNECTING → DISCONNECTED</li>
 *   <li>Transfer flow: CONNECTED → TRANSFERRING → CONNECTED → DISCONNECTED</li>
 *   <li>Connection failure: CONNECTING → DISCONNECTED</li>
 *   <li>Action failure handling: demonstrates action failure prevents state change</li>
 * </ul>
 *
 * <h3>Action Execution Design</h3>
 * <p>
 * The state machine framework follows the <b>action-first transition</b> principle:
 * <ul>
 *   <li>Action executes BEFORE state change</li>
 *   <li>If action throws an exception, state does NOT change</li>
 *   <li>{@link StateMachineException} is propagated to the caller</li>
 * </ul>
 * <p>
 * <b>Actions bound to transitions:</b>
 * <ul>
 *   <li>CONNECTING → CONNECTED: {@code ConnectionEstablishedAction} (register channel, start heartbeat)</li>
 *   <li>CONNECTING → DISCONNECTED: {@code ConnectionFailedAction} (record failure, cleanup resources)</li>
 *   <li>CONNECTED → RECONNECTING: {@code ConnectionDroppedAction} (pause processing, init reconnection)</li>
 *   <li>CONNECTED → DISCONNECTED: {@code CloseRequestAction} (send close frame, release resources)</li>
 *   <li>RECONNECTING → CONNECTED: {@code ReconnectSuccessAction} (resume processing, flush buffered messages)</li>
 *   <li>CONNECTED → HELD: {@code HoldRequestAction} (pause delivery, start hold music)</li>
 *   <li>HELD → CONNECTED: {@code HoldResumeAction} (stop hold music, resume delivery, flush messages)</li>
 *   <li>CONNECTED → TRANSFERRING: {@code TransferStartAction} (pause processing, establish target connection)</li>
 *   <li>TRANSFERRING → CONNECTED: {@code TransferCompleteAction} (verify integrity, resume processing, close old channel)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Run all demos
 * AgentConnectorDemo.main(new String[]{});
 * }</pre>
 */
public class AgentConnectorDemo {

    public static void main(String[] args) {
        DemoLogger.printTitle("Agent Connector Interaction State Machine Demo");

        // Build and register the interaction state machine (must be done before creating service)
        InteractionStateMachineFactory.build();
        DemoLogger.printInfo("State machine registered: " + InteractionStateMachineFactory.MACHINE_ID);
        DemoLogger.printInfo("Action-first transition: action executes before state change");

        runBasicConnectionFlow();
        runHoldFlow();
        runReconnectionFlow();
        runReconnectionExhaustedFlow();
        runTransferFlow();
        runConnectionFailureFlow();
        runActionFailureDemo();

        DemoLogger.printTitle("All Demos Completed Successfully");
    }

    /**
     * Demo 1: Basic connection flow.
     * <p>
     * CONNECTING → CONNECTED → DISCONNECTED
     */
    public static void runBasicConnectionFlow() {
        DemoLogger.printSection("Demo 1: Basic Connection Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-001", InteractionState.CONNECTING);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");
        DemoLogger.printInitialState("CONNECTING", "int-001", "HK");

        // Connection established
        DemoLogger.printAction("ConnectionEstablishedAction", "Register channel, start heartbeat, notify upstream");
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_ESTABLISHED, service);

        // Active communication
        DemoLogger.printInfo("Active communication in progress...");

        // Customer closes
        DemoLogger.printAction("CloseRequestAction", "Send close frame, flush messages, release resources");
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-001");
        DemoLogger.printDemoComplete("Basic Connection Flow", true);
    }

    /**
     * Demo 2: Hold flow.
     * <p>
     * CONNECTED → HELD → CONNECTED → DISCONNECTED
     */
    public static void runHoldFlow() {
        DemoLogger.printSection("Demo 2: Hold Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-002", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "SG");
        DemoLogger.printInitialState("CONNECTED", "int-002", "SG");

        // Agent puts on hold
        DemoLogger.printAction("HoldRequestAction", "Pause delivery, start hold music, buffer messages");
        ctx = fireAndPrint(ctx, InteractionFact.HOLD_REQUEST, service);

        DemoLogger.printInfo("Customer on hold, playing hold music...");

        // Agent resumes
        DemoLogger.printAction("HoldResumeAction", "Stop hold music, resume delivery, flush buffered messages");
        ctx = fireAndPrint(ctx, InteractionFact.HOLD_RESUME, service);

        // Close
        DemoLogger.printAction("CloseRequestAction", "Send close frame, release resources");
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-002");
        DemoLogger.printDemoComplete("Hold Flow", true);
    }

    /**
     * Demo 3: Reconnection flow.
     * <p>
     * CONNECTED → RECONNECTING → CONNECTED → DISCONNECTED
     */
    public static void runReconnectionFlow() {
        DemoLogger.printSection("Demo 3: Reconnection Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-003", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "UK");
        DemoLogger.printInitialState("CONNECTED", "int-003", "UK");

        // Connection dropped
        DemoLogger.printAction("ConnectionDroppedAction", "Pause processing, init reconnection, buffer messages");
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_DROPPED, service);

        DemoLogger.printInfo("Attempting reconnection (attempt 1/3)...");

        // Reconnect succeeded
        DemoLogger.printAction("ReconnectSuccessAction", "Register new channel, resume processing, flush buffered messages");
        ctx = fireAndPrint(ctx, InteractionFact.RECONNECT_SUCCESS, service);

        DemoLogger.printInfo("Connection restored, resuming communication...");

        // Close
        DemoLogger.printAction("CloseRequestAction", "Send close frame, release resources");
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-003");
        DemoLogger.printDemoComplete("Reconnection Flow", true);
    }

    /**
     * Demo 4: Reconnection exhausted flow.
     * <p>
     * CONNECTED → RECONNECTING → DISCONNECTED (max retries exhausted)
     */
    public static void runReconnectionExhaustedFlow() {
        DemoLogger.printSection("Demo 4: Reconnection Exhausted Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-004", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");
        DemoLogger.printInitialState("CONNECTED", "int-004", "HK");

        // Connection dropped
        DemoLogger.printAction("ConnectionDroppedAction", "Pause processing, init reconnection");
        ctx = fireAndPrint(ctx, InteractionFact.CONNECTION_DROPPED, service);

        DemoLogger.printInfo("Reconnection attempt 1 failed...");
        fireAndPrint(ctx, InteractionFact.RECONNECT_FAILED, service);

        DemoLogger.printInfo("Reconnection attempt 2 failed...");
        fireAndPrint(ctx, InteractionFact.RECONNECT_FAILED, service);

        DemoLogger.printInfo("Reconnection attempt 3 failed, max retries exhausted...");
        fireAndPrint(ctx, InteractionFact.RECONNECT_EXHAUSTED, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-004");
        DemoLogger.printInfo("Permanent failure, connection could not be restored");
        DemoLogger.printDemoComplete("Reconnection Exhausted Flow", true);
    }

    /**
     * Demo 5: Transfer flow (channel-level).
     * <p>
     * CONNECTED → TRANSFERRING → CONNECTED → DISCONNECTED
     */
    public static void runTransferFlow() {
        DemoLogger.printSection("Demo 5: Channel Transfer Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-005", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");
        DemoLogger.printInitialState("CONNECTED", "int-005", "HK");

        // Start channel transfer
        DemoLogger.printAction("TransferStartAction", "Pause processing, establish target connection, transfer state");
        ctx = fireAndPrint(ctx, InteractionFact.TRANSFER_START, service);

        DemoLogger.printInfo("Transferring channel to new node...");

        // Transfer completed
        DemoLogger.printAction("TransferCompleteAction", "Verify integrity, resume processing, close old channel");
        ctx = fireAndPrint(ctx, InteractionFact.TRANSFER_COMPLETE, service);

        DemoLogger.printInfo("Channel transferred, communication continues on new node...");

        // Close
        DemoLogger.printAction("CloseRequestAction", "Send close frame, release resources");
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-005");
        DemoLogger.printDemoComplete("Channel Transfer Flow", true);
    }

    /**
     * Demo 6: Connection failure flow.
     * <p>
     * CONNECTING → DISCONNECTED (connection failed)
     */
    public static void runConnectionFailureFlow() {
        DemoLogger.printSection("Demo 6: Connection Failure Flow");
        DemoLogger.resetCounter();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-006", InteractionState.CONNECTING);
        AgentConnectorStateContext ctx = createContext(interaction, "SG");
        DemoLogger.printInitialState("CONNECTING", "int-006", "SG");

        // Connection failed
        DemoLogger.printAction("ConnectionFailedAction", "Record failure reason, cleanup resources, trigger reconnection strategy");
        fireAndPrint(ctx, InteractionFact.CONNECTION_FAILED, service);

        DemoLogger.printFinalState("DISCONNECTED", "int-006");
        DemoLogger.printInfo("Connection failed (network error, auth failure, etc.)");
        DemoLogger.printDemoComplete("Connection Failure Flow", true);
    }

    /**
     * Demo 7: Action failure handling.
     * <p>
     * Demonstrates the action-first transition design principle:
     * when an action throws an exception, the state does NOT change.
     */
    public static void runActionFailureDemo() {
        DemoLogger.printSection("Demo 7: Action Failure Handling (Action-First Transition)");
        DemoLogger.resetCounter();

        DemoLogger.printInfo("Core design principle:");
        System.out.println("           - Action executes BEFORE state change");
        System.out.println("           - If action throws exception, state does NOT change");
        System.out.println("           - StateMachineException is propagated to caller");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-007", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");
        DemoLogger.printInitialState("CONNECTED", "int-007", "HK");

        // Try to fire an event that has no transition from CONNECTED
        DemoLogger.printInfo("Attempting invalid transition: CONNECTION_ESTABLISHED from CONNECTED");
        try {
            StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> result =
                    service.fire(ctx, InteractionFact.CONNECTION_ESTABLISHED);
            DemoLogger.printError("Unexpected success: transition should have failed");
        } catch (StateMachineException e) {
            String message = e.getMessage().length() > 80
                    ? e.getMessage().substring(0, 80) + "..."
                    : e.getMessage();
            DemoLogger.printError("StateMachineException: " + message);
            DemoLogger.printInfo("State remains: CONNECTED (no transition, no state change)");
        }

        DemoLogger.printInfo("Note: To test actual action failure, bind an action that throws an exception");
        DemoLogger.printInfo("      in InteractionStateMachineFactory, or use FailoverStateMachine decorator.");
        DemoLogger.printDemoComplete("Action Failure Handling", true);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates an InteractionInstance with the given ID and state.
     */
    private static InteractionInstance createInteraction(String interactionId, InteractionState state) {
        return InteractionInstance.builder()
                .interactionId(interactionId)
                .conversationId("conv-" + interactionId)
                .channelType("WEBSOCKET")
                .state(state)
                .needReconnect(false)
                .build();
    }

    /**
     * Creates an AgentConnectorStateContext with the given interaction and market.
     */
    private static AgentConnectorStateContext createContext(InteractionInstance interaction, String market) {
        return AgentConnectorStateContext.builder()
                .interaction(interaction)
                .market(market)
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Updates the interaction state in the context and returns a new context.
     */
    private static AgentConnectorStateContext updateContextState(
            AgentConnectorStateContext ctx, InteractionState newState) {

        InteractionInstance updatedInteraction = new InteractionInstance(
                ctx.interaction().interactionId(),
                ctx.interaction().conversationId(),
                ctx.interaction().channelType(),
                newState,
                ctx.interaction().needReconnect()
        );

        return AgentConnectorStateContext.builder()
                .interaction(updatedInteraction)
                .market(ctx.market())
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
        StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> result =
                service.fire(ctx, fact);

        InteractionState to = result.getTargetState();
        boolean accepted = result.isTransitionAccepted();

        DemoLogger.printTransition(from.name(), fact.name(), to.name(), accepted);

        return updateContextState(ctx, to);
    }
}
