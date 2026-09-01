package com.selfdevelopment.ai.messaging.cbol.statemachine;

import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;
import com.selfdevelopment.ai.messaging.statemachine.persistence.OptimisticLockException;
import com.selfdevelopment.ai.messaging.statemachine.persistence.StateRepository;
import com.selfdevelopment.ai.messaging.statemachine.persistence.VersionedState;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceMdcHelper;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.StateTransitionRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Function;

@Slf4j
public class CbolStateMachineService {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm;
    private final StateRepository<ConversationState, String> stateRepository;
    private final int maxRetries;

    /**
     * Creates a service without persistent state storage (stateless mode).
     * Caller must manage state persistence externally.
     */
    public CbolStateMachineService() {
        this(null, DEFAULT_MAX_RETRIES);
    }

    /**
     * Creates a service with persistent state storage and optimistic locking.
     *
     * @param stateRepository the state repository for persistence
     */
    public CbolStateMachineService(StateRepository<ConversationState, String> stateRepository) {
        this(stateRepository, DEFAULT_MAX_RETRIES);
    }

    /**
     * Creates a service with persistent state storage, optimistic locking, and custom retry count.
     *
     * @param stateRepository the state repository for persistence
     * @param maxRetries      maximum number of retries on optimistic lock conflict
     */
    public CbolStateMachineService(StateRepository<ConversationState, String> stateRepository,
                                    int maxRetries) {
        this.convSm = CbolStateMachineRegistry.get(ConversationStateMachineFactory.MACHINE_ID);
        this.stateRepository = stateRepository;
        this.maxRetries = maxRetries;
    }

    /**
     * Fires a conversation fact event through the state machine.
     * <p>
     * If a state repository is configured, this method uses optimistic locking:
     * it loads the current state from the repository, executes the transition,
     * and saves the new state using compare-and-set. On version conflict, it
     * retries up to {@code maxRetries} times.
     *
     * @param ctx  the conversation context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the state context after the transition
     * @throws NullPointerException      if ctx or fact is null
     * @throws OptimisticLockException  if all retries fail due to version conflict
     */
    public StateContext<ConversationState, ConversationFact, CbolStateContext> fire(
            CbolStateContext ctx, ConversationFact fact) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(ctx.conversation(), "ctx.conversation must not be null");
        Objects.requireNonNull(ctx.traceContext(), "ctx.traceContext must not be null");

        if (stateRepository != null) {
            return fireWithLock(ctx, fact);
        }
        return fireStateless(ctx, fact);
    }

    /**
     * Fires an event with optimistic locking and automatic retry.
     */
    private StateContext<ConversationState, ConversationFact, CbolStateContext> fireWithLock(
            CbolStateContext ctx, ConversationFact fact) {
        String conversationId = ctx.conversation().conversationId();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // 1. Load current state with version
            VersionedState<ConversationState> current = stateRepository.load(conversationId);
            if (current == null) {
                throw new IllegalStateException("Conversation not found in repository: " + conversationId);
            }

            // 2. Build context with loaded state
            CbolStateContext ctxWithState = withState(ctx, current.state());

            // 3. Execute state machine
            StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                    fireStateless(ctxWithState, fact);

            // 4. Save with optimistic lock
            long newVersion = stateRepository.compareAndSet(
                    conversationId, current.version(), result.getTargetState());

            if (newVersion >= 0) {
                // Success
                log.debug("State saved successfully, conversationId={}, version={}->{}",
                        conversationId, current.version(), newVersion);
                return result;
            }

            // Version conflict, retry
            log.warn("Optimistic lock conflict, attempt {}/{}, conversationId={}, expectedVersion={}",
                    attempt, maxRetries, conversationId, current.version());
        }

        // All retries exhausted
        VersionedState<ConversationState> latest = stateRepository.load(conversationId);
        throw new OptimisticLockException(
                conversationId,
                latest != null ? latest.version() : -1,
                latest != null ? latest.version() : -1);
    }

    /**
     * Fires an event without persistent state storage (stateless mode).
     */
    private StateContext<ConversationState, ConversationFact, CbolStateContext> fireStateless(
            CbolStateContext ctx, ConversationFact fact) {
        TraceMdcHelper.set(ctx.traceContext());
        long start = System.currentTimeMillis();
        try {
            ConversationState from = ctx.conversation().state();
            StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                    convSm.fireEvent(from, fact, ctx);

            StateTransitionRecord record = StateTransitionRecord.builder()
                    .businessId(ctx.conversation().conversationId())
                    .fromState(from.name())
                    .toState(result.getTargetState().name())
                    .fact(fact.name())
                    .guardResult(result.isTransitionAccepted())
                    .timestampMs(System.currentTimeMillis())
                    .traceId(ctx.traceContext().traceId())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            log.info("StateTransitionRecord: {}", record);
            return result;
        } finally {
            TraceMdcHelper.clear();
        }
    }

    /**
     * Creates a new context with the given state, preserving all other fields.
     */
    private CbolStateContext withState(CbolStateContext ctx, ConversationState state) {
        return CbolStateContext.builder()
                .conversation(ctx.conversation().withState(state))
                .interaction(ctx.interaction())
                .marketConfig(ctx.marketConfig())
                .traceContext(ctx.traceContext())
                .build();
    }

    /**
     * Convenience method that returns only the target state.
     */
    public ConversationState fireAndGetState(CbolStateContext ctx, ConversationFact fact) {
        return fire(ctx, fact).getTargetState();
    }

    /**
     * Returns whether this service uses persistent state storage.
     *
     * @return true if a state repository is configured
     */
    public boolean isPersistent() {
        return stateRepository != null;
    }
}
