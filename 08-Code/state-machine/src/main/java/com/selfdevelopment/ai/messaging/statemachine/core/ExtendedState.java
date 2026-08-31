package com.selfdevelopment.ai.messaging.statemachine.core;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended state for a state machine execution.
 * <p>
 * Provides a key-value store that persists across transitions within a single
 * state machine interaction, allowing actions and guards to share data without
 * polluting the business context.
 * <p>
 * Thread-safe: backed by a {@link ConcurrentHashMap}.
 */
public final class ExtendedState {

    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /**
     * Stores a value.
     *
     * @param key   the key
     * @param value the value (may be null)
     * @return this extended state for fluent chaining
     */
    public ExtendedState set(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        variables.put(key, value);
        return this;
    }

    /**
     * Retrieves a value.
     *
     * @param key the key
     * @param <T> the expected type
     * @return the value, or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) variables.get(key);
    }

    /**
     * Retrieves a value with a default.
     *
     * @param key          the key
     * @param defaultValue the default value if absent
     * @param <T>          the expected type
     * @return the value, or defaultValue if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) variables.getOrDefault(key, defaultValue);
    }

    /**
     * Checks whether a key exists.
     *
     * @param key the key
     * @return true if present
     */
    public boolean contains(String key) {
        return variables.containsKey(key);
    }

    /**
     * Removes a key.
     *
     * @param key the key
     * @return this extended state for fluent chaining
     */
    public ExtendedState remove(String key) {
        variables.remove(key);
        return this;
    }

    /**
     * Returns an unmodifiable view of all variables.
     *
     * @return the variables map
     */
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * Clears all variables.
     */
    public void clear() {
        variables.clear();
    }

    @Override
    public String toString() {
        return "ExtendedState" + variables;
    }
}
