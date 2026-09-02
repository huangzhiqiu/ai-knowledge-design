package com.selfdevelopment.statemachine.core;

import com.selfdevelopment.statemachine.api.StateMachine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExtendedState}.
 */
class ExtendedStateTest {

    private ExtendedState state;

    @BeforeEach
    void setUp() {
        state = new ExtendedState();
    }

    @Test
    void shouldSetAndGet() {
        state.set("key", "value");
        assertEquals("value", state.get("key"));
    }

    @Test
    void shouldReturnNullForMissingKey() {
        assertNull(state.get("nonexistent"));
    }

    @Test
    void shouldReturnDefaultForMissingKey() {
        assertEquals("default", state.getOrDefault("nonexistent", "default"));
    }

    @Test
    void shouldReturnValueOverDefaultWhenPresent() {
        state.set("key", "actual");
        assertEquals("actual", state.getOrDefault("key", "default"));
    }

    @Test
    void shouldReportContains() {
        assertFalse(state.contains("key"));
        state.set("key", "value");
        assertTrue(state.contains("key"));
    }

    @Test
    void shouldRemoveKey() {
        state.set("key", "value");
        assertTrue(state.contains("key"));
        state.remove("key");
        assertFalse(state.contains("key"));
    }

    @Test
    void shouldClearAll() {
        state.set("a", 1);
        state.set("b", 2);
        assertEquals(2, state.getVariables().size());
        state.clear();
        assertEquals(0, state.getVariables().size());
    }

    @Test
    void shouldReturnUnmodifiableVariables() {
        state.set("key", "value");
        Map<String, Object> vars = state.getVariables();
        assertThrows(UnsupportedOperationException.class, () -> vars.put("new", "value"));
    }

    @Test
    void shouldSupportFluentChaining() {
        ExtendedState result = state.set("a", 1).set("b", 2).remove("a");
        assertSame(state, result);
        assertFalse(state.contains("a"));
        assertTrue(state.contains("b"));
    }

    @Test
    void shouldThrowOnNullKey() {
        assertThrows(NullPointerException.class, () -> state.set(null, "value"));
    }

    @Test
    void shouldThrowOnNullValue() {
        // ConcurrentHashMap does not allow null values
        assertThrows(NullPointerException.class, () -> state.set("key", null));
    }

    @Test
    void shouldOverwriteExistingKey() {
        state.set("key", "old");
        state.set("key", "new");
        assertEquals("new", state.get("key"));
    }

    @Test
    void shouldToStringContainVariables() {
        state.set("key", "value");
        String str = state.toString();
        assertTrue(str.contains("key"));
        assertTrue(str.contains("value"));
    }

    @Test
    void shouldSupportDifferentValueTypes() {
        state.set("string", "hello");
        state.set("int", 42);
        state.set("bool", true);
        state.set("obj", new Object());

        assertEquals("hello", state.get("string"));
        assertEquals(Integer.valueOf(42), state.get("int"));
        assertEquals(Boolean.TRUE, state.get("bool"));
        assertNotNull(state.get("obj"));
    }
}
