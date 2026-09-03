package com.selfdevelopment.chatengine.demo;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.exception.StateMachineException;

/**
 * Demo for the Chat Engine Conversation State Machine.
 * <p>
 * Demonstrates the complete conversation lifecycle with action execution:
 * <ul>
 *   <li>Basic flow: NEW → INITIATED → IN_PROGRESS → TRANSFERRED → IN_PROGRESS → ENDING → CLOSED</li>
 *   <li>Survey flow: IN_PROGRESS (messaging) → IN_PROGRESS (survey, internal) → ENDING → CLOSED</li>
 *   <li>Multi-market configuration (HK, SG, UK)</li>
 *   <li>Transfer failure flow with TransferFailedAction execution</li>
 *   <li>Action failure handling: action failure prevents state transition</li>
 * </ul>
 *
 * <h3>Action Execution Design</h3>
 * <p>
 * Each state transition has an associated action that must execute successfully
 * before the state changes. If an action throws an exception, the state does NOT
 * change and a {@link StateMachineException} is propagated.
 * <p>
 * <b>Actions bound to transitions:</b>
 * <ul>
 *   <li>NEW → INITIATED: {@code ConversationInitAction} (validate config, allocate resources)</li>
 *   <li>INITIATED → IN_PROGRESS: {@code CustomerConnectAction} (create record, send welcome)</li>
 *   <li>IN_PROGRESS → TRANSFERRED: {@code TransferRequestAction} (route to agent queue)</li>
 *   <li>TRANSFERRED → INITIATED: {@code TransferFailedAction} (cleanup, re-route)</li>
 *   <li>IN_PROGRESS → ENDING: {@code CustomerCloseAction} (close conversation, release resources)</li>
 *   <li>IN_PROGRESS → IN_PROGRESS (internal): {@code SurveyStartAction} (send survey invitation)</li>
 *   <li>IN_PROGRESS → ENDING: {@code SurveyCompleteAction} (save results, calculate score)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Run all demos
 * ChatEngineDemo.main(new String[]{});
 * }</pre>
 */
public class ChatEngineDemo {

    public static void main(String[] args) {
        System.out.println("=== Chat Engine State Machine Demo ===\n");

        // Build and register the conversation state machine (must be done before creating service)
        ConversationStateMachineFactory.build();
        System.out.println("State machine registered: " + ConversationStateMachineFactory.MACHINE_ID);
        System.out.println("Action execution: each transition requires successful action execution\n");

        runBasicConversationFlow();
        System.out.println();

        runSurveyFlow();
        System.out.println();

        runMultiMarketDemo();
        System.out.println();

        runTransferFailureFlow();
        System.out.println();

        runActionFailureDemo();
    }

    /**
     * Demo 1: Basic conversation flow with action execution.
     * <p>
     * NEW → INITIATED → IN_PROGRESS → TRANSFERRED → IN_PROGRESS → ENDING → CLOSED
     * <p>
     * NEW is the initial state: conversation record created (customer opened chat window),
     * but no messages exchanged yet. CONVERSATION_INITIATED triggers preparation work
     * (ConversationInitAction: validate config, allocate resources, setup routing).
     * Then CUSTOMER_CONNECT transitions to IN_PROGRESS (CustomerConnectAction).
     * <p>
     * Each transition executes its associated action:
     * <ul>
     *   <li>CONVERSATION_INITIATED: ConversationInitAction</li>
     *   <li>CUSTOMER_CONNECT: CustomerConnectAction</li>
     *   <li>TRANSFER_REQUEST: TransferRequestAction</li>
     *   <li>CUSTOMER_CLOSE: CustomerCloseAction</li>
     * </ul>
     */
    public static void runBasicConversationFlow() {
        System.out.println("--- Demo 1: Basic Conversation Flow (with Action Execution) ---");

        // 1. Create the service (stateless mode)
        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        // 2. Create a conversation instance in NEW state (initial state)
        //    NEW: conversation record created, but initialization not done yet
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-001")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.NEW)
                .surveyEnabled(false)
                .surveyCompleted(false)
                .build();

        // 3. Create context with trace
        CbolStateContext ctx = createContext(conversation, "HK");

        // 4. Fire events to drive the state machine
        //    Each transition executes its associated action
        System.out.println("  Initial state: NEW (conversation created, waiting for initialization)");
        System.out.println("  [Action: ConversationInitAction - validate config, allocate resources, setup routing]");
        ctx = printTransition(ctx, ConversationFact.CONVERSATION_INITIATED, service);

        System.out.println("  [Action: CustomerConnectAction - create record, send welcome]");
        ctx = printTransition(ctx, ConversationFact.CUSTOMER_CONNECT, service);

        ctx = printTransition(ctx, ConversationFact.AGENT_ATTACHED, service);

        System.out.println("  [Action: TransferRequestAction - route to agent queue]");
        ctx = printTransition(ctx, ConversationFact.TRANSFER_REQUEST, service);

        ctx = printTransition(ctx, ConversationFact.TRANSFER_CONNECTED, service);

        System.out.println("  [Action: CustomerCloseAction - close conversation, release resources]");
        ctx = printTransition(ctx, ConversationFact.CUSTOMER_CLOSE, service);

        ctx = printTransition(ctx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT, service);

        System.out.println("Final state: CLOSED");
    }

    /**
     * Demo 2: Survey flow with action execution.
     * <p>
     * IN_PROGRESS (messaging) → IN_PROGRESS (survey, internal) → ENDING → CLOSED
     * <p>
     * Actions:
     * <ul>
     *   <li>SURVEY_START: SurveyStartAction (send survey invitation)</li>
     *   <li>SURVEY_COMPLETE: SurveyCompleteAction (save results, calculate score)</li>
     * </ul>
     */
    public static void runSurveyFlow() {
        System.out.println("--- Demo 2: Survey Flow (Survey as In-Progress with Actions) ---");

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        // Create conversation with survey enabled
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-002")
                .market("SG")
                .tenantId("tenant-sg-001")
                .state(ConversationState.IN_PROGRESS)
                .surveyEnabled(true)
                .surveyCompleted(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "SG");

        System.out.println("Initial state: IN_PROGRESS (surveyEnabled=true)");

        // closeConversation automatically routes to SURVEY_START when survey is enabled
        System.out.println("  [Action: SurveyStartAction - send survey invitation, set timeout]");
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.closeConversation(ctx);
        System.out.printf("  closeConversation() → %s (fact: SURVEY_START)%n", result.getTargetState());

        // Update context with new state
        ctx = updateContextState(ctx, result.getTargetState());

        // Complete the survey
        System.out.println("  [Action: SurveyCompleteAction - save results, calculate NPS/CSAT]");
        result = service.completeSurvey(ctx);
        System.out.printf("  completeSurvey() → %s (fact: SURVEY_COMPLETE)%n", result.getTargetState());

        ctx = updateContextState(ctx, result.getTargetState());

        // Ending grace timeout → CLOSED
        result = service.fire(ctx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT);
        System.out.printf("  SYS_ENDING_GRACE_TIMEOUT → %s%n", result.getTargetState());

        System.out.println("Final state: CLOSED");
    }

    /**
     * Demo 3: Multi-market configuration.
     * <p>
     * Demonstrates how different markets (HK, SG, UK) can have different
     * timeout configurations and feature flags.
     */
    public static void runMultiMarketDemo() {
        System.out.println("--- Demo 3: Multi-Market Configuration ---");

        // HK market: default config, survey disabled
        StateMachineMarketConfig hkConfig = StateMachineMarketConfig.defaultConfig();
        System.out.printf("HK market: idleTimeout=%ds, transferTimeout=%ds, surveyEnabled=%s%n",
                hkConfig.customerIdleSeconds(), hkConfig.transferTimeoutSeconds(), hkConfig.surveyEnabled());

        // SG market: survey enabled, shorter timeouts
        StateMachineMarketConfig sgConfig = StateMachineMarketConfig.builder()
                .customerIdleSeconds(180)
                .transferTimeoutSeconds(120)
                .endingGraceSeconds(60)
                .surveyEnabled(true)
                .transferEnabled(true)
                .genesysEnabled(true)
                .fallbackRoutingStrategy("QUEUE")
                .build();
        System.out.printf("SG market: idleTimeout=%ds, transferTimeout=%ds, surveyEnabled=%s, genesysEnabled=%s%n",
                sgConfig.customerIdleSeconds(), sgConfig.transferTimeoutSeconds(),
                sgConfig.surveyEnabled(), sgConfig.genesysEnabled());

        // UK market: transfer disabled, different fallback strategy
        StateMachineMarketConfig ukConfig = StateMachineMarketConfig.builder()
                .customerIdleSeconds(600)
                .transferTimeoutSeconds(300)
                .endingGraceSeconds(180)
                .surveyEnabled(false)
                .transferEnabled(false)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("AI_BOT")
                .build();
        System.out.printf("UK market: idleTimeout=%ds, transferEnabled=%s, fallbackStrategy=%s%n",
                ukConfig.customerIdleSeconds(), ukConfig.transferEnabled(), ukConfig.fallbackRoutingStrategy());

        System.out.println("Multi-market configuration allows per-market behavior control");
    }

    /**
     * Demo 4: Transfer failure flow with TransferFailedAction execution.
     * <p>
     * IN_PROGRESS → TRANSFERRED → (transfer failed) → INITIATED (reset)
     * <p>
     * When a transfer fails, TransferFailedAction executes (records failure,
     * cleans up state, triggers re-routing) and the conversation resets to
     * INITIATED state for re-routing.
     */
    public static void runTransferFailureFlow() {
        System.out.println("--- Demo 4: Transfer Failure Flow (with TransferFailedAction) ---");

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-004")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.IN_PROGRESS)
                .surveyEnabled(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "HK");

        System.out.println("Initial state: IN_PROGRESS");

        // Transfer request
        System.out.println("  [Action: TransferRequestAction - request routing]");
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(ctx, ConversationFact.TRANSFER_REQUEST);
        System.out.printf("  TRANSFER_REQUEST → %s%n", result.getTargetState());
        ctx = updateContextState(ctx, result.getTargetState());

        // Transfer failed → reset to INITIATED
        System.out.println("  [Action: TransferFailedAction - record failure, cleanup, trigger re-routing]");
        result = service.fire(ctx, ConversationFact.TRANSFER_FAILED);
        System.out.printf("  TRANSFER_FAILED → %s (conversation reset for re-routing)%n", result.getTargetState());

        System.out.println("Final state: INITIATED (ready for re-routing)");
    }

    /**
     * Demo 5: Action failure handling.
     * <p>
     * Demonstrates that when an action throws an exception, the state does NOT change.
     * This is the core design principle: "from A to B requires successful action execution".
     * <p>
     * In this demo, we use a custom state machine with a failing action to show that
     * action failure prevents state transition.
     */
    public static void runActionFailureDemo() {
        System.out.println("--- Demo 5: Action Failure Handling (Action Failure Prevents State Change) ---");

        System.out.println("Core design principle:");
        System.out.println("  - Action executes BEFORE state change");
        System.out.println("  - If action throws exception, state does NOT change");
        System.out.println("  - StateMachineException is propagated to caller");
        System.out.println();

        // Demonstrate with the actual state machine using an invalid event
        // This shows that when no transition is found, the state doesn't change
        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-005")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.IN_PROGRESS)
                .surveyEnabled(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "HK");

        System.out.println("Initial state: IN_PROGRESS");

        // Try to fire an event that has no transition from IN_PROGRESS
        // This demonstrates that invalid events don't change state
        try {
            StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                    service.fire(ctx, ConversationFact.SURVEY_COMPLETE);
            System.out.printf("  SURVEY_COMPLETE → %s (unexpected, should have failed)%n", result.getTargetState());
        } catch (StateMachineException e) {
            System.out.printf("  SURVEY_COMPLETE → StateMachineException: %s%n", e.getMessage());
            System.out.println("  State remains: IN_PROGRESS (no transition, no state change)");
        }

        System.out.println();
        System.out.println("Note: To test action failure specifically, use FailoverStateMachine decorator");
        System.out.println("      which catches action exceptions and triggers a failover event.");
        System.out.println("      See statemachine-core/resilience/FailoverStateMachine for details.");
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates a CbolStateContext with the given conversation and market.
     * <p>
     * Note: InteractionInstance is intentionally NOT included here.
     * Conversation (chat-engine) and Interaction (agent-connector) are
     * independent state machines that communicate via events.
     */
    private static CbolStateContext createContext(ConversationInstance conversation, String market) {
        TraceContext traceContext = TraceContext.generate();

        StateMachineMarketConfig marketConfig = StateMachineMarketConfig.defaultConfig();

        return CbolStateContext.builder()
                .conversation(conversation)
                .marketConfig(marketConfig)
                .traceContext(traceContext)
                .build();
    }

    /**
     * Updates the conversation state in the context and returns a new context.
     */
    private static CbolStateContext updateContextState(CbolStateContext ctx, ConversationState newState) {
        ConversationInstance updatedConversation = ctx.conversation().withState(newState);
        return CbolStateContext.builder()
                .conversation(updatedConversation)
                .marketConfig(ctx.marketConfig())
                .traceContext(ctx.traceContext())
                .build();
    }

    /**
     * Fires an event, prints the transition result, and returns the updated context.
     */
    private static CbolStateContext printTransition(
            CbolStateContext ctx,
            ConversationFact fact,
            ChatEngineStateMachineService service) {

        ConversationState from = ctx.conversation().state();
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(ctx, fact);

        ConversationState to = result.getTargetState();
        boolean accepted = result.isTransitionAccepted();

        System.out.printf("  %s --(%s)--> %s [accepted=%s]%n", from, fact, to, accepted);

        // Return updated context with new state
        return updateContextState(ctx, to);
    }
}
