package com.selfdevelopment.chatengine.demo;

import com.alibaba.cola.statemachine.impl.StateMachineException;
import com.selfdevelopment.chatengine.action.ConversationActionService;
import com.selfdevelopment.chatengine.action.ending.AllInteractionsEndedAction;
import com.selfdevelopment.chatengine.action.ending.EndingActionsCompletedAction;
import com.selfdevelopment.chatengine.action.ending.EndingStartedAction;
import com.selfdevelopment.chatengine.action.ending.EndingTimeoutAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferCompletedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferFailedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferStartedAction;
import com.selfdevelopment.chatengine.action.genesys.ConsultTransferEndedAction;
import com.selfdevelopment.chatengine.action.genesys.ConsultTransferStartedAction;
import com.selfdevelopment.chatengine.action.lifecycle.InboundMessageReceivedAction;
import com.selfdevelopment.chatengine.action.lifecycle.InteractionBecameActiveAction;
import com.selfdevelopment.chatengine.action.lifecycle.SessionStartedAction;
import com.selfdevelopment.chatengine.action.survey.SurveySkippedAction;
import com.selfdevelopment.chatengine.action.survey.SurveySubmittedAction;
import com.selfdevelopment.chatengine.action.survey.SurveyTimeoutAction;
import com.selfdevelopment.chatengine.action.system.CustomerIdleTimeoutAction;
import com.selfdevelopment.chatengine.action.system.DownstreamUnavailableAction;
import com.selfdevelopment.chatengine.action.system.SystemErrorAction;
import com.selfdevelopment.chatengine.action.transfer.SourceInteractionTransferredAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionConnectFailedAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionConnectedAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionInitiatedAction;
import com.selfdevelopment.chatengine.action.transfer.TransferTimeoutAction;
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
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0):
 * - States: NEW, INITIATED, ACTIVE, IN_PROGRESS, TRANSFERRED, ENDING, CLOSED
 * - Transfer failure/timeout does NOT rollback, returns directly to INITIATED
 * - Survey is field-based in ENDING, not a separate state
 */
public class ChatEngineDemo {

    public static void main(String[] args) {
        DemoLogger.printTitle("Chat Engine Conversation State Machine Demo (COLA)");

        // Build and register the conversation state machine with default Actions
        java.util.Map<ConversationFact, com.alibaba.cola.statemachine.Action<ConversationState, ConversationFact, CbolStateContext>> actions = createDefaultActions();
        ConversationStateMachineFactory.create(actions);
        DemoLogger.printInfo("State machine registered: " + ConversationStateMachineFactory.MACHINE_ID);

        runBasicConversationFlow();
        runSurveyFlow();
        runMultiMarketDemo();
        runTransferFailureFlow();

        DemoLogger.printTitle("All Demos Completed Successfully");
    }

    /**
     * Creates default Action instances for the demo.
     * <p>
     * These are simple instantiations without any dependency injection.
     * For Spring-managed Actions with dependencies, use the Spring configuration.
     *
     * @return the Map of ConversationFact to Action with default Action instances
     */
    private static java.util.Map<ConversationFact, com.alibaba.cola.statemachine.Action<ConversationState, ConversationFact, CbolStateContext>> createDefaultActions() {
        java.util.Map<ConversationFact, com.alibaba.cola.statemachine.Action<ConversationState, ConversationFact, CbolStateContext>> actions =
                new java.util.EnumMap<>(ConversationFact.class);

        // LIFECYCLE
        actions.put(ConversationFact.SESSION_STARTED, new SessionStartedAction());
        actions.put(ConversationFact.INTERACTION_BECAME_ACTIVE, new InteractionBecameActiveAction());
        actions.put(ConversationFact.INBOUND_MESSAGE_RECEIVED, new InboundMessageReceivedAction());
        actions.put(ConversationFact.ALL_INTERACTIONS_ENDED, new AllInteractionsEndedAction());

        // TRANSFER
        actions.put(ConversationFact.SOURCE_INTERACTION_TRANSFERRED, new SourceInteractionTransferredAction());
        actions.put(ConversationFact.TARGET_INTERACTION_INITIATED, new TargetInteractionInitiatedAction());
        actions.put(ConversationFact.TARGET_INTERACTION_CONNECTED, new TargetInteractionConnectedAction());
        actions.put(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED, new TargetInteractionConnectFailedAction());
        actions.put(ConversationFact.TRANSFER_TIMEOUT, new TransferTimeoutAction());

        // ENDING
        actions.put(ConversationFact.ENDING_STARTED, new EndingStartedAction());
        actions.put(ConversationFact.ENDING_TIMEOUT, new EndingTimeoutAction());
        actions.put(ConversationFact.ENDING_ACTIONS_COMPLETED, new EndingActionsCompletedAction());

        // SURVEY
        actions.put(ConversationFact.SURVEY_SUBMITTED, new SurveySubmittedAction());
        actions.put(ConversationFact.SURVEY_TIMEOUT, new SurveyTimeoutAction());
        actions.put(ConversationFact.SURVEY_SKIPPED, new SurveySkippedAction());

        // GENESYS
        actions.put(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED, new ConsultTransferStartedAction());
        actions.put(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED, new ConsultTransferEndedAction());
        actions.put(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED, new AgentTransferStartedAction());
        actions.put(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED, new AgentTransferCompletedAction());
        actions.put(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED, new AgentTransferFailedAction());

        // SYSTEM
        actions.put(ConversationFact.CUSTOMER_IDLE_TIMEOUT, new CustomerIdleTimeoutAction());
        actions.put(ConversationFact.SYSTEM_ERROR, new SystemErrorAction());
        actions.put(ConversationFact.DOWNSTREAM_UNAVAILABLE, new DownstreamUnavailableAction());

        return actions;
    }

    /**
     * Demo 1: Basic conversation flow with action execution.
     * NEW → INITIATED → ACTIVE → IN_PROGRESS → TRANSFERRED → ACTIVE → ENDING → CLOSED
     */
    public static void runBasicConversationFlow() {
        DemoLogger.printSection("Demo 1: Basic Conversation Flow (with Action Execution)");
        DemoLogger.resetCounter();

        ChatEngineStateMachineService service = new ChatEngineStateMachineService(createDefaultActions());

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
        DemoLogger.printAction("SessionStartedAction", "Initiate downstream assignment, create record");
        ctx = fireAndPrint(ctx, ConversationFact.SESSION_STARTED, service);

        // INITIATED → ACTIVE
        DemoLogger.printAction("InteractionBecameActiveAction", "Set activeAt, send welcome message");
        ctx = fireAndPrint(ctx, ConversationFact.INTERACTION_BECAME_ACTIVE, service);

        // ACTIVE → IN_PROGRESS
        DemoLogger.printAction("InboundMessageReceivedAction", "Set lastInboundAt, record first response");
        ctx = fireAndPrint(ctx, ConversationFact.INBOUND_MESSAGE_RECEIVED, service);

        // IN_PROGRESS → TRANSFERRED
        DemoLogger.printAction("SourceInteractionTransferredAction", "Set transferInFlight, detach source");
        ctx = fireAndPrint(ctx, ConversationFact.SOURCE_INTERACTION_TRANSFERRED, service);

        // TRANSFERRED → TRANSFERRED (internal): target initiated
        DemoLogger.printAction("TargetInteractionInitiatedAction", "Execute ConnectTargetInteractionCmd");
        ctx = fireAndPrint(ctx, ConversationFact.TARGET_INTERACTION_INITIATED, service);

        // TRANSFERRED → ACTIVE: target connected
        DemoLogger.printInfo("Target interaction connected");
        ctx = fireAndPrint(ctx, ConversationFact.TARGET_INTERACTION_CONNECTED, service);

        // ACTIVE → ENDING
        DemoLogger.printAction("EndingStartedAction", "Set endReason, trigger ending actions");
        ctx = fireAndPrint(ctx, ConversationFact.ENDING_STARTED, service);

        // ENDING → CLOSED
        DemoLogger.printInfo("Ending timeout, finalizing conversation");
        ctx = fireAndPrint(ctx, ConversationFact.ENDING_TIMEOUT, service);

        DemoLogger.printFinalState("CLOSED", "conv-001");
        DemoLogger.printDemoComplete("Basic Conversation Flow", true);
    }

    /**
     * Demo 2: Survey flow with action execution.
     * Survey is field-based in ENDING, not a separate state.
     * IN_PROGRESS → ENDING (survey sent) → ENDING (survey submitted) → CLOSED
     */
    public static void runSurveyFlow() {
        DemoLogger.printSection("Demo 2: Survey Flow (Survey as Field in ENDING)");
        DemoLogger.resetCounter();

        ChatEngineStateMachineService service = new ChatEngineStateMachineService(createDefaultActions());

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

        // IN_PROGRESS → ENDING: ending started (survey will be sent if eligible)
        DemoLogger.printAction("EndingStartedAction", "Set endReason, send survey if eligible");
        ctx = fireAndPrint(ctx, ConversationFact.ENDING_STARTED, service);

        // ENDING → ENDING (internal): survey submitted
        DemoLogger.printInfo("Survey submitted (surveyStatus=SUBMITTED)");
        ctx = fireAndPrint(ctx, ConversationFact.SURVEY_SUBMITTED, service);

        // ENDING → ENDING (internal): all interactions ended
        DemoLogger.printInfo("All interactions ended (interactionsClosed=true)");
        ctx = fireAndPrint(ctx, ConversationFact.ALL_INTERACTIONS_ENDED, service);

        // ENDING → ENDING (internal): ending actions completed
        DemoLogger.printInfo("Ending actions completed (endingActionsDone=true)");
        ctx = fireAndPrint(ctx, ConversationFact.ENDING_ACTIONS_COMPLETED, service);

        // ENDING → CLOSED
        DemoLogger.printInfo("Ending timeout, finalizing conversation");
        ctx = fireAndPrint(ctx, ConversationFact.ENDING_TIMEOUT, service);

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
                .surveyTimeoutSeconds(90)
                .surveyEnabled(true)
                .transferEnabled(true)
                .genesysEnabled(true)
                .fallbackRoutingStrategy("QUEUE")
                .maxRetries(3)
                .retryBaseDelayMs(1000)
                .retryMaxDelayMs(30000)
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
                .surveyTimeoutSeconds(120)
                .surveyEnabled(false)
                .transferEnabled(false)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("AI_BOT")
                .maxRetries(5)
                .retryBaseDelayMs(2000)
                .retryMaxDelayMs(60000)
                .build();
        DemoLogger.printInfo("UK Market: transfer disabled, AI_BOT fallback strategy");
        System.out.printf("           idleTimeout=%ds, transferEnabled=%s, fallbackStrategy=%s%n",
                ukConfig.customerIdleSeconds(), ukConfig.transferEnabled(), ukConfig.fallbackRoutingStrategy());

        DemoLogger.printInfo("Multi-market configuration allows per-market behavior control");
        DemoLogger.printDemoComplete("Multi-Market Configuration", true);
    }

    /**
     * Demo 4: Transfer failure flow with TargetInteractionConnectFailedAction execution.
     * IN_PROGRESS → TRANSFERRED → (transfer failed) → INITIATED (no rollback, re-route)
     */
    public static void runTransferFailureFlow() {
        DemoLogger.printSection("Demo 4: Transfer Failure Flow (No Rollback, Re-route to INITIATED)");
        DemoLogger.resetCounter();

        ChatEngineStateMachineService service = new ChatEngineStateMachineService(createDefaultActions());

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
        DemoLogger.printAction("SourceInteractionTransferredAction", "Set transferInFlight, detach source");
        ctx = fireAndPrint(ctx, ConversationFact.SOURCE_INTERACTION_TRANSFERRED, service);

        // Transfer failed → reset to INITIATED (no rollback)
        DemoLogger.printAction("TargetInteractionConnectFailedAction", "Record failure, trigger re-routing");
        ctx = fireAndPrint(ctx, ConversationFact.TARGET_INTERACTION_CONNECT_FAILED, service);

        DemoLogger.printFinalState("INITIATED", "conv-004");
        DemoLogger.printInfo("Conversation reset to INITIATED (no rollback), ready for re-routing");
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
