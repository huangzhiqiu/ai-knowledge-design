package com.selfdevelopment.chatengine.action;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceMdcHelper;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.statemachine.api.Action;
import com.selfdevelopment.statemachine.core.StateContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Worker for executing state machine actions asynchronously.
 * <p>
 * <b>RESERVED UTILITY CLASS - Currently not used in production code.</b>
 * <p>
 * The current state machine design uses <b>action-first transition</b>: actions are executed
 * synchronously before state transition. If an action fails, an exception is thrown and the
 * state remains unchanged. This ensures state consistency.
 * <p>
 * This worker is reserved for future use cases where non-critical actions (e.g., sending
 * notifications, audit logging) can be executed asynchronously without affecting the main
 * state transition flow.
 * <p>
 * Uses a bounded thread pool to prevent OOM under high load.
 * Propagates trace context (MDC) to worker threads.
 * <p>
 * This worker directly uses the core {@link Action} interface from statemachine-core,
 * ensuring consistency with the state machine framework.
 * <p>
 * Supports three submission modes:
 * <ul>
 *   <li>{@link #submit(Action, StateContext)}: Fire-and-forget, exceptions are logged only</li>
 *   <li>{@link #submitWithResult(Action, StateContext)}: Returns CompletableFuture for result tracking</li>
 *   <li>{@link #submitWithCallback(Action, StateContext, Consumer, Consumer)}: Success/failure callbacks</li>
 * </ul>
 */
@Slf4j
public class ActionWorker {

    private static final int DEFAULT_CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_MAX_POOL_SIZE = DEFAULT_CORE_POOL_SIZE * 2;
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    private final ExecutorService executor;

    public ActionWorker() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE,
                DEFAULT_KEEP_ALIVE_SECONDS, DEFAULT_QUEUE_CAPACITY);
    }

    public ActionWorker(int corePoolSize, int maxPoolSize,
                        long keepAliveSeconds, int queueCapacity) {
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory("cbol-action-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Submits an action for asynchronous execution (fire-and-forget).
     * Trace context (MDC) is automatically propagated to the worker thread.
     * <p>
     * Exceptions are caught and logged only. Use {@link #submitWithResult} or
     * {@link #submitWithCallback} if you need to handle execution failures.
     *
     * @param action the action to execute (core Action interface)
     * @param ctx    the state context containing trace information
     * @throws NullPointerException if action or ctx is null
     */
    public void submit(Action<ConversationState, ConversationFact, CbolStateContext> action,
                       StateContext<ConversationState, ConversationFact, CbolStateContext> ctx) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        submitWithResult(action, ctx).exceptionally(ex -> {
            CbolStateContext businessCtx = ctx.getBusinessContext();
            String conversationId = (businessCtx != null && businessCtx.conversation() != null)
                    ? businessCtx.conversation().conversationId() : "unknown";
            log.error("Action execution failed, conversationId={}", conversationId, ex);
            return null;
        });
    }

    /**
     * Submits an action for asynchronous execution and returns a CompletableFuture.
     * Trace context (MDC) is automatically propagated to the worker thread.
     * <p>
     * The returned CompletableFuture completes normally when the action succeeds,
     * or completes exceptionally if the action throws an exception.
     *
     * @param action the action to execute (core Action interface)
     * @param ctx    the state context containing trace information
     * @return a CompletableFuture that completes when the action finishes
     * @throws NullPointerException if action or ctx is null
     */
    public CompletableFuture<Void> submitWithResult(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            StateContext<ConversationState, ConversationFact, CbolStateContext> ctx) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        CbolStateContext businessCtx = ctx.getBusinessContext();
        Objects.requireNonNull(businessCtx, "ctx.businessContext must not be null");
        Objects.requireNonNull(businessCtx.traceContext(), "ctx.traceContext must not be null");

        return CompletableFuture.runAsync(() -> {
            try {
                TraceMdcHelper.set(businessCtx.traceContext());
                action.execute(ctx);
            } finally {
                TraceMdcHelper.clear();
            }
        }, executor);
    }

    /**
     * Submits an action for asynchronous execution with success and failure callbacks.
     * Trace context (MDC) is automatically propagated to the worker thread.
     *
     * @param action        the action to execute (core Action interface)
     * @param ctx           the state context containing trace information
     * @param onSuccess     callback invoked when the action succeeds (may be null)
     * @param onFailure     callback invoked when the action fails (may be null)
     * @throws NullPointerException if action or ctx is null
     */
    public void submitWithCallback(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            StateContext<ConversationState, ConversationFact, CbolStateContext> ctx,
            Consumer<StateContext<ConversationState, ConversationFact, CbolStateContext>> onSuccess,
            Consumer<Throwable> onFailure) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        submitWithResult(action, ctx)
                .thenRun(() -> {
                    if (onSuccess != null) {
                        onSuccess.accept(ctx);
                    }
                })
                .exceptionally(ex -> {
                    if (onFailure != null) {
                        onFailure.accept(ex);
                    }
                    return null;
                });
    }

    /**
     * Shuts down the worker thread pool.
     * Waits for up to 5 seconds for running tasks to complete.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the underlying executor service for advanced configuration
     * (e.g., monitoring, metrics collection).
     *
     * @return the underlying ExecutorService instance
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Thread factory that creates named threads for easier debugging.
     */
    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
