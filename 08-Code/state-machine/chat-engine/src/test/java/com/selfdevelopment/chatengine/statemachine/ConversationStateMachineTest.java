package com.selfdevelopment.chatengine.statemachine;

import com.selfdevelopment.statemachine.api.StateMachineRegistry;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversationStateMachineTest {

    private static ChatEngineStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.build();
        service = new ChatEngineStateMachineService();
    }

    @AfterAll
    static void tearDown() {
        StateMachineRegistry.getInstance().clear();
    }

    private CbolStateContext buildCtx(ConversationState state) {
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-001")
                .market("SG")
                .state(state)
                .build();
        return CbolStateContext.builder()
                .conversation(conv)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(TraceContext.generate())
                .build();
    }

    @Test
    void testNewToInitiated() {
        // NEW is the initial state: conversation created, preparation not done yet
        // CONVERSATION_INITIATED triggers preparation work (ConversationInitAction)
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(buildCtx(ConversationState.NEW), ConversationFact.CONVERSATION_INITIATED);
        assertEquals(ConversationState.INITIATED, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void testInitToActive() {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(buildCtx(ConversationState.INITIATED), ConversationFact.CUSTOMER_CONNECT);
        assertEquals(ConversationState.IN_PROGRESS, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
    }

    @Test
    void testActiveToTransferred() {
        assertEquals(ConversationState.TRANSFERRED,
                service.fireAndGetState(buildCtx(ConversationState.IN_PROGRESS), ConversationFact.TRANSFER_REQUEST));
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
                service.fireAndGetState(buildCtx(ConversationState.IN_PROGRESS), ConversationFact.CUSTOMER_CLOSE));
    }

    @Test
    void testCustomerIdleToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fireAndGetState(buildCtx(ConversationState.IN_PROGRESS), ConversationFact.SYS_CUSTOMER_IDLE));
    }

    @Test
    void testEndingGraceTimeoutToClosed() {
        assertEquals(ConversationState.CLOSED,
                service.fireAndGetState(buildCtx(ConversationState.ENDING), ConversationFact.SYS_ENDING_GRACE_TIMEOUT));
    }
}
