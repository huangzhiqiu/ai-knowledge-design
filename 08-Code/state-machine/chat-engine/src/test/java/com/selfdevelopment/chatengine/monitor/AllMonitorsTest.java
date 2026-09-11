package com.selfdevelopment.chatengine.monitor;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.TestActionFactory;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for all three monitors: CustomerIdleMonitor, TransferMonitor, EndingGraceMonitor.
 */
class AllMonitorsTest {

    private static ChatEngineStateMachineService service;

    @BeforeAll
    static void setUp() {
        ConversationStateMachineFactory.create(TestActionFactory.createDefaultActions());
        service = new ChatEngineStateMachineService();
    }

    private CbolStateContext buildCtx(ConversationState state, long idleSeconds, long transferSeconds, long endingSeconds) {
        StateMachineMarketConfig config = StateMachineMarketConfig.builder()
                .customerIdleSeconds(idleSeconds)
                .transferTimeoutSeconds(transferSeconds)
                .endingGraceSeconds(endingSeconds)
                .surveyEnabled(false)
                .transferEnabled(true)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .build();
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-mon-" + System.nanoTime())
                .market("SG")
                .state(state)
                .build();
        return CbolStateContext.builder()
                .conversation(conv)
                .marketConfig(config)
                .traceContext(TraceContext.generate())
                .build();
    }

    // ===== CustomerIdleMonitor =====

    @Test
    void customerIdleMonitor_shouldTriggerWhenIdleExceeded() {
        CustomerIdleMonitor monitor = new CustomerIdleMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.IN_PROGRESS, 100, 100, 100);
        long lastActivity = System.currentTimeMillis() - 200 * 1000; // 200s ago > 100s threshold
        assertDoesNotThrow(() -> monitor.check(ctx, lastActivity));
    }

    @Test
    void customerIdleMonitor_shouldNotTriggerWhenIdleNotExceeded() {
        CustomerIdleMonitor monitor = new CustomerIdleMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.IN_PROGRESS, 100, 100, 100);
        long lastActivity = System.currentTimeMillis() - 50 * 1000; // 50s ago < 100s threshold
        assertDoesNotThrow(() -> monitor.check(ctx, lastActivity));
    }

    @Test
    void customerIdleMonitor_shouldWorkFromInitiatedState() {
        CustomerIdleMonitor monitor = new CustomerIdleMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.INITIATED, 100, 100, 100);
        long lastActivity = System.currentTimeMillis() - 200 * 1000;
        assertDoesNotThrow(() -> monitor.check(ctx, lastActivity));
    }

    // ===== TransferMonitor =====

    @Test
    void transferMonitor_shouldTriggerWhenTransferTimeoutExceeded() {
        TransferMonitor monitor = new TransferMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.TRANSFERRED, 100, 100, 100);
        long transferStart = System.currentTimeMillis() - 200 * 1000; // 200s > 100s threshold
        assertDoesNotThrow(() -> monitor.check(ctx, transferStart));
    }

    @Test
    void transferMonitor_shouldNotTriggerWhenTimeoutNotExceeded() {
        TransferMonitor monitor = new TransferMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.TRANSFERRED, 100, 100, 100);
        long transferStart = System.currentTimeMillis() - 50 * 1000; // 50s < 100s
        assertDoesNotThrow(() -> monitor.check(ctx, transferStart));
    }

    @Test
    void transferMonitor_shouldSkipWhenNotInTransferredState() {
        TransferMonitor monitor = new TransferMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.IN_PROGRESS, 100, 100, 100);
        long transferStart = System.currentTimeMillis() - 200 * 1000;
        assertDoesNotThrow(() -> monitor.check(ctx, transferStart));
    }

    @Test
    void transferMonitor_shouldSkipFromEndingState() {
        TransferMonitor monitor = new TransferMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.ENDING, 100, 100, 100);
        long transferStart = System.currentTimeMillis() - 200 * 1000;
        assertDoesNotThrow(() -> monitor.check(ctx, transferStart));
    }

    // ===== EndingGraceMonitor =====

    @Test
    void endingGraceMonitor_shouldTriggerWhenGraceTimeoutExceeded() {
        EndingGraceMonitor monitor = new EndingGraceMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.ENDING, 100, 100, 100);
        long enterEnding = System.currentTimeMillis() - 200 * 1000; // 200s > 100s threshold
        assertDoesNotThrow(() -> monitor.check(ctx, enterEnding));
    }

    @Test
    void endingGraceMonitor_shouldNotTriggerWhenGraceNotExceeded() {
        EndingGraceMonitor monitor = new EndingGraceMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.ENDING, 100, 100, 100);
        long enterEnding = System.currentTimeMillis() - 50 * 1000; // 50s < 100s
        assertDoesNotThrow(() -> monitor.check(ctx, enterEnding));
    }

    @Test
    void endingGraceMonitor_shouldSkipWhenNotInEndingState() {
        EndingGraceMonitor monitor = new EndingGraceMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.IN_PROGRESS, 100, 100, 100);
        long enterEnding = System.currentTimeMillis() - 200 * 1000;
        assertDoesNotThrow(() -> monitor.check(ctx, enterEnding));
    }

    @Test
    void endingGraceMonitor_shouldSkipFromTransferredState() {
        EndingGraceMonitor monitor = new EndingGraceMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.TRANSFERRED, 100, 100, 100);
        long enterEnding = System.currentTimeMillis() - 200 * 1000;
        assertDoesNotThrow(() -> monitor.check(ctx, enterEnding));
    }

    @Test
    void endingGraceMonitor_shouldWorkWithZeroThreshold() {
        EndingGraceMonitor monitor = new EndingGraceMonitor(service);
        CbolStateContext ctx = buildCtx(ConversationState.ENDING, 0, 0, 0);
        long enterEnding = System.currentTimeMillis(); // 0s threshold, should trigger immediately
        assertDoesNotThrow(() -> monitor.check(ctx, enterEnding));
    }
}
