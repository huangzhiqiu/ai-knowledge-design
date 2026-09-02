package com.selfdevelopment.chatengine.action;

import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActionWorker's advanced submission modes:
 * - submitWithResult: returns CompletableFuture for result tracking
 * - submitWithCallback: success/failure callbacks
 */
class ActionWorkerTest {

    private ActionWorker worker;
    private CbolStateContext businessCtx;
    private StateContext<ConversationState, ConversationFact, CbolStateContext> stateCtx;

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
        stateCtx = StateContext.<ConversationState, ConversationFact, CbolStateContext>builder()
                .sourceState(ConversationState.INITIATED)
                .targetState(ConversationState.IN_PROGRESS)
                .event(ConversationFact.CUSTOMER_CONNECT)
                .businessContext(businessCtx)
                .transitionAccepted(true)
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
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> executed.set(true);

        CompletableFuture<Void> future = worker.submitWithResult(action, stateCtx);

        // Should complete successfully
        future.get(3, TimeUnit.SECONDS);
        assertTrue(executed.get(), "Action should have been executed");
        assertTrue(future.isDone(), "Future should be done");
        assertFalse(future.isCompletedExceptionally(), "Future should not complete exceptionally");
    }

    @Test
    void testSubmitWithResultFailure() {
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            throw new RuntimeException("intentional test error");
        };

        CompletableFuture<Void> future = worker.submitWithResult(action, stateCtx);

        // Should complete exceptionally
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RuntimeException, "Cause should be RuntimeException");
        assertEquals("intentional test error", ex.getCause().getMessage());
        assertTrue(future.isCompletedExceptionally(), "Future should complete exceptionally");
    }

    @Test
    void testSubmitWithResultThenApply() throws ExecutionException, InterruptedException, TimeoutException {
        AtomicBoolean executed = new AtomicBoolean(false);
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> executed.set(true);

        String result = worker.submitWithResult(action, stateCtx)
                .thenApply(v -> "action completed")
                .get(3, TimeUnit.SECONDS);

        assertEquals("action completed", result);
        assertTrue(executed.get());
    }

    @Test
    void testSubmitWithResultExceptionally() throws ExecutionException, InterruptedException, TimeoutException {
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            throw new RuntimeException("test error");
        };

        String result = worker.submitWithResult(action, stateCtx)
                .thenApply(v -> "success")
                .exceptionally(ex -> "recovered: " + ex.getMessage())
                .get(3, TimeUnit.SECONDS);

        assertTrue(result.startsWith("recovered:"), "Should recover from exception");
    }

    @Test
    void testSubmitWithResultNullActionThrows() {
        assertThrows(NullPointerException.class, () -> worker.submitWithResult(null, stateCtx));
    }

    @Test
    void testSubmitWithResultNullContextThrows() {
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {};
        assertThrows(NullPointerException.class, () -> worker.submitWithResult(action, null));
    }

    // ==================== submitWithCallback Tests ====================

    @Test
    void testSubmitWithCallbackSuccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean successCalled = new AtomicBoolean(false);
        AtomicBoolean failureCalled = new AtomicBoolean(false);

        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {};

        worker.submitWithCallback(action, stateCtx,
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
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            throw new RuntimeException("callback test error");
        };

        worker.submitWithCallback(action, stateCtx,
                ctx -> {
                    successCalled.set(true);
                    latch.countDown();
                },
                ex -> {
                    failureCalled.set(true);
                    capturedError.set(ex);
                    latch.countDown();
                });

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback should be invoked");
        assertFalse(successCalled.get(), "Success callback should not be called");
        assertTrue(failureCalled.get(), "Failure callback should be called");
        assertNotNull(capturedError.get(), "Error should be captured");
        assertTrue(capturedError.get().getMessage().contains("callback test error"),
                "Error message should contain the original error");
    }

    @Test
    void testSubmitWithCallbackNullCallbacks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> latch.countDown();

        // Both callbacks null should not throw
        assertDoesNotThrow(() -> worker.submitWithCallback(action, stateCtx, null, null));
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitWithCallbackOnlySuccessCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {};

        worker.submitWithCallback(action, stateCtx,
                ctx -> {
                    successCalled.set(true);
                    latch.countDown();
                },
                null);

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(successCalled.get());
    }

    @Test
    void testSubmitWithCallbackOnlyFailureCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean failureCalled = new AtomicBoolean(false);

        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            throw new RuntimeException("test");
        };

        worker.submitWithCallback(action, stateCtx,
                null,
                ex -> {
                    failureCalled.set(true);
                    latch.countDown();
                });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertTrue(failureCalled.get());
    }

    // ==================== Backward Compatibility Tests ====================

    @Test
    void testSubmitFireAndForgetStillWorks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> latch.countDown();

        // Original submit method should still work
        worker.submit(action, stateCtx);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitExceptionIsCaughtAndLogged() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Action<ConversationState, ConversationFact, CbolStateContext> action = ctx -> {
            try {
                throw new RuntimeException("fire and forget error");
            } finally {
                latch.countDown();
            }
        };

        // Should not throw to caller
        assertDoesNotThrow(() -> worker.submit(action, stateCtx));
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    // ==================== getExecutor Tests ====================

    @Test
    void testGetExecutorReturnsNonNull() {
        assertNotNull(worker.getExecutor());
        assertFalse(worker.getExecutor().isShutdown());
    }

    @Test
    void testGetExecutorAfterShutdown() {
        worker.shutdown();
        assertTrue(worker.getExecutor().isShutdown());
    }
}
