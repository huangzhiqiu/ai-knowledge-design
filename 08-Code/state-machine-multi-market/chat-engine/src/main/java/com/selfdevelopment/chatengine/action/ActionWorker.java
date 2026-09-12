package com.selfdevelopment.chatengine.action;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceMdcHelper;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 * Uses COLA {@link Action} interface.
 */
@Slf4j
@Component
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
     *
     * @param action the action to execute (COLA Action interface)
     * @param from   the source state
     * @param to     the target state
     * @param event  the event that triggered the transition
     * @param ctx    the business context
     */
    public void submit(Action<ConversationState, ConversationFact, CbolStateContext> action,
                       ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        submitWithResult(action, from, to, event, ctx).exceptionally(ex -> {
            String conversationId = (ctx.conversation() != null)
                    ? ctx.conversation().conversationId() : "unknown";
            log.error("Action execution failed, conversationId={}", conversationId, ex);
            return null;
        });
    }

    /**
     * Submits an action for asynchronous execution and returns a CompletableFuture.
     * Trace context (MDC) is automatically propagated to the worker thread.
     *
     * @param action the action to execute (COLA Action interface)
     * @param from   the source state
     * @param to     the target state
     * @param event  the event that triggered the transition
     * @param ctx    the business context
     * @return a CompletableFuture that completes when the action finishes
     */
    public CompletableFuture<Void> submitWithResult(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(ctx.traceContext(), "ctx.traceContext must not be null");

        return CompletableFuture.runAsync(() -> {
            try {
                TraceMdcHelper.set(ctx.traceContext());
                action.execute(from, to, event, ctx);
            } finally {
                TraceMdcHelper.clear();
            }
        }, executor);
    }

    /**
     * Submits an action for asynchronous execution with success and failure callbacks.
     * Trace context (MDC) is automatically propagated to the worker thread.
     *
     * @param action    the action to execute (COLA Action interface)
     * @param from      the source state
     * @param to        the target state
     * @param event     the event that triggered the transition
     * @param ctx       the business context
     * @param onSuccess callback invoked when the action succeeds (may be null)
     * @param onFailure callback invoked when the action fails (may be null)
     */
    public void submitWithCallback(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx,
            Consumer<CbolStateContext> onSuccess,
            Consumer<Throwable> onFailure) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        submitWithResult(action, from, to, event, ctx)
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
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Named thread factory for better debugging.
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
