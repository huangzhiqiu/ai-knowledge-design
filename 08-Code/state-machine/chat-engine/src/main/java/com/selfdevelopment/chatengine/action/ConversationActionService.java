package com.selfdevelopment.chatengine.action;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.StateMachine;
import com.selfdevelopment.chatengine.action.exception.ActionExceptionHandlerRegistry;
import com.selfdevelopment.chatengine.action.exception.ExceptionHandlingAction;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for managing Conversation Actions and their association with ConversationFact events.
 * <p>
 * This service provides a universal, auto-discovering Action management layer. Actions are
 * automatically discovered via the {@link HandlesFact} annotation and indexed by
 * {@link ConversationActionRegistry}. Adding a new Action requires only two steps:
 * <ol>
 *   <li>Annotate the Action class with {@code @Component}</li>
 *   <li>Annotate it with {@code @HandlesFact(ConversationFact.XXX)}</li>
 * </ol>
 * No changes to this service are needed when adding new Actions.
 * <p>
 * Actions are automatically wrapped with exception handling via {@link ExceptionHandlingAction},
 * ensuring that exceptions during Action execution do not block state transitions.
 * <p>
 * Usage:
 * <pre>{@code
 * // Spring environment (recommended) - auto-discovery + exception handling
 * ConversationActionService actionService = ...;
 * StateMachine<...> sm = actionService.buildWithSpringActions();
 *
 * // Non-Spring environment - explicit Map
 * Map<ConversationFact, Action<...>> actions = new EnumMap<>(ConversationFact.class);
 * actions.put(ConversationFact.SESSION_STARTED, new SessionStartedAction());
 * StateMachine<...> sm = ConversationActionService.buildWithActions(actions);
 * }</pre>
 *
 * @see HandlesFact
 * @see ConversationActionRegistry
 * @see ConversationStateMachineFactory
 * @see ExceptionHandlingAction
 * @see ActionExceptionHandlerRegistry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationActionService {

    private final ConversationActionRegistry actionRegistry;
    private final ActionExceptionHandlerRegistry exceptionHandlerRegistry;

    /**
     * Builds the conversation state machine using Spring-managed Actions from the registry.
     * <p>
     * This is the recommended method for Spring applications. Actions are automatically
     * discovered via the {@link HandlesFact} annotation and wrapped with exception handling
     * via {@link ExceptionHandlingAction}. Exceptions during Action execution do not block
     * state transitions.
     *
     * @return the configured conversation state machine
     */
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithSpringActions() {
        log.debug("Building conversation state machine with auto-discovered Actions ({} registered)",
                actionRegistry.size());
        return buildWithRegistry(actionRegistry, exceptionHandlerRegistry);
    }

    /**
     * Builds the conversation state machine using the ActionRegistry.
     *
     * @param registry the conversation action registry
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if registry is null
     */
    /**
     * Builds the conversation state machine using the ActionRegistry.
     *
     * @param registry the conversation action registry
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if registry is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithRegistry(
            ConversationActionRegistry registry) {
        Assert.notNull(registry, "registry must not be null");
        log.debug("Building conversation state machine with ActionRegistry");
        return ConversationStateMachineFactory.buildWithActionProvider(registry::getAction);
    }

    /**
     * Builds the conversation state machine using the ActionRegistry with exception handling.
     * <p>
     * Actions are wrapped with {@link ExceptionHandlingAction} to ensure that exceptions
     * during Action execution do not block state transitions.
     *
     * @param registry                 the conversation action registry
     * @param exceptionHandlerRegistry the registry for finding exception handlers
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if registry or exceptionHandlerRegistry is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithRegistry(
            ConversationActionRegistry registry,
            ActionExceptionHandlerRegistry exceptionHandlerRegistry) {
        Assert.notNull(registry, "registry must not be null");
        Assert.notNull(exceptionHandlerRegistry, "exceptionHandlerRegistry must not be null");
        log.debug("Building conversation state machine with ActionRegistry and exception handling");
        return ConversationStateMachineFactory.buildWithActionProvider(
                fact -> wrapWithExceptionHandling(registry.getAction(fact), exceptionHandlerRegistry));
    }

    /**
     * Builds the conversation state machine with an explicit Map of Actions.
     * <p>
     * This method is useful for testing or non-Spring environments where Actions are
     * manually instantiated. For Spring applications, use {@link #buildWithSpringActions()}.
     *
     * @param actions map of ConversationFact to Action
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if actions is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActions(
            Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actions) {
        Assert.notNull(actions, "actions must not be null");
        log.debug("Building conversation state machine with explicit Actions ({} provided)", actions.size());
        return ConversationStateMachineFactory.buildWithActionProvider(toActionProvider(actions));
    }

    /**
     * Builds the conversation state machine with an explicit Map of Actions and exception handling.
     * <p>
     * Actions are wrapped with {@link ExceptionHandlingAction} to ensure that exceptions
     * during Action execution do not block state transitions.
     *
     * @param actions                  map of ConversationFact to Action
     * @param exceptionHandlerRegistry the registry for finding exception handlers
     * @return the configured conversation state machine
     * @throws IllegalArgumentException if actions or exceptionHandlerRegistry is null
     */
    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> buildWithActions(
            Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actions,
            ActionExceptionHandlerRegistry exceptionHandlerRegistry) {
        Assert.notNull(actions, "actions must not be null");
        Assert.notNull(exceptionHandlerRegistry, "exceptionHandlerRegistry must not be null");
        log.debug("Building conversation state machine with explicit Actions and exception handling ({} provided)",
                actions.size());
        return ConversationStateMachineFactory.buildWithActionProvider(
                toActionProvider(actions, exceptionHandlerRegistry));
    }

    /**
     * Converts a Map of Actions to an ActionProvider function.
     * <p>
     * Uses the map directly for O(1) lookup. Returns null and logs a warning if no
     * Action is found for a given Fact.
     *
     * @param actions map of ConversationFact to Action
     * @return the ActionProvider function
     * @throws IllegalArgumentException if actions is null
     */
    public static Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> toActionProvider(
            Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actions) {
        Assert.notNull(actions, "actions must not be null");
        return fact -> {
            Action<ConversationState, ConversationFact, CbolStateContext> action = actions.get(fact);
            if (action == null) {
                log.warn("No Action found for ConversationFact: {}", fact);
            }
            return action;
        };
    }

    /**
     * Converts a Map of Actions to an ActionProvider function with exception handling.
     * <p>
     * Each Action is wrapped with {@link ExceptionHandlingAction} to ensure that exceptions
     * during Action execution do not block state transitions.
     *
     * @param actions                  map of ConversationFact to Action
     * @param exceptionHandlerRegistry the registry for finding exception handlers
     * @return the ActionProvider function
     * @throws IllegalArgumentException if actions or exceptionHandlerRegistry is null
     */
    public static Function<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> toActionProvider(
            Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actions,
            ActionExceptionHandlerRegistry exceptionHandlerRegistry) {
        Assert.notNull(actions, "actions must not be null");
        Assert.notNull(exceptionHandlerRegistry, "exceptionHandlerRegistry must not be null");
        return fact -> {
            Action<ConversationState, ConversationFact, CbolStateContext> action = actions.get(fact);
            if (action == null) {
                log.warn("No Action found for ConversationFact: {}", fact);
                return null;
            }
            return wrapWithExceptionHandling(action, exceptionHandlerRegistry);
        };
    }

    /**
     * Wraps an Action with exception handling.
     * <p>
     * If the Action is already wrapped, it is returned as-is. Otherwise, it is wrapped
     * with {@link ExceptionHandlingAction} to ensure that exceptions during Action execution
     * do not block state transitions.
     *
     * @param action                   the Action to wrap
     * @param exceptionHandlerRegistry the registry for finding exception handlers
     * @return the wrapped Action, or null if action is null
     */
    private static Action<ConversationState, ConversationFact, CbolStateContext> wrapWithExceptionHandling(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            ActionExceptionHandlerRegistry exceptionHandlerRegistry) {
        if (action == null) {
            return null;
        }
        if (action instanceof ExceptionHandlingAction) {
            return action; // Already wrapped
        }
        return new ExceptionHandlingAction<>(action, exceptionHandlerRegistry);
    }

    /**
     * Returns an unmodifiable view of all registered Actions from the registry.
     *
     * @return unmodifiable map of fact to Action
     */
    public Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> getAllActions() {
        return actionRegistry.getAllActions();
    }

    /**
     * Returns the number of registered Actions.
     *
     * @return the number of registered Actions
     */
    public int getActionCount() {
        return actionRegistry.size();
    }

    /**
     * Returns whether an Action is registered for the given fact.
     *
     * @param fact the conversation fact
     * @return true if an Action is registered, false otherwise
     */
    public boolean hasAction(ConversationFact fact) {
        return actionRegistry.hasAction(fact);
    }

    /**
     * Creates an empty EnumMap for Actions.
     * <p>
     * Convenience method for creating action maps in tests or non-Spring environments.
     *
     * @return a new empty EnumMap
     */
    public static Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> newActionMap() {
        return new EnumMap<>(ConversationFact.class);
    }

    /**
     * Creates an unmodifiable singleton map with a single Action.
     * <p>
     * Convenience method for tests that only need a single Action.
     *
     * @param fact the conversation fact
     * @param action the action to register
     * @return an unmodifiable map with the single Action
     */
    public static Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> singletonActionMap(
            ConversationFact fact,
            Action<ConversationState, ConversationFact, CbolStateContext> action) {
        Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> map = newActionMap();
        map.put(fact, action);
        return Collections.unmodifiableMap(map);
    }
}
