package com.selfdevelopment.statemachine.idempotency;

import com.selfdevelopment.statemachine.idempotency.impl.InMemoryProcessedEventStore;

import com.selfdevelopment.statemachine.idempotency.impl.IdempotentStateMachineDecorator;

import com.selfdevelopment.statemachine.core.Transition;

import com.selfdevelopment.statemachine.api.Action;

import com.selfdevelopment.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IdempotentStateMachineDecorator}.
 */
class IdempotentStateMachineDecoratorTest {

    enum TestState { A, B, C }
    enum TestEvent { GO, BACK }

    private StateMachine<TestState, TestEvent, Void> machine;
    private IdempotentStateMachineDecorator<TestState, TestEvent, Void> idempotent;
    private InMemoryProcessedEventStore eventStore;

    @BeforeEach
    void setUp() {
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .transition()
                    .from(TestState.B).on(TestEvent.GO).to(TestState.C)
                .and()
                .build();

        eventStore = new InMemoryProcessedEventStore();
        idempotent = new IdempotentStateMachineDecorator<>(machine, eventStore);
    }

    @Test
    void shouldProcessEventFirstTime() {
        StateContext<TestState, TestEvent, Void> result =
                idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");

        assertEquals(TestState.B, result.getTargetState());
        assertTrue(result.isTransitionAccepted());
        assertTrue(idempotent.isEventProcessed("event-1"));
    }

    @Test
    void shouldReturnCachedResultForDuplicateEvent() {
        // First invocation
        StateContext<TestState, TestEvent, Void> first =
                idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");

        // Second invocation with same event ID — should return cached result
        StateContext<TestState, TestEvent, Void> second =
                idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");

        assertSame(first, second);
        assertEquals(1, idempotent.getProcessedEventCount());
    }

    @Test
    void shouldProcessDifferentEventsIndependently() {
        idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");
        idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-2");

        assertEquals(2, idempotent.getProcessedEventCount());
        assertTrue(idempotent.isEventProcessed("event-1"));
        assertTrue(idempotent.isEventProcessed("event-2"));
    }

    @Test
    void shouldNotExecuteActionTwiceForDuplicateEvent() {
        AtomicInteger actionCount = new AtomicInteger(0);

        StateMachine<TestState, TestEvent, Void> machineWithAction =
                StateMachineBuilder.<TestState, TestEvent, Void>builder("action-test")
                    .transition()
                        .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                        .perform(ctx -> actionCount.incrementAndGet())
                    .and()
                    .build();

        IdempotentStateMachineDecorator<TestState, TestEvent, Void> idempotentWithAction =
                new IdempotentStateMachineDecorator<>(machineWithAction);

        idempotentWithAction.fireEvent(TestState.A, TestEvent.GO, null, "event-1");
        idempotentWithAction.fireEvent(TestState.A, TestEvent.GO, null, "event-1");

        assertEquals(1, actionCount.get(), "Action should only execute once");
    }

    @Test
    void shouldGetCachedResult() {
        StateContext<TestState, TestEvent, Void> result =
                idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");

        assertSame(result, idempotent.getCachedResult("event-1"));
    }

    @Test
    void shouldReturnNullForUnprocessedEventCachedResult() {
        assertNull(idempotent.getCachedResult("nonexistent"));
    }

    @Test
    void shouldClearIdempotencyCache() {
        idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");
        assertEquals(1, idempotent.getProcessedEventCount());

        idempotent.clearIdempotencyCache();
        assertEquals(0, idempotent.getProcessedEventCount());
        assertFalse(idempotent.isEventProcessed("event-1"));

        // After clearing, the event can be reprocessed
        StateContext<TestState, TestEvent, Void> result =
                idempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");
        assertEquals(TestState.B, result.getTargetState());
    }

    @Test
    void shouldThrowOnNullEventId() {
        assertThrows(NullPointerException.class, () ->
                idempotent.fireEvent(TestState.A, TestEvent.GO, null, (String) null));
    }

    @Test
    void shouldUseDefaultInMemoryStoreWhenNotProvided() {
        IdempotentStateMachineDecorator<TestState, TestEvent, Void> defaultIdempotent =
                new IdempotentStateMachineDecorator<>(machine);

        StateContext<TestState, TestEvent, Void> result =
                defaultIdempotent.fireEvent(TestState.A, TestEvent.GO, null, "event-1");

        assertEquals(TestState.B, result.getTargetState());
        assertTrue(defaultIdempotent.isEventProcessed("event-1"));
    }

    @Test
    void shouldDelegateLifecycleMethods() {
        assertFalse(idempotent.isStarted());
        idempotent.start();
        assertTrue(idempotent.isStarted());
        idempotent.stop();
        assertFalse(idempotent.isStarted());
    }

    @Test
    void shouldDelegateQueryMethods() {
        assertTrue(idempotent.hasTransition(TestState.A, TestEvent.GO));
        assertFalse(idempotent.hasTransition(TestState.A, TestEvent.BACK));
        assertTrue(idempotent.canFire(TestState.A, TestEvent.GO, null));
        assertEquals(2, idempotent.getTransitionCount());
        assertEquals("test", idempotent.getMachineId());
        assertEquals(TestState.A, idempotent.getInitialState());
    }

    @Test
    void shouldHandleNullEventIdInStore() {
        assertFalse(eventStore.isProcessed(null));
        assertFalse(eventStore.markProcessed(null));
        assertDoesNotThrow(() -> eventStore.remove(null));
    }

    @Test
    void shouldManageEventStore() {
        assertEquals(0, eventStore.size());

        assertTrue(eventStore.markProcessed("event-1"));
        assertFalse(eventStore.markProcessed("event-1"));  // Already processed
        assertEquals(1, eventStore.size());

        eventStore.remove("event-1");
        assertEquals(0, eventStore.size());

        eventStore.markProcessed("event-2");
        eventStore.clear();
        assertEquals(0, eventStore.size());
    }
}
