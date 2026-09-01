package com.selfdevelopment.ai.messaging.statemachine.idempotency;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ProcessedEventStore}.
 * <p>
 * Uses {@link ConcurrentHashMap#newKeySet()} for thread-safe storage.
 * Suitable for single-node deployments or testing.
 * <p>
 * For distributed deployments, implement with Redis SET or a database
 * with a unique constraint on eventId.
 */
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isProcessed(String eventId) {
        if (eventId == null) {
            return false;
        }
        return processedEvents.contains(eventId);
    }

    @Override
    public boolean markProcessed(String eventId) {
        if (eventId == null) {
            return false;
        }
        return processedEvents.add(eventId);
    }

    @Override
    public void remove(String eventId) {
        if (eventId != null) {
            processedEvents.remove(eventId);
        }
    }

    @Override
    public int size() {
        return processedEvents.size();
    }

    @Override
    public void clear() {
        processedEvents.clear();
    }

    /**
     * Returns an unmodifiable view of all processed event IDs.
     *
     * @return unmodifiable set of event IDs
     */
    public Set<String> getAll() {
        return Collections.unmodifiableSet(processedEvents);
    }
}
