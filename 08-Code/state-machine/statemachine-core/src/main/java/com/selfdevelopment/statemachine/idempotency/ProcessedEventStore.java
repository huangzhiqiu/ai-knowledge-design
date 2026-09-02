package com.selfdevelopment.statemachine.idempotency;

import java.util.Set;

/**
 * Store for tracking processed event IDs to support idempotent event processing.
 * <p>
 * Implementations should provide a way to check if an event has already been
 * processed and to mark events as processed.
 */
public interface ProcessedEventStore {

    /**
     * Checks whether an event has already been processed.
     *
     * @param eventId the unique event identifier
     * @return true if the event has been processed
     */
    boolean isProcessed(String eventId);

    /**
     * Marks an event as processed.
     *
     * @param eventId the unique event identifier
     * @return true if the event was newly marked (not previously processed)
     */
    boolean markProcessed(String eventId);

    /**
     * Removes an event from the processed set (for testing or rollback).
     *
     * @param eventId the unique event identifier
     */
    void remove(String eventId);

    /**
     * Returns the number of processed events.
     *
     * @return the count
     */
    int size();

    /**
     * Clears all processed events.
     */
    void clear();
}
