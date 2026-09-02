package com.selfdevelopment.chatengine.repository;

import com.selfdevelopment.statemachine.api.StateMachine;

import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.persistence.VersionedState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void shouldSaveAndLoadState() {
        repository.saveState("conv-1", ConversationState.INITIATED);

        VersionedState<ConversationState> loaded = repository.loadState("conv-1");
        assertNotNull(loaded);
        assertEquals(ConversationState.INITIATED, loaded.state());
        assertTrue(loaded.version() >= 0);
    }

    @Test
    void shouldReturnNullForMissingConversation() {
        assertNull(repository.loadState("nonexistent"));
    }

    @Test
    void shouldCompareAndSetSuccessfully() {
        long initialVersion = repository.saveState("conv-1", ConversationState.INITIATED);

        long newVersion = repository.compareAndSetState(
                "conv-1", initialVersion, ConversationState.IN_PROGRESS);

        assertTrue(newVersion >= 0);
        assertEquals(ConversationState.IN_PROGRESS, repository.loadState("conv-1").state());
    }

    @Test
    void shouldFailCompareAndSetOnVersionMismatch() {
        long initialVersion = repository.saveState("conv-1", ConversationState.INITIATED);

        // Use wrong version
        long result = repository.compareAndSetState(
                "conv-1", initialVersion + 999, ConversationState.IN_PROGRESS);

        assertEquals(-1, result);
        // State should remain unchanged
        assertEquals(ConversationState.INITIATED, repository.loadState("conv-1").state());
    }

    @Test
    void shouldIncrementVersionOnEachSave() {
        long v1 = repository.saveState("conv-1", ConversationState.INITIATED);
        long v2 = repository.saveState("conv-1", ConversationState.IN_PROGRESS);

        assertTrue(v2 > v1);
    }

    @Test
    void shouldCheckExistence() {
        assertFalse(repository.exists("conv-1"));

        repository.saveState("conv-1", ConversationState.INITIATED);

        assertTrue(repository.exists("conv-1"));
    }

    @Test
    void shouldDeleteConversation() {
        repository.saveState("conv-1", ConversationState.INITIATED);
        assertTrue(repository.exists("conv-1"));

        assertTrue(repository.delete("conv-1"));
        assertFalse(repository.exists("conv-1"));
    }

    @Test
    void shouldSaveAndFindConversationInstance() {
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-1")
                .state(ConversationState.INITIATED)
                .market("HK")
                .build();

        repository.saveConversation(conversation);

        assertTrue(repository.findConversation("conv-1").isPresent());
        assertEquals("HK", repository.findConversation("conv-1").get().market());
        assertEquals(1, repository.getConversationCount());
    }

    @Test
    void shouldUpdateConversationStateOnSaveState() {
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-1")
                .state(ConversationState.INITIATED)
                .market("HK")
                .build();

        repository.saveConversation(conversation);
        repository.saveState("conv-1", ConversationState.IN_PROGRESS);

        assertEquals(ConversationState.IN_PROGRESS,
                repository.findConversation("conv-1").get().state());
    }

    @Test
    void shouldUpdateConversationStateOnCompareAndSet() {
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId("conv-1")
                .state(ConversationState.INITIATED)
                .market("HK")
                .build();

        repository.saveConversation(conversation);
        long version = repository.loadState("conv-1").version();
        repository.compareAndSetState("conv-1", version, ConversationState.IN_PROGRESS);

        assertEquals(ConversationState.IN_PROGRESS,
                repository.findConversation("conv-1").get().state());
    }

    @Test
    void shouldClearRepository() {
        repository.saveState("conv-1", ConversationState.INITIATED);
        repository.saveState("conv-2", ConversationState.IN_PROGRESS);

        repository.clear();

        assertEquals(0, repository.getConversationCount());
    }

    @Test
    void shouldReturnUnderlyingStateRepository() {
        assertNotNull(repository.getStateRepository());
    }
}
