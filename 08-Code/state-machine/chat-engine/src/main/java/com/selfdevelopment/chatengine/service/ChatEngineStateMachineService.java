package com.selfdevelopment.chatengine.service;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.impl.StateMachineException;
import com.selfdevelopment.chatengine.action.ConversationActionService;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceMdcHelper;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.model.StateTransitionRecord;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service for firing conversation events through the state machine.
 * <p>
 * Uses COLA StateMachine. A new state machine instance with a unique ID is created
 * for each fire/verify operation to avoid state pollution and ensure thread safety.
 * The state machine itself is stateless (only stores transition rules), but creating
 * new instances with unique IDs avoids COLA's "already built" exception.
 * <p>
 * Stateless mode — caller must manage state persistence externally.
 */
@Slf4j
@Service
public class ChatEngineStateMachineService {

    private final Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actionProvider;

    /**
     * Creates a service using the injected ConversationActionService.
     * <p>
     * Each fire/verify operation creates a new state machine instance with
     * auto-discovered Actions from the registry.
     *
     * @param actionService the conversation action service
     */
    public ChatEngineStateMachineService(ConversationActionService actionService) {
        Assert.notNull(actionService, "actionService must not be null");
        this.actionProvider = fact -> actionService.getAllActions().get(fact);
    }

    /**
     * Creates a service with an explicit action provider function.
     * <p>
     * This constructor is primarily for testing — it allows injecting a custom
     * action provider that creates mock or custom Actions.
     *
     * @param actionProvider function that maps ConversationFact to Action
     */
    public ChatEngineStateMachineService(
            Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actionProvider) {
        Assert.notNull(actionProvider, "actionProvider must not be null");
        this.actionProvider = actionProvider;
    }

    /**
     * Creates a service with an explicit map of Actions.
     * <p>
     * This constructor is useful for non-Spring environments or testing where Actions
     * are manually instantiated. Each fire/verify operation creates a new state machine
     * instance with the provided Actions.
     *
     * @param actions map of ConversationFact to Action
     */
    public ChatEngineStateMachineService(
            Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actions) {
        Assert.notNull(actions, "actions must not be null");
        this.actionProvider = ConversationActionService.toActionProvider(actions);
    }

    /**
     * Creates a new state machine instance with a unique ID.
     * <p>
     * Called for each fire/verify operation to ensure a fresh instance with a unique ID,
     * avoiding COLA StateMachine's "already built" exception.
     *
     * @return a new state machine instance with a unique ID
     */
    private StateMachine<ConversationState, ConversationFact, CbolStateContext> createStateMachine() {
        String uniqueMachineId = ConversationStateMachineFactory.MACHINE_ID + "-" + UUID.randomUUID();
        return ConversationStateMachineFactory.buildWithActionProvider(actionProvider, uniqueMachineId);
    }

    /**
     * Fires a conversation fact event through the state machine.
     * <p>
     * A new state machine instance with a unique ID is created for this operation.
     *
     * @param ctx  the conversation context (must not be null)
     * @param fact the event to fire (must not be null)
     * @return the target state after the transition
     * @throws IllegalArgumentException if ctx or fact is null
     * @throws StateMachineException    if the transition fails
     */
    public ConversationState fire(CbolStateContext ctx, ConversationFact fact) {
        Assert.notNull(ctx, "ctx must not be null");
        Assert.notNull(fact, "fact must not be null");
        Assert.notNull(ctx.conversation(), "ctx.conversation must not be null");
        Assert.notNull(ctx.traceContext(), "ctx.traceContext must not be null");

        return fireStateless(ctx, fact);
    }

    /**
     * Fires an event without persistent state storage (stateless mode).
     * <p>
     * Creates a new state machine instance with a unique ID for this operation. Wraps any
     * state machine exception with conversation context (conversationId, fact, current state)
     * to make troubleshooting easier.
     */
    private ConversationState fireStateless(CbolStateContext ctx, ConversationFact fact) {
        TraceMdcHelper.set(ctx.traceContext());
        long start = System.currentTimeMillis();
        String conversationId = ctx.conversation().conversationId();
        ConversationState from = ctx.conversation().state();

        // Create a fresh state machine instance with a unique ID for this operation
        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = createStateMachine();

        try {
            ConversationState target = sm.fireEvent(from, fact, ctx);

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
     * <p>
     * A new state machine instance with a unique ID is created for this operation.
     *
     * @param currentState the current state
     * @param fact         the event to verify
     * @return true if the event can be fired, false otherwise
     */
    public boolean verify(ConversationState currentState, ConversationFact fact) {
        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = createStateMachine();
        return sm.verify(currentState, fact);
    }
}
