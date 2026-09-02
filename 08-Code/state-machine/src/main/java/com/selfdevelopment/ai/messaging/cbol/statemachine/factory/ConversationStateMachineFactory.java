package com.selfdevelopment.ai.messaging.cbol.statemachine.factory;

import com.selfdevelopment.ai.messaging.cbol.service.CbolStateMachineService;

import com.selfdevelopment.ai.messaging.cbol.statemachine.registry.CbolStateMachineRegistry;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;

public class ConversationStateMachineFactory {

    public static final String MACHINE_ID = "conversation";

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                StateMachineBuilder.builder(MACHINE_ID);

        builder.transition()
                .from(ConversationState.INITIATED)
                .on(ConversationFact.CUSTOMER_CONNECT)
                .to(ConversationState.ACTIVE)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.TRANSFER_REQUEST)
                .to(ConversationState.TRANSFERRED)
                .and();

        // v6: transfer failed -> INITIATED (no rollback)
        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_FAILED)
                .to(ConversationState.INITIATED)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_TIMEOUT)
                .to(ConversationState.INITIATED)
                .and();

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.CUSTOMER_CLOSE)
                .to(ConversationState.ENDING)
                .and();

        // ===== SURVEY FLOW (survey as in-progress state, controlled by state machine) =====
        // When a conversation ends but survey is enabled, fire SURVEY_START instead of CUSTOMER_CLOSE.
        // The business layer (CbolStateMachineService.closeConversation) decides which event to fire
        // based on conversation.surveyEnabled().

        builder.transition()
                .from(ConversationState.ACTIVE)
                .on(ConversationFact.SURVEY_START)
                .to(ConversationState.SURVEY_IN_PROGRESS)
                .and();

        builder.transition()
                .from(ConversationState.TRANSFERRED)
                .on(ConversationFact.SURVEY_START)
                .to(ConversationState.SURVEY_IN_PROGRESS)
                .and();

        // Survey completes normally → ENDING
        builder.transition()
                .from(ConversationState.SURVEY_IN_PROGRESS)
                .on(ConversationFact.SURVEY_COMPLETE)
                .to(ConversationState.ENDING)
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
