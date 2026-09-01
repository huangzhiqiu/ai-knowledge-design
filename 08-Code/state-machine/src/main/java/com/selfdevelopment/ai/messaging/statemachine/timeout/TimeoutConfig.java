package com.selfdevelopment.ai.messaging.statemachine.timeout;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for a state timeout.
 * <p>
 * When the state machine enters a state with a timeout config, a timer is started.
 * If the state is not left before the timeout duration elapses, the configured
 * timeout event is automatically fired.
 * <p>
 * This enables patterns like:
 * <ul>
 *   <li>Idle timeout: if no user message within N seconds, auto-close conversation</li>
 *   <li>Transfer timeout: if agent doesn't accept within N seconds, auto-fail transfer</li>
 *   <li>Ending grace: wait N seconds before finalizing a closed conversation</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * TimeoutConfig<TestState, TestEvent> config = TimeoutConfig.<TestState, TestEvent>builder()
 *     .state(TestState.WAITING)
 *     .timeoutEvent(TestEvent.TIMEOUT)
 *     .duration(30)
 *     .timeUnit(TimeUnit.SECONDS)
 *     .build();
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 */
public final class TimeoutConfig<S, E> {

    private final S state;
    private final E timeoutEvent;
    private final long duration;
    private final TimeUnit timeUnit;
    private final boolean repeat;

    private TimeoutConfig(Builder<S, E> builder) {
        this.state = Objects.requireNonNull(builder.state, "state must not be null");
        this.timeoutEvent = Objects.requireNonNull(builder.timeoutEvent, "timeoutEvent must not be null");
        if (builder.duration <= 0) {
            throw new IllegalArgumentException("duration must be > 0");
        }
        this.duration = builder.duration;
        this.timeUnit = Objects.requireNonNull(builder.timeUnit, "timeUnit must not be null");
        this.repeat = builder.repeat;
    }

    /**
     * Returns the state this timeout applies to.
     */
    public S getState() {
        return state;
    }

    /**
     * Returns the event to fire when the timeout occurs.
     */
    public E getTimeoutEvent() {
        return timeoutEvent;
    }

    /**
     * Returns the timeout duration.
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Returns the time unit for the duration.
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Returns the timeout duration in milliseconds.
     */
    public long getDurationMs() {
        return timeUnit.toMillis(duration);
    }

    /**
     * Returns whether the timeout should repeat (re-arm after firing).
     * <p>
     * Default is false (one-shot). Use true for periodic timeouts like
     * "send a reminder every 60 seconds while in WAITING state".
     */
    public boolean isRepeat() {
        return repeat;
    }

    /**
     * Creates a builder.
     */
    public static <S, E> Builder<S, E> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link TimeoutConfig}.
     */
    public static final class Builder<S, E> {
        private S state;
        private E timeoutEvent;
        private long duration;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private boolean repeat = false;

        public Builder<S, E> state(S state) {
            this.state = state;
            return this;
        }

        public Builder<S, E> timeoutEvent(E timeoutEvent) {
            this.timeoutEvent = timeoutEvent;
            return this;
        }

        public Builder<S, E> duration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder<S, E> timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        public Builder<S, E> repeat(boolean repeat) {
            this.repeat = repeat;
            return this;
        }

        public TimeoutConfig<S, E> build() {
            return new TimeoutConfig<>(this);
        }
    }
}
