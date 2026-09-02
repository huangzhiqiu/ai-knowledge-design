package com.selfdevelopment.chatengine.service;

import com.selfdevelopment.chatengine.context.TraceContext;

import com.selfdevelopment.chatengine.statemachine.registry.CbolStateMachineRegistry;

import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;

import com.selfdevelopment.statemachine.core.StateContext;
import com.selfdevelopment.statemachine.api.StateMachine;
import com.selfdevelopment.statemachine.persistence.OptimisticLockException;
import com.selfdevelopment.statemachine.persistence.StateRepository;
import com.selfdevelopment.statemachine.persistence.VersionedState;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceMdcHelper;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.StateTransitionRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Function;

@Slf4j
public class ChatEngineStateMachineService {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm;
    private final StateRepository<ConversationState, String> stateRepository;
    private final int maxRetries;

    /**
     * Creates a service without persistent state storage (stateless mode).
     * Caller must manage state persistence externally.
     */
    public ChatEngineStateMachineService() {
        this(null, DEFAULT_MAX_RETRIES);
    }

    /**
     * Creates a service with persistent state storage and optimistic locking.
     *
     * @param stateRepository the state repository for persistence
     */
    public ChatEngineStateMachineService(StateRepository<ConversationState, String> stateRepository) {
        this(stateRepository, DEFAULT_MAX_RETRIES);
    }

    /**
     * Creates a service with persistent state storage, optimistic locking, and custom retry count.
     *
     * @param stateRepository the state repository for persistence
     * @param maxRetries      maximum number of retries on optimistic lock conflict
     */
    public ChatEngineStateMachineService(StateRepository<ConversationState, String> stateRepository,
                                    int maxRetries) {
        this(CbolStateMachineRegistry.get(ConversationStateMachineFactory.MACHINE_ID),
                stateRepository, maxRetries);
    }

    /**
     * Creates a service with an explicitly injected state machine.
     * <p>
     * This constructor is primarily for testing — it allows injecting a mock or
     * custom state machine instead of looking it up from the global registry.
     *
     * @param convSm          the conversation state machine (must not be null)
     * @param stateRepository the state repository for persistence (null for stateless mode)
     * @param maxRetries      maximum number of retries on optimistic lock conflict
     */
    public ChatEngineStateMachineService(StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm,
                                    StateRepository<ConversationState, String> stateRepository,
                                    int maxRetries) {
        this.convSm = Objects.requireNonNull(convSm, "convSm must not be null");
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
     * <p>
     * Wraps any state machine exception with conversation context (conversationId, fact,
     * current state) to make troubleshooting easier.
     */
    private StateContext<ConversationState, ConversationFact, CbolStateContext> fireStateless(
            CbolStateContext ctx, ConversationFact fact) {
        TraceMdcHelper.set(ctx.traceContext());
        long start = System.currentTimeMillis();
        String conversationId = ctx.conversation().conversationId();
        ConversationState from = ctx.conversation().state();
        try {
            StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                    convSm.fireEvent(from, fact, ctx);

            StateTransitionRecord record = StateTransitionRecord.builder()
                    .businessId(conversationId)
                    .fromState(from.name())
                    .toState(result.getTargetState() != null ? result.getTargetState().name() : "null")
                    .fact(fact.name())
                    .guardResult(result.isTransitionAccepted())
                    .timestampMs(System.currentTimeMillis())
                    .traceId(ctx.traceContext().traceId())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            log.info("StateTransitionRecord: {}", record);
            return result;
        } catch (RuntimeException ex) {
            // Wrap with conversation context for easier troubleshooting
            String message = String.format(
                    "State machine transition failed: conversationId=%s, from=%s, fact=%s, traceId=%s: %s",
                    conversationId, from, fact, ctx.traceContext().traceId(), ex.getMessage());
            log.error(message, ex);
            throw new com.selfdevelopment.statemachine.exception.StateMachineException(
                    message, ex);
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
                .marketConfig(ctx.marketConfig())
                .traceContext(ctx.traceContext())
                .build();
    }

    /**
     * Convenience method that returns only the target state.
     *
     * @throws IllegalStateException if the transition was rejected and target state is null
     */
    public ConversationState fireAndGetState(CbolStateContext ctx, ConversationFact fact) {
        StateContext<ConversationState, ConversationFact, CbolStateContext> result = fire(ctx, fact);
        if (result.getTargetState() == null) {
            throw new IllegalStateException(
                    "Transition returned null target state (rejected): conversationId="
                            + ctx.conversation().conversationId() + ", fact=" + fact);
        }
        return result.getTargetState();
    }

    /**
     * Closes a conversation, automatically routing through the survey flow if enabled.
     * <p>
     * If {@code conversation.surveyEnabled()} is true, fires {@link ConversationFact#SURVEY_START}
     * to enter {@link ConversationState#SURVEY_IN_PROGRESS}. Otherwise fires
     * {@link ConversationFact#CUSTOMER_CLOSE} to go directly to {@link ConversationState#ENDING}.
     * <p>
     * This ensures the survey is treated as an in-progress state controlled by the state machine
     * flow, rather than a boolean flag on the conversation instance.
     *
     * @param ctx the conversation context
     * @return the state context after the transition
     */
    public StateContext<ConversationState, ConversationFact, CbolStateContext> closeConversation(
            CbolStateContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(ctx.conversation(), "ctx.conversation must not be null");

        ConversationFact fact = ctx.conversation().surveyEnabled()
                ? ConversationFact.SURVEY_START
                : ConversationFact.CUSTOMER_CLOSE;

        log.debug("closeConversation: surveyEnabled={}, firing={}",
                ctx.conversation().surveyEnabled(), fact);

        return fire(ctx, fact);
    }

    /**
     * Completes the survey and transitions to ENDING.
     * <p>
     * The survey is a sub-phase within IN_PROGRESS, not a separate state.
     * This method is valid when the conversation is in {@link ConversationState#IN_PROGRESS}
     * and the survey sub-phase has been started (via SURVEY_START).
     *
     * @param ctx the conversation context
     * @return the state context after the transition
     */
    public StateContext<ConversationState, ConversationFact, CbolStateContext> completeSurvey(
            CbolStateContext ctx) {
        return fire(ctx, ConversationFact.SURVEY_COMPLETE);
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
