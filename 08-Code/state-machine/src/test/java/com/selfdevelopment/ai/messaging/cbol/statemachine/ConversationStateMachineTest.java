package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.cbol.statemachine.registry.CbolStateMachineRegistry;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

import com.selfdevelopment.ai.messaging.cbol.service.CbolStateMachineService;

import com.selfdevelopment.ai.messaging.cbol.statemachine.factory.ConversationStateMachineFactory;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.model.InteractionInstance;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversationStateMachineTest {

    private static CbolStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.build();
        service = new CbolStateMachineService();
    }

    @AfterAll
    static void tearDown() {
        CbolStateMachineRegistry.clear();
    }

    private CbolStateContext buildCtx(ConversationState state) {
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-001")
                .market("SG")
                .state(state)
                .build();
        InteractionInstance interaction = InteractionInstance.builder()
                .interactionId("int-001")
                .build();
        return CbolStateContext.builder()
                .conversation(conv)
                .interaction(interaction)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(TraceContext.generate())
                .build();
    }

    @Test
    void testInitToActive() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(buildCtx(ConversationState.INITIATED), ConversationFact.CUSTOMER_CONNECT);
        assertEquals(ConversationState.ACTIVE, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void testActiveToTransferred() {
        assertEquals(ConversationState.TRANSFERRED,
                service.fireAndGetState(buildCtx(ConversationState.ACTIVE), ConversationFact.TRANSFER_REQUEST));
    }

    @Test
    void testTransferredFailedToInitiated() {
        assertEquals(ConversationState.INITIATED,
                service.fireAndGetState(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TRANSFER_FAILED));
    }

    @Test
    void testTransferredTimeoutToInitiated() {
        assertEquals(ConversationState.INITIATED,
                service.fireAndGetState(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TRANSFER_TIMEOUT));
    }

    @Test
    void testActiveToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.ACTIVE), ConversationFact.CUSTOMER_CLOSE));
    }

    @Test
    void testCustomerIdleToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.ACTIVE), ConversationFact.SYS_CUSTOMER_IDLE));
    }

    @Test
    void testEndingGraceTimeoutToClosed() {
        assertEquals(ConversationState.CLOSED,
                service.fireAndGetState(buildCtx(ConversationState.ENDING), ConversationFact.SYS_ENDING_GRACE_TIMEOUT));
    }
}
