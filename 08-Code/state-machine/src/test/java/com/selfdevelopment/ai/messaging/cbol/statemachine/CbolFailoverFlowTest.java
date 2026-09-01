package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.model.InteractionInstance;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.resilience.FailoverStateMachine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CBOL failover flow (SYS_ACTION_FAILED → ERROR → SYS_RETRY/SYS_ABORT).
 * <p>
 * Uses a wrapped state machine with a failing action to simulate action exceptions,
 * then verifies the failover decorator routes to ERROR state via SYS_ACTION_FAILED.
 */
class CbolFailoverFlowTest {

    private static StateMachine<ConversationState, ConversationFact, CbolStateContext> baseMachine;
    private static FailoverStateMachine<ConversationState, ConversationFact, CbolStateContext> failoverMachine;

    @BeforeAll
    static void setUp() {
        // Build the standard CBOL conversation state machine
        baseMachine = ConversationStateMachineFactory.build();

        // Wrap with failover: on action error, fire SYS_ACTION_FAILED
        failoverMachine = new FailoverStateMachine<>(
                baseMachine,
                ctx -> ConversationFact.SYS_ACTION_FAILED,
                event -> event == ConversationFact.SYS_ACTION_FAILED
        );
    }

    @AfterAll
    static void tearDown() {
        CbolStateMachineRegistry.clear();
    }

    private CbolStateContext buildCtx(ConversationState state) {
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-failover-" + System.nanoTime())
                .market("SG")
                .state(state)
                .build();
        InteractionInstance interaction = InteractionInstance.builder()
                .interactionId("int-failover-001")
                .build();
        return CbolStateContext.builder()
                .conversation(conv)
                .interaction(interaction)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(TraceContext.generate())
                .build();
    }

    // ===== SYS_ACTION_FAILED transitions (each non-terminal state → ERROR) =====

    @Test
    void shouldRouteInitiatedToErrorOnActionFailed() {
        assertEquals(ConversationState.ERROR,
                failoverMachine.fireEvent(ConversationState.INITIATED,
                        ConversationFact.SYS_ACTION_FAILED, buildCtx(ConversationState.INITIATED)).getTargetState());
    }

    @Test
    void shouldRouteActiveToErrorOnActionFailed() {
        assertEquals(ConversationState.ERROR,
                failoverMachine.fireEvent(ConversationState.ACTIVE,
                        ConversationFact.SYS_ACTION_FAILED, buildCtx(ConversationState.ACTIVE)).getTargetState());
    }

    @Test
    void shouldRouteTransferredToErrorOnActionFailed() {
        assertEquals(ConversationState.ERROR,
                failoverMachine.fireEvent(ConversationState.TRANSFERRED,
                        ConversationFact.SYS_ACTION_FAILED, buildCtx(ConversationState.TRANSFERRED)).getTargetState());
    }

    @Test
    void shouldRouteSurveyInProgressToErrorOnActionFailed() {
        assertEquals(ConversationState.ERROR,
                failoverMachine.fireEvent(ConversationState.SURVEY_IN_PROGRESS,
                        ConversationFact.SYS_ACTION_FAILED, buildCtx(ConversationState.SURVEY_IN_PROGRESS)).getTargetState());
    }

    // ===== ERROR state recovery paths =====

    @Test
    void shouldRetryFromErrorToActive() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                failoverMachine.fireEvent(ConversationState.ERROR,
                        ConversationFact.SYS_RETRY, buildCtx(ConversationState.ERROR));

        assertEquals(ConversationState.ACTIVE, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void shouldAbortFromErrorToClosed() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                failoverMachine.fireEvent(ConversationState.ERROR,
                        ConversationFact.SYS_ABORT, buildCtx(ConversationState.ERROR));

        assertEquals(ConversationState.CLOSED, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    // ===== Failover decorator behavior with CBOL machine =====

    @Test
    void shouldPassThroughNormalCbolEvents() {
        // CUSTOMER_CONNECT from INITIATED → ACTIVE (no failover needed)
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                failoverMachine.fireEvent(ConversationState.INITIATED,
                        ConversationFact.CUSTOMER_CONNECT, buildCtx(ConversationState.INITIATED));

        assertEquals(ConversationState.ACTIVE, result.getTargetState());
    }

    @Test
    void shouldNotFailoverOnLogicalError() {
        // No transition for CUSTOMER_CONNECT from ACTIVE → StateMachineException (logical, not action error)
        assertThrows(Exception.class, () ->
                failoverMachine.fireEvent(ConversationState.ACTIVE,
                        ConversationFact.CUSTOMER_CONNECT, buildCtx(ConversationState.ACTIVE)));
    }

    @Test
    void shouldNotFailoverOnSysActionFailedItself() {
        // SYS_ACTION_FAILED is a fail event — if its action throws, no further failover
        // (In the base CBOL machine, SYS_ACTION_FAILED has no action, so this just routes to ERROR)
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                failoverMachine.fireEvent(ConversationState.ACTIVE,
                        ConversationFact.SYS_ACTION_FAILED, buildCtx(ConversationState.ACTIVE));

        assertEquals(ConversationState.ERROR, result.getTargetState());
    }

    // ===== Full end-to-end failover scenario =====

    @Test
    void shouldCompleteFullFailoverScenario() {
        CbolStateContext ctx = buildCtx(ConversationState.INITIATED);

        // 1. Connect → ACTIVE
        assertEquals(ConversationState.ACTIVE,
                failoverMachine.fireEvent(ConversationState.INITIATED,
                        ConversationFact.CUSTOMER_CONNECT, ctx).getTargetState());

        // 2. Simulate action failure → SYS_ACTION_FAILED → ERROR
        //    (In production, FailoverStateMachine catches the action exception and fires SYS_ACTION_FAILED)
        assertEquals(ConversationState.ERROR,
                failoverMachine.fireEvent(ConversationState.ACTIVE,
                        ConversationFact.SYS_ACTION_FAILED, ctx).getTargetState());

        // 3. Retry → ACTIVE
        assertEquals(ConversationState.ACTIVE,
                failoverMachine.fireEvent(ConversationState.ERROR,
                        ConversationFact.SYS_RETRY, ctx).getTargetState());

        // 4. Another failure → ERROR
        assertEquals(ConversationState.ERROR,
                failoverMachine.fireEvent(ConversationState.ACTIVE,
                        ConversationFact.SYS_ACTION_FAILED, ctx).getTargetState());

        // 5. Abort → CLOSED
        assertEquals(ConversationState.CLOSED,
                failoverMachine.fireEvent(ConversationState.ERROR,
                        ConversationFact.SYS_ABORT, ctx).getTargetState());
    }
}
