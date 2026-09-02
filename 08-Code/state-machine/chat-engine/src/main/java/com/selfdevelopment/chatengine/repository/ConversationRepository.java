package com.selfdevelopment.chatengine.repository;

import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.persistence.impl.InMemoryStateRepository;
import com.selfdevelopment.statemachine.persistence.StateRepository;
import com.selfdevelopment.statemachine.persistence.VersionedState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for conversation state persistence.
 * <p>
 * Integrates with the state machine's {@link StateRepository} for optimistic locking,
 * while also storing full conversation metadata (market, customerId, etc.).
 * <p>
 * This implementation uses an in-memory store. For production, implement with
 * a persistent database (MySQL, MongoDB, etc.) while maintaining the same interface.
 * <p>
 * Usage:
 * <pre>{@code
 * ConversationRepository repository = new ConversationRepository();
 *
 * // Save initial state
 * repository.saveState("conv-123", ConversationState.INITIATED);
 *
 * // Load with version
 * VersionedState<ConversationState> current = repository.loadState("conv-123");
 *
 * // Update with optimistic lock
 * long newVersion = repository.compareAndSetState("conv-123", current.version(), ConversationState.ACTIVE);
 * }</pre>
 */
public class ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationRepository.class);

    private final StateRepository<ConversationState, String> stateRepository =
            new InMemoryStateRepository<>();
    private final Map<String, ConversationInstance> conversations = new ConcurrentHashMap<>();

    /**
     * Saves a conversation instance with its current state.
     *
     * @param conversation the conversation instance
     */
    public void saveConversation(ConversationInstance conversation) {
        Objects.requireNonNull(conversation, "conversation must not be null");
        conversations.put(conversation.conversationId(), conversation);
        stateRepository.save(conversation.conversationId(), conversation.state());
        log.debug("Saved conversation: id={}, state={}", conversation.conversationId(), conversation.state());
    }

    /**
     * Finds a conversation instance by ID.
     *
     * @param conversationId the conversation ID
     * @return the conversation instance, or empty if not found
     */
    public Optional<ConversationInstance> findConversation(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }

    /**
     * Loads the versioned state of a conversation.
     *
     * @param conversationId the conversation ID
     * @return the versioned state, or null if not found
     */
    public VersionedState<ConversationState> loadState(String conversationId) {
        return stateRepository.load(conversationId);
    }

    /**
     * Saves the state without version check (force save).
     *
     * @param conversationId the conversation ID
     * @param state          the new state
     * @return the new version
     */
    public long saveState(String conversationId, ConversationState state) {
        long version = stateRepository.save(conversationId, state);
        conversations.computeIfPresent(conversationId, (id, conv) -> conv.withState(state));
        log.debug("Saved state: conversationId={}, state={}, version={}", conversationId, state, version);
        return version;
    }

    /**
     * Saves the state with optimistic locking (compare-and-set).
     *
     * @param conversationId  the conversation ID
     * @param expectedVersion the expected current version
     * @param newState        the new state
     * @return the new version if successful, or -1 if version mismatch
     */
    public long compareAndSetState(String conversationId, long expectedVersion, ConversationState newState) {
        long newVersion = stateRepository.compareAndSet(conversationId, expectedVersion, newState);
        if (newVersion >= 0) {
            conversations.computeIfPresent(conversationId, (id, conv) -> conv.withState(newState));
            log.debug("Compare-and-set success: conversationId={}, state={}, version={}",
                    conversationId, newState, newVersion);
        } else {
            log.warn("Compare-and-set failed: conversationId={}, expectedVersion={}",
                    conversationId, expectedVersion);
        }
        return newVersion;
    }

    /**
     * Checks whether a conversation exists.
     */
    public boolean exists(String conversationId) {
        return stateRepository.exists(conversationId);
    }

    /**
     * Deletes a conversation.
     */
    public boolean delete(String conversationId) {
        conversations.remove(conversationId);
        return stateRepository.delete(conversationId);
    }

    /**
     * Returns the number of stored conversations.
     */
    public int getConversationCount() {
        return conversations.size();
    }

    /**
     * Clears all stored data (use with caution, primarily for testing).
     */
    public void clear() {
        conversations.clear();
        log.info("Conversation repository cleared");
    }

    /**
     * Returns the underlying state repository for advanced operations.
     */
    public StateRepository<ConversationState, String> getStateRepository() {
        return stateRepository;
    }
}
