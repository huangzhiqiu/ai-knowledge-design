package com.selfdevelopment.chatengine.statemachine;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the survey flow.
 * <p>
 * Survey is a sub-phase within IN_PROGRESS, NOT a separate state:
 * SURVEY_START is an internal transition (IN_PROGRESS → IN_PROGRESS),
 * SURVEY_COMPLETE transitions from IN_PROGRESS to ENDING.
 */
class SurveyStateFlowTest {

    private static ChatEngineStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.build();
        service = new ChatEngineStateMachineService();
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
    void shouldStartSurveyFromInProgress() {
        // SURVEY_START is an internal transition: state remains IN_PROGRESS
        ConversationState result = service.fire(
                buildCtx(ConversationState.IN_PROGRESS, true), ConversationFact.SURVEY_START);
        assertEquals(ConversationState.IN_PROGRESS, result);
    }

    @Test
    void shouldStartSurveyFromTransferred() {
        assertEquals(ConversationState.IN_PROGRESS,
                service.fire(buildCtx(ConversationState.TRANSFERRED, true),
                        ConversationFact.SURVEY_START));
    }

    @Test
    void shouldNotStartSurveyFromInitiated() {
        // COLA state machine returns source state when no transition matches
        ConversationState result = service.fire(
                buildCtx(ConversationState.INITIATED, true), ConversationFact.SURVEY_START);
        assertEquals(ConversationState.INITIATED, result, "State should remain INITIATED");
    }

    // ===== SURVEY_COMPLETE transitions =====

    @Test
    void shouldCompleteSurveyToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fire(buildCtx(ConversationState.IN_PROGRESS, true),
                        ConversationFact.SURVEY_COMPLETE));
    }

    // ===== SYS_SURVEY_TIMEOUT transitions =====

    @Test
    void shouldTimeoutSurveyToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fire(buildCtx(ConversationState.IN_PROGRESS, true),
                        ConversationFact.SYS_SURVEY_TIMEOUT));
    }

    // ===== Full end-to-end survey flow =====

    @Test
    void shouldCompleteFullSurveyFlow() {
        // 1. Connect → IN_PROGRESS
        CbolStateContext ctx = buildCtx(ConversationState.INITIATED, true);
        assertEquals(ConversationState.IN_PROGRESS,
                service.fire(ctx, ConversationFact.CUSTOMER_CONNECT));

        // 2. Start survey (internal transition, stays IN_PROGRESS)
        CbolStateContext activeCtx = buildCtx(ConversationState.IN_PROGRESS, true);
        assertEquals(ConversationState.IN_PROGRESS,
                service.fire(activeCtx, ConversationFact.SURVEY_START));

        // 3. Complete survey → ENDING
        CbolStateContext surveyCtx = buildCtx(ConversationState.IN_PROGRESS, true);
        assertEquals(ConversationState.ENDING,
                service.fire(surveyCtx, ConversationFact.SURVEY_COMPLETE));

        // 4. Ending grace timeout → CLOSED
        CbolStateContext endingCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.CLOSED,
                service.fire(endingCtx, ConversationFact.SYS_ENDING_GRACE_TIMEOUT));
    }
}
