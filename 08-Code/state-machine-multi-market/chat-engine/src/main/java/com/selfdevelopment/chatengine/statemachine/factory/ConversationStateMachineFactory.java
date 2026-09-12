package com.selfdevelopment.chatengine.statemachine.factory;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.Condition;
import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.selfdevelopment.chatengine.action.ConditionalAction;
import com.selfdevelopment.chatengine.action.ConversationActionRegistry;
import com.selfdevelopment.chatengine.action.ConversationActionService;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Function;

/**
 * Factory for building the Conversation state machine.
 * <p>
 * Uses COLA StateMachine builder API. This factory focuses solely on state machine
 * construction - defining states, transitions, and events. Action management is
 * handled by {@link ConversationActionService}.
 * <p>
 * Usage:
 * <pre>{@code
 * // With Spring-managed Actions (recommended)
 * ConversationActionService actionService = ...;
 * StateMachine<...> sm = actionService.buildWithSpringActions();
 *
 * // With explicit Actions
 * ConversationActionService.ConversationActions actions = ...;
 * StateMachine<...> sm = ConversationActionService.buildWithActions(actions);
 *
 * // With custom ActionProvider
 * StateMachine<...> sm = ConversationStateMachineFactory.buildWithActionProvider(fact -> ...);
 * }</pre>
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
     * Delegates to {@link ConversationActionService#buildWithRegistry(ConversationActionRegistry)}.
     *
     * @return the configured conversation state machine
     */
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithSpringActions() {
        return ConversationActionService.buildWithRegistry(actionRegistry);
    }

    /**
     * Creates and registers the conversation state machine with all transition rules.
     * Uses double-checked locking for thread-safe singleton initialization.
     * <p>
     * The caller must provide the Action instances to use. For Spring-managed Actions
     * with dependencies, use {@link #buildWithSpringActions()} instead.
     *
     * @param actions map of ConversationFact to Action
     * @return the configured and registered conversation state machine
     * @throws NullPointerException if actions is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> create(
            java.util.Map<ConversationFact, com.alibaba.cola.statemachine.Action<ConversationState, ConversationFact, CbolStateContext>> actions) {
        Objects.requireNonNull(actions, "actions must not be null");

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
                StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                        ConversationActionService.buildWithActions(actions);
                StateMachineFactory.register(sm);
                return sm;
            } catch (Exception e) {
                // State machine already built, return existing instance
                return StateMachineFactory.get(MACHINE_ID);
            }
        }
    }

    /**
     * Builds the conversation state machine with an Action provider function.
     * <p>
     * This is the core construction method that all other build methods delegate to.
     * It accepts a function that maps ConversationFact to the corresponding Action,
     * making the code more intuitive and declarative.
     * <p>
     * Uses the default machine ID {@link #MACHINE_ID}.
     *
     * @param actionProvider function that maps ConversationFact to Action
     * @return the configured conversation state machine
     * @throws NullPointerException if actionProvider is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActionProvider(
            Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actionProvider) {
        return buildWithActionProvider(actionProvider, MACHINE_ID);
    }

    /**
     * Builds the conversation state machine with an Action provider function and custom machine ID.
     * <p>
     * This overload allows specifying a custom machine ID, which is useful when multiple
     * state machine instances need to be created (e.g., per-fire instance creation).
     *
     * @param actionProvider function that maps ConversationFact to Action
     * @param machineId the unique ID for this state machine instance
     * @return the configured conversation state machine
     * @throws NullPointerException if actionProvider or machineId is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActionProvider(
            Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actionProvider,
            String machineId) {
        Objects.requireNonNull(actionProvider, "actionProvider must not be null");
        Objects.requireNonNull(machineId, "machineId must not be null");

        StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                StateMachineBuilderFactory.create();

        // Helper: extract condition from action.
        // All Actions implement ConditionalAction with a default ALWAYS_TRUE condition.
        // instanceof check kept for safety with external Action implementations.
        Function<ConversationFact, Condition<CbolStateContext>> conditionProvider = fact -> {
            Action<ConversationState, ConversationFact, CbolStateContext> action = actionProvider.apply(fact);
            if (action instanceof ConditionalAction) {
                return ((ConditionalAction<ConversationState, ConversationFact, CbolStateContext>) action).getCondition();
            }
            return ctx -> true; // Fallback for plain Action implementations
        };

        // ===== 5.1 BASIC LIFECYCLE =====
        // COLA API order: from → to → on → when → perform

        // NEW → INITIATED: session started, conversation initialization prepared
        builder.externalTransition()
                .from(ConversationState.NEW)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.SESSION_STARTED)
                .when(conditionProvider.apply(ConversationFact.SESSION_STARTED))
                .perform(actionProvider.apply(ConversationFact.SESSION_STARTED));

        // INITIATED → ACTIVE: interaction became active (InteractionState=CONNECTED)
        builder.externalTransition()
                .from(ConversationState.INITIATED)
                .to(ConversationState.ACTIVE)
                .on(ConversationFact.INTERACTION_BECAME_ACTIVE)
                .when(conditionProvider.apply(ConversationFact.INTERACTION_BECAME_ACTIVE))
                .perform(actionProvider.apply(ConversationFact.INTERACTION_BECAME_ACTIVE));

        // ACTIVE → IN_PROGRESS: inbound message received
        builder.externalTransition()
                .from(ConversationState.ACTIVE)
                .to(ConversationState.IN_PROGRESS)
                .on(ConversationFact.INBOUND_MESSAGE_RECEIVED)
                .when(conditionProvider.apply(ConversationFact.INBOUND_MESSAGE_RECEIVED))
                .perform(actionProvider.apply(ConversationFact.INBOUND_MESSAGE_RECEIVED));

        // INITIATED → INITIATED: downstream unavailable (stay, notify)
        builder.internalTransition()
                .within(ConversationState.INITIATED)
                .on(ConversationFact.DOWNSTREAM_UNAVAILABLE)
                .when(conditionProvider.apply(ConversationFact.DOWNSTREAM_UNAVAILABLE))
                .perform(actionProvider.apply(ConversationFact.DOWNSTREAM_UNAVAILABLE));

        // ===== 5.2 CROSS-CHANNEL TRANSFER (TRANSFERRED, 180s deadline) =====
        // Latest policy: transfer failure/timeout does NOT rollback, Conversation returns directly to INITIATED

        // IN_PROGRESS → TRANSFERRED: source interaction transferred
        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.TRANSFERRED)
                .on(ConversationFact.SOURCE_INTERACTION_TRANSFERRED)
                .when(conditionProvider.apply(ConversationFact.SOURCE_INTERACTION_TRANSFERRED))
                .perform(actionProvider.apply(ConversationFact.SOURCE_INTERACTION_TRANSFERRED));

        // TRANSFERRED → TRANSFERRED (internal): target interaction initiated
        builder.internalTransition()
                .within(ConversationState.TRANSFERRED)
                .on(ConversationFact.TARGET_INTERACTION_INITIATED)
                .when(conditionProvider.apply(ConversationFact.TARGET_INTERACTION_INITIATED))
                .perform(actionProvider.apply(ConversationFact.TARGET_INTERACTION_INITIATED));

        // TRANSFERRED → ACTIVE: target interaction connected (no rollback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.ACTIVE)
                .on(ConversationFact.TARGET_INTERACTION_CONNECTED)
                .when(conditionProvider.apply(ConversationFact.TARGET_INTERACTION_CONNECTED))
                .perform(actionProvider.apply(ConversationFact.TARGET_INTERACTION_CONNECTED));

        // TRANSFERRED → INITIATED: target connect failed (no rollback, re-route/fallback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED)
                .when(conditionProvider.apply(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED))
                .perform(actionProvider.apply(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED));

        // TRANSFERRED → INITIATED: transfer timeout (>=180s, no rollback, re-route/fallback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.TRANSFER_TIMEOUT)
                .when(conditionProvider.apply(ConversationFact.TRANSFER_TIMEOUT))
                .perform(actionProvider.apply(ConversationFact.TRANSFER_TIMEOUT));

        // ===== 5.3 ENTER ENDING (unified convergence entry) =====

        // INITIATED/ACTIVE/IN_PROGRESS/TRANSFERRED → ENDING: ending started (set endReason, trigger ending actions)
        builder.externalTransitions()
                .fromAmong(ConversationState.INITIATED, ConversationState.ACTIVE,
                        ConversationState.IN_PROGRESS, ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.ENDING_STARTED)
                .when(conditionProvider.apply(ConversationFact.ENDING_STARTED))
                .perform(actionProvider.apply(ConversationFact.ENDING_STARTED));

        // ANY(except CLOSED) → ENDING: system error (endReason=SYSTEM_ERROR, trigger ending actions)
        builder.externalTransitions()
                .fromAmong(ConversationState.NEW, ConversationState.INITIATED, ConversationState.ACTIVE,
                        ConversationState.IN_PROGRESS, ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYSTEM_ERROR)
                .when(conditionProvider.apply(ConversationFact.SYSTEM_ERROR))
                .perform(actionProvider.apply(ConversationFact.SYSTEM_ERROR));

        // ===== 5.4 CUSTOMER IDLE (ideal rule: full coverage enter ENDING, reason=customer idle) =====

        // INITIATED/ACTIVE/IN_PROGRESS/TRANSFERRED → ENDING: customer idle timeout
        builder.externalTransitions()
                .fromAmong(ConversationState.INITIATED, ConversationState.ACTIVE,
                        ConversationState.IN_PROGRESS, ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.CUSTOMER_IDLE_TIMEOUT)
                .when(conditionProvider.apply(ConversationFact.CUSTOMER_IDLE_TIMEOUT))
                .perform(actionProvider.apply(ConversationFact.CUSTOMER_IDLE_TIMEOUT));

        // ENDING → ENDING (internal): customer idle timeout (no-op, can confirm reason=customer idle)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.CUSTOMER_IDLE_TIMEOUT)
                .when(conditionProvider.apply(ConversationFact.CUSTOMER_IDLE_TIMEOUT));

        // ===== 7.2 ENDING CONVERGENCE RULES (two conditions + timeout forced) =====
        // endingActionsDone=true (ENDING_ACTIONS_COMPLETED)
        // interactionsClosed=true (ALL_INTERACTIONS_ENDED)
        // both true → CLOSED
        // or ENDING_TIMEOUT → forced CLOSED

        // ENDING → ENDING (internal): ending actions completed (set endingActionsDone=true; if interactionsClosed=true then CLOSED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.ENDING_ACTIONS_COMPLETED)
                .when(conditionProvider.apply(ConversationFact.ENDING_ACTIONS_COMPLETED))
                .perform(actionProvider.apply(ConversationFact.ENDING_ACTIONS_COMPLETED));

        // ENDING → ENDING (internal): all interactions ended (set interactionsClosed=true; if endingActionsDone=true then CLOSED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.ALL_INTERACTIONS_ENDED)
                .when(conditionProvider.apply(ConversationFact.ALL_INTERACTIONS_ENDED))
                .perform(actionProvider.apply(ConversationFact.ALL_INTERACTIONS_ENDED));

        // ENDING → CLOSED: ending timeout (forced close, record alert reason)
        builder.externalTransition()
                .from(ConversationState.ENDING)
                .to(ConversationState.CLOSED)
                .on(ConversationFact.ENDING_TIMEOUT)
                .when(conditionProvider.apply(ConversationFact.ENDING_TIMEOUT))
                .perform(actionProvider.apply(ConversationFact.ENDING_TIMEOUT));

        // ===== 7.4 SURVEY FIELD-BASED (no longer SURVEY state, handled in ENDING) =====
        // Survey events are internal transitions in ENDING (no-op, update surveyStatus field)

        // ENDING → ENDING (internal): survey submitted (surveyStatus=SUBMITTED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_SUBMITTED)
                .when(conditionProvider.apply(ConversationFact.SURVEY_SUBMITTED))
                .perform(actionProvider.apply(ConversationFact.SURVEY_SUBMITTED));

        // ENDING → ENDING (internal): survey timeout (surveyStatus=TIMEOUT, endReason=CUSTOMER_IDLE)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_TIMEOUT)
                .when(conditionProvider.apply(ConversationFact.SURVEY_TIMEOUT))
                .perform(actionProvider.apply(ConversationFact.SURVEY_TIMEOUT));

        // ENDING → ENDING (internal): survey skipped (surveyStatus=SKIPPED)
        builder.internalTransition()
                .within(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_SKIPPED)
                .when(conditionProvider.apply(ConversationFact.SURVEY_SKIPPED))
                .perform(actionProvider.apply(ConversationFact.SURVEY_SKIPPED));

        // ===== GENESYS SAME-CHANNEL / CONSULT (conversation no-op) =====
        // These events are handled at the Interaction level, Conversation state machine treats them as no-op
        // They can occur in ACTIVE or IN_PROGRESS states (during active conversation)

        // ACTIVE → ACTIVE (internal): Genesys consult transfer started (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.ACTIVE)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED));

        // ACTIVE → ACTIVE (internal): Genesys consult transfer ended (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.ACTIVE)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED));

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys consult transfer started (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED));

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys consult transfer ended (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED));

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys agent transfer started (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED));

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys agent transfer completed (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED));

        // IN_PROGRESS → IN_PROGRESS (internal): Genesys agent transfer failed (no-op at conversation level, record audit)
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED)
                .when(conditionProvider.apply(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED))
                .perform(actionProvider.apply(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED));

        return builder.build(machineId);
    }
}
