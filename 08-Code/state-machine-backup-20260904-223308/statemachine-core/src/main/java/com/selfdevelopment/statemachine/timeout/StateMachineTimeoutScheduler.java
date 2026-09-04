package com.selfdevelopment.statemachine.timeout;

/**
 * Scheduler for state machine timeouts.
 * <p>
 * Implementations are responsible for:
 * <ul>
 *   <li>Scheduling timeout tasks when entering a state</li>
 *   <li>Cancelling timeout tasks when leaving a state</li>
 *   <li>Firing the timeout event when the timer elapses</li>
 * </ul>
 * <p>
 * The scheduler is keyed by entity ID, allowing multiple concurrent state machine
 * instances (e.g., multiple conversations) to each have their own active timers.
 *
 * @param <S> the state type
 * @param <E> the event type
 */
public interface StateMachineTimeoutScheduler<S, E> {

    /**
     * Schedules a timeout for the given entity.
     * <p>
     * If a timeout is already scheduled for this entity, it is cancelled first.
     *
     * @param entityId the entity identifier (e.g., conversation ID)
     * @param config   the timeout configuration
     * @param callback the callback to invoke when the timeout occurs
     */
    void schedule(String entityId, TimeoutConfig<S, E> config, TimeoutCallback<S, E> callback);

    /**
     * Cancels the scheduled timeout for the given entity.
     * <p>
     * If no timeout is scheduled, this is a no-op.
     *
     * @param entityId the entity identifier
     */
    void cancel(String entityId);

    /**
     * Checks whether a timeout is currently scheduled for the given entity.
     *
     * @param entityId the entity identifier
     * @return true if a timeout is scheduled
     */
    boolean isScheduled(String entityId);

    /**
     * Returns the remaining time in milliseconds for the entity's scheduled timeout.
     *
     * @param entityId the entity identifier
     * @return remaining time in ms, or -1 if no timeout is scheduled
     */
    long getRemainingMs(String entityId);

    /**
     * Shuts down the scheduler, cancelling all pending timeouts.
     */
    void shutdown();

    /**
     * Callback invoked when a timeout occurs.
     *
     * @param <S> the state type
     * @param <E> the event type
     */
    @FunctionalInterface
    interface TimeoutCallback<S, E> {
        /**
         * Called when the timeout elapses.
         *
         * @param entityId     the entity identifier
         * @param timeoutEvent the event to fire
         */
        void onTimeout(String entityId, E timeoutEvent);
    }
}
