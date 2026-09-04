package com.selfdevelopment.chatengine.repository;

import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationRepository.
 */
class ConversationRepositoryTest {

    private ConversationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ConversationRepository();
    }

    private ConversationInstance buildConversation(String id, ConversationState state) {
        return ConversationInstance.builder()
                .conversationId(id)
                .state(state)
                .market("HK")
                .build();
    }

    @Test
    void shouldSaveAndLoadState() {
        repository.saveConversation(buildConversation("conv-1", ConversationState.INITIATED));

        Optional<ConversationRepository.VersionedState> loaded = repository.loadState("conv-1");
        assertTrue(loaded.isPresent());
        assertEquals(ConversationState.INITIATED, loaded.get().state());
        assertTrue(loaded.get().version() >= 0);
    }

    @Test
    void shouldReturnEmptyForMissingConversation() {
        assertTrue(repository.loadState("nonexistent").isEmpty());
    }

    @Test
    void shouldCompareAndSetSuccessfully() {
        repository.saveConversation(buildConversation("conv-1", ConversationState.INITIATED));
        long initialVersion = repository.loadState("conv-1").get().version();

        boolean result = repository.compareAndSetState(
                "conv-1", initialVersion, ConversationState.IN_PROGRESS);

        assertTrue(result);
        assertEquals(ConversationState.IN_PROGRESS, repository.loadState("conv-1").get().state());
    }

    @Test
    void shouldFailCompareAndSetOnVersionMismatch() {
        repository.saveConversation(buildConversation("conv-1", ConversationState.INITIATED));
        long initialVersion = repository.loadState("conv-1").get().version();

        // Use wrong version
        boolean result = repository.compareAndSetState(
                "conv-1", initialVersion + 999, ConversationState.IN_PROGRESS);

        assertFalse(result);
        // State should remain unchanged
        assertEquals(ConversationState.INITIATED, repository.loadState("conv-1").get().state());
    }

    @Test
    void shouldIncrementVersionOnEachUpdate() {
        repository.saveConversation(buildConversation("conv-1", ConversationState.INITIATED));
        long v1 = repository.loadState("conv-1").get().version();

        repository.compareAndSetState("conv-1", v1, ConversationState.IN_PROGRESS);
        long v2 = repository.loadState("conv-1").get().version();

        assertTrue(v2 > v1);
    }

    @Test
    void shouldDeleteConversation() {
        repository.saveConversation(buildConversation("conv-1", ConversationState.INITIATED));
        assertTrue(repository.findById("conv-1").isPresent());

        repository.delete("conv-1");
        assertTrue(repository.findById("conv-1").isEmpty());
    }

    @Test
    void shouldSaveAndFindConversationInstance() {
        ConversationInstance conversation = buildConversation("conv-1", ConversationState.INITIATED);

        repository.saveConversation(conversation);

        assertTrue(repository.findById("conv-1").isPresent());
        assertEquals("HK", repository.findById("conv-1").get().market());
        assertEquals(1, repository.count());
    }

    @Test
    void shouldUpdateConversationStateOnCompareAndSet() {
        repository.saveConversation(buildConversation("conv-1", ConversationState.INITIATED));
        long version = repository.loadState("conv-1").get().version();

        repository.compareAndSetState("conv-1", version, ConversationState.IN_PROGRESS);

        assertEquals(ConversationState.IN_PROGRESS,
                repository.findById("conv-1").get().state());
    }
}
