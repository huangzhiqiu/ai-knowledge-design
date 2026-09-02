package com.selfdevelopment.ai.messaging.cbol.action;

import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceMdcHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker for executing state machine actions asynchronously.
 * <p>
 * Uses a bounded thread pool to prevent OOM under high load.
 * Propagates trace context (MDC) to worker threads.
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
     * Submits an action for asynchronous execution.
     * Trace context (MDC) is automatically propagated to the worker thread.
     *
     * @param action the action to execute
     * @param ctx    the state context containing trace information
     * @throws NullPointerException if action or ctx is null
     */
    public void submit(CbolAction action, CbolStateContext ctx) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(ctx.traceContext(), "ctx.traceContext must not be null");

        executor.submit(() -> {
            try {
                TraceMdcHelper.set(ctx.traceContext());
                action.execute(ctx);
            } catch (RuntimeException e) {
                log.error("Action execution failed, conversationId={}",
                        ctx.conversation() != null ? ctx.conversation().conversationId() : "unknown", e);
            } finally {
                TraceMdcHelper.clear();
            }
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
