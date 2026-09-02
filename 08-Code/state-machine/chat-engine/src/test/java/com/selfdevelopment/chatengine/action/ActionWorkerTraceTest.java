package com.selfdevelopment.chatengine.action;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.context.TraceMdcHelper;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ActionWorkerTraceTest {

    private ActionWorker worker;
    private TraceContext traceContext;
    private CbolStateContext businessCtx;
    private StateContext<ConversationState, ConversationFact, CbolStateContext> stateCtx;

    @BeforeEach
    void setup() {
        worker = new ActionWorker();
        traceContext = TraceContext.generate();
        ConversationInstance conv = ConversationInstance.builder().conversationId("conv-002").build();
        businessCtx = CbolStateContext.builder()
                .conversation(conv)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(traceContext)
                .build();
        stateCtx = StateContext.<ConversationState, ConversationFact, CbolStateContext>builder()
                .sourceState(ConversationState.INITIATED)
                .targetState(ConversationState.ACTIVE)
                .event(ConversationFact.CUSTOMER_CONNECT)
                .businessContext(businessCtx)
                .transitionAccepted(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown();
        TraceMdcHelper.clear();
    }

    @Test
    void testTraceMdcPropagate() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] capturedFromCtx = new String[1];
        final String[] capturedFromMdc = new String[1];
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            capturedFromCtx[0] = ctx.getBusinessContext().traceContext().traceId();
            capturedFromMdc[0] = MDC.get(TraceMdcHelper.MDC_TRACE_ID);
            latch.countDown();
        };
        worker.submit(action, stateCtx);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(traceContext.traceId(), capturedFromCtx[0]);
        assertNotNull(capturedFromMdc[0], "MDC traceId should not be null");
        assertEquals(traceContext.traceId(), capturedFromMdc[0]);
    }

    @Test
    void testCustomThreadPoolConfig() throws InterruptedException {
        ActionWorker customWorker = new ActionWorker(1, 2, 30, 100);
        CountDownLatch latch = new CountDownLatch(1);
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> latch.countDown();
        customWorker.submit(action, stateCtx);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        customWorker.shutdown();
    }

    @Test
    void testActionExceptionIsCaught() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            try {
                throw new RuntimeException("intentional test error");
            } finally {
                latch.countDown();
            }
        };
        // Should not throw
        worker.submit(action, stateCtx);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void testNullActionThrows() {
        assertThrows(NullPointerException.class, () -> worker.submit(null, stateCtx));
    }

    @Test
    void testNullContextThrows() {
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {};
        assertThrows(NullPointerException.class, () -> worker.submit(action, null));
    }

    @Test
    void testShutdownTwiceIsSafe() {
        worker.shutdown();
        // Second shutdown should not throw
        assertDoesNotThrow(() -> worker.shutdown());
    }
}
