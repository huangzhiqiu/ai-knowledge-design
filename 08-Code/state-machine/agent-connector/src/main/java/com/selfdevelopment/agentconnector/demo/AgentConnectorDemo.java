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
 *   <li>Hold flow: CONNECTED → HELD → CONNECTED</li>
 *   <li>Reconnection flow: CONNECTED → RECONNECTING → CONNECTED</li>
 *   <li>Reconnection exhausted flow: CONNECTED → RECONNECTING → DISCONNECTED</li>
 *   <li>Transfer flow: CONNECTED → TRANSFERRING → CONNECTED</li>
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
 * Currently, agent-connector transitions do not have concrete action implementations bound.
 * To add actions, implement the core {@code Action<InteractionState, InteractionFact, AgentConnectorStateContext>}
 * interface and bind them in {@link InteractionStateMachineFactory} using {@code .perform(action)}.
 * <p>
 * For action failure handling with automatic failover, use the {@code FailoverStateMachine} decorator
 * from statemachine-core (see {@code com.selfdevelopment.statemachine.resilience.impl.FailoverStateMachine}).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Run all demos
 * AgentConnectorDemo.main(new String[]{});
 * }</pre>
 */
public class AgentConnectorDemo {

    public static void main(String[] args) {
        System.out.println("=== Agent Connector Interaction State Machine Demo ===\n");

        // Build and register the interaction state machine (must be done before creating service)
        InteractionStateMachineFactory.build();
        System.out.println("State machine registered: " + InteractionStateMachineFactory.MACHINE_ID);
        System.out.println("Action-first transition: action executes before state change\n");

        runBasicConnectionFlow();
        System.out.println();

        runHoldFlow();
        System.out.println();

        runReconnectionFlow();
        System.out.println();

        runReconnectionExhaustedFlow();
        System.out.println();

        runTransferFlow();
        System.out.println();

        runConnectionFailureFlow();
        System.out.println();

        runActionFailureDemo();
    }

    /**
     * Demo 1: Basic connection flow.
     * <p>
     * CONNECTING → CONNECTED → DISCONNECTED
     * <p>
     * This is the simplest flow: establish a connection, communicate, then close.
     */
    public static void runBasicConnectionFlow() {
        System.out.println("--- Demo 1: Basic Connection Flow ---");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        // Create interaction in CONNECTING state
        InteractionInstance interaction = createInteraction("int-001", InteractionState.CONNECTING);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");

        System.out.println("Initial state: CONNECTING");

        // Connection established
        fireAndPrint(ctx, InteractionFact.CONNECTION_ESTABLISHED, service);
        ctx = updateContextState(ctx, InteractionState.CONNECTED);

        // Active communication happens here...
        System.out.println("  [Active communication in progress...]");

        // Customer closes
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        System.out.println("Final state: DISCONNECTED");
    }

    /**
     * Demo 2: Hold flow.
     * <p>
     * CONNECTED → HELD → CONNECTED → DISCONNECTED
     * <p>
     * Agent puts customer on hold, then resumes the conversation.
     */
    public static void runHoldFlow() {
        System.out.println("--- Demo 2: Hold Flow ---");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-002", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "SG");

        System.out.println("Initial state: CONNECTED");

        // Agent puts on hold
        fireAndPrint(ctx, InteractionFact.HOLD_REQUEST, service);
        ctx = updateContextState(ctx, InteractionState.HELD);

        System.out.println("  [Customer on hold, playing hold music...]");

        // Agent resumes
        fireAndPrint(ctx, InteractionFact.HOLD_RESUME, service);
        ctx = updateContextState(ctx, InteractionState.CONNECTED);

        // Close
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        System.out.println("Final state: DISCONNECTED");
    }

    /**
     * Demo 3: Reconnection flow.
     * <p>
     * CONNECTED → RECONNECTING → CONNECTED → DISCONNECTED
     * <p>
     * Connection drops unexpectedly, system attempts to reconnect, and succeeds.
     */
    public static void runReconnectionFlow() {
        System.out.println("--- Demo 3: Reconnection Flow ---");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-003", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "UK");

        System.out.println("Initial state: CONNECTED");

        // Connection dropped
        fireAndPrint(ctx, InteractionFact.CONNECTION_DROPPED, service);
        ctx = updateContextState(ctx, InteractionState.RECONNECTING);

        System.out.println("  [Attempting reconnection (attempt 1/3)...]");

        // Reconnect succeeded
        fireAndPrint(ctx, InteractionFact.RECONNECT_SUCCESS, service);
        ctx = updateContextState(ctx, InteractionState.CONNECTED);

        System.out.println("  [Connection restored, resuming communication...]");

        // Close
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        System.out.println("Final state: DISCONNECTED");
    }

    /**
     * Demo 4: Reconnection exhausted flow.
     * <p>
     * CONNECTED → RECONNECTING → DISCONNECTED (max retries exhausted)
     */
    public static void runReconnectionExhaustedFlow() {
        System.out.println("--- Demo 4: Reconnection Exhausted Flow ---");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-004", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");

        System.out.println("Initial state: CONNECTED");

        // Connection dropped
        fireAndPrint(ctx, InteractionFact.CONNECTION_DROPPED, service);
        ctx = updateContextState(ctx, InteractionState.RECONNECTING);

        System.out.println("  [Reconnection attempt 1 failed...]");
        fireAndPrint(ctx, InteractionFact.RECONNECT_FAILED, service);

        System.out.println("  [Reconnection attempt 2 failed...]");
        fireAndPrint(ctx, InteractionFact.RECONNECT_FAILED, service);

        System.out.println("  [Reconnection attempt 3 failed, max retries exhausted...]");
        fireAndPrint(ctx, InteractionFact.RECONNECT_EXHAUSTED, service);

        System.out.println("Final state: DISCONNECTED (permanent failure)");
    }

    /**
     * Demo 5: Transfer flow (channel-level).
     * <p>
     * CONNECTED → TRANSFERRING → CONNECTED → DISCONNECTED
     * <p>
     * Channel transfer initiated (e.g., WebSocket handoff to another node),
     * transfer completes, communication continues on the new channel.
     */
    public static void runTransferFlow() {
        System.out.println("--- Demo 5: Channel Transfer Flow ---");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-005", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");

        System.out.println("Initial state: CONNECTED");

        // Start channel transfer
        fireAndPrint(ctx, InteractionFact.TRANSFER_START, service);
        ctx = updateContextState(ctx, InteractionState.TRANSFERRING);

        System.out.println("  [Transferring channel to new node...]");

        // Transfer completed
        fireAndPrint(ctx, InteractionFact.TRANSFER_COMPLETE, service);
        ctx = updateContextState(ctx, InteractionState.CONNECTED);

        System.out.println("  [Channel transferred, communication continues on new node...]");

        // Close
        fireAndPrint(ctx, InteractionFact.CLOSE_REQUEST, service);

        System.out.println("Final state: DISCONNECTED");
    }

    /**
     * Demo 6: Connection failure flow.
     * <p>
     * CONNECTING → DISCONNECTED (connection failed)
     */
    public static void runConnectionFailureFlow() {
        System.out.println("--- Demo 6: Connection Failure Flow ---");

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-006", InteractionState.CONNECTING);
        AgentConnectorStateContext ctx = createContext(interaction, "SG");

        System.out.println("Initial state: CONNECTING");

        // Connection failed (network error, auth failure, etc.)
        fireAndPrint(ctx, InteractionFact.CONNECTION_FAILED, service);

        System.out.println("Final state: DISCONNECTED (connection failed)");
    }

    /**
     * Demo 7: Action failure handling.
     * <p>
     * Demonstrates the action-first transition design principle:
     * when an action throws an exception, the state does NOT change.
     * <p>
     * In this demo, we show that invalid events (no transition defined)
     * do not change state. For actual action failure testing, use the
     * FailoverStateMachine decorator from statemachine-core.
     */
    public static void runActionFailureDemo() {
        System.out.println("--- Demo 7: Action Failure Handling (Action-First Transition) ---");

        System.out.println("Core design principle:");
        System.out.println("  - Action executes BEFORE state change");
        System.out.println("  - If action throws exception, state does NOT change");
        System.out.println("  - StateMachineException is propagated to caller");
        System.out.println();

        AgentConnectorStateMachineService service = new AgentConnectorStateMachineService();

        InteractionInstance interaction = createInteraction("int-007", InteractionState.CONNECTED);
        AgentConnectorStateContext ctx = createContext(interaction, "HK");

        System.out.println("Initial state: CONNECTED");

        // Try to fire an event that has no transition from CONNECTED
        // This demonstrates that invalid events don't change state
        try {
            StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> result =
                    service.fire(ctx, InteractionFact.CONNECTION_ESTABLISHED);
            System.out.printf("  CONNECTION_ESTABLISHED → %s (unexpected, should have failed)%n",
                    result.getTargetState());
        } catch (StateMachineException e) {
            System.out.printf("  CONNECTION_ESTABLISHED → StateMachineException: %s%n",
                    e.getMessage().length() > 80 ? e.getMessage().substring(0, 80) + "..." : e.getMessage());
            System.out.println("  State remains: CONNECTED (no transition, no state change)");
        }

        System.out.println();
        System.out.println("Note: To test actual action failure, bind an action that throws an exception");
        System.out.println("      in InteractionStateMachineFactory, or use FailoverStateMachine decorator.");
        System.out.println("      See statemachine-core/resilience/FailoverStateMachine for details.");
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
     * Fires an event and prints the transition result.
     */
    private static void fireAndPrint(
            AgentConnectorStateContext ctx,
            InteractionFact fact,
            AgentConnectorStateMachineService service) {

        InteractionState from = ctx.interaction().state();
        StateContext<InteractionState, InteractionFact, AgentConnectorStateContext> result =
                service.fire(ctx, fact);

        InteractionState to = result.getTargetState();
        boolean accepted = result.isTransitionAccepted();

        System.out.printf("  %s --(%s)--> %s [accepted=%s]%n", from, fact, to, accepted);
    }
}
