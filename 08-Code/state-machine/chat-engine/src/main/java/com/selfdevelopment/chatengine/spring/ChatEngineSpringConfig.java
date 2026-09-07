package com.selfdevelopment.chatengine.spring;

import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.spring.aop.StateMachineLoggingAspect;
import com.selfdevelopment.chatengine.spring.aop.StateMachinePerformanceAspect;
import com.selfdevelopment.chatengine.spring.service.SpringChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring configuration for chat engine state machine.
 * <p>
 * Registers chat engine components as Spring Beans for dependency injection.
 * This configuration is optional - the library can also be used without Spring
 * via the static factory methods.
 * <p>
 * Includes AOP support for logging and performance monitoring.
 * <p>
 * Usage:
 * <pre>
 * {@code @Import(ChatEngineSpringConfig.class)}
 * public class MyAppConfig { ... }
 * </pre>
 */
@Configuration
@EnableAspectJAutoProxy
public class ChatEngineSpringConfig {

    /**
     * Creates the conversation state machine factory bean.
     * Initializes the state machine on startup.
     *
     * @return the conversation state machine factory
     */
    @Bean
    public ConversationStateMachineFactory conversationStateMachineFactory() {
        // Initialize state machine on startup
        ConversationStateMachineFactory.create();
        return new ConversationStateMachineFactory();
    }

    /**
     * Creates the Spring-aware chat engine state machine service bean.
     * This service publishes Spring events on state transitions.
     *
     * @return the Spring-aware chat engine state machine service
     */
    @Bean
    public SpringChatEngineStateMachineService springChatEngineStateMachineService() {
        return new SpringChatEngineStateMachineService();
    }

    /**
     * Creates the chat engine state machine service bean (alias for Spring-aware service).
     *
     * @param springChatEngineStateMachineService the Spring-aware service
     * @return the chat engine state machine service
     */
    @Bean
    public ChatEngineStateMachineService chatEngineStateMachineService(
            SpringChatEngineStateMachineService springChatEngineStateMachineService) {
        return springChatEngineStateMachineService;
    }

    /**
     * Creates the state machine logging aspect bean.
     *
     * @return the logging aspect
     */
    @Bean
    public StateMachineLoggingAspect stateMachineLoggingAspect() {
        return new StateMachineLoggingAspect();
    }

    /**
     * Creates the state machine performance monitoring aspect bean.
     *
     * @return the performance aspect
     */
    @Bean
    public StateMachinePerformanceAspect stateMachinePerformanceAspect() {
        return new StateMachinePerformanceAspect();
    }
}
