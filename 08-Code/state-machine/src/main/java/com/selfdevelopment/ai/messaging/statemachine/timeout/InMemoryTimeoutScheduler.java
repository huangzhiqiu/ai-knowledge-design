package com.selfdevelopment.ai.messaging.statemachine.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of {@link StateMachineTimeoutScheduler} using
 * {@link ScheduledExecutorService}.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe: uses {@link ConcurrentHashMap} for entity-to-timer mapping</li>
 *   <li>One-shot and repeating timeouts</li>
 *   <li>Automatic cancellation on state change</li>
 *   <li>Named daemon threads for easy debugging</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachineTimeoutScheduler<ConvState, ConvEvent> scheduler =
 *     new InMemoryTimeoutScheduler<>("conversation-timeout", 2);
 *
 * // Schedule a 30-second idle timeout
 * scheduler.schedule("conv-123", TimeoutConfig.<ConvState, ConvEvent>builder()
 *     .state(ConvState.ACTIVE)
 *     .timeoutEvent(ConvEvent.IDLE_TIMEOUT)
 *     .duration(30)
 *     .timeUnit(TimeUnit.SECONDS)
 *     .build(), (entityId, event) -> {
 *         // fire the timeout event on the state machine
 *     });
 *
 * // Cancel when state changes
 * scheduler.cancel("conv-123");
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 */
public class InMemoryTimeoutScheduler<S, E> implements StateMachineTimeoutScheduler<S, E> {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTimeoutScheduler.class);

    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledTimeout<S, E>> scheduledTimeouts = new ConcurrentHashMap<>();
    private volatile boolean shutdown = false;

    /**
     * Creates a scheduler with a single daemon thread.
     */
    public InMemoryTimeoutScheduler() {
        this("statemachine-timeout", 1);
    }

    /**
     * Creates a scheduler with the given thread pool name and size.
     *
     * @param poolName  the thread pool name (used for thread naming)
     * @param poolSize  the number of threads in the pool
     */
    public InMemoryTimeoutScheduler(String poolName, int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be > 0");
        }
        this.executor = Executors.newScheduledThreadPool(poolSize, new NamedDaemonThreadFactory(poolName));
    }

    /**
     * Creates a scheduler with a custom executor.
     *
     * @param executor the scheduled executor service
     */
    public InMemoryTimeoutScheduler(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public void schedule(String entityId, TimeoutConfig<S, E> config, TimeoutCallback<S, E> callback) {
        if (shutdown) {
            log.warn("Scheduler is shut down, ignoring schedule for entity={}", entityId);
            return;
        }
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        // Cancel any existing timeout for this entity
        cancel(entityId);

        long delayMs = config.getDurationMs();
        ScheduledFuture<?> future;

        if (config.isRepeat()) {
            future = executor.scheduleAtFixedRate(() -> {
                try {
                    log.debug("Repeating timeout fired for entity={}, event={}", entityId, config.getTimeoutEvent());
                    callback.onTimeout(entityId, config.getTimeoutEvent());
                } catch (Exception e) {
                    log.error("Error in repeating timeout callback for entity={}", entityId, e);
                }
            }, delayMs, delayMs, TimeUnit.MILLISECONDS);
        } else {
            future = executor.schedule(() -> {
                try {
                    log.debug("Timeout fired for entity={}, event={}", entityId, config.getTimeoutEvent());
                    // Remove from map before callback (one-shot)
                    scheduledTimeouts.remove(entityId);
                    callback.onTimeout(entityId, config.getTimeoutEvent());
                } catch (Exception e) {
                    log.error("Error in timeout callback for entity={}", entityId, e);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }

        scheduledTimeouts.put(entityId, new ScheduledTimeout<>(config, future, System.currentTimeMillis() + delayMs));
        log.debug("Scheduled timeout for entity={}, state={}, event={}, delay={}ms, repeat={}",
                entityId, config.getState(), config.getTimeoutEvent(), delayMs, config.isRepeat());
    }

    @Override
    public void cancel(String entityId) {
        if (entityId == null) {
            return;
        }
        ScheduledTimeout<S, E> existing = scheduledTimeouts.remove(entityId);
        if (existing != null) {
            existing.future().cancel(false);
            log.debug("Cancelled timeout for entity={}", entityId);
        }
    }

    @Override
    public boolean isScheduled(String entityId) {
        if (entityId == null) {
            return false;
        }
        ScheduledTimeout<S, E> timeout = scheduledTimeouts.get(entityId);
        return timeout != null && !timeout.future().isDone();
    }

    @Override
    public long getRemainingMs(String entityId) {
        if (entityId == null) {
            return -1;
        }
        ScheduledTimeout<S, E> timeout = scheduledTimeouts.get(entityId);
        if (timeout == null || timeout.future().isDone()) {
            return -1;
        }
        return Math.max(0, timeout.deadlineMs() - System.currentTimeMillis());
    }

    @Override
    public void shutdown() {
        shutdown = true;
        // Cancel all pending timeouts
        scheduledTimeouts.values().forEach(t -> t.future().cancel(false));
        scheduledTimeouts.clear();
        executor.shutdownNow();
        log.info("Timeout scheduler shut down");
    }

    /**
     * Returns the number of currently scheduled timeouts.
     */
    public int scheduledCount() {
        return (int) scheduledTimeouts.values().stream()
                .filter(t -> !t.future().isDone())
                .count();
    }

    // ===== Internal record =====

    private record ScheduledTimeout<S, E>(
            TimeoutConfig<S, E> config,
            ScheduledFuture<?> future,
            long deadlineMs
    ) {}

    // ===== Named thread factory =====

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String poolName;
        private final AtomicInteger counter = new AtomicInteger(0);

        NamedDaemonThreadFactory(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, poolName + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
