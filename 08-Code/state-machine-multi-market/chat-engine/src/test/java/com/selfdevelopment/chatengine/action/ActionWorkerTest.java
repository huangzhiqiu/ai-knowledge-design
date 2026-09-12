package com.selfdevelopment.chatengine.action;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActionWorker's advanced submission modes:
 * - submitWithResult: returns CompletableFuture for result tracking
 * - submitWithCallback: success/failure callbacks
 */
class ActionWorkerTest {

    private ActionWorker worker;
    private CbolStateContext businessCtx;

    @BeforeEach
    void setup() {
        worker = new ActionWorker();
        TraceContext traceContext = TraceContext.generate();
        ConversationInstance conv = ConversationInstance.builder()
                .conversationId("conv-test-001")
                .market("SG")
                .state(ConversationState.INITIATED)
                .build();
        businessCtx = CbolStateContext.builder()
                .conversation(conv)
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(traceContext)
                .build();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown();
    }

    // ==================== submitWithResult Tests ====================

    @Test
    void testSubmitWithResultSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        AtomicBoolean executed = new AtomicBoolean(false);
        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> executed.set(true);

        CompletableFuture<Void> future = worker.submitWithResult(
                action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, businessCtx);

        future.get(3, TimeUnit.SECONDS);
        assertTrue(executed.get(), "Action should have been executed");
        assertTrue(future.isDone(), "Future should be done");
        assertFalse(future.isCompletedExceptionally(), "Future should not complete exceptionally");
    }

    @Test
    void testSubmitWithResultFailure() {
        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> {
                    throw new RuntimeException("intentional test error");
                };

        CompletableFuture<Void> future = worker.submitWithResult(
                action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, businessCtx);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RuntimeException, "Cause should be RuntimeException");
        assertEquals("intentional test error", ex.getCause().getMessage());
        assertTrue(future.isCompletedExceptionally(), "Future should complete exceptionally");
    }

    @Test
    void testSubmitWithResultThenApply() throws ExecutionException, InterruptedException, TimeoutException {
        AtomicBoolean executed = new AtomicBoolean(false);
        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> executed.set(true);

        String result = worker.submitWithResult(
                        action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                        ConversationFact.SESSION_STARTED, businessCtx)
                .thenApply(v -> "action completed")
                .get(3, TimeUnit.SECONDS);

        assertEquals("action completed", result);
        assertTrue(executed.get());
    }

    @Test
    void testSubmitWithResultExceptionally() throws ExecutionException, InterruptedException, TimeoutException {
        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> {
                    throw new RuntimeException("test error");
                };

        String result = worker.submitWithResult(
                        action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                        ConversationFact.SESSION_STARTED, businessCtx)
                .thenApply(v -> "success")
                .exceptionally(ex -> "recovered: " + ex.getMessage())
                .get(3, TimeUnit.SECONDS);

        assertTrue(result.startsWith("recovered:"), "Should recover from exception");
    }

    @Test
    void testSubmitWithResultNullActionThrows() {
        assertThrows(NullPointerException.class, () -> worker.submitWithResult(
                null, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, businessCtx));
    }

    @Test
    void testSubmitWithResultNullContextThrows() {
        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> {};
        assertThrows(NullPointerException.class, () -> worker.submitWithResult(
                action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, null));
    }

    // ==================== submitWithCallback Tests ====================

    @Test
    void testSubmitWithCallbackSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean successCalled = new AtomicBoolean(false);
        AtomicBoolean failureCalled = new AtomicBoolean(false);

        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> {};

        worker.submitWithCallback(
                action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, businessCtx,
                ctx -> {
                    successCalled.set(true);
                    latch.countDown();
                },
                ex -> {
                    failureCalled.set(true);
                    latch.countDown();
                });

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback should be invoked");
        assertTrue(successCalled.get(), "Success callback should be called");
        assertFalse(failureCalled.get(), "Failure callback should not be called");
    }

    @Test
    void testSubmitWithCallbackFailure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean successCalled = new AtomicBoolean(false);
        AtomicBoolean failureCalled = new AtomicBoolean(false);

        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> {
                    throw new RuntimeException("test error");
                };

        worker.submitWithCallback(
                action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, businessCtx,
                ctx -> {
                    successCalled.set(true);
                    latch.countDown();
                },
                ex -> {
                    failureCalled.set(true);
                    latch.countDown();
                });

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback should be invoked");
        assertFalse(successCalled.get(), "Success callback should not be called");
        assertTrue(failureCalled.get(), "Failure callback should be called");
    }

    // ==================== submit (fire-and-forget) Tests ====================

    @Test
    void testSubmitFireAndForget() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        Action<ConversationState, ConversationFact, CbolStateContext> action =
                (from, to, event, ctx) -> {
                    executed.set(true);
                    latch.countDown();
                };

        worker.submit(
                action, ConversationState.INITIATED, ConversationState.IN_PROGRESS,
                ConversationFact.SESSION_STARTED, businessCtx);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Action should be executed");
        assertTrue(executed.get(), "Action should have been executed");
    }
}
