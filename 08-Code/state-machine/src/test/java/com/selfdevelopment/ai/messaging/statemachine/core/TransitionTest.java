package com.selfdevelopment.ai.messaging.statemachine.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Transition}.
 */
class TransitionTest {

    enum TestState { A, B, C }
    enum TestEvent { E1, E2 }

    @Test
    void shouldCreateTransitionWithAllFields() {
        Guard<TestState, TestEvent, Void> guard = ctx -> true;
        Action<TestState, TestEvent, Void> action = ctx -> {};
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, guard, action, TransitionKind.EXTERNAL);

        assertEquals(TestState.A, t.getSourceState());
        assertEquals(TestEvent.E1, t.getEvent());
        assertEquals(TestState.B, t.getTargetState());
        assertNotNull(t.getGuard());
        assertNotNull(t.getAction());
        assertEquals(TransitionKind.EXTERNAL, t.getKind());
        assertFalse(t.isInternal());
    }

    @Test
    void shouldDefaultToExternalKind() {
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        assertEquals(TransitionKind.EXTERNAL, t.getKind());
    }

    @Test
    void shouldSupportInternalKind() {
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.A, null, null, TransitionKind.INTERNAL);
        assertTrue(t.isInternal());
    }

    @Test
    void shouldMatchSourceAndEvent() {
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        assertTrue(t.matches(TestState.A, TestEvent.E1));
        assertFalse(t.matches(TestState.B, TestEvent.E1));
        assertFalse(t.matches(TestState.A, TestEvent.E2));
    }

    @Test
    void shouldPassGuardWhenNull() {
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        StateContext<TestState, TestEvent, Void> ctx = StateContext.<TestState, TestEvent, Void>builder()
                .sourceState(TestState.A).targetState(TestState.B).event(TestEvent.E1).build();
        assertTrue(t.isGuardSatisfied(ctx));
    }

    @Test
    void shouldEvaluateGuard() {
        Guard<TestState, TestEvent, Void> guard = ctx -> ctx.getSourceState() == TestState.A;
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, guard, null);

        StateContext<TestState, TestEvent, Void> ctxA = StateContext.<TestState, TestEvent, Void>builder()
                .sourceState(TestState.A).build();
        assertTrue(t.isGuardSatisfied(ctxA));

        StateContext<TestState, TestEvent, Void> ctxB = StateContext.<TestState, TestEvent, Void>builder()
                .sourceState(TestState.B).build();
        assertFalse(t.isGuardSatisfied(ctxB));
    }

    @Test
    void shouldExecuteAction() {
        AtomicInteger counter = new AtomicInteger(0);
        Action<TestState, TestEvent, Void> action = ctx -> counter.incrementAndGet();
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, action);

        StateContext<TestState, TestEvent, Void> ctx = StateContext.<TestState, TestEvent, Void>builder().build();
        t.executeAction(ctx);
        assertEquals(1, counter.get());
    }

    @Test
    void shouldNotThrowWhenActionNull() {
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        StateContext<TestState, TestEvent, Void> ctx = StateContext.<TestState, TestEvent, Void>builder().build();
        assertDoesNotThrow(() -> t.executeAction(ctx));
    }

    @Test
    void shouldImplementEquals() {
        Transition<TestState, TestEvent, Void> t1 = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        Transition<TestState, TestEvent, Void> t2 = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        Transition<TestState, TestEvent, Void> t3 = new Transition<>(
                TestState.A, TestEvent.E1, TestState.C, null, null);

        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertEquals(t1, t1);
        assertNotEquals(t1, null);
        assertNotEquals(t1, "not a transition");
    }

    @Test
    void shouldImplementHashCode() {
        Transition<TestState, TestEvent, Void> t1 = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        Transition<TestState, TestEvent, Void> t2 = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void shouldToStringContainStatesAndEvent() {
        Transition<TestState, TestEvent, Void> t = new Transition<>(
                TestState.A, TestEvent.E1, TestState.B, null, null);
        String str = t.toString();
        assertTrue(str.contains("A"));
        assertTrue(str.contains("B"));
        assertTrue(str.contains("E1"));
        assertTrue(str.contains("EXTERNAL"));
    }

    @Test
    void shouldThrowOnNullSource() {
        assertThrows(NullPointerException.class, () ->
                new Transition<>(null, TestEvent.E1, TestState.B, null, null));
    }

    @Test
    void shouldThrowOnNullEvent() {
        assertThrows(NullPointerException.class, () ->
                new Transition<>(TestState.A, null, TestState.B, null, null));
    }

    @Test
    void shouldThrowOnNullTarget() {
        assertThrows(NullPointerException.class, () ->
                new Transition<>(TestState.A, TestEvent.E1, null, null, null));
    }
}
