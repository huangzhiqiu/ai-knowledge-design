package com.selfdevelopment.chatengine.statemachine.factory;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.selfdevelopment.chatengine.action.impl.ConversationInitAction;
import com.selfdevelopment.chatengine.action.impl.CustomerCloseAction;
import com.selfdevelopment.chatengine.action.impl.CustomerConnectAction;
import com.selfdevelopment.chatengine.action.impl.SurveyCompleteAction;
import com.selfdevelopment.chatengine.action.impl.SurveyStartAction;
import com.selfdevelopment.chatengine.action.impl.TransferFailedAction;
import com.selfdevelopment.chatengine.action.impl.TransferRequestAction;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;

/**
 * Factory for building the Conversation state machine.
 * <p>
 * Uses COLA StateMachine builder API.
 * <p>
 * Conversation states: NEW, INITIATED, IN_PROGRESS, TRANSFERRED, ENDING, ERROR, CLOSED
 */
public class ConversationStateMachineFactory {

    public static final String MACHINE_ID = "conversation";

    // Action instances (stateless, can be shared)
    private static final ConversationInitAction CONVERSATION_INIT_ACTION = new ConversationInitAction();
    private static final CustomerConnectAction CUSTOMER_CONNECT_ACTION = new CustomerConnectAction();
    private static final TransferRequestAction TRANSFER_REQUEST_ACTION = new TransferRequestAction();
    private static final TransferFailedAction TRANSFER_FAILED_ACTION = new TransferFailedAction();
    private static final CustomerCloseAction CUSTOMER_CLOSE_ACTION = new CustomerCloseAction();
    private static final SurveyStartAction SURVEY_START_ACTION = new SurveyStartAction();
    private static final SurveyCompleteAction SURVEY_COMPLETE_ACTION = new SurveyCompleteAction();

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
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

            StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                    StateMachineBuilderFactory.create();

        // ===== NORMAL FLOW =====
        // COLA API order: from → to → on → when → perform

        // NEW → INITIATED: conversation initialization prepared
        builder.externalTransition()
                .from(ConversationState.NEW)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.CONVERSATION_INITIATED)
                .perform(CONVERSATION_INIT_ACTION);

        // INITIATED → IN_PROGRESS: customer connects
        builder.externalTransition()
                .from(ConversationState.INITIATED)
                .to(ConversationState.IN_PROGRESS)
                .on(ConversationFact.CUSTOMER_CONNECT)
                .perform(CUSTOMER_CONNECT_ACTION);

        // IN_PROGRESS → TRANSFERRED: transfer requested
        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.TRANSFERRED)
                .on(ConversationFact.TRANSFER_REQUEST)
                .perform(TRANSFER_REQUEST_ACTION);

        // IN_PROGRESS → IN_PROGRESS (internal): agent attached
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.AGENT_ATTACHED);

        // TRANSFERRED → IN_PROGRESS: transfer connected successfully
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.IN_PROGRESS)
                .on(ConversationFact.TRANSFER_CONNECTED);

        // TRANSFERRED → INITIATED: transfer failed (v6: no rollback)
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.TRANSFER_FAILED)
                .perform(TRANSFER_FAILED_ACTION);

        // TRANSFERRED → INITIATED: transfer timeout
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.TRANSFER_TIMEOUT)
                .perform(TRANSFER_FAILED_ACTION);

        // IN_PROGRESS → ENDING: customer closes
        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.ENDING)
                .on(ConversationFact.CUSTOMER_CLOSE)
                .perform(CUSTOMER_CLOSE_ACTION);

        // ===== SURVEY FLOW (survey is a sub-phase within IN_PROGRESS, NOT a separate state) =====

        // IN_PROGRESS → IN_PROGRESS (internal): survey starts
        builder.internalTransition()
                .within(ConversationState.IN_PROGRESS)
                .on(ConversationFact.SURVEY_START)
                .perform(SURVEY_START_ACTION);

        // TRANSFERRED → IN_PROGRESS: survey starts after transfer
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.IN_PROGRESS)
                .on(ConversationFact.SURVEY_START)
                .perform(SURVEY_START_ACTION);

        // IN_PROGRESS → ENDING: survey completes normally
        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SURVEY_COMPLETE)
                .perform(SURVEY_COMPLETE_ACTION);

        // IN_PROGRESS → ENDING: survey timeout (system-driven)
        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYS_SURVEY_TIMEOUT);

        // ===== SYSTEM EVENTS (from monitors) =====

        // SYS_CUSTOMER_IDLE: various states → ENDING
        builder.externalTransition()
                .from(ConversationState.NEW)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYS_CUSTOMER_IDLE);

        builder.externalTransition()
                .from(ConversationState.INITIATED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYS_CUSTOMER_IDLE);

        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYS_CUSTOMER_IDLE);

        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.ENDING)
                .on(ConversationFact.SYS_CUSTOMER_IDLE);

        // SYS_TRANSFER_TIMEOUT: TRANSFERRED → INITIATED
        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.INITIATED)
                .on(ConversationFact.SYS_TRANSFER_TIMEOUT)
                .perform(TRANSFER_FAILED_ACTION);

        // SYS_ENDING_GRACE_TIMEOUT: ENDING → CLOSED
        builder.externalTransition()
                .from(ConversationState.ENDING)
                .to(ConversationState.CLOSED)
                .on(ConversationFact.SYS_ENDING_GRACE_TIMEOUT);

        // ===== FAILOVER FLOW =====

        // SYS_ACTION_FAILED: various states → ERROR
        builder.externalTransition()
                .from(ConversationState.NEW)
                .to(ConversationState.ERROR)
                .on(ConversationFact.SYS_ACTION_FAILED);

        builder.externalTransition()
                .from(ConversationState.INITIATED)
                .to(ConversationState.ERROR)
                .on(ConversationFact.SYS_ACTION_FAILED);

        builder.externalTransition()
                .from(ConversationState.IN_PROGRESS)
                .to(ConversationState.ERROR)
                .on(ConversationFact.SYS_ACTION_FAILED);

        builder.externalTransition()
                .from(ConversationState.TRANSFERRED)
                .to(ConversationState.ERROR)
                .on(ConversationFact.SYS_ACTION_FAILED);

        // ERROR state recovery paths
        builder.externalTransition()
                .from(ConversationState.ERROR)
                .to(ConversationState.IN_PROGRESS)
                .on(ConversationFact.SYS_RETRY);

        builder.externalTransition()
                .from(ConversationState.ERROR)
                .to(ConversationState.CLOSED)
                .on(ConversationFact.SYS_ABORT);

        // Build and register
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = builder.build(MACHINE_ID);
            StateMachineFactory.register(sm);
            return sm;
        } catch (Exception e) {
            // State machine already built, return existing instance
            return StateMachineFactory.get(MACHINE_ID);
        }
        }
    }
}
