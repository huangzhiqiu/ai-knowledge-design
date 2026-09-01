package com.selfdevelopment.ai.messaging.statemachine.persistence;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of {@link StateRepository} with optimistic locking.
 * <p>
 * Uses {@link ConcurrentHashMap} for thread-safe storage and an atomic version
 * counter per entity. Suitable for testing, single-node deployments, or as a
 * reference implementation.
 * <p>
 * For production use, implement {@link StateRepository} with Redis, JDBC,
 * or another persistent store that supports compare-and-set operations.
 *
 * @param <S>  the state type
 * @param <ID> the entity identifier type
 */
public class InMemoryStateRepository<S, ID> implements StateRepository<S, ID> {

    private final ConcurrentHashMap<ID, VersionedState<S>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ID, AtomicLong> versionCounters = new ConcurrentHashMap<>();

    @Override
    public VersionedState<S> load(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        return store.get(id);
    }

    @Override
    public long compareAndSet(ID id, long expectedVersion, S newState) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(newState, "newState must not be null");

        final boolean[] success = {false};
        final long[] newVersion = {-1};

        store.compute(id, (key, current) -> {
            if (current == null) {
                // Entity doesn't exist; only allow if expectedVersion is 0 (initial creation)
                if (expectedVersion == 0) {
                    long version = nextVersion(id);
                    newVersion[0] = version;
                    success[0] = true;
                    return new VersionedState<>(newState, version);
                }
                return null;
            }

            if (current.version() == expectedVersion) {
                long version = nextVersion(id);
                newVersion[0] = version;
                success[0] = true;
                return new VersionedState<>(newState, version);
            }

            // Version mismatch — don't update
            return current;
        });

        return success[0] ? newVersion[0] : -1;
    }

    @Override
    public long save(ID id, S state) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(state, "state must not be null");

        long version = nextVersion(id);
        store.put(id, new VersionedState<>(state, version));
        return version;
    }

    @Override
    public boolean exists(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        return store.containsKey(id);
    }

    @Override
    public boolean delete(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        versionCounters.remove(id);
        return store.remove(id) != null;
    }

    /**
     * Returns the number of entities in the repository.
     *
     * @return the count
     */
    public int size() {
        return store.size();
    }

    /**
     * Removes all entities from the repository.
     */
    public void clear() {
        store.clear();
        versionCounters.clear();
    }

    private long nextVersion(ID id) {
        return versionCounters
                .computeIfAbsent(id, k -> new AtomicLong(0))
                .incrementAndGet();
    }
}
