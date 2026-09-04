package com.selfdevelopment.chatengine.demo;

import com.alibaba.cola.statemachine.impl.StateMachineException;
import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;

/**
 * Demo for the Chat Engine Conversation State Machine.
 * <p>
 * Uses COLA StateMachine. Demonstrates the complete conversation lifecycle with action execution.
 */
public class ChatEngineDemo {

    public static void main(String[] args) {
        DemoLogger.printTitle("Chat Engine Conversation State Machine Demo (COLA)");

        // Build and register the conversation state machine
        ConversationStateMachineFactory.build();
        DemoLogger.printInfo("State machine registered: " + ConversationStateMachineFactory.MACHINE_ID);

        runBasicConversationFlow();
        runSurveyFlow();
        runMultiMarketDemo();
        runTransferFailureFlow();

        DemoLogger.printTitle("All Demos Completed Successfully");
    }

    /**
     * Demo 1: Basic conversation flow with action execution.
     * NEW → INITIATED → IN_PROGRESS → TRANSFERRED → IN_PROGRESS → ENDING → CLOSED
     */
    public static void runBasicConversationFlow() {
        DemoLogger.printSection("Demo 1: Basic Conversation Flow (with Action Execution)");
        DemoLogger.resetCounter();

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-001")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.NEW)
                .surveyEnabled(false)
                .surveyCompleted(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "HK");
        DemoLogger.printInitialState("NEW", "conv-001", "HK");

        // NEW → INITIATED
        DemoLogger.printAction("ConversationInitAction", "Validate config, allocate resources, setup routing");
        ctx = fireAndPrint(ctx, ConversationFact.CONVERSATION_INITIATED, service);

        // INITIATED → IN_PROGRESS
        DemoLogger.printAction("CustomerConnectAction", "Create record, send welcome message");
        ctx = fireAndPrint(ctx, ConversationFact.CUSTOMER_CONNECT, service);

        // IN_PROGRESS → TRANSFERRED
        DemoLogger.printAction("TransferRequestAction", "Route to agent queue, initiate transfer");
        ctx = fireAndPrint(ctx, ConversationFact.TRANSFER_REQUEST, service);

        // TRANSFERRED → IN_PROGRESS
        DemoLogger.printInfo("Transfer connected");
        ctx = fireAndPrint(ctx, ConversationFact.TRANSFER_CONNECTED, service);

        // IN_PROGRESS → ENDING
        DemoLogger.printAction("CustomerCloseAction", "Close conversation, release resources");
        ctx = fireAndPrint(ctx, ConversationFact.CUSTOMER_CLOSE, service);

        // ENDING → CLOSED
        DemoLogger.printInfo("Ending grace timeout, finalizing conversation");
        ctx = fireAndPrint(ctx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT, service);

        DemoLogger.printFinalState("CLOSED", "conv-001");
        DemoLogger.printDemoComplete("Basic Conversation Flow", true);
    }

    /**
     * Demo 2: Survey flow with action execution.
     * IN_PROGRESS (messaging) → IN_PROGRESS (survey, internal) → ENDING → CLOSED
     */
    public static void runSurveyFlow() {
        DemoLogger.printSection("Demo 2: Survey Flow (Survey as In-Progress Sub-phase)");
        DemoLogger.resetCounter();

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-002")
                .market("SG")
                .tenantId("tenant-sg-001")
                .state(ConversationState.IN_PROGRESS)
                .surveyEnabled(true)
                .surveyCompleted(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "SG");
        DemoLogger.printInitialState("IN_PROGRESS", "conv-002", "SG");

        // IN_PROGRESS → IN_PROGRESS (internal): survey starts
        DemoLogger.printAction("SurveyStartAction", "Send survey invitation, set survey timeout");
        ctx = fireAndPrint(ctx, ConversationFact.SURVEY_START, service);

        // IN_PROGRESS → ENDING: survey completes
        DemoLogger.printAction("SurveyCompleteAction", "Save results, calculate NPS/CSAT score");
        ctx = fireAndPrint(ctx, ConversationFact.SURVEY_COMPLETE, service);

        // ENDING → CLOSED
        DemoLogger.printInfo("Ending grace timeout, finalizing conversation");
        ctx = fireAndPrint(ctx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT, service);

        DemoLogger.printFinalState("CLOSED", "conv-002");
        DemoLogger.printDemoComplete("Survey Flow", true);
    }

    /**
     * Demo 3: Multi-market configuration.
     */
    public static void runMultiMarketDemo() {
        DemoLogger.printSection("Demo 3: Multi-Market Configuration");
        DemoLogger.resetCounter();

        // HK market: default config
        StateMachineMarketConfig hkConfig = StateMachineMarketConfig.defaultConfig();
        DemoLogger.printInfo("HK Market: default configuration");
        System.out.printf("           idleTimeout=%ds, transferTimeout=%ds, surveyEnabled=%s%n",
                hkConfig.customerIdleSeconds(), hkConfig.transferTimeoutSeconds(), hkConfig.surveyEnabled());

        // SG market: survey enabled
        StateMachineMarketConfig sgConfig = StateMachineMarketConfig.builder()
                .customerIdleSeconds(180)
                .transferTimeoutSeconds(120)
                .endingGraceSeconds(60)
                .surveyEnabled(true)
                .transferEnabled(true)
                .genesysEnabled(true)
                .fallbackRoutingStrategy("QUEUE")
                .build();
        DemoLogger.printInfo("SG Market: survey enabled, shorter timeouts, Genesys enabled");
        System.out.printf("           idleTimeout=%ds, transferTimeout=%ds, surveyEnabled=%s, genesysEnabled=%s%n",
                sgConfig.customerIdleSeconds(), sgConfig.transferTimeoutSeconds(),
                sgConfig.surveyEnabled(), sgConfig.genesysEnabled());

        // UK market: transfer disabled
        StateMachineMarketConfig ukConfig = StateMachineMarketConfig.builder()
                .customerIdleSeconds(600)
                .transferTimeoutSeconds(300)
                .endingGraceSeconds(180)
                .surveyEnabled(false)
                .transferEnabled(false)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("AI_BOT")
                .build();
        DemoLogger.printInfo("UK Market: transfer disabled, AI_BOT fallback strategy");
        System.out.printf("           idleTimeout=%ds, transferEnabled=%s, fallbackStrategy=%s%n",
                ukConfig.customerIdleSeconds(), ukConfig.transferEnabled(), ukConfig.fallbackRoutingStrategy());

        DemoLogger.printInfo("Multi-market configuration allows per-market behavior control");
        DemoLogger.printDemoComplete("Multi-Market Configuration", true);
    }

    /**
     * Demo 4: Transfer failure flow with TransferFailedAction execution.
     * IN_PROGRESS → TRANSFERRED → (transfer failed) → INITIATED (reset)
     */
    public static void runTransferFailureFlow() {
        DemoLogger.printSection("Demo 4: Transfer Failure Flow (with TransferFailedAction)");
        DemoLogger.resetCounter();

        ChatEngineStateMachineService service = new ChatEngineStateMachineService();

        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-004")
                .market("HK")
                .tenantId("tenant-hk-001")
                .state(ConversationState.IN_PROGRESS)
                .surveyEnabled(false)
                .build();

        CbolStateContext ctx = createContext(conversation, "HK");
        DemoLogger.printInitialState("IN_PROGRESS", "conv-004", "HK");

        // Transfer request
        DemoLogger.printAction("TransferRequestAction", "Request routing to agent queue");
        ctx = fireAndPrint(ctx, ConversationFact.TRANSFER_REQUEST, service);

        // Transfer failed → reset to INITIATED
        DemoLogger.printAction("TransferFailedAction", "Record failure, cleanup state, trigger re-routing");
        ctx = fireAndPrint(ctx, ConversationFact.TRANSFER_FAILED, service);

        DemoLogger.printFinalState("INITIATED", "conv-004");
        DemoLogger.printInfo("Conversation reset to INITIATED, ready for re-routing");
        DemoLogger.printDemoComplete("Transfer Failure Flow", true);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates a CbolStateContext with the given conversation and market.
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
    private static CbolStateContext fireAndPrint(
            CbolStateContext ctx,
            ConversationFact fact,
            ChatEngineStateMachineService service) {

        ConversationState from = ctx.conversation().state();
        try {
            ConversationState to = service.fire(ctx, fact);
            DemoLogger.printTransition(from.name(), fact.name(), to.name(), true);
            return updateContextState(ctx, to);
        } catch (StateMachineException e) {
            DemoLogger.printError("Transition failed: " + e.getMessage());
            DemoLogger.printTransition(from.name(), fact.name(), from.name(), false);
            return ctx;
        }
    }
}
