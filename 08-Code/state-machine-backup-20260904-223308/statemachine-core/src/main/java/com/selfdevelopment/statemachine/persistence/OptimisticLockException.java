package com.selfdevelopment.statemachine.persistence;

import com.selfdevelopment.statemachine.exception.StateMachineException;

/**
 * Exception thrown when an optimistic locking conflict occurs.
 * <p>
 * This happens when another thread or process has modified the entity state
 * since it was loaded, causing the version number to mismatch.
 * <p>
 * Callers should typically retry the operation by reloading the latest state
 * and re-applying the event.
 */
public class OptimisticLockException extends StateMachineException {

    private final Object entityId;
    private final long expectedVersion;
    private final long actualVersion;

    public OptimisticLockException(Object entityId, long expectedVersion, long actualVersion) {
        super(String.format("Optimistic lock conflict for entity '%s': expected version %d, actual version %d",
                entityId, expectedVersion, actualVersion));
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public Object getEntityId() {
        return entityId;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public long getActualVersion() {
        return actualVersion;
    }
}
