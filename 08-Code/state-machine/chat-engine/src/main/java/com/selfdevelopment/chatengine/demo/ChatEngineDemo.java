package com.selfdevelopment.chatengine.demo;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.enums.EndReason;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.model.InteractionInstance;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.statemachine.core.StateContext;

/**
 * Demo for the Chat Engine Conversation State Machine.
 * <p>
 * Demonstrates the complete conversation lifecycle:
 * <ul>
 *   <li>Basic flow: INITIATED → ACTIVE → TRANSFERRED → ACTIVE → ENDING → CLOSED</li>
 *   <li>Survey flow: ACTIVE → SURVEY_IN_PROGRESS → ENDING → CLOSED</li>
 *   <li>Multi-market configuration (HK, SG, UK)</li>
 *   <li>TraceId propagation for distributed tracing</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Run the basic conversation flow demo
 * ChatEngineDemo.runBasicConversationFlow();
 *
 * // Run the survey flow demo
 * ChatEngineDemo.runSurveyFlow();
 *
 * // Run the multi-market demo
 * ChatEngineDemo.runMultiMarketDemo();
 * }</pre>
 */
public class ChatEngineDemo {

    public static void main(String[] args) {
        System.out.println("=== Chat Engine State Machine Demo ===\n");

        runBasicConversationFlow();
        System.out.println();

        runSurveyFlow();
        System.out.println();

        runMultiMarketDemo();
        System.out.println();

        runTransferFailureFlow();
    }

    /**
     * Demo 1: Basic conversation flow.
     * <p>
     * INITIATED → ACTIVE → TRANSFERRED → ACTIVE → ENDING → CLOSED
     */
    public static void runBasicConversationFlow() {
        System.out.println("--- Demo 1: Basic Conversation Flow ---");

        // 1. Create the service (stateless mode)
        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        // 2. Create a conversation instance in INITIATED state
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-001")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.INITIATED)
                .endReason(null)
                .transferOutcome(null)
                .surveyEnabled(false)
                .surveyCompleted(false)
                .build();

        // 3. Create context with trace
        CbolStateContext ctx = createContext(conversation, "HK");

        // 4. Fire events to drive the state machine
        printTransition(ctx, ConversationFact.CUSTOMER_CONNECT, service);
        printTransition(ctx, ConversationFact.AGENT_ATTACHED, service);
        printTransition(ctx, ConversationFact.TRANSFER_REQUEST, service);
        printTransition(ctx, ConversationFact.TRANSFER_CONNECTED, service);
        printTransition(ctx, ConversationFact.CUSTOMER_CLOSE, service);
        printTransition(ctx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT, service);

        System.out.println("Final state: CLOSED");
    }

    /**
     * Demo 2: Survey flow (survey as in-progress state).
     * <p>
     * ACTIVE → SURVEY_IN_PROGRESS → ENDING → CLOSED
     * <p>
     * When survey is enabled, closing the conversation enters SURVEY_IN_PROGRESS
     * instead of directly going to ENDING. The survey is controlled by the state
     * machine flow (SURVEY_START → SURVEY_IN_PROGRESS → SURVEY_COMPLETE → ENDING).
     */
    public static void runSurveyFlow() {
        System.out.println("--- Demo 2: Survey Flow (Survey as In-Progress) ---");

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        // Create conversation with survey enabled
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-002")
                .market("SG")
                .tenantId("tenant-sg-001")
                .state(ConversationState.ACTIVE)
                .surveyEnabled(true)
                .surveyCompleted(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "SG");

        System.out.println("Initial state: ACTIVE (surveyEnabled=true)");

        // closeConversation automatically routes to SURVEY_START when survey is enabled
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.closeConversation(ctx);
        System.out.printf("  closeConversation() → %s (fact: SURVEY_START)%n", result.getTargetState());

        // Update context with new state
        ctx = updateContextState(ctx, result.getTargetState());

        // Complete the survey
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
     * Demo 4: Transfer failure flow.
     * <p>
     * ACTIVE → TRANSFERRED → (transfer failed) → INITIATED (reset)
     * <p>
     * When a transfer fails, the conversation resets to INITIATED state
     * so it can be re-routed to another agent or AI bot.
     */
    public static void runTransferFailureFlow() {
        System.out.println("--- Demo 4: Transfer Failure Flow ---");

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-004")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.ACTIVE)
                .surveyEnabled(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "HK");

        System.out.println("Initial state: ACTIVE");

        // Transfer request
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(ctx, ConversationFact.TRANSFER_REQUEST);
        System.out.printf("  TRANSFER_REQUEST → %s%n", result.getTargetState());
        ctx = updateContextState(ctx, result.getTargetState());

        // Transfer failed → reset to INITIATED
        result = service.fire(ctx, ConversationFact.TRANSFER_FAILED);
        System.out.printf("  TRANSFER_FAILED → %s (conversation reset for re-routing)%n", result.getTargetState());

        System.out.println("Final state: INITIATED (ready for re-routing)");
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates a CbolStateContext with the given conversation and market.
     */
    private static CbolStateContext createContext(ConversationInstance conversation, String market) {
        TraceContext traceContext = TraceContext.generate();

        InteractionInstance interaction = InteractionInstance.builder()
                .interactionId("int-" + conversation.conversationId())
                .conversationId(conversation.conversationId())
                .channelType("WEBCHAT")
                .state("CONNECTED")
                .needReconnect(false)
                .build();

        StateMachineMarketConfig marketConfig = StateMachineMarketConfig.defaultConfig();

        return CbolStateContext.builder()
                .conversation(conversation)
                .interaction(interaction)
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
                .interaction(ctx.interaction())
                .marketConfig(ctx.marketConfig())
                .traceContext(ctx.traceContext())
                .build();
    }

    /**
     * Fires an event and prints the transition result.
     */
    private static void printTransition(
            CbolStateContext ctx,
            ConversationFact fact,
            ChatEngineStateMachineService service) {

        ConversationState from = ctx.conversation().state();
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(ctx, fact);

        ConversationState to = result.getTargetState();
        boolean accepted = result.isTransitionAccepted();

        System.out.printf("  %s --(%s)--> %s [accepted=%s]%n", from, fact, to, accepted);
    }
}
