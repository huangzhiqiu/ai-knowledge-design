package com.selfdevelopment.chatengine.repository;

import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for conversation state persistence.
 * <p>
 * <b>RESERVED CODE - Simplified in-memory implementation.</b>
 * <p>
 * Stores conversation metadata and state with optimistic locking (version-based).
 * For production, implement with a persistent database (MySQL, MongoDB, etc.)
 * while maintaining the same interface.
 * <p>
 * Usage:
 * <pre>{@code
 * ConversationRepository repository = new ConversationRepository();
 *
 * // Save initial state
 * repository.saveConversation(conversation);
 *
 * // Load with version
 * Optional<VersionedState> current = repository.loadState("conv-123");
 *
 * // Update with optimistic lock
 * boolean success = repository.compareAndSetState("conv-123", version, ConversationState.IN_PROGRESS);
 * }</pre>
 */
public class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final Map<String, ConversationInstance> conversations = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> versions = new ConcurrentHashMap<>();

    /**
     * Saves a conversation instance with its current state.
     *
     * @param conversation the conversation instance
     */
    public void saveConversation(ConversationInstance conversation) {
        Objects.requireNonNull(conversation, "conversation must not be null");
        conversations.put(conversation.conversationId(), conversation);
        versions.computeIfAbsent(conversation.conversationId(), k -> new AtomicLong(0));
        log.debug("Saved conversation: id={}, state={}", conversation.conversationId(), conversation.state());
    }

    /**
     * Finds a conversation instance by ID.
     *
     * @param conversationId the conversation ID
     * @return Optional containing the conversation instance, or empty if not found
     */
    public Optional<ConversationInstance> findById(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }

    /**
     * Loads the current state with version for optimistic locking.
     *
     * @param conversationId the conversation ID
     * @return Optional containing the versioned state, or empty if not found
     */
    public Optional<VersionedState> loadState(String conversationId) {
        ConversationInstance conversation = conversations.get(conversationId);
        AtomicLong version = versions.get(conversationId);
        if (conversation == null || version == null) {
            return Optional.empty();
        }
        return Optional.of(new VersionedState(conversation.state(), version.get()));
    }

    /**
     * Updates the state using optimistic locking (compare-and-set).
     *
     * @param conversationId   the conversation ID
     * @param expectedVersion  the expected current version
     * @param newState         the new state to set
     * @return true if the update succeeded, false if version conflict
     */
    public boolean compareAndSetState(String conversationId, long expectedVersion, ConversationState newState) {
        ConversationInstance conversation = conversations.get(conversationId);
        AtomicLong version = versions.get(conversationId);
        if (conversation == null || version == null) {
            log.warn("Conversation not found for CAS: id={}", conversationId);
            return false;
        }

        if (version.compareAndSet(expectedVersion, expectedVersion + 1)) {
            ConversationInstance updated = conversation.withState(newState);
            conversations.put(conversationId, updated);
            log.debug("State updated via CAS: id={}, version={}->{}, state={}",
                    conversationId, expectedVersion, expectedVersion + 1, newState);
            return true;
        }

        log.warn("CAS version conflict: id={}, expectedVersion={}, actualVersion={}",
                conversationId, expectedVersion, version.get());
        return false;
    }

    /**
     * Deletes a conversation by ID.
     *
     * @param conversationId the conversation ID
     */
    public void delete(String conversationId) {
        conversations.remove(conversationId);
        versions.remove(conversationId);
        log.debug("Deleted conversation: id={}", conversationId);
    }

    /**
     * Returns the number of conversations in the repository.
     *
     * @return the count of conversations
     */
    public int count() {
        return conversations.size();
    }

    /**
     * Versioned state record for optimistic locking.
     */
    public record VersionedState(ConversationState state, long version) {}
}
