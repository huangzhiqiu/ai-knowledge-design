package com.selfdevelopment.chatengine.statemachine;

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
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;

/**
 * Test utility for creating default ConversationActions for testing.
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
     * Creates default ConversationActions for testing.
     *
     * @return the ConversationActions holder with default Action instances
     */
    public static ConversationStateMachineFactory.ConversationActions createDefaultActions() {
        return new ConversationStateMachineFactory.ConversationActions(
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
}
