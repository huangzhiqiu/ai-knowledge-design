package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.model.InteractionInstance;
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
        assertEquals(ConversationState.ACTIVE,
                service.fire(buildCtx(ConversationState.INITIATED), ConversationFact.CUSTOMER_CONNECT));
    }

    @Test
    void testActiveToTransferred() {
        assertEquals(ConversationState.TRANSFERRED,
                service.fire(buildCtx(ConversationState.ACTIVE), ConversationFact.TRANSFER_REQUEST));
    }

    @Test
    void testTransferredFailedToInitiated() {
        assertEquals(ConversationState.INITIATED,
                service.fire(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TRANSFER_FAILED));
    }

    @Test
    void testTransferredTimeoutToInitiated() {
        assertEquals(ConversationState.INITIATED,
                service.fire(buildCtx(ConversationState.TRANSFERRED), ConversationFact.TRANSFER_TIMEOUT));
    }

    @Test
    void testActiveToEnding() {
        assertEquals(ConversationState.ENDING,
                service.fire(buildCtx(ConversationState.ACTIVE), ConversationFact.CUSTOMER_CLOSE));
    }
}