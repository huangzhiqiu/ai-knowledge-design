package com.selfdevelopment.chatengine.service;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.StateMachineFactory;
import com.alibaba.cola.statemachine.impl.StateMachineException;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceMdcHelper;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.StateTransitionRecord;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Service for firing conversation events through the state machine.
 * <p>
 * Uses COLA StateMachine. Stateless mode — caller must manage state persistence externally.
 */
@Slf4j
@Service
public class ChatEngineStateMachineService {

    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm;

    /**
     * Creates a service using the globally registered conversation state machine.
     * <p>
     * The state machine must be registered before this constructor is called.
     * In Spring applications, the state machine is automatically registered by
     * the configuration. For non-Spring usage, call
     * {@link ConversationStateMachineFactory#create(ConversationStateMachineFactory.ConversationActions)}
     * first to register the state machine.
     *
     * @throws IllegalStateException if the conversation state machine is not registered
     */
    public ChatEngineStateMachineService() {
        this(getRegisteredStateMachine());
    }

    /**
     * Retrieves the globally registered conversation state machine.
     *
     * @return the registered conversation state machine
     * @throws IllegalStateException if the state machine is not registered
     */
    private static StateMachine<ConversationState, ConversationFact, CbolStateContext> getRegisteredStateMachine() {
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                    StateMachineFactory.get(ConversationStateMachineFactory.MACHINE_ID);
            if (sm == null) {
                throw new IllegalStateException(
                        "Conversation state machine is not registered. " +
                        "Call ConversationStateMachineFactory.create(actions) first, " +
                        "or use the Spring configuration to auto-register it.");
            }
            return sm;
        } catch (StateMachineException e) {
            throw new IllegalStateException(
                    "Conversation state machine is not registered. " +
                    "Call ConversationStateMachineFactory.create(actions) first, " +
                    "or use the Spring configuration to auto-register it.", e);
        }
    }

    /**
     * Creates a service with an explicitly injected state machine.
     * <p>
     * This constructor is primarily for testing — it allows injecting a mock or
     * custom state machine instead of looking it up from the global factory.
     *
     * @param convSm the conversation state machine (must not be null)
     */
    public ChatEngineStateMachineService(StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm) {
        this.convSm = Objects.requireNonNull(convSm, "convSm must not be null");
    }

    /**
     * Fires a conversation fact event through the state machine.
     *
     * @param ctx  the conversation context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the target state after the transition
     * @throws NullPointerException     if ctx or fact is null
     * @throws StateMachineException    if the transition fails
     */
    public ConversationState fire(CbolStateContext ctx, ConversationFact fact) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(ctx.conversation(), "ctx.conversation must not be null");
        Objects.requireNonNull(ctx.traceContext(), "ctx.traceContext must not be null");

        return fireStateless(ctx, fact);
    }

    /**
     * Fires an event without persistent state storage (stateless mode).
     * <p>
     * Wraps any state machine exception with conversation context (conversationId, fact,
     * current state) to make troubleshooting easier.
     */
    private ConversationState fireStateless(CbolStateContext ctx, ConversationFact fact) {
        TraceMdcHelper.set(ctx.traceContext());
        long start = System.currentTimeMillis();
        String conversationId = ctx.conversation().conversationId();
        ConversationState from = ctx.conversation().state();
        try {
            ConversationState target = convSm.fireEvent(from, fact, ctx);

            StateTransitionRecord record = StateTransitionRecord.builder()
                    .businessId(conversationId)
                    .fromState(from.name())
                    .toState(target != null ? target.name() : "null")
                    .fact(fact.name())
                    .guardResult(target != null)
                    .timestampMs(System.currentTimeMillis())
                    .traceId(ctx.traceContext().traceId())
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
            log.info("StateTransitionRecord: {}", record);
            return target;
        } catch (RuntimeException ex) {
            // Wrap with conversation context for easier troubleshooting
            String message = String.format(
                    "State machine transition failed: conversationId=%s, from=%s, fact=%s, traceId=%s: %s",
                    conversationId, from, fact, ctx.traceContext().traceId(), ex.getMessage());
            log.error(message, ex);
            StateMachineException smEx = new StateMachineException(message);
            smEx.initCause(ex);
            throw smEx;
        } finally {
            TraceMdcHelper.clear();
        }
    }

    /**
     * Verifies if an event can be fired from the current state.
     *
     * @param currentState the current state
     * @param fact         the event to verify
     * @return true if the event can be fired, false otherwise
     */
    public boolean verify(ConversationState currentState, ConversationFact fact) {
        return convSm.verify(currentState, fact);
    }
}
