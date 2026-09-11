package com.selfdevelopment.chatengine.action.exception;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registry for Action exception handlers, automatically discovered via Spring.
 * <p>
 * This registry automatically discovers all Spring-managed {@link ActionExceptionHandler}
 * beans and sorts them by priority (higher priority first). When an exception occurs
 * during Action execution, the registry finds the first handler that can handle the
 * exception and delegates to it.
 * <p>
 * If no specific handler is found, the fallback handler is used.
 * <p>
 * Usage:
 * <pre>{@code
 * @Component
 * public class MyService {
 *     private final ActionExceptionHandlerRegistry exceptionHandlerRegistry;
 *
 *     public void doSomething() {
 *         exceptionHandlerRegistry.handleException(ex, from, to, fact, ctx);
 *     }
 * }
 * }</pre>
 *
 * @see ActionExceptionHandler
 * @see ExceptionHandlingAction
 */
@Slf4j
@Component
public class ActionExceptionHandlerRegistry implements InitializingBean {

    private final ApplicationContext applicationContext;
    private final List<ActionExceptionHandler> handlers = new ArrayList<>();
    private ActionExceptionHandler fallbackHandler;

    /**
     * Creates the registry with the Spring application context for bean discovery.
     *
     * @param applicationContext the Spring application context
     */
    public ActionExceptionHandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Initializes the registry by scanning all ActionExceptionHandler beans and sorting
     * them by priority. Called automatically by Spring after bean properties are set.
     */
    @Override
    public void afterPropertiesSet() {
        // Discover all handler beans
        var handlerBeans = applicationContext.getBeansOfType(ActionExceptionHandler.class);

        for (var entry : handlerBeans.entrySet()) {
            ActionExceptionHandler handler = entry.getValue();
            if (handler instanceof FallbackActionExceptionHandler) {
                this.fallbackHandler = handler;
                log.debug("Registered fallback exception handler: {}", handler.getClass().getSimpleName());
            } else {
                handlers.add(handler);
                log.debug("Registered exception handler: {} (priority={})",
                        handler.getClass().getSimpleName(), handler.getPriority());
            }
        }

        // Sort by priority (higher first)
        handlers.sort(Comparator.comparingInt(ActionExceptionHandler::getPriority).reversed());

        // Ensure fallback handler exists
        if (fallbackHandler == null) {
            fallbackHandler = new FallbackActionExceptionHandler();
            log.warn("No FallbackActionExceptionHandler bean found, using default instance");
        }

        log.info("ActionExceptionHandlerRegistry initialized with {} handlers (fallback: {})",
                handlers.size(), fallbackHandler.getClass().getSimpleName());
    }

    /**
     * Finds the appropriate handler for the given exception and delegates to it.
     * <p>
     * Handlers are checked in priority order (higher first). The first handler that
     * can handle the exception is used. If no handler matches, the fallback handler
     * is used.
     *
     * @param ex   the exception thrown during Action execution
     * @param from the source state of the transition
     * @param to   the target state of the transition
     * @param fact the event/fact that triggered the transition
     * @param ctx  the state machine context
     * @throws IllegalArgumentException if ex is null
     */
    public void handleException(Throwable ex, ConversationState from, ConversationState to,
                                ConversationFact fact, CbolStateContext ctx) {
        Assert.notNull(ex, "ex must not be null");

        // Find the first handler that can handle this exception
        for (ActionExceptionHandler handler : handlers) {
            if (handler.canHandle(ex)) {
                log.debug("Using exception handler: {} for exception: {}",
                        handler.getClass().getSimpleName(), ex.getClass().getSimpleName());
                try {
                    handler.handle(ex, from, to, fact, ctx);
                } catch (Exception handlerEx) {
                    // Never let handler exceptions propagate
                    log.error("Exception handler {} threw an exception while handling {}",
                            handler.getClass().getSimpleName(), ex.getClass().getSimpleName(), handlerEx);
                }
                return;
            }
        }

        // Use fallback handler
        log.debug("Using fallback exception handler for exception: {}", ex.getClass().getSimpleName());
        try {
            fallbackHandler.handle(ex, from, to, fact, ctx);
        } catch (Exception handlerEx) {
            // Never let fallback handler exceptions propagate
            log.error("Fallback exception handler threw an exception while handling {}",
                    ex.getClass().getSimpleName(), handlerEx);
        }
    }

    /**
     * Registers a custom exception handler programmatically.
     * <p>
     * This method is useful for registering handlers outside of Spring context,
     * such as in tests or dynamic configurations.
     *
     * @param handler the exception handler to register
     * @throws IllegalArgumentException if handler is null
     */
    public void registerHandler(ActionExceptionHandler handler) {
        Assert.notNull(handler, "handler must not be null");
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(ActionExceptionHandler::getPriority).reversed());
        log.debug("Registered exception handler programmatically: {} (priority={})",
                handler.getClass().getSimpleName(), handler.getPriority());
    }

    /**
     * Returns the number of registered handlers (excluding fallback).
     *
     * @return the number of registered handlers
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * Returns the fallback handler.
     *
     * @return the fallback handler
     */
    public ActionExceptionHandler getFallbackHandler() {
        return fallbackHandler;
    }
}
