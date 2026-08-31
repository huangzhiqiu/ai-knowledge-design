package com.selfdevelopment.ai.messaging.cbol.monitor;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.model.InteractionInstance;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineRegistry;
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;
import com.selfdevelopment.ai.messaging.cbol.statemachine.ConversationStateMachineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitorTimeoutTest {

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.build();
    }

    @AfterAll
    static void tearDown() {
        CbolStateMachineRegistry.clear();
    }

    private CbolStateContext buildCtx(ConversationState state) {
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-mon-01")
                .state(state)
                .build();
        InteractionInstance interaction = InteractionInstance.builder()
                .interactionId("int-mon-01")
                .build();
        return CbolStateContext.builder()
                .conversation(conv)
                .interaction(interaction)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(TraceContext.generate())
                .build();
    }

    @Test
    void testCustomerIdleMonitorHit() {
        CbolStateMachineService service = new CbolStateMachineService();
        CustomerIdleMonitor monitor = new CustomerIdleMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.ACTIVE);
        long lastActivity = System.currentTimeMillis() - 310 * 1000;
        assertDoesNotThrow(() -> monitor.check(ctx, lastActivity));
    }

    @Test
    void testTransferMonitorSkipWhenNotTransferred() {
        CbolStateMachineService service = new CbolStateMachineService();
        TransferMonitor monitor = new TransferMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.ACTIVE);
        assertDoesNotThrow(() -> monitor.check(ctx, System.currentTimeMillis() - 200 * 1000));
    }
}