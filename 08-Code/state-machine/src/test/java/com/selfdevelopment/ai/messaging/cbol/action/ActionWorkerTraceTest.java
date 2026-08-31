package com.selfdevelopment.ai.messaging.cbol.action;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceMdcHelper;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.model.InteractionInstance;
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
    private CbolStateContext ctx;

    @BeforeEach
    void setup() {
        worker = new ActionWorker();
        traceContext = TraceContext.generate();
        ConversationInstance conv = ConversationInstance.builder().conversationId("conv-002").build();
        InteractionInstance interaction = InteractionInstance.builder().interactionId("int-002").build();
        ctx = CbolStateContext.builder()
                .conversation(conv)
                .interaction(interaction)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(traceContext)
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
        CbolAction action = cxt -> {
            capturedFromCtx[0] = cxt.traceContext().traceId();
            capturedFromMdc[0] = MDC.get(TraceMdcHelper.MDC_TRACE_ID);
            latch.countDown();
        };
        worker.submit(action, ctx);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        // 验证 ctx 正确传递到异步线程
        assertEquals(traceContext.traceId(), capturedFromCtx[0]);
        // 验证 MDC traceId 已设置（slf4j-simple 环境下 MDC 应正常工作）
        assertNotNull(capturedFromMdc[0], "MDC traceId should not be null");
        assertEquals(traceContext.traceId(), capturedFromMdc[0]);
    }
}