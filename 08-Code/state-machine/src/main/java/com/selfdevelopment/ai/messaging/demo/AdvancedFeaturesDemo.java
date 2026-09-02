package com.selfdevelopment.ai.messaging.demo;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.impl.EventSourcedStateMachine;
import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.impl.InMemoryStateTransitionStore;
import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.StateTransitionEvent;
import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.StateTransitionStore;
import com.selfdevelopment.ai.messaging.statemachine.idempotency.impl.IdempotentStateMachineDecorator;
import com.selfdevelopment.ai.messaging.statemachine.idempotency.impl.InMemoryProcessedEventStore;
import com.selfdevelopment.ai.messaging.statemachine.idempotency.ProcessedEventStore;
import com.selfdevelopment.ai.messaging.statemachine.persistence.impl.InMemoryStateRepository;
import com.selfdevelopment.ai.messaging.statemachine.persistence.StateRepository;
import com.selfdevelopment.ai.messaging.statemachine.persistence.VersionedState;
import com.selfdevelopment.ai.messaging.statemachine.resilience.impl.FailoverStateMachine;
import com.selfdevelopment.ai.messaging.statemachine.resilience.impl.ResilientStateMachine;
import com.selfdevelopment.ai.messaging.statemachine.resilience.impl.ReturnSourceFailureHandler;

import java.util.List;
import java.util.Optional;

/**
 * Advanced features demo.
 * <p>
 * Demonstrates the decorator-based advanced features:
 * <ul>
 *   <li>Persistence with optimistic locking (StateRepository)</li>
 *   <li>Idempotent event processing (deduplication by event ID)</li>
 *   <li>Event sourcing (full audit trail, replay, state reconstruction)</li>
 *   <li>Resilience (failure handling strategies)</li>
 *   <li>Failover (action error → fail event → ERROR state)</li>
 *   <li>Decorator composition (stacking multiple features)</li>
 * </ul>
 */
public class AdvancedFeaturesDemo {

    enum TaskState { PENDING, RUNNING, COMPLETED, FAILED }
    enum TaskEvent { START, COMPLETE, FAIL, RETRY }

    static class TaskContext {
        final String taskId;
        TaskContext(String taskId) { this.taskId = taskId; }
    }

    public static void main(String[] args) {
        System.out.println("=== Advanced Features Demo ===\n");

        StateMachine<TaskState, TaskEvent, TaskContext> base = buildBaseMachine();

        // 1. Persistence with optimistic locking
        System.out.println("--- 1. Persistence & Optimistic Locking ---");
        demoPersistence(base);

        // 2. Idempotency
        System.out.println("\n--- 2. Idempotent Event Processing ---");
        demoIdempotency(base);

        // 3. Event Sourcing
        System.out.println("\n--- 3. Event Sourcing (Audit Trail & Replay) ---");
        demoEventSourcing(base);

        // 4. Resilience
        System.out.println("\n--- 4. Resilience (Failure Handling) ---");
        demoResilience(base);

        // 5. Failover
        System.out.println("\n--- 5. Failover (Action Error → Fail Event) ---");
        demoFailover(base);

        // 6. Decorator composition
        System.out.println("\n--- 6. Decorator Composition (Stacked Features) ---");
        demoComposition(base);

        System.out.println("\n=== Demo complete ===");
    }

    private static StateMachine<TaskState, TaskEvent, TaskContext> buildBaseMachine() {
        return StateMachineBuilder.<TaskState, TaskEvent, TaskContext>builder("task-demo")
                .initialState(TaskState.PENDING)
                .endStates(TaskState.COMPLETED, TaskState.FAILED)
                .transition().from(TaskState.PENDING).on(TaskEvent.START).to(TaskState.RUNNING).and()
                .transition().from(TaskState.RUNNING).on(TaskEvent.COMPLETE).to(TaskState.COMPLETED).and()
                .transition().from(TaskState.RUNNING).on(TaskEvent.FAIL).to(TaskState.FAILED).and()
                .transition().from(TaskState.FAILED).on(TaskEvent.RETRY).to(TaskState.RUNNING).and()
                .build();
    }

    /**
     * Demo 1: State persistence with optimistic locking.
     */
    private static void demoPersistence(StateMachine<TaskState, TaskEvent, TaskContext> base) {
        StateRepository<TaskState, String> repo = new InMemoryStateRepository<>();
        TaskContext ctx = new TaskContext("task-001");

        // Save initial state
        long version = repo.save("task-001", TaskState.PENDING);
        System.out.println("  Saved initial state: PENDING (v=" + version + ")");

        // Load and transition
        VersionedState<TaskState> current = repo.load("task-001");
        StateContext<TaskState, TaskEvent, TaskContext> result =
                base.fireEvent(current.state(), TaskEvent.START, ctx);

        // Save with optimistic lock (compareAndSet)
        long newVersion = repo.compareAndSet("task-001", current.version(), result.getTargetState());
        System.out.println("  Transitioned: PENDING → RUNNING (v=" + newVersion + ")");

        // Simulate concurrent update (stale version)
        try {
            repo.compareAndSet("task-001", current.version(), TaskState.COMPLETED);
        } catch (Exception e) {
            System.out.println("  Stale write rejected (expected): " + e.getClass().getSimpleName());
        }

        System.out.println("  Final state: " + repo.load("task-001").state());
    }

    /**
     * Demo 2: Idempotent event processing.
     */
    private static void demoIdempotency(StateMachine<TaskState, TaskEvent, TaskContext> base) {
        ProcessedEventStore eventStore = new InMemoryProcessedEventStore();
        IdempotentStateMachineDecorator<TaskState, TaskEvent, TaskContext> idempotent =
                new IdempotentStateMachineDecorator<>(base, eventStore);

        TaskContext ctx = new TaskContext("task-002");

        // First call with event ID "evt-001"
        StateContext<TaskState, TaskEvent, TaskContext> r1 =
                idempotent.fireEvent(TaskState.PENDING, TaskEvent.START, ctx, "evt-001");
        System.out.println("  First call (evt-001): PENDING → " + r1.getTargetState());

        // Second call with SAME event ID — should return cached result, not re-process
        StateContext<TaskState, TaskEvent, TaskContext> r2 =
                idempotent.fireEvent(TaskState.PENDING, TaskEvent.START, ctx, "evt-001");
        System.out.println("  Duplicate call (evt-001): returned cached result → " + r2.getTargetState());

        // Third call with different event ID — should process normally
        StateContext<TaskState, TaskEvent, TaskContext> r3 =
                idempotent.fireEvent(TaskState.RUNNING, TaskEvent.COMPLETE, ctx, "evt-002");
        System.out.println("  New event (evt-002): RUNNING → " + r3.getTargetState());
    }

    /**
     * Demo 3: Event sourcing (audit trail, replay, reconstruction).
     */
    private static void demoEventSourcing(StateMachine<TaskState, TaskEvent, TaskContext> base) {
        StateTransitionStore<TaskState, TaskEvent> store = new InMemoryStateTransitionStore<>();
        EventSourcedStateMachine<TaskState, TaskEvent, TaskContext> eventSourced =
                new EventSourcedStateMachine<>(base, store, "task-003");

        TaskContext ctx = new TaskContext("task-003");

        // Fire some events
        eventSourced.fireEvent(TaskState.PENDING, TaskEvent.START, ctx);
        eventSourced.fireEvent(TaskState.RUNNING, TaskEvent.COMPLETE, ctx);

        // Replay full history
        List<StateTransitionEvent<TaskState, TaskEvent>> history = store.replay("task-003");
        System.out.println("  Event history (" + history.size() + " events):");
        for (StateTransitionEvent<TaskState, TaskEvent> evt : history) {
            System.out.printf("    %s --[%s]--> %s (accepted=%s, duration=%dms)%n",
                    evt.getFromState(), evt.getEvent(), evt.getToState(),
                    evt.isAccepted(), evt.getDurationMs());
        }

        // Reconstruct current state
        Optional<TaskState> reconstructed = store.reconstructState("task-003");
        System.out.println("  Reconstructed state: " + reconstructed.orElse(null));

        // Last event
        Optional<StateTransitionEvent<TaskState, TaskEvent>> last = store.lastEvent("task-003");
        System.out.println("  Last event: " + last.map(e -> e.getFromState() + " → " + e.getToState()).orElse("none"));
    }

    /**
     * Demo 4: Resilience (failure handling strategies).
     */
    private static void demoResilience(StateMachine<TaskState, TaskEvent, TaskContext> base) {
        TaskContext ctx = new TaskContext("task-004");

        // ReturnSourceFailureHandler: returns source state instead of throwing
        ResilientStateMachine<TaskState, TaskEvent, TaskContext> resilient =
                new ResilientStateMachine<>(base, new ReturnSourceFailureHandler<>());

        // Fire an invalid event (no transition from COMPLETED)
        StateContext<TaskState, TaskEvent, TaskContext> result =
                resilient.fireEvent(TaskState.COMPLETED, TaskEvent.START, ctx);

        System.out.println("  Invalid event from COMPLETED:");
        System.out.println("    accepted=" + result.isTransitionAccepted());
        System.out.println("    targetState=" + result.getTargetState() + " (returns source state)");
        System.out.println("    No exception thrown (graceful degradation)");
    }

    /**
     * Demo 5: Failover (action error → fail event → ERROR state).
     */
    private static void demoFailover(StateMachine<TaskState, TaskEvent, TaskContext> base) {
        TaskContext ctx = new TaskContext("task-005");

        // Build a machine with a failing action
        StateMachine<TaskState, TaskEvent, TaskContext> failingMachine =
                StateMachineBuilder.<TaskState, TaskEvent, TaskContext>builder("failover-demo")
                        .initialState(TaskState.PENDING)
                        .transition()
                            .from(TaskState.PENDING)
                            .on(TaskEvent.START)
                            .to(TaskState.RUNNING)
                            .perform(c -> { throw new RuntimeException("Simulated action failure"); })
                        .and()
                        .transition()
                            .from(TaskState.PENDING)
                            .on(TaskEvent.FAIL)
                            .to(TaskState.FAILED)
                        .and()
                        .build();

        // Wrap with failover: on action error, fire FAIL event
        FailoverStateMachine<TaskState, TaskEvent, TaskContext> failover =
                new FailoverStateMachine<>(
                        failingMachine,
                        context -> TaskEvent.FAIL,          // generate fail event
                        event -> event == TaskEvent.FAIL    // don't failover fail events themselves
                );

        StateContext<TaskState, TaskEvent, TaskContext> result =
                failover.fireEvent(TaskState.PENDING, TaskEvent.START, ctx);

        System.out.println("  Fired START (action throws RuntimeException):");
        System.out.println("    Failover intercepted action error");
        System.out.println("    Generated FAIL event and re-fired");
        System.out.println("    Final state: PENDING → " + result.getTargetState() + " (via fail branch)");
    }

    /**
     * Demo 6: Decorator composition (stacking multiple features).
     */
    private static void demoComposition(StateMachine<TaskState, TaskEvent, TaskContext> base) {
        TaskContext ctx = new TaskContext("task-006");

        // Stack: Idempotent → EventSourced → Resilient → base
        ProcessedEventStore idempotentStore = new InMemoryProcessedEventStore();
        StateTransitionStore<TaskState, TaskEvent> eventStore = new InMemoryStateTransitionStore<>();

        StateMachine<TaskState, TaskEvent, TaskContext> pipeline =
                new ResilientStateMachine<>(
                        new EventSourcedStateMachine<>(
                                new IdempotentStateMachineDecorator<>(base, idempotentStore),
                                eventStore,
                                "task-006"
                        ),
                        new ReturnSourceFailureHandler<>()
                );

        System.out.println("  Composed pipeline: Resilient → EventSourced → Idempotent → base");
        System.out.println("  Firing events with deduplication + audit + graceful failure:");

        pipeline.fireEvent(TaskState.PENDING, TaskEvent.START, ctx);
        pipeline.fireEvent(TaskState.RUNNING, TaskEvent.COMPLETE, ctx);

        System.out.println("    Events in audit log: " + eventStore.count("task-006"));
        System.out.println("    Reconstructed state: " + eventStore.reconstructState("task-006").orElse(null));
        System.out.println("    All features active simultaneously");
    }
}
