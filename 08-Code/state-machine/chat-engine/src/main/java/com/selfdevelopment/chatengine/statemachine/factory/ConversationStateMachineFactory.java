package com.selfdevelopment.chatengine.statemachine.factory;

import com.selfdevelopment.chatengine.action.impl.CustomerCloseAction;
import com.selfdevelopment.chatengine.action.impl.CustomerConnectAction;
import com.selfdevelopment.chatengine.action.impl.SurveyCompleteAction;
import com.selfdevelopment.chatengine.action.impl.SurveyStartAction;
import com.selfdevelopment.chatengine.action.impl.TransferFailedAction;
import com.selfdevelopment.chatengine.action.impl.TransferRequestAction;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;

import com.selfdevelopment.chatengine.statemachine.registry.CbolStateMachineRegistry;

import com.selfdevelopment.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;

public class ConversationStateMachineFactory {

    public static final String MACHINE_ID = "conversation";

    // Action instances (stateless, can be shared)
    // These actions directly implement the core Action<S, E, C> interface from statemachine-core
    private static final CustomerConnectAction CUSTOMER_CONNECT_ACTION = new CustomerConnectAction();
    private static final TransferRequestAction TRANSFER_REQUEST_ACTION = new TransferRequestAction();
    private static final TransferFailedAction TRANSFER_FAILED_ACTION = new TransferFailedAction();
    private static final CustomerCloseAction CUSTOMER_CLOSE_ACTION = new CustomerCloseAction();
    private static final SurveyStartAction SURVEY_START_ACTION = new SurveyStartAction();
    private static final SurveyCompleteAction SURVEY_COMPLETE_ACTION = new SurveyCompleteAction();

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                StateMachineBuilder.builder(MACHINE_ID);

        // INITIATED → ACTIVE: customer connects, execute CustomerConnectAction
        builder.transition()
                .from(ConversationState.INITIATED)
                .on(ConversationFact.CUSTOMER_CONNECT)
                .to(ConversationState.ACTIVE)
                .perform(CUSTOMER_CONNECT_ACTION)
                .and();

        // ACTIVE → TRANSFERRED: transfer requested, execute TransferRequestAction
        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.TRANSFER_REQUEST)
                .to(ConversationState.TRANSFERRED)
                .perform(TRANSFER_REQUEST_ACTION)
                .and();

        // Agent attached (internal transition, stays in ACTIVE)
        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.AGENT_ATTACHED)
                .to(ConversationState.ACTIVE)
                .internal()
                .and();

        // Transfer connected successfully → back to ACTIVE
        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_CONNECTED)
                .to(ConversationState.ACTIVE)
                .and();

        // v6: transfer failed -> INITIATED (no rollback), execute TransferFailedAction
        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_FAILED)
                .to(ConversationState.INITIATED)
                .perform(TRANSFER_FAILED_ACTION)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_TIMEOUT)
                .to(ConversationState.INITIATED)
                .perform(TRANSFER_FAILED_ACTION)
                .and();

        // ACTIVE → ENDING: customer closes, execute CustomerCloseAction
        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.CUSTOMER_CLOSE)
                .to(ConversationState.ENDING)
                .perform(CUSTOMER_CLOSE_ACTION)
                .and();

        // ===== SURVEY FLOW (survey as in-progress state, controlled by state machine) =====
        // When a conversation ends but survey is enabled, fire SURVEY_START instead of CUSTOMER_CLOSE.
        // The business layer (ChatEngineStateMachineService.closeConversation) decides which event to fire
        // based on conversation.surveyEnabled().

        // ACTIVE → SURVEY_IN_PROGRESS: survey starts, execute SurveyStartAction
        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.SURVEY_START)
                .to(ConversationState.SURVEY_IN_PROGRESS)
                .perform(SURVEY_START_ACTION)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SURVEY_START)
                .to(ConversationState.SURVEY_IN_PROGRESS)
                .perform(SURVEY_START_ACTION)
                .and();

        // Survey completes normally → ENDING, execute SurveyCompleteAction
        builder.transition()
                .from(ConversationState.SURVEY_IN_PROGRESS)
                .on(ConversationFact.SURVEY_COMPLETE)
                .to(ConversationState.ENDING)
                .perform(SURVEY_COMPLETE_ACTION)
                .and();

        // Survey timeout → ENDING (system-driven)
        builder.transition()
                .from(ConversationState.SURVEY_IN_PROGRESS)
                .on(ConversationFact.SYS_SURVEY_TIMEOUT)
                .to(ConversationState.ENDING)
                .and();

        // Customer leaves during survey → ENDING
        builder.transition()
                .from(ConversationState.SURVEY_IN_PROGRESS)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        // Customer explicitly closes during survey → ENDING
        builder.transition()
                .from(ConversationState.SURVEY_IN_PROGRESS)
                .on(ConversationFact.CUSTOMER_CLOSE)
                .to(ConversationState.ENDING)
                .and();

        // SYSTEM events (from monitors)
        builder.transition()
                .from(ConversationState.INITIATED)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SYS_CUSTOMER_IDLE)
                .to(ConversationState.ENDING)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SYS_TRANSFER_TIMEOUT)
                .to(ConversationState.INITIATED)
                .perform(TRANSFER_FAILED_ACTION)
                .and();

        builder.transition()
                .from(ConversationState.ENDING)
                .on(ConversationFact.SYS_ENDING_GRACE_TIMEOUT)
                .to(ConversationState.CLOSED)
                .and();

        // ===== FAILOVER FLOW (action error → SYS_ACTION_FAILED → ERROR → retry/abort) =====
        // When an action throws an unhandled RuntimeException, FailoverStateMachine automatically
        // fires SYS_ACTION_FAILED. Each non-terminal state routes to ERROR for centralized handling.
        // From ERROR: SYS_RETRY returns to ACTIVE, SYS_ABORT terminates to CLOSED.

        builder.transition()
                .from(ConversationState.INITIATED)
                .on(ConversationFact.SYS_ACTION_FAILED)
                .to(ConversationState.ERROR)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.SYS_ACTION_FAILED)
                .to(ConversationState.ERROR)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SYS_ACTION_FAILED)
                .to(ConversationState.ERROR)
                .and();

        builder.transition()
                .from(ConversationState.SURVEY_IN_PROGRESS)
                .on(ConversationFact.SYS_ACTION_FAILED)
                .to(ConversationState.ERROR)
                .and();

        // ERROR state recovery paths
        builder.transition()
                .from(ConversationState.ERROR)
                .on(ConversationFact.SYS_RETRY)
                .to(ConversationState.ACTIVE)
                .and();

        builder.transition()
                .from(ConversationState.ERROR)
                .on(ConversationFact.SYS_ABORT)
                .to(ConversationState.CLOSED)
                .and();

        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = builder.build();
        CbolStateMachineRegistry.register(sm);
        return sm;
    }
}
