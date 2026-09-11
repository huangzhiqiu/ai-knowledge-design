package com.selfdevelopment.chatengine.action;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for Conversation Actions, indexed by the ConversationFact they handle.
 * <p>
 * This registry automatically discovers all Spring-managed Action beans annotated with
 * {@link HandlesFact} and indexes them by the declared fact. This eliminates the need
 * for manual Action instantiation and binding in the state machine factory.
 * <p>
 * Usage:
 * <pre>{@code
 * @Component
 * public class MyService {
 *     private final ConversationActionRegistry actionRegistry;
 *
 *     public MyService(ConversationActionRegistry actionRegistry) {
 *         this.actionRegistry = actionRegistry;
 *     }
 *
 *     public void doSomething() {
 *         Action<ConversationState, ConversationFact, CbolStateContext> action =
 *             actionRegistry.getAction(ConversationFact.SESSION_STARTED);
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @see HandlesFact
 * @see com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory
 */
@Slf4j
@Component
public class ConversationActionRegistry implements InitializingBean {

    private final ApplicationContext applicationContext;
    private final Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> actionByFact =
            new EnumMap<>(ConversationFact.class);

    /**
     * Creates the registry with the Spring application context for bean discovery.
     *
     * @param applicationContext the Spring application context
     */
    public ConversationActionRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Initializes the registry by scanning all Action beans and indexing them by fact.
     * Called automatically by Spring after bean properties are set.
     */
    @Override
    public void afterPropertiesSet() {
        Map<String, Object> actionBeans = applicationContext.getBeansWithAnnotation(HandlesFact.class);

        for (Map.Entry<String, Object> entry : actionBeans.entrySet()) {
            Object bean = entry.getValue();
            HandlesFact annotation = bean.getClass().getAnnotation(HandlesFact.class);

            if (annotation == null) {
                log.warn("Bean {} has @HandlesFact annotation but it could not be resolved", entry.getKey());
                continue;
            }

            if (!(bean instanceof Action)) {
                log.warn("Bean {} is annotated with @HandlesFact but does not implement Action interface", entry.getKey());
                continue;
            }

            @SuppressWarnings("unchecked")
            Action<ConversationState, ConversationFact, CbolStateContext> action =
                    (Action<ConversationState, ConversationFact, CbolStateContext>) bean;

            ConversationFact fact = annotation.value();
            Action<ConversationState, ConversationFact, CbolStateContext> existing = actionByFact.put(fact, action);

            if (existing != null) {
                log.warn("Duplicate Action for fact {}: {} replaced by {}", fact, existing.getClass().getSimpleName(), action.getClass().getSimpleName());
            } else {
                log.debug("Registered Action {} for fact {}", action.getClass().getSimpleName(), fact);
            }
        }

        log.info("ConversationActionRegistry initialized with {} Actions for {} facts",
                actionByFact.size(), ConversationFact.values().length);
    }

    /**
     * Returns the Action registered for the given fact.
     *
     * @param fact the conversation fact
     * @return the Action for the fact, or null if no Action is registered
     * @throws NullPointerException if fact is null
     */
    public Action<ConversationState, ConversationFact, CbolStateContext> getAction(ConversationFact fact) {
        Objects.requireNonNull(fact, "fact must not be null");
        return actionByFact.get(fact);
    }

    /**
     * Returns the Action registered for the given fact, or throws if not found.
     *
     * @param fact the conversation fact
     * @return the Action for the fact
     * @throws NullPointerException if fact is null
     * @throws IllegalStateException if no Action is registered for the fact
     */
    public Action<ConversationState, ConversationFact, CbolStateContext> getRequiredAction(ConversationFact fact) {
        Action<ConversationState, ConversationFact, CbolStateContext> action = getAction(fact);
        if (action == null) {
            throw new IllegalStateException("No Action registered for fact: " + fact);
        }
        return action;
    }

    /**
     * Returns whether an Action is registered for the given fact.
     *
     * @param fact the conversation fact
     * @return true if an Action is registered, false otherwise
     */
    public boolean hasAction(ConversationFact fact) {
        return actionByFact.containsKey(fact);
    }

    /**
     * Returns an unmodifiable view of all registered Actions indexed by fact.
     *
     * @return unmodifiable map of fact to Action
     */
    public Map<ConversationFact, Action<ConversationState, ConversationFact, CbolStateContext>> getAllActions() {
        return Collections.unmodifiableMap(actionByFact);
    }

    /**
     * Returns the number of registered Actions.
     *
     * @return the number of registered Actions
     */
    public int size() {
        return actionByFact.size();
    }
}
