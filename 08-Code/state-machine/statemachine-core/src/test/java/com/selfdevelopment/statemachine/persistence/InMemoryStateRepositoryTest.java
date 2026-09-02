package com.selfdevelopment.statemachine.persistence;

import com.selfdevelopment.statemachine.persistence.impl.InMemoryStateRepository;

import com.selfdevelopment.statemachine.api.StateMachine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryStateRepository}.
 */
class InMemoryStateRepositoryTest {

    private InMemoryStateRepository<String, String> repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryStateRepository<>();
    }

    @Test
    void shouldSaveAndLoadState() {
        repository.save("conv-1", "ACTIVE");

        VersionedState<String> loaded = repository.load("conv-1");
        assertNotNull(loaded);
        assertEquals("ACTIVE", loaded.state());
        assertEquals(1, loaded.version());
    }

    @Test
    void shouldReturnNullForNonExistentEntity() {
        assertNull(repository.load("nonexistent"));
    }

    @Test
    void shouldIncrementVersionOnEachSave() {
        repository.save("conv-1", "STATE1");
        repository.save("conv-1", "STATE2");
        repository.save("conv-1", "STATE3");

        VersionedState<String> loaded = repository.load("conv-1");
        assertEquals("STATE3", loaded.state());
        assertEquals(3, loaded.version());
    }

    @Test
    void shouldCompareAndSetSuccessfully() {
        repository.save("conv-1", "STATE1");
        VersionedState<String> current = repository.load("conv-1");

        long newVersion = repository.compareAndSet("conv-1", current.version(), "STATE2");

        assertTrue(newVersion > 0);
        assertEquals("STATE2", repository.load("conv-1").state());
        assertEquals(current.version() + 1, repository.load("conv-1").version());
    }

    @Test
    void shouldFailCompareAndSetOnVersionMismatch() {
        repository.save("conv-1", "STATE1");

        // Try to update with wrong expected version
        long result = repository.compareAndSet("conv-1", 999, "STATE2");

        assertEquals(-1, result);
        assertEquals("STATE1", repository.load("conv-1").state());
    }

    @Test
    void shouldCreateNewEntityWithCompareAndSetVersionZero() {
        long result = repository.compareAndSet("new-conv", 0, "INITIAL");

        assertTrue(result > 0);
        assertTrue(repository.exists("new-conv"));
        assertEquals("INITIAL", repository.load("new-conv").state());
    }

    @Test
    void shouldNotCreateNewEntityWithNonZeroExpectedVersion() {
        long result = repository.compareAndSet("new-conv", 1, "INITIAL");

        assertEquals(-1, result);
        assertFalse(repository.exists("new-conv"));
    }

    @Test
    void shouldCheckExistence() {
        assertFalse(repository.exists("conv-1"));
        repository.save("conv-1", "STATE1");
        assertTrue(repository.exists("conv-1"));
    }

    @Test
    void shouldDeleteEntity() {
        repository.save("conv-1", "STATE1");
        assertTrue(repository.exists("conv-1"));

        assertTrue(repository.delete("conv-1"));
        assertFalse(repository.exists("conv-1"));
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentEntity() {
        assertFalse(repository.delete("nonexistent"));
    }

    @Test
    void shouldReportSize() {
        assertEquals(0, repository.size());
        repository.save("conv-1", "STATE1");
        repository.save("conv-2", "STATE2");
        assertEquals(2, repository.size());
    }

    @Test
    void shouldClearAllEntities() {
        repository.save("conv-1", "STATE1");
        repository.save("conv-2", "STATE2");
        assertEquals(2, repository.size());

        repository.clear();
        assertEquals(0, repository.size());
    }

    @Test
    void shouldHandleConcurrentUpdates() throws InterruptedException {
        repository.save("conv-1", "STATE0");
        VersionedState<String> initial = repository.load("conv-1");

        Thread t1 = new Thread(() -> repository.compareAndSet("conv-1", initial.version(), "STATE1"));
        Thread t2 = new Thread(() -> repository.compareAndSet("conv-1", initial.version(), "STATE2"));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Only one should succeed
        String finalState = repository.load("conv-1").state();
        assertTrue(finalState.equals("STATE1") || finalState.equals("STATE2"));
        assertEquals(initial.version() + 1, repository.load("conv-1").version());
    }

    @Test
    void shouldThrowOnNullId() {
        assertThrows(NullPointerException.class, () -> repository.load(null));
        assertThrows(NullPointerException.class, () -> repository.save(null, "STATE"));
        assertThrows(NullPointerException.class, () -> repository.exists(null));
        assertThrows(NullPointerException.class, () -> repository.delete(null));
    }

    @Test
    void shouldThrowOnNullState() {
        assertThrows(NullPointerException.class, () -> repository.save("conv-1", null));
        assertThrows(NullPointerException.class, () -> repository.compareAndSet("conv-1", 0, null));
    }

    @Test
    void shouldCreateInitialVersionedState() {
        VersionedState<String> vs = VersionedState.initial("START");
        assertEquals("START", vs.state());
        assertEquals(0, vs.version());
    }
}
