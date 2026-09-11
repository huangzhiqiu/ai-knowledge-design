package com.selfdevelopment.chatengine.statemachine.factory;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.selfdevelopment.chatengine.action.ending.EndingStartedAction;
import com.selfdevelopment.chatengine.action.ending.EndingTimeoutAction;
import com.selfdevelopment.chatengine.action.ending.AllInteractionsEndedAction;
import com.selfdevelopment.chatengine.action.ending.EndingActionsCompletedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferCompletedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferFailedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferStartedAction;
import com.selfdevelopment.chatengine.action.genesys.ConsultTransferEndedAction;
import com.selfdevelopment.chatengine.action.genesys.ConsultTransferStartedAction;
import com.selfdevelopment.chatengine.action.lifecycle.SessionStartedAction;
import com.selfdevelopment.chatengine.action.lifecycle.InteractionBecameActiveAction;
import com.selfdevelopment.chatengine.action.lifecycle.InboundMessageReceivedAction;
import com.selfdevelopment.chatengine.action.survey.SurveySubmittedAction;
import com.selfdevelopment.chatengine.action.survey.SurveyTimeoutAction;
import com.selfdevelopment.chatengine.action.survey.SurveySkippedAction;
import com.selfdevelopment.chatengine.action.system.CustomerIdleTimeoutAction;
import com.selfdevelopment.chatengine.action.system.SystemErrorAction;
import com.selfdevelopment.chatengine.action.system.DownstreamUnavailableAction;
import com.selfdevelopment.chatengine.action.transfer.SourceInteractionTransferredAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionInitiatedAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionConnectedAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionConnectFailedAction;
import com.selfdevelopment.chatengine.action.transfer.TransferTimeoutAction;
import com.selfdevelopment.chatengine.action.ConversationActionRegistry;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import org.springframework.stereotype.Component;

/**
 * Factory for building the Conversation state machine.
 * <p>
 * Uses COLA StateMachine builder API. Can be used as a Spring-managed bean
 * (with injected ActionRegistry) or as a static utility (with default Actions).
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0):
 * - Conversation states: NEW, INITIATED, ACTIVE, IN_PROGRESS, TRANSFERRED, ENDING, CLOSED
 * - Transfer failure/timeout does NOT rollback; Conversation returns directly to INITIATED
 * - Survey is field-based (surveyStatus) in ENDING, not a separate state
 * - Customer Idle ideal logic: all wait-capable states timeout -> ENDING
 * - ENDING is irreversible, defaults to 120s forced convergence to CLOSED
 */
@Component
public class ConversationStateMachineFactory {

    public static final String MACHINE_ID = "conversation";

    private final ConversationActionRegistry actionRegistry;

    /**
     * Creates the factory with the injected ActionRegistry (Spring usage).
     *
     * @param actionRegistry the conversation action registry
     */
    public ConversationStateMachineFactory(ConversationActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    /**
     * Builds the conversation state machine using Spring-managed Actions from the registry.
     * <p>
     * This is the recommended method for Spring applications. It retrieves all Actions
     * from the injected ConversationActionRegistry and builds the state machine with them.
     * <p>
     * The state machine built by this method is NOT registered automatically.
     * Caller should register it via StateMachineFactory.register() if needed.
     *
     * @return the configured conversation state machine
     */
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithSpringActions() {
        return buildWithRegistry(actionRegistry);
    }

    /**
     * Creates and registers the conversation state machine with all transition rules.
     * Uses double-checked locking for thread-safe singleton initialization.
     * <p>
     * <b>Note:</b> This static method uses default Action instances (without dependency injection).
     * For Spring-managed Actions with dependencies, use {@link #buildWithSpringActions()} instead.
     *
     * @return the configured and registered conversation state machine
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> create() {
        // Try to get existing state machine first
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (Exception ignored) {
            // State machine not built yet
        }

        synchronized (ConversationStateMachineFactory.class) {
            // Double-check after acquiring lock
            try {
                StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                        StateMachineFactory.get(MACHINE_ID);
                if (existing != null) {
                    return existing;
                }
            } catch (Exception ignored) {
                // State machine not built yet
            }

            // Build and register
            try {
                StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = build();
                StateMachineFactory.register(sm);
                return sm;
            } catch (Exception e) {
                // State machine already built, return existing instance
                return StateMachineFactory.get(MACHINE_ID);
            }
        }
    }

    /**
     * Builds the conversation state machine without registering it.
     * <p>
     * Creates default Action instances (for non-Spring usage) and delegates to
     * {@link #buildWithActions(ConversationActions)} for the actual construction.
     * This ensures a single source of truth for state transition rules.
     *
     * @return the configured conversation state machine
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        ConversationActions defaultActions = createDefaultActions();
        return buildWithActions(defaultActions);
    }

    /**
     * Creates default Action instances for non-Spring usage.
     * <p>
     * These are simple instantiations without any dependency injection.
     * For Spring-managed Actions with dependencies, use {@link #buildWithRegistry} instead.
     *
     * @return the ConversationActions holder with default Action instances
     */
    private static ConversationActions createDefaultActions() {
        return new ConversationActions(
                new SessionStartedAction(),
                new InteractionBecameActiveAction(),
                new InboundMessageReceivedAction(),
                new SourceInteractionTransferredAction(),
                new TargetInteractionInitiatedAction(),
                new TargetInteractionConnectedAction(),
                new TargetInteractionConnectFailedAction(),
                new TransferTimeoutAction(),
                new EndingStartedAction(),
                new EndingTimeoutAction(),
                new AllInteractionsEndedAction(),
                new EndingActionsCompletedAction(),
                new SurveySubmittedAction(),
                new SurveyTimeoutAction(),
                new SurveySkippedAction(),
                new ConsultTransferStartedAction(),
                new ConsultTransferEndedAction(),
                new AgentTransferStartedAction(),
                new AgentTransferCompletedAction(),
                new AgentTransferFailedAction(),
                new CustomerIdleTimeoutAction(),
                new SystemErrorAction(),
                new DownstreamUnavailableAction()
        );
    }

    /**
     * Builds the conversation state machine with injected Action instances.
     * <p>
     * This method is designed for Spring dependency injection - it accepts a
     * ConversationActions holder containing Spring-managed Action beans, allowing
     * Actions to have their own dependencies (Repository, Service, etc.).
     * <p>
     * The state machine built by this method is NOT registered automatically.
     * Caller should register it via StateMachineFactory.register() if needed.
     *
     * @param actions the ConversationActions holder containing all Action instances
     * @return the configured conversation state machine
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActions(ConversationActions actions) {
        StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                StateMachineBuilderFactory.create();

        // ===== 5.1 BASIC LIFECYCLE =====
        // COLA API order: from → to → on → when → perform

        // NEW → INITIATED: session started, conversation initialization prepared
        builder.externalTransition()
                .from(ConversationState.NEW)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.SESSION_STARTED)
                .perform(actions.sessionStartedAction);

        // INITIATED → ACTIVE: interaction became active (InteractionState=CONNECTED)
        builder.externalTransition()
                .from(ConversationState.INITIATED)
                .to(ConversationState.ACTIVE)
                .on(ConversationFact.INTERACTION_BECAME_ACTIVE)
                .perform(actions.interactionBecameActiveAction);

        // ACTIVE → IN_PROGRESS: inbound message received
        builder.externalTransition()
                .from(ConversationState.ACTIVE)
                .to(ConversationState.IN_PROGRESS)
                .on(ConversationFact.INBOUND_MESSAGE_RECEIVED)
                .perform(actions.inboundMessageReceivedAction);

        // INITIATED → INITIATED: downstream unavailable (stay, notify)
        builder.internalTransition()
                .within(ConversationState.INITIATED)
                .on(ConversationFact.DOWNSTREAM_UNAVAILABLE)
                .perform(actions.downstreamUnavailableAction);

        // ===== 5.2 CROSS-CHANNEL TRANSFER (TRANSFERRED, 180s deadline) =====
        // Latest policy: transfer failure/timeout does NOT rollback, Conversation returns directly to INITIATED

        // IN_PROGRESS → TRANSFERRED: source interaction transferred
        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.TRANSFERRED)
                .on(ConversationFact.SOURCE_INTERACTION_TRANSFERRED)
                .perform(actions.sourceInteractionTransferredAction);

        // TRANSFERRED → TRANSFERRED (internal): target interaction initiated
        builder.internalTransition()
                .within(ConversationState.TRANSFERRED)
                .on(ConversationFact.TARGET_INTERACTION_INITIATED)
                .perform(actions.targetInteractionInitiatedAction);

        // TRANSFERRED → ACTIVE: target interaction connected (no rollback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.ACTIVE)
                .on(ConversationFact.TARGET_INTERACTION_CONNECTED)
                .perform(actions.targetInteractionConnectedAction);

        // TRANSFERRED → INITIATED: target connect failed (no rollback, re-route/fallback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED)
                .perform(actions.targetInteractionConnectFailedAction);

        // TRANSFERRED → INITIATED: transfer timeout (>=180s, no rollback, re-route/fallback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.TRANSFER_TIMEOUT)
                .perform(actions.transferTimeoutAction);

        // ===== 5.3 ENTER ENDING (unified convergence entry) =====

        // INITIATED/ACTIVE/IN_PROGRESS/TRANSFERRED → ENDING: ending started (set endReason, trigger ending actions)
        builder.externalTransitions()
                .fromAmong(ConversationState.INITIATED, ConversationState.ACTIVE,
                        ConversationState.IN_PROGRESS, ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.ENDING_STARTED)
                .perform(actions.endingStartedAction);

        // ANY(except CLOSED) → ENDING: system error (endReason=SYSTEM_ERROR, trigger ending actions)
        builder.externalTransitions()
                .fromAmong(ConversationState.NEW, ConversationState.INITIATED, ConversationState.ACTIVE,
                        ConversationState.IN_PROGRESS, ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYSTEM_ERROR)
                .perform(actions.systemErrorAction);

        // ===== 5.4 CUSTOMER IDLE (ideal rule: full coverage enter ENDING, reason=customer idle) =====

        // INITIATED/ACTIVE/IN_PROGRESS/TRANSFERRED → ENDING: customer idle timeout
        builder.externalTransitions()
                .fromAmong(ConversationState.INITIATED, ConversationState.ACTIVE,
                        ConversationState.IN_PROGRESS, ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.CUSTOMER_IDLE_TIMEOUT)
                .perform(actions.customerIdleTimeoutAction);

        // ENDING → ENDING (internal): customer idle timeout (no-op, can confirm reason=customer idle)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.CUSTOMER_IDLE_TIMEOUT);

        // ===== 7.2 ENDING CONVERGENCE RULES (two conditions + timeout forced) =====
        // endingActionsDone=true (ENDING_ACTIONS_COMPLETED)
        // interactionsClosed=true (ALL_INTERACTIONS_ENDED)
        // both true → CLOSED
        // or ENDING_TIMEOUT → forced CLOSED

        // ENDING → ENDING (internal): ending actions completed (set endingActionsDone=true; if interactionsClosed=true then CLOSED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.ENDING_ACTIONS_COMPLETED)
                .perform(actions.endingActionsCompletedAction);

        // ENDING → ENDING (internal): all interactions ended (set interactionsClosed=true; if endingActionsDone=true then CLOSED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.ALL_INTERACTIONS_ENDED)
                .perform(actions.allInteractionsEndedAction);

        // ENDING → CLOSED: ending timeout (forced close, record alert reason)
        builder.externalTransition()
                .from(ConversationState.ENDING)
                .to(ConversationState.CLOSED)
                .on(ConversationFact.ENDING_TIMEOUT)
                .perform(actions.endingTimeoutAction);

        // ===== 7.4 SURVEY FIELD-BASED (no longer SURVEY state, handled in ENDING) =====
        // Survey events are internal transitions in ENDING (no-op, update surveyStatus field)

        // ENDING → ENDING (internal): survey submitted (surveyStatus=SUBMITTED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_SUBMITTED)
                .perform(actions.surveySubmittedAction);

        // ENDING → ENDING (internal): survey timeout (surveyStatus=TIMEOUT, endReason=CUSTOMER_IDLE)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_TIMEOUT)
                .perform(actions.surveyTimeoutAction);

        // ENDING → ENDING (internal): survey skipped (surveyStatus=SKIPPED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_SKIPPED)
                .perform(actions.surveySkippedAction);

        // ===== GENESYS SAME-CHANNEL / CONSULT (conversation no-op) =====
        // These events are handled at the Interaction level, Conversation state machine treats them as no-op
        // They can occur in ACTIVE or IN_PROGRESS states (during active conversation)

        // ACTIVE → ACTIVE (internal): Genesys consult transfer started (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.ACTIVE)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED)
                .perform(actions.consultTransferStartedAction);

        // ACTIVE → ACTIVE (internal): Genesys consult transfer ended (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.ACTIVE)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED)
                .perform(actions.consultTransferEndedAction);

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys consult transfer started (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED)
                .perform(actions.consultTransferStartedAction);

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys consult transfer ended (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED)
                .perform(actions.consultTransferEndedAction);

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys agent transfer started (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED)
                .perform(actions.agentTransferStartedAction);

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys agent transfer completed (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED)
                .perform(actions.agentTransferCompletedAction);

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys agent transfer failed (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED)
                .perform(actions.agentTransferFailedAction);

        return builder.build(MACHINE_ID);
    }

    /**
     * Builds the conversation state machine using the ActionRegistry.
     * <p>
     * This method retrieves all Actions from the {@link com.selfdevelopment.chatengine.action.ConversationActionRegistry}
     * and builds the state machine with them. This is the recommended approach for Spring
     * applications, as it eliminates manual Action instantiation and binding.
     * <p>
     * The registry automatically discovers all Action beans annotated with
     * {@link com.selfdevelopment.chatengine.action.HandlesFact} and indexes them by fact.
     *
     * @param registry the conversation action registry containing all Actions
     * @return the configured conversation state machine
     * @throws NullPointerException if registry is null
     * @throws IllegalStateException if a required Action is missing from the registry
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithRegistry(
            com.selfdevelopment.chatengine.action.ConversationActionRegistry registry) {
        java.util.Objects.requireNonNull(registry, "registry must not be null");

        ConversationActions actions = new ConversationActions(
                (SessionStartedAction) registry.getRequiredAction(ConversationFact.SESSION_STARTED),
                (InteractionBecameActiveAction) registry.getRequiredAction(ConversationFact.INTERACTION_BECAME_ACTIVE),
                (InboundMessageReceivedAction) registry.getRequiredAction(ConversationFact.INBOUND_MESSAGE_RECEIVED),
                (SourceInteractionTransferredAction) registry.getRequiredAction(ConversationFact.SOURCE_INTERACTION_TRANSFERRED),
                (TargetInteractionInitiatedAction) registry.getRequiredAction(ConversationFact.TARGET_INTERACTION_INITIATED),
                (TargetInteractionConnectedAction) registry.getRequiredAction(ConversationFact.TARGET_INTERACTION_CONNECTED),
                (TargetInteractionConnectFailedAction) registry.getRequiredAction(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED),
                (TransferTimeoutAction) registry.getRequiredAction(ConversationFact.TRANSFER_TIMEOUT),
                (EndingStartedAction) registry.getRequiredAction(ConversationFact.ENDING_STARTED),
                (EndingTimeoutAction) registry.getRequiredAction(ConversationFact.ENDING_TIMEOUT),
                (AllInteractionsEndedAction) registry.getRequiredAction(ConversationFact.ALL_INTERACTIONS_ENDED),
                (EndingActionsCompletedAction) registry.getRequiredAction(ConversationFact.ENDING_ACTIONS_COMPLETED),
                (SurveySubmittedAction) registry.getRequiredAction(ConversationFact.SURVEY_SUBMITTED),
                (SurveyTimeoutAction) registry.getRequiredAction(ConversationFact.SURVEY_TIMEOUT),
                (SurveySkippedAction) registry.getRequiredAction(ConversationFact.SURVEY_SKIPPED),
                (ConsultTransferStartedAction) registry.getRequiredAction(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED),
                (ConsultTransferEndedAction) registry.getRequiredAction(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED),
                (AgentTransferStartedAction) registry.getRequiredAction(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED),
                (AgentTransferCompletedAction) registry.getRequiredAction(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED),
                (AgentTransferFailedAction) registry.getRequiredAction(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED),
                (CustomerIdleTimeoutAction) registry.getRequiredAction(ConversationFact.CUSTOMER_IDLE_TIMEOUT),
                (SystemErrorAction) registry.getRequiredAction(ConversationFact.SYSTEM_ERROR),
                (DownstreamUnavailableAction) registry.getRequiredAction(ConversationFact.DOWNSTREAM_UNAVAILABLE)
        );

        return buildWithActions(actions);
    }

    /**
     * Holder for all Conversation Action instances.
     * <p>
     * Used for Spring dependency injection - Spring can inject all Action beans
     * into this holder, which is then passed to buildWithActions().
     * <p>
     * This allows the state machine to use Spring-managed Action beans with
     * their own dependencies (Repository, Service, etc.) while keeping the
     * factory logic clean and testable.
     */
    public static class ConversationActions {
        // LIFECYCLE actions
        public final SessionStartedAction sessionStartedAction;
        public final InteractionBecameActiveAction interactionBecameActiveAction;
        public final InboundMessageReceivedAction inboundMessageReceivedAction;

        // TRANSFER actions
        public final SourceInteractionTransferredAction sourceInteractionTransferredAction;
        public final TargetInteractionInitiatedAction targetInteractionInitiatedAction;
        public final TargetInteractionConnectedAction targetInteractionConnectedAction;
        public final TargetInteractionConnectFailedAction targetInteractionConnectFailedAction;
        public final TransferTimeoutAction transferTimeoutAction;

        // ENDING actions
        public final EndingStartedAction endingStartedAction;
        public final EndingTimeoutAction endingTimeoutAction;
        public final AllInteractionsEndedAction allInteractionsEndedAction;
        public final EndingActionsCompletedAction endingActionsCompletedAction;

        // SURVEY actions
        public final SurveySubmittedAction surveySubmittedAction;
        public final SurveyTimeoutAction surveyTimeoutAction;
        public final SurveySkippedAction surveySkippedAction;

        // GENESYS actions
        public final ConsultTransferStartedAction consultTransferStartedAction;
        public final ConsultTransferEndedAction consultTransferEndedAction;
        public final AgentTransferStartedAction agentTransferStartedAction;
        public final AgentTransferCompletedAction agentTransferCompletedAction;
        public final AgentTransferFailedAction agentTransferFailedAction;

        // SYSTEM actions
        public final CustomerIdleTimeoutAction customerIdleTimeoutAction;
        public final SystemErrorAction systemErrorAction;
        public final DownstreamUnavailableAction downstreamUnavailableAction;

        /**
         * Creates a ConversationActions holder with all Action instances.
         * Used by Spring configuration to inject managed Action beans.
         */
        public ConversationActions(
                SessionStartedAction sessionStartedAction,
                InteractionBecameActiveAction interactionBecameActiveAction,
                InboundMessageReceivedAction inboundMessageReceivedAction,
                SourceInteractionTransferredAction sourceInteractionTransferredAction,
                TargetInteractionInitiatedAction targetInteractionInitiatedAction,
                TargetInteractionConnectedAction targetInteractionConnectedAction,
                TargetInteractionConnectFailedAction targetInteractionConnectFailedAction,
                TransferTimeoutAction transferTimeoutAction,
                EndingStartedAction endingStartedAction,
                EndingTimeoutAction endingTimeoutAction,
                AllInteractionsEndedAction allInteractionsEndedAction,
                EndingActionsCompletedAction endingActionsCompletedAction,
                SurveySubmittedAction surveySubmittedAction,
                SurveyTimeoutAction surveyTimeoutAction,
                SurveySkippedAction surveySkippedAction,
                ConsultTransferStartedAction consultTransferStartedAction,
                ConsultTransferEndedAction consultTransferEndedAction,
                AgentTransferStartedAction agentTransferStartedAction,
                AgentTransferCompletedAction agentTransferCompletedAction,
                AgentTransferFailedAction agentTransferFailedAction,
                CustomerIdleTimeoutAction customerIdleTimeoutAction,
                SystemErrorAction systemErrorAction,
                DownstreamUnavailableAction downstreamUnavailableAction) {
            this.sessionStartedAction = sessionStartedAction;
            this.interactionBecameActiveAction = interactionBecameActiveAction;
            this.inboundMessageReceivedAction = inboundMessageReceivedAction;
            this.sourceInteractionTransferredAction = sourceInteractionTransferredAction;
            this.targetInteractionInitiatedAction = targetInteractionInitiatedAction;
            this.targetInteractionConnectedAction = targetInteractionConnectedAction;
            this.targetInteractionConnectFailedAction = targetInteractionConnectFailedAction;
            this.transferTimeoutAction = transferTimeoutAction;
            this.endingStartedAction = endingStartedAction;
            this.endingTimeoutAction = endingTimeoutAction;
            this.allInteractionsEndedAction = allInteractionsEndedAction;
            this.endingActionsCompletedAction = endingActionsCompletedAction;
            this.surveySubmittedAction = surveySubmittedAction;
            this.surveyTimeoutAction = surveyTimeoutAction;
            this.surveySkippedAction = surveySkippedAction;
            this.consultTransferStartedAction = consultTransferStartedAction;
            this.consultTransferEndedAction = consultTransferEndedAction;
            this.agentTransferStartedAction = agentTransferStartedAction;
            this.agentTransferCompletedAction = agentTransferCompletedAction;
            this.agentTransferFailedAction = agentTransferFailedAction;
            this.customerIdleTimeoutAction = customerIdleTimeoutAction;
            this.systemErrorAction = systemErrorAction;
            this.downstreamUnavailableAction = downstreamUnavailableAction;
        }
    }
}
