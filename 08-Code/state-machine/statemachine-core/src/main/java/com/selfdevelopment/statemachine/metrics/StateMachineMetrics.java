package com.selfdevelopment.statemachine.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Collects and exposes state machine metrics via Micrometer.
 * <p>
 * Metrics collected:
 * <ul>
 *   <li><b>statemachine.transition.duration</b> (Timer) — Time taken for each transition</li>
 *   <li><b>statemachine.transition.success</b> (Counter) — Successful transitions</li>
 *   <li><b>statemachine.transition.error</b> (Counter) — Failed transitions (action errors)</li>
 *   <li><b>statemachine.transition.denied</b> (Counter) — Denied transitions (no rule/guard failed)</li>
 *   <li><b>statemachine.event.received</b> (Counter) — Total events received</li>
 * </ul>
 * <p>
 * All metrics are tagged with: {@code machine}, {@code from}, {@code to}, {@code event}.
 * <p>
 * Usage:
 * <pre>{@code
 * MeterRegistry registry = ...; // Spring's auto-configured registry or SimpleMeterRegistry
 * StateMachineMetrics metrics = new StateMachineMetrics(registry);
 *
 * // Record a successful transition
 * metrics.recordSuccess("order-machine", "CREATED", "PAID", "PAY", durationMs);
 *
 * // Record a denied transition
 * metrics.recordDenied("order-machine", "PAID", "PAID", "CANCEL", "guard failed");
 * }</pre>
 */
public class StateMachineMetrics {

    private final MeterRegistry registry;
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    // Metric names
    public static final String METRIC_TRANSITION_DURATION = "statemachine.transition.duration";
    public static final String METRIC_TRANSITION_SUCCESS = "statemachine.transition.success";
    public static final String METRIC_TRANSITION_ERROR = "statemachine.transition.error";
    public static final String METRIC_TRANSITION_DENIED = "statemachine.transition.denied";
    public static final String METRIC_EVENT_RECEIVED = "statemachine.event.received";

    /**
     * Creates a metrics collector with the given Micrometer registry.
     *
     * @param registry the Micrometer meter registry
     */
    public StateMachineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records a successful transition.
     *
     * @param machineId  the state machine identifier
     * @param fromState  the source state
     * @param toState    the target state
     * @param event      the triggering event
     * @param durationMs the transition duration in milliseconds
     */
    public void recordSuccess(String machineId, String fromState, String toState,
                              String event, long durationMs) {
        String[] tags = tags(machineId, fromState, toState, event);
        timer(machineId, fromState, toState, event).record(durationMs, TimeUnit.MILLISECONDS);
        counter(METRIC_TRANSITION_SUCCESS, tags).increment();
    }

    /**
     * Records a transition that failed due to an action error.
     *
     * @param machineId the state machine identifier
     * @param fromState the source state
     * @param toState   the intended target state
     * @param event     the triggering event
     * @param errorType the error type (e.g., "action_failed", "exception")
     */
    public void recordError(String machineId, String fromState, String toState,
                            String event, String errorType) {
        String[] tags = tagsWithExtra(machineId, fromState, toState, event, "error", errorType);
        counter(METRIC_TRANSITION_ERROR, tags).increment();
    }

    /**
     * Records a denied transition (no matching rule or guard failed).
     *
     * @param machineId the state machine identifier
     * @param fromState the source state
     * @param event     the triggering event
     * @param reason    the denial reason (e.g., "no_transition", "guard_failed")
     */
    public void recordDenied(String machineId, String fromState, String event, String reason) {
        String[] tags = new String[]{
                "machine", machineId,
                "from", fromState,
                "event", event,
                "reason", reason
        };
        counter(METRIC_TRANSITION_DENIED, tags).increment();
    }

    /**
     * Records that an event was received (before processing).
     *
     * @param machineId the state machine identifier
     * @param fromState the source state
     * @param event     the event
     */
    public void recordEventReceived(String machineId, String fromState, String event) {
        String[] tags = new String[]{
                "machine", machineId,
                "from", fromState,
                "event", event
        };
        counter(METRIC_EVENT_RECEIVED, tags).increment();
    }

    // ===== Private helpers =====

    private String[] tags(String machineId, String fromState, String toState, String event) {
        return new String[]{
                "machine", machineId,
                "from", fromState,
                "to", toState,
                "event", event
        };
    }

    private String[] tagsWithExtra(String machineId, String fromState, String toState,
                                    String event, String extraKey, String extraValue) {
        return new String[]{
                "machine", machineId,
                "from", fromState,
                "to", toState,
                "event", event,
                extraKey, extraValue
        };
    }

    private Timer timer(String machineId, String fromState, String toState, String event) {
        String key = machineId + "|" + fromState + "|" + toState + "|" + event;
        return timerCache.computeIfAbsent(key, k ->
                Timer.builder(METRIC_TRANSITION_DURATION)
                        .description("Time taken for state machine transitions")
                        .tag("machine", machineId)
                        .tag("from", fromState)
                        .tag("to", toState)
                        .tag("event", event)
                        .register(registry));
    }

    private Counter counter(String name, String[] tags) {
        String key = name + "|" + String.join("|", tags);
        return counterCache.computeIfAbsent(key, k ->
                Counter.builder(name)
                        .tags(tags)
                        .register(registry));
    }
}
