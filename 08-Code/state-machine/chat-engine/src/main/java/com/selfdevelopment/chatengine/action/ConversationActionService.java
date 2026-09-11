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
import org.springframework.stereotype.Service;

import java.util.Objects;
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
@Service
public class ConversationActionService {

    private final ConversationActionRegistry actionRegistry;

    /**
     * Creates the service with the injected ActionRegistry (Spring usage).
     *
     * @param actionRegistry the conversation action registry
     */
    public ConversationActionService(ConversationActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    /**
     * Builds the conversation state machine using Spring-managed Actions from the registry.
     * <p>
     * This is the recommended method for Spring applications.
     *
     * @return the configured conversation state machine
     */
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithSpringActions() {
        return buildWithRegistry(actionRegistry);
    }

    /**
     * Builds the conversation state machine using the ActionRegistry.
     *
     * @param registry the conversation action registry
     * @return the configured conversation state machine
     * @throws NullPointerException if registry is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithRegistry(
            ConversationActionRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        return ConversationStateMachineFactory.buildWithActionProvider(registry::getAction);
    }

    /**
     * Builds the conversation state machine with injected Action instances.
     *
     * @param actions the ConversationActions holder containing all Action instances
     * @return the configured conversation state machine
     * @throws NullPointerException if actions is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActions(
            ConversationActions actions) {
        Objects.requireNonNull(actions, "actions must not be null");
        return ConversationStateMachineFactory.buildWithActionProvider(toActionProvider(actions));
    }

    /**
     * Converts a ConversationActions holder to an ActionProvider function.
     *
     * @param actions the ConversationActions holder
     * @return the ActionProvider function
     */
    public static Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> toActionProvider(
            ConversationActions actions) {
        return fact -> switch (fact) {
            case SESSION_STARTED -> actions.sessionStartedAction;
            case INTERACTION_BECAME_ACTIVE -> actions.interactionBecameActiveAction;
            case INBOUND_MESSAGE_RECEIVED -> actions.inboundMessageReceivedAction;
            case SOURCE_INTERACTION_TRANSFERRED -> actions.sourceInteractionTransferredAction;
            case TARGET_INTERACTION_INITIATED -> actions.targetInteractionInitiatedAction;
            case TARGET_INTERACTION_CONNECTED -> actions.targetInteractionConnectedAction;
            case TARGET_INTERACTION_CONNECT_FAILED -> actions.targetInteractionConnectFailedAction;
            case TRANSFER_TIMEOUT -> actions.transferTimeoutAction;
            case ENDING_STARTED -> actions.endingStartedAction;
            case ENDING_TIMEOUT -> actions.endingTimeoutAction;
            case ALL_INTERACTIONS_ENDED -> actions.allInteractionsEndedAction;
            case ENDING_ACTIONS_COMPLETED -> actions.endingActionsCompletedAction;
            case SURVEY_SUBMITTED -> actions.surveySubmittedAction;
            case SURVEY_TIMEOUT -> actions.surveyTimeoutAction;
            case SURVEY_SKIPPED -> actions.surveySkippedAction;
            case GENESYS_CONSULT_TRANSFER_STARTED -> actions.consultTransferStartedAction;
            case GENESYS_CONSULT_TRANSFER_ENDED -> actions.consultTransferEndedAction;
            case GENESYS_AGENT_TRANSFER_STARTED -> actions.agentTransferStartedAction;
            case GENESYS_AGENT_TRANSFER_COMPLETED -> actions.agentTransferCompletedAction;
            case GENESYS_AGENT_TRANSFER_FAILED -> actions.agentTransferFailedAction;
            case CUSTOMER_IDLE_TIMEOUT -> actions.customerIdleTimeoutAction;
            case SYSTEM_ERROR -> actions.systemErrorAction;
            case DOWNSTREAM_UNAVAILABLE -> actions.downstreamUnavailableAction;
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
         * Creates the holder with all Action instances.
         *
         * @param sessionStartedAction the session started action
         * @param interactionBecameActiveAction the interaction became active action
         * @param inboundMessageReceivedAction the inbound message received action
         * @param sourceInteractionTransferredAction the source interaction transferred action
         * @param targetInteractionInitiatedAction the target interaction initiated action
         * @param targetInteractionConnectedAction the target interaction connected action
         * @param targetInteractionConnectFailedAction the target interaction connect failed action
         * @param transferTimeoutAction the transfer timeout action
         * @param endingStartedAction the ending started action
         * @param endingTimeoutAction the ending timeout action
         * @param allInteractionsEndedAction the all interactions ended action
         * @param endingActionsCompletedAction the ending actions completed action
         * @param surveySubmittedAction the survey submitted action
         * @param surveyTimeoutAction the survey timeout action
         * @param surveySkippedAction the survey skipped action
         * @param consultTransferStartedAction the consult transfer started action
         * @param consultTransferEndedAction the consult transfer ended action
         * @param agentTransferStartedAction the agent transfer started action
         * @param agentTransferCompletedAction the agent transfer completed action
         * @param agentTransferFailedAction the agent transfer failed action
         * @param customerIdleTimeoutAction the customer idle timeout action
         * @param systemErrorAction the system error action
         * @param downstreamUnavailableAction the downstream unavailable action
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
