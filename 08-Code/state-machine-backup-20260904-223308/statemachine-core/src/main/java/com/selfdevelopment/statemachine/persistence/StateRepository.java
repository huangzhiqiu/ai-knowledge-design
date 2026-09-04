package com.selfdevelopment.statemachine.persistence;

/**
 * Repository interface for persisting and retrieving entity states.
 * <p>
 * Implementations must support optimistic locking via version numbers to
 * prevent lost updates in concurrent environments.
 *
 * @param <S> the state type
 * @param <ID> the entity identifier type
 */
public interface StateRepository<S, ID> {

    /**
     * Loads the current state of an entity.
     *
     * @param id the entity identifier
     * @return the versioned state, or null if not found
     */
    VersionedState<S> load(ID id);

    /**
     * Saves the state with optimistic locking (compare-and-set).
     * <p>
     * The save succeeds only if the current version in the store matches
     * the expected version. This prevents lost updates when multiple threads
     * or processes attempt to modify the same entity concurrently.
     *
     * @param id              the entity identifier
     * @param expectedVersion the version expected to be in the store
     * @param newState        the new state to save
     * @return the new version if save succeeded, or -1 if version mismatch
     */
    long compareAndSet(ID id, long expectedVersion, S newState);

    /**
     * Force-saves the state without version check.
     * Use with caution — this can overwrite concurrent changes.
     *
     * @param id    the entity identifier
     * @param state the state to save
     * @return the new version
     */
    long save(ID id, S state);

    /**
     * Checks whether an entity exists in the repository.
     *
     * @param id the entity identifier
     * @return true if the entity exists
     */
    boolean exists(ID id);

    /**
     * Deletes an entity from the repository.
     *
     * @param id the entity identifier
     * @return true if the entity was deleted
     */
    boolean delete(ID id);
}
