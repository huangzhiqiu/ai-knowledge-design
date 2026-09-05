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
 * Survey is field-based in ENDING, NOT a separate state:
 * - SURVEY_SUBMITTED is an internal transition (ENDING → ENDING)
 * - SURVEY_TIMEOUT is an internal transition (ENDING → ENDING)
 * - SURVEY_SKIPPED is an internal transition (ENDING → ENDING)
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0).
 */
class SurveyStateFlowTest {

    private static ChatEngineStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.create();
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

    // ===== SURVEY_SUBMITTED transitions (ENDING → ENDING, internal) =====

    @Test
    void shouldSubmitSurveyFromEnding() {
        // SURVEY_SUBMITTED is an internal transition: state remains ENDING
        ConversationState result = service.fire(
                buildCtx(ConversationState.ENDING, true), ConversationFact.SURVEY_SUBMITTED);
        assertEquals(ConversationState.ENDING, result);
    }

    @Test
    void shouldNotSubmitSurveyFromInProgress() {
        // COLA state machine returns source state when no transition matches
        ConversationState result = service.fire(
                buildCtx(ConversationState.IN_PROGRESS, true), ConversationFact.SURVEY_SUBMITTED);
        assertEquals(ConversationState.IN_PROGRESS, result, "State should remain IN_PROGRESS");
    }

    // ===== SURVEY_TIMEOUT transitions (ENDING → ENDING, internal) =====

    @Test
    void shouldTimeoutSurveyFromEnding() {
        // SURVEY_TIMEOUT is an internal transition: state remains ENDING
        ConversationState result = service.fire(
                buildCtx(ConversationState.ENDING, true), ConversationFact.SURVEY_TIMEOUT);
        assertEquals(ConversationState.ENDING, result);
    }

    // ===== SURVEY_SKIPPED transitions (ENDING → ENDING, internal) =====

    @Test
    void shouldSkipSurveyFromEnding() {
        // SURVEY_SKIPPED is an internal transition: state remains ENDING
        ConversationState result = service.fire(
                buildCtx(ConversationState.ENDING, true), ConversationFact.SURVEY_SKIPPED);
        assertEquals(ConversationState.ENDING, result);
    }

    // ===== Full end-to-end survey flow =====

    @Test
    void shouldCompleteFullSurveyFlow() {
        // 1. Ending started → ENDING (survey will be sent if eligible)
        CbolStateContext endingCtx = buildCtx(ConversationState.IN_PROGRESS, true);
        assertEquals(ConversationState.ENDING,
                service.fire(endingCtx, ConversationFact.ENDING_STARTED));

        // 2. Survey submitted (internal transition, stays ENDING)
        CbolStateContext surveyCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.ENDING,
                service.fire(surveyCtx, ConversationFact.SURVEY_SUBMITTED));

        // 3. All interactions ended (internal transition, stays ENDING)
        CbolStateContext interactionsCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.ENDING,
                service.fire(interactionsCtx, ConversationFact.ALL_INTERACTIONS_ENDED));

        // 4. Ending actions completed (internal transition, stays ENDING)
        CbolStateContext actionsCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.ENDING,
                service.fire(actionsCtx, ConversationFact.ENDING_ACTIONS_COMPLETED));

        // 5. Ending timeout → CLOSED
        CbolStateContext finalCtx = buildCtx(ConversationState.ENDING, true);
        assertEquals(ConversationState.CLOSED,
                service.fire(finalCtx, ConversationFact.ENDING_TIMEOUT));
    }
}
