package com.selfdevelopment.statemachine.metrics;

import com.selfdevelopment.statemachine.core.ExtendedState;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.core.Transition;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import com.selfdevelopment.statemachine.api.StateMachineListener;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Collection;
import java.util.Objects;

/**
 * Decorator that adds Micrometer metrics collection to a {@link StateMachine}.
 * <p>
 * Wraps a state machine and automatically records metrics for every event:
 * <ul>
 *   <li>Event received count</li>
 *   <li>Transition duration (Timer)</li>
 *   <li>Success / error / denied counters</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * StateMachine<OrderState, OrderEvent, OrderContext> machine = ...;
 * MeterRegistry registry = ...;
 *
 * StateMachine<OrderState, OrderEvent, OrderContext> monitored =
 *     new MonitoredStateMachine<>(machine, registry);
 *
 * // All fireEvent calls are automatically instrumented
 * monitored.fireEvent(OrderState.CREATED, OrderEvent.PAY, context);
 * }</pre>
 *
 * @param <S> the state type
 * @param <E> the event type
 * @param <C> the context type
 */
public class MonitoredStateMachine<S, E, C> implements StateMachine<S, E, C> {

    private final StateMachine<S, E, C> delegate;
    private final StateMachineMetrics metrics;

    /**
     * Creates a monitored state machine.
     *
     * @param delegate the underlying state machine
     * @param registry the Micrometer meter registry
     */
    public MonitoredStateMachine(StateMachine<S, E, C> delegate, MeterRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.metrics = new StateMachineMetrics(Objects.requireNonNull(registry, "registry must not be null"));
    }

    /**
     * Creates a monitored state machine with a pre-built metrics collector.
     *
     * @param delegate the underlying state machine
     * @param metrics  the metrics collector
     */
    public MonitoredStateMachine(StateMachine<S, E, C> delegate, StateMachineMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context) {
        return fireEvent(sourceState, event, context, null);
    }

    @Override
    public StateContext<S, E, C> fireEvent(S sourceState, E event, C context, ExtendedState extendedState) {
        String machineId = delegate.getMachineId();
        String from = sourceState != null ? sourceState.toString() : "null";
        String eventName = event != null ? event.toString() : "null";

        // Record event received
        metrics.recordEventReceived(machineId, from, eventName);

        long start = System.nanoTime();
        try {
            StateContext<S, E, C> result = delegate.fireEvent(sourceState, event, context, extendedState);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            String to = result.getTargetState() != null ? result.getTargetState().toString() : "null";
            metrics.recordSuccess(machineId, from, to, eventName, durationMs);
            return result;

        } catch (StateMachineException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String message = e.getMessage() != null ? e.getMessage() : "unknown";

            if (message.contains("No transition found") || message.contains("guard condition failed")) {
                metrics.recordDenied(machineId, from, eventName,
                        message.contains("No transition") ? "no_transition" : "guard_failed");
            } else {
                metrics.recordError(machineId, from, from, eventName, "action_failed");
            }
            throw e;
        } catch (RuntimeException e) {
            metrics.recordError(machineId, from, from, eventName, e.getClass().getSimpleName());
            throw e;
        }
    }

    // ===== Delegated methods =====

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public boolean hasTransition(S sourceState, E event) {
        return delegate.hasTransition(sourceState, event);
    }

    @Override
    public boolean canFire(S sourceState, E event, C context) {
        return delegate.canFire(sourceState, event, context);
    }

    @Override
    public int getTransitionCount() {
        return delegate.getTransitionCount();
    }

    @Override
    public Collection<Transition<S, E, C>> getAllTransitions() {
        return delegate.getAllTransitions();
    }

    @Override
    public String getMachineId() {
        return delegate.getMachineId();
    }

    @Override
    public S getInitialState() {
        return delegate.getInitialState();
    }

    @Override
    public Collection<S> getEndStates() {
        return delegate.getEndStates();
    }

    @Override
    public void addListener(StateMachineListener<S, E, C> listener) {
        delegate.addListener(listener);
    }

    @Override
    public void removeListener(StateMachineListener<S, E, C> listener) {
        delegate.removeListener(listener);
    }

    /**
     * Returns the metrics collector for direct access.
     *
     * @return the metrics collector
     */
    public StateMachineMetrics getMetrics() {
        return metrics;
    }
}
