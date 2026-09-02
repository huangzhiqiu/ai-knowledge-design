package com.selfdevelopment.chatengine.statemachine;

import com.selfdevelopment.chatengine.statemachine.registry.CbolStateMachineRegistry;

import com.selfdevelopment.statemachine.api.StateMachine;

import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;

import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the survey-in-progress state flow.
 * <p>
 * Survey is treated as an "in-progress" state controlled by the state machine:
 * SURVEY_START → SURVEY_IN_PROGRESS → (SURVEY_COMPLETE | SYS_SURVEY_TIMEOUT | SYS_CUSTOMER_IDLE | CUSTOMER_CLOSE) → ENDING.
 */
class SurveyStateFlowTest {

    private static ChatEngineStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.build();
        service = new ChatEngineStateMachineService();
    }

    @AfterAll
    static void tearDown() {
        CbolStateMachineRegistry.clear();
    }

    private CbolStateContext buildCtx(ConversationState state, boolean surveyEnabled) {
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-survey-" + System.nanoTime())
                .market("SG")
                .state(state)
                .surveyEnabled(surveyEnabled)
                .build();
        return CbolStateContext.builder()
                .conversation(conv)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(TraceContext.generate())
                .build();
    }

    // ===== SURVEY_START transitions =====

    @Test
    void shouldEnterSurveyFromActive() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(buildCtx(ConversationState.ACTIVE, true), ConversationFact.SURVEY_START);

        assertEquals(ConversationState.SURVEY_IN_PROGRESS, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void shouldEnterSurveyFromTransferred() {
        assertEquals(ConversationState.SURVEY_IN_PROGRESS,
                service.fireAndGetState(buildCtx(ConversationState.TRANSFERRED, true),
                        ConversationFact.SURVEY_START));
    }

    @Test
    void shouldNotEnterSurveyFromInitiated() {
        assertThrows(StateMachineException.class, () ->
                service.fire(buildCtx(ConversationState.INITIATED, true), ConversationFact.SURVEY_START));
    }

    // ===== SURVEY_COMPLETE transitions =====

    @Test
    void shouldCompleteSurveyToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true),
                        ConversationFact.SURVEY_COMPLETE));
    }

    @Test
    void shouldNotCompleteSurveyFromActive() {
        assertThrows(StateMachineException.class, () ->
                service.fire(buildCtx(ConversationState.ACTIVE, true), ConversationFact.SURVEY_COMPLETE));
    }

    // ===== SYS_SURVEY_TIMEOUT transitions =====

    @Test
    void shouldTimeoutSurveyToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true),
                        ConversationFact.SYS_SURVEY_TIMEOUT));
    }

    // ===== SYS_CUSTOMER_IDLE during survey =====

    @Test
    void shouldCustomerIdleDuringSurveyToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true),
                        ConversationFact.SYS_CUSTOMER_IDLE));
    }

    // ===== CUSTOMER_CLOSE during survey =====

    @Test
    void shouldCustomerCloseDuringSurveyToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true),
                        ConversationFact.CUSTOMER_CLOSE));
    }

    // ===== closeConversation convenience method =====

    @Test
    void shouldCloseConversationWithSurveyEnabled() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.closeConversation(buildCtx(ConversationState.ACTIVE, true));

        assertEquals(ConversationState.SURVEY_IN_PROGRESS, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void shouldCloseConversationWithoutSurveyEnabled() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.closeConversation(buildCtx(ConversationState.ACTIVE, false));

        assertEquals(ConversationState.ENDING, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void shouldCloseConversationFromTransferredWithSurvey() {
        assertEquals(ConversationState.SURVEY_IN_PROGRESS,
                service.closeConversation(buildCtx(ConversationState.TRANSFERRED, true)).getTargetState());
    }

    // ===== completeSurvey convenience method =====

    @Test
    void shouldCompleteSurveyViaConvenienceMethod() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.completeSurvey(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true));

        assertEquals(ConversationState.ENDING, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    // ===== Full end-to-end survey flow =====

    @Test
    void shouldCompleteFullSurveyFlow() {
        // 1. Connect → ACTIVE
        CbolStateContext ctx = buildCtx(ConversationState.INITIATED, true);
        assertEquals(ConversationState.ACTIVE,
                service.fireAndGetState(ctx, ConversationFact.CUSTOMER_CONNECT));

        // 2. Close with survey enabled → SURVEY_IN_PROGRESS
        CbolStateContext activeCtx = buildCtx(ConversationState.ACTIVE, true);
        assertEquals(ConversationState.SURVEY_IN_PROGRESS,
                service.closeConversation(activeCtx).getTargetState());

        // 3. Complete survey → ENDING
        CbolStateContext surveyCtx = buildCtx(ConversationState.SURVEY_IN_PROGRESS, true);
        assertEquals(ConversationState.ENDING,
                service.completeSurvey(surveyCtx).getTargetState());

        // 4. Ending grace timeout → CLOSED
        CbolStateContext endingCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.CLOSED,
                service.fireAndGetState(endingCtx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT));
    }

    @Test
    void shouldCompleteSurveyFlowWithTimeout() {
        // SURVEY_IN_PROGRESS → SYS_SURVEY_TIMEOUT → ENDING → SYS_ENDING_GRACE_TIMEOUT → CLOSED
        CbolStateContext surveyCtx = buildCtx(ConversationState.SURVEY_IN_PROGRESS, true);
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(surveyCtx, ConversationFact.SYS_SURVEY_TIMEOUT));

        CbolStateContext endingCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.CLOSED,
                service.fireAndGetState(endingCtx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT));
    }

    // ===== Invalid transitions from SURVEY_IN_PROGRESS =====

    @Test
    void shouldNotTransferFromSurvey() {
        assertThrows(StateMachineException.class, () ->
                service.fire(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true),
                        ConversationFact.TRANSFER_REQUEST));
    }

    @Test
    void shouldNotConnectFromSurvey() {
        assertThrows(StateMachineException.class, () ->
                service.fire(buildCtx(ConversationState.SURVEY_IN_PROGRESS, true),
                        ConversationFact.CUSTOMER_CONNECT));
    }
}
