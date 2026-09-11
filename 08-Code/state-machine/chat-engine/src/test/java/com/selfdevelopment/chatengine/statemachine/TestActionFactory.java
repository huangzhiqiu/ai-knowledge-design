package com.selfdevelopment.chatengine.statemachine;

import com.alibaba.cola.statemachine.Action;
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

import java.util.EnumMap;
import java.util.Map;

/**
 * Test utility for creating default Action maps for testing.
 * <p>
 * This class provides a convenient way to create Action instances for unit tests
 * without requiring Spring context. Actions created here are simple instantiations
 * without any dependency injection.
 */
public final class TestActionFactory {

    private TestActionFactory() {
        // Utility class, no instantiation
    }

    /**
     * Creates default Action map for testing.
     *
     * @return the Map of ConversationFact to Action with default Action instances
     */
    public static Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> createDefaultActions() {
        Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actions =
                new EnumMap<>(ConversationFact.class);

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
}
