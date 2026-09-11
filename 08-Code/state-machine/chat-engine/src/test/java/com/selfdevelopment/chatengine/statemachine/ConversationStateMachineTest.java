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

class ConversationStateMachineTest {

    private static ChatEngineStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.create(TestActionFactory.createDefaultActions());
        service = new ChatEngineStateMachineService();
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
        // SESSION_STARTED triggers preparation work (SessionStartedAction)
        ConversationState result = service.fire(buildCtx(ConversationState.NEW), ConversationFact.SESSION_STARTED);
        assertEquals(ConversationState.INITIATED, result);
    }

    @Test
    void testInitiatedToActive() {
        // INITIATED → ACTIVE: interaction became active (InteractionBecameActiveAction)
        ConversationState result = service.fire(buildCtx(ConversationState.INITIATED), ConversationFact.INTERACTION_BECAME_ACTIVE);
        assertEquals(ConversationState.ACTIVE, result);
    }

    @Test
    void testActiveToInProgress() {
        // ACTIVE → IN_PROGRESS: inbound message received (InboundMessageReceivedAction)
        ConversationState result = service.fire(buildCtx(ConversationState.ACTIVE), ConversationFact.INBOUND_MESSAGE_RECEIVED);
        assertEquals(ConversationState.IN_PROGRESS, result);
    }

    @Test
    void testInProgressToTransferred() {
        // IN_PROGRESS → TRANSFERRED: source interaction transferred (SourceInteractionTransferredAction)
        assertEquals(ConversationState.TRANSFERRED,
                service.fire(buildCtx(ConversationState.IN_PROGRESS), ConversationFact.SOURCE_INTERACTION_TRANSFERRED));
    }

    @Test
    void testTransferredToActive() {
        // TRANSFERRED → ACTIVE: target interaction connected (TargetInteractionConnectedAction)
        assertEquals(ConversationState.ACTIVE,
                service.fire(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TARGET_INTERACTION_CONNECTED));
    }

    @Test
    void testTransferredFailedToInitiated() {
        // TRANSFERRED → INITIATED: target connect failed (no rollback, re-route)
        assertEquals(ConversationState.INITIATED,
                service.fire(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TARGET_INTERACTION_CONNECT_FAILED));
    }

    @Test
    void testTransferredTimeoutToInitiated() {
        // TRANSFERRED → INITIATED: transfer timeout (no rollback, re-route)
        assertEquals(ConversationState.INITIATED,
                service.fire(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TRANSFER_TIMEOUT));
    }

    @Test
    void testInProgressToEnding() {
        // IN_PROGRESS → ENDING: ending started (EndingStartedAction)
        assertEquals(ConversationState.ENDING,
                service.fire(buildCtx(ConversationState.IN_PROGRESS), ConversationFact.ENDING_STARTED));
    }

    @Test
    void testCustomerIdleToEnding() {
        // IN_PROGRESS → ENDING: customer idle timeout (CustomerIdleTimeoutAction)
        assertEquals(ConversationState.ENDING,
                service.fire(buildCtx(ConversationState.IN_PROGRESS), ConversationFact.CUSTOMER_IDLE_TIMEOUT));
    }

    @Test
    void testEndingTimeoutToClosed() {
        // ENDING → CLOSED: ending timeout (forced close)
        assertEquals(ConversationState.CLOSED,
                service.fire(buildCtx(ConversationState.ENDING), ConversationFact.ENDING_TIMEOUT));
    }
}
