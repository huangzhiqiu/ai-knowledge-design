package com.selfdevelopment.statemachine.eventsourcing;

import com.selfdevelopment.statemachine.eventsourcing.impl.EventSourcedStateMachine;

import com.selfdevelopment.statemachine.eventsourcing.impl.InMemoryStateTransitionStore;

import com.selfdevelopment.statemachine.core.Transition;

import com.selfdevelopment.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.exception.StateMachineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryStateTransitionStore} and {@link EventSourcedStateMachine}.
 */
class EventSourcingTest {

    enum TestState { A, B, C }
    enum TestEvent { GO, BACK, INVALID }

    private InMemoryStateTransitionStore<TestState, TestEvent> store;
    private StateMachine<TestState, TestEvent, Void> machine;
    private EventSourcedStateMachine<TestState, TestEvent, Void> eventSourced;

    @BeforeEach
    void setUp() {
        store = new InMemoryStateTransitionStore<>();
        machine = StateMachineBuilder.<TestState, TestEvent, Void>builder("test")
                .initialState(TestState.A)
                .transition()
                    .from(TestState.A).on(TestEvent.GO).to(TestState.B)
                .and()
                .transition()
                    .from(TestState.B).on(TestEvent.GO).to(TestState.C)
                .and()
                .build();
        eventSourced = new EventSourcedStateMachine<>(machine, store, "entity-1");
    }

    // ===== InMemoryStateTransitionStore tests =====

    @Test
    void shouldAppendAndReplayEvents() {
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1")
                .fromState(TestState.A)
                .toState(TestState.B)
                .event(TestEvent.GO)
                .accepted(true)
                .build());

        List<StateTransitionEvent<TestState, TestEvent>> events = store.replay("entity-1");
        assertEquals(1, events.size());
        assertEquals(TestState.A, events.get(0).getFromState());
        assertEquals(TestState.B, events.get(0).getToState());
    }

    @Test
    void shouldReconstructStateFromEvents() {
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.A).toState(TestState.B)
                .event(TestEvent.GO).accepted(true).build());
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.B).toState(TestState.C)
                .event(TestEvent.GO).accepted(true).build());

        Optional<TestState> state = store.reconstructState("entity-1");
        assertTrue(state.isPresent());
        assertEquals(TestState.C, state.get());
    }

    @Test
    void shouldIgnoreDeniedEventsInReconstruction() {
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.A).toState(TestState.B)
                .event(TestEvent.GO).accepted(true).build());
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.B).toState(TestState.B)
                .event(TestEvent.INVALID).accepted(false).build());

        Optional<TestState> state = store.reconstructState("entity-1");
        assertTrue(state.isPresent());
        assertEquals(TestState.B, state.get());  // denied event ignored
    }

    @Test
    void shouldReturnEmptyForUnknownEntity() {
        assertTrue(store.reconstructState("nonexistent").isEmpty());
        assertTrue(store.lastEvent("nonexistent").isEmpty());
        assertEquals(0, store.count("nonexistent"));
        assertFalse(store.hasEvents("nonexistent"));
    }

    @Test
    void shouldReplayUpToTimestamp() {
        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now().minusSeconds(5);
        Instant t3 = Instant.now();

        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.A).toState(TestState.B)
                .event(TestEvent.GO).accepted(true).timestamp(t1).build());
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.B).toState(TestState.C)
                .event(TestEvent.GO).accepted(true).timestamp(t2).build());
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.C).toState(TestState.A)
                .event(TestEvent.BACK).accepted(true).timestamp(t3).build());

        List<StateTransitionEvent<TestState, TestEvent>> events = store.replayUpTo("entity-1", t2);
        assertEquals(2, events.size());
    }

    @Test
    void shouldReturnLastEvent() {
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.A).toState(TestState.B)
                .event(TestEvent.GO).accepted(true).build());

        Optional<StateTransitionEvent<TestState, TestEvent>> last = store.lastEvent("entity-1");
        assertTrue(last.isPresent());
        assertEquals(TestEvent.GO, last.get().getEvent());
    }

    @Test
    void shouldClearEvents() {
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-1").fromState(TestState.A).toState(TestState.B)
                .event(TestEvent.GO).accepted(true).build());
        store.append(StateTransitionEvent.<TestState, TestEvent>builder()
                .entityId("entity-2").fromState(TestState.A).toState(TestState.B)
                .event(TestEvent.GO).accepted(true).build());

        assertEquals(2, store.entityCount());
        store.clear("entity-1");
        assertEquals(1, store.entityCount());
        store.clearAll();
        assertEquals(0, store.entityCount());
    }

    @Test
    void shouldThrowOnNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> store.append(null));
    }

    // ===== EventSourcedStateMachine tests =====

    @Test
    void shouldRecordSuccessfulTransition() {
        StateContext<TestState, TestEvent, Void> result =
                eventSourced.fireEvent(TestState.A, TestEvent.GO, null);

        assertEquals(TestState.B, result.getTargetState());
        assertEquals(1, store.count("entity-1"));
        assertTrue(store.lastEvent("entity-1").get().isAccepted());
    }

    @Test
    void shouldRecordDeniedTransition() {
        assertThrows(StateMachineException.class, () ->
                eventSourced.fireEvent(TestState.A, TestEvent.INVALID, null));

        assertEquals(1, store.count("entity-1"));
        assertFalse(store.lastEvent("entity-1").get().isAccepted());
        assertNotNull(store.lastEvent("entity-1").get().getDenialReason());
    }

    @Test
    void shouldReconstructStateAfterMultipleTransitions() {
        eventSourced.fireEvent(TestState.A, TestEvent.GO, null);  // A -> B
        eventSourced.fireEvent(TestState.B, TestEvent.GO, null);  // B -> C

        Optional<TestState> state = store.reconstructState("entity-1");
        assertTrue(state.isPresent());
        assertEquals(TestState.C, state.get());
    }

    @Test
    void shouldReturnStore() {
        assertSame(store, eventSourced.getStore());
    }
}
