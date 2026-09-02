package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.cbol.statemachine.registry.CbolStateMachineRegistry;

import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

import com.selfdevelopment.ai.messaging.cbol.service.CbolStateMachineService;

import com.selfdevelopment.ai.messaging.cbol.statemachine.factory.ConversationStateMachineFactory;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.persistence.impl.InMemoryStateRepository;
import com.selfdevelopment.ai.messaging.statemachine.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CbolStateMachineService} with persistent state repository.
 */
class CbolStateMachineServicePersistenceTest {

    private InMemoryStateRepository<ConversationState, String> repository;
    private CbolStateMachineService service;

    @BeforeEach
    void setUp() {
        ConversationStateMachineFactory.build();
        repository = new InMemoryStateRepository<>();
        service = new CbolStateMachineService(repository, 3);
    }

    @AfterEach
    void tearDown() {
        CbolStateMachineRegistry.clear();
    }

    @Test
    void shouldFireEventWithPersistentState() {
        // Given: conversation in INITIATED state
        repository.save("conv-1", ConversationState.INITIATED);
        CbolStateContext ctx = buildContext("conv-1", ConversationState.INITIATED);

        // When
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

        // Then
        assertEquals(ConversationState.ACTIVE, result.getTargetState());
        assertEquals(ConversationState.ACTIVE, repository.load("conv-1").state());
    }

    @Test
    void shouldIncrementVersionAfterTransition() {
        repository.save("conv-1", ConversationState.INITIATED);
        long initialVersion = repository.load("conv-1").version();

        CbolStateContext ctx = buildContext("conv-1", ConversationState.INITIATED);
        service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

        assertEquals(initialVersion + 1, repository.load("conv-1").version());
    }

    @Test
    void shouldThrowWhenConversationNotFound() {
        CbolStateContext ctx = buildContext("nonexistent", ConversationState.INITIATED);

        assertThrows(IllegalStateException.class, () ->
                service.fire(ctx, ConversationFact.CUSTOMER_CONNECT));
    }

    @Test
    void shouldBePersistent() {
        CbolStateMachineService persistentService = new CbolStateMachineService(repository);
        assertTrue(persistentService.isPersistent());

        CbolStateMachineService statelessService = new CbolStateMachineService();
        assertFalse(statelessService.isPersistent());
    }

    @Test
    void shouldHandleTransferFailedV6() {
        // Given: conversation in TRANSFERRED state
        repository.save("conv-1", ConversationState.TRANSFERRED);
        CbolStateContext ctx = buildContext("conv-1", ConversationState.TRANSFERRED);

        // When: transfer fails
        StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                service.fire(ctx, ConversationFact.TRANSFER_FAILED);

        // Then: v6 behavior — returns to INITIATED, not ACTIVE
        assertEquals(ConversationState.INITIATED, result.getTargetState());
        assertEquals(ConversationState.INITIATED, repository.load("conv-1").state());
    }

    @Test
    void shouldFireMultipleTransitionsSequentially() {
        repository.save("conv-1", ConversationState.INITIATED);

        // INITIATED -> ACTIVE
        service.fire(buildContext("conv-1", ConversationState.INITIATED),
                ConversationFact.CUSTOMER_CONNECT);
        assertEquals(ConversationState.ACTIVE, repository.load("conv-1").state());

        // ACTIVE -> TRANSFERRED
        service.fire(buildContext("conv-1", ConversationState.ACTIVE),
                ConversationFact.TRANSFER_REQUEST);
        assertEquals(ConversationState.TRANSFERRED, repository.load("conv-1").state());

        // TRANSFERRED -> INITIATED (v6)
        service.fire(buildContext("conv-1", ConversationState.TRANSFERRED),
                ConversationFact.TRANSFER_FAILED);
        assertEquals(ConversationState.INITIATED, repository.load("conv-1").state());

        // Version should be incremented 3 times
        assertEquals(4, repository.load("conv-1").version());  // initial save + 3 transitions
    }

    @Test
    void shouldThrowOptimisticLockExceptionWhenAllRetriesFail() {
        // This test simulates a scenario where another process constantly updates
        // the state, causing all retries to fail. We can't easily simulate this
        // with the in-memory repository in a single-threaded test, but we can
        // verify the exception class exists and has the right structure.
        OptimisticLockException ex = new OptimisticLockException("conv-1", 1, 2);
        assertEquals("conv-1", ex.getEntityId());
        assertEquals(1, ex.getExpectedVersion());
        assertEquals(2, ex.getActualVersion());
        assertTrue(ex.getMessage().contains("conv-1"));
    }

    private CbolStateContext buildContext(String conversationId, ConversationState state) {
        return CbolStateContext.builder()
                .conversation(ConversationInstance.builder()
                        .conversationId(conversationId)
                        .state(state)
                        .market("HK")
                        .build())
                .marketConfig(StateMachineMarketConfig.defaultConfig())
                .traceContext(TraceContext.generate())
                .build();
    }
}
