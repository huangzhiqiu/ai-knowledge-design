package com.selfdevelopment.chatengine.action;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.StateMachine;
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
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for managing Conversation Actions and their association with ConversationFact events.
 * <p>
 * This service handles all Action-related operations, including:
 * <ul>
 *   <li>Holding Action instances in a structured holder</li>
 *   <li>Converting Actions/Registry to ActionProvider functions</li>
 *   <li>Building state machines with Actions from various sources</li>
 * </ul>
 * <p>
 * The {@link ConversationStateMachineFactory} focuses solely on state machine construction,
 * while this service manages the Action layer.
 *
 * @see ConversationActions
 * @see ConversationActionRegistry
 * @see ConversationStateMachineFactory
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationActionService {

    private final ConversationActionRegistry actionRegistry;

    /**
     * Builds the conversation state machine using Spring-managed Actions from the registry.
     * <p>
     * This is the recommended method for Spring applications.
     *
     * @return the configured conversation state machine
     */
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithSpringActions() {
        log.debug("Building conversation state machine with Spring-managed Actions");
        return buildWithRegistry(actionRegistry);
    }

    /**
     * Builds the conversation state machine using the ActionRegistry.
     *
     * @param registry the conversation action registry
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if registry is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithRegistry(
            ConversationActionRegistry registry) {
        Assert.notNull(registry, "registry must not be null");
        log.debug("Building conversation state machine with ActionRegistry");
        return ConversationStateMachineFactory.buildWithActionProvider(registry::getAction);
    }

    /**
     * Builds the conversation state machine with injected Action instances.
     *
     * @param actions the ConversationActions holder containing all Action instances
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if actions is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActions(
            ConversationActions actions) {
        Assert.notNull(actions, "actions must not be null");
        log.debug("Building conversation state machine with explicit Actions");
        return ConversationStateMachineFactory.buildWithActionProvider(toActionProvider(actions));
    }

    /**
     * Converts a ConversationActions holder to an ActionProvider function.
     * <p>
     * Uses an EnumMap for efficient O(1) lookup instead of a large switch expression.
     *
     * @param actions the ConversationActions holder
     * @return the ActionProvider function
     * @throws IllegalArgumentException if actions is null
     */
    public static Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> toActionProvider(
            ConversationActions actions) {
        Assert.notNull(actions, "actions must not be null");

        Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actionMap =
                new EnumMap<>(ConversationFact.class);

        // LIFECYCLE
        actionMap.put(ConversationFact.SESSION_STARTED, actions.getSessionStartedAction());
        actionMap.put(ConversationFact.INTERACTION_BECAME_ACTIVE, actions.getInteractionBecameActiveAction());
        actionMap.put(ConversationFact.INBOUND_MESSAGE_RECEIVED, actions.getInboundMessageReceivedAction());
        actionMap.put(ConversationFact.ALL_INTERACTIONS_ENDED, actions.getAllInteractionsEndedAction());

        // TRANSFER
        actionMap.put(ConversationFact.SOURCE_INTERACTION_TRANSFERRED, actions.getSourceInteractionTransferredAction());
        actionMap.put(ConversationFact.TARGET_INTERACTION_INITIATED, actions.getTargetInteractionInitiatedAction());
        actionMap.put(ConversationFact.TARGET_INTERACTION_CONNECTED, actions.getTargetInteractionConnectedAction());
        actionMap.put(ConversationFact.TARGET_INTERACTION_CONNECT_FAILED, actions.getTargetInteractionConnectFailedAction());
        actionMap.put(ConversationFact.TRANSFER_TIMEOUT, actions.getTransferTimeoutAction());

        // ENDING
        actionMap.put(ConversationFact.ENDING_STARTED, actions.getEndingStartedAction());
        actionMap.put(ConversationFact.ENDING_TIMEOUT, actions.getEndingTimeoutAction());
        actionMap.put(ConversationFact.ENDING_ACTIONS_COMPLETED, actions.getEndingActionsCompletedAction());

        // SURVEY
        actionMap.put(ConversationFact.SURVEY_SUBMITTED, actions.getSurveySubmittedAction());
        actionMap.put(ConversationFact.SURVEY_TIMEOUT, actions.getSurveyTimeoutAction());
        actionMap.put(ConversationFact.SURVEY_SKIPPED, actions.getSurveySkippedAction());

        // GENESYS
        actionMap.put(ConversationFact.GENESYS_CONSULT_TRANSFER_STARTED, actions.getConsultTransferStartedAction());
        actionMap.put(ConversationFact.GENESYS_CONSULT_TRANSFER_ENDED, actions.getConsultTransferEndedAction());
        actionMap.put(ConversationFact.GENESYS_AGENT_TRANSFER_STARTED, actions.getAgentTransferStartedAction());
        actionMap.put(ConversationFact.GENESYS_AGENT_TRANSFER_COMPLETED, actions.getAgentTransferCompletedAction());
        actionMap.put(ConversationFact.GENESYS_AGENT_TRANSFER_FAILED, actions.getAgentTransferFailedAction());

        // SYSTEM
        actionMap.put(ConversationFact.CUSTOMER_IDLE_TIMEOUT, actions.getCustomerIdleTimeoutAction());
        actionMap.put(ConversationFact.SYSTEM_ERROR, actions.getSystemErrorAction());
        actionMap.put(ConversationFact.DOWNSTREAM_UNAVAILABLE, actions.getDownstreamUnavailableAction());

        return fact -> {
            Action<ConversationState, ConversationFact, CbolStateContext> action = actionMap.get(fact);
            if (action == null) {
                log.warn("No Action found for ConversationFact: {}", fact);
            }
            return action;
        };
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
     * <p>
     * Uses Lombok {@link Getter} and {@link RequiredArgsConstructor} to eliminate
     * boilerplate code for getters and constructor.
     */
    @Getter
    @RequiredArgsConstructor
    public static class ConversationActions {

        // LIFECYCLE actions
        private final SessionStartedAction sessionStartedAction;
        private final InteractionBecameActiveAction interactionBecameActiveAction;
        private final InboundMessageReceivedAction inboundMessageReceivedAction;

        // TRANSFER actions
        private final SourceInteractionTransferredAction sourceInteractionTransferredAction;
        private final TargetInteractionInitiatedAction targetInteractionInitiatedAction;
        private final TargetInteractionConnectedAction targetInteractionConnectedAction;
        private final TargetInteractionConnectFailedAction targetInteractionConnectFailedAction;
        private final TransferTimeoutAction transferTimeoutAction;

        // ENDING actions
        private final EndingStartedAction endingStartedAction;
        private final EndingTimeoutAction endingTimeoutAction;
        private final AllInteractionsEndedAction allInteractionsEndedAction;
        private final EndingActionsCompletedAction endingActionsCompletedAction;

        // SURVEY actions
        private final SurveySubmittedAction surveySubmittedAction;
        private final SurveyTimeoutAction surveyTimeoutAction;
        private final SurveySkippedAction surveySkippedAction;

        // GENESYS actions
        private final ConsultTransferStartedAction consultTransferStartedAction;
        private final ConsultTransferEndedAction consultTransferEndedAction;
        private final AgentTransferStartedAction agentTransferStartedAction;
        private final AgentTransferCompletedAction agentTransferCompletedAction;
        private final AgentTransferFailedAction agentTransferFailedAction;

        // SYSTEM actions
        private final CustomerIdleTimeoutAction customerIdleTimeoutAction;
        private final SystemErrorAction systemErrorAction;
        private final DownstreamUnavailableAction downstreamUnavailableAction;
    }
}
