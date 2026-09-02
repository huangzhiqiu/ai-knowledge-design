package com.selfdevelopment.ai.messaging.statemachine.eventsourcing.impl;

import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.StateTransitionStore;

import com.selfdevelopment.ai.messaging.statemachine.eventsourcing.StateTransitionEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of {@link StateTransitionStore}.
 * <p>
 * Uses {@link ConcurrentHashMap} for entity-to-events mapping and
 * {@link CopyOnWriteArrayList} for thread-safe event lists.
 * <p>
 * Suitable for testing, single-node deployments, or as a reference implementation.
 * For production, implement with a persistent store (JDBC, MongoDB, Kafka, etc.).
 *
 * @param <S> the state type
 * @param <E> the event type
 */
public class InMemoryStateTransitionStore<S, E> implements StateTransitionStore<S, E> {

    private final Map<String, List<StateTransitionEvent<S, E>>> eventsByEntity = new ConcurrentHashMap<>();

    @Override
    public void append(StateTransitionEvent<S, E> event) {
        if (event == null || event.getEntityId() == null) {
            throw new IllegalArgumentException("event and event.entityId must not be null");
        }
        eventsByEntity.computeIfAbsent(event.getEntityId(), k -> new CopyOnWriteArrayList<>())
                .add(event);
    }

    @Override
    public List<StateTransitionEvent<S, E>> replay(String entityId) {
        if (entityId == null) {
            return List.of();
        }
        List<StateTransitionEvent<S, E>> events = eventsByEntity.get(entityId);
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        // Return a copy to prevent external modification
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    @Override
    public List<StateTransitionEvent<S, E>> replayUpTo(String entityId, Instant upTo) {
        if (entityId == null || upTo == null) {
            return List.of();
        }
        return replay(entityId).stream()
                .filter(e -> e.getTimestamp() != null && !e.getTimestamp().isAfter(upTo))
                .toList();
    }

    @Override
    public Optional<S> reconstructState(String entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        List<StateTransitionEvent<S, E>> events = eventsByEntity.get(entityId);
        if (events == null || events.isEmpty()) {
            return Optional.empty();
        }
        // Find the last accepted event and return its toState
        for (int i = events.size() - 1; i >= 0; i--) {
            StateTransitionEvent<S, E> event = events.get(i);
            if (event.isAccepted() && event.getToState() != null) {
                return Optional.of(event.getToState());
            }
        }
        return Optional.empty();
    }

    @Override
    public int count(String entityId) {
        if (entityId == null) {
            return 0;
        }
        List<StateTransitionEvent<S, E>> events = eventsByEntity.get(entityId);
        return events != null ? events.size() : 0;
    }

    @Override
    public boolean hasEvents(String entityId) {
        return count(entityId) > 0;
    }

    @Override
    public Optional<StateTransitionEvent<S, E>> lastEvent(String entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        List<StateTransitionEvent<S, E>> events = eventsByEntity.get(entityId);
        if (events == null || events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(events.get(events.size() - 1));
    }

    @Override
    public void clear(String entityId) {
        if (entityId != null) {
            eventsByEntity.remove(entityId);
        }
    }

    @Override
    public void clearAll() {
        eventsByEntity.clear();
    }

    /**
     * Returns the total number of entities with events.
     *
     * @return the entity count
     */
    public int entityCount() {
        return eventsByEntity.size();
    }

    /**
     * Returns the total number of events across all entities.
     *
     * @return the total event count
     */
    public int totalEventCount() {
        return eventsByEntity.values().stream().mapToInt(List::size).sum();
    }
}
