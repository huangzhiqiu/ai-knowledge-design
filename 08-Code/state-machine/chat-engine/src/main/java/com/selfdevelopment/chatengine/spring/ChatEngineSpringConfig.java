package com.selfdevelopment.chatengine.spring;

import com.alibaba.cola.statemachine.StateMachine;
import com.selfdevelopment.chatengine.action.ending.AllInteractionsEndedAction;
import com.selfdevelopment.chatengine.action.ending.EndingActionsCompletedAction;
import com.selfdevelopment.chatengine.action.ending.EndingStartedAction;
import com.selfdevelopment.chatengine.action.ending.EndingTimeoutAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferCompletedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferFailedAction;
import com.selfdevelopment.chatengine.action.genesys.AgentTransferStartedAction;
import com.selfdevelopment.chatengine.action.genesys.ConsultTransferEndedAction;
import com.selfdevelopment.chatengine.action.genesys.ConsultTransferStartedAction;
import com.selfdevelopment.chatengine.action.lifecycle.InboundMessageReceivedAction;
import com.selfdevelopment.chatengine.action.lifecycle.InteractionBecameActiveAction;
import com.selfdevelopment.chatengine.action.lifecycle.SessionStartedAction;
import com.selfdevelopment.chatengine.action.survey.SurveySkippedAction;
import com.selfdevelopment.chatengine.action.survey.SurveySubmittedAction;
import com.selfdevelopment.chatengine.action.survey.SurveyTimeoutAction;
import com.selfdevelopment.chatengine.action.system.CustomerIdleTimeoutAction;
import com.selfdevelopment.chatengine.action.system.DownstreamUnavailableAction;
import com.selfdevelopment.chatengine.action.system.SystemErrorAction;
import com.selfdevelopment.chatengine.action.transfer.SourceInteractionTransferredAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionConnectFailedAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionConnectedAction;
import com.selfdevelopment.chatengine.action.transfer.TargetInteractionInitiatedAction;
import com.selfdevelopment.chatengine.action.transfer.TransferTimeoutAction;
import com.selfdevelopment.chatengine.action.ConversationActionRegistry;
import com.selfdevelopment.chatengine.action.ConversationActionService;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.spring.aop.StateMachineLoggingAspect;
import com.selfdevelopment.chatengine.spring.aop.StateMachinePerformanceAspect;
import com.selfdevelopment.chatengine.spring.service.SpringChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration for chat engine state machine.
 * <p>
 * Registers chat engine components as Spring Beans for dependency injection.
 * This configuration is optional - the library can also be used without Spring
 * via the static factory methods.
 * <p>
 * Includes:
 * <ul>
 *   <li>@ComponentScan for automatic Action bean discovery</li>
 *   <li>ConversationActions holder with injected Action beans</li>
 *   <li>State machine bean built with injected Actions</li>
 *   <li>Spring-aware state machine service with event publishing</li>
 *   <li>AOP support for logging and performance monitoring</li>
 *   <li>@EnableScheduling for scheduled tasks (monitors, timeouts)</li>
 *   <li>@EnableAsync for asynchronous method execution</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code @Import(ChatEngineSpringConfig.class)}
 * public class MyAppConfig { ... }
 * </pre>
 */
@Configuration
@EnableAspectJAutoProxy
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
        "com.selfdevelopment.chatengine.action",
        "com.selfdevelopment.chatengine.service",
        "com.selfdevelopment.chatengine.monitor",
        "com.selfdevelopment.chatengine.repository",
        "com.selfdevelopment.chatengine.config",
        "com.selfdevelopment.chatengine.ingress"
})
public class ChatEngineSpringConfig {

    /**
     * Creates the conversation state machine bean using the injected Factory.
     * <p>
     * Uses the Spring-managed ConversationStateMachineFactory bean, which uses
     * the ConversationActionRegistry to automatically discover and bind Actions.
     * This eliminates manual Action instantiation and uses Spring-managed Action beans.
     * The state machine is then registered with StateMachineFactory.
     *
     * @param factory the Spring-managed conversation state machine factory
     * @return the conversation state machine
     */
    @Bean
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine(
            ConversationStateMachineFactory factory) {
        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                factory.buildWithSpringActions();
        // Register the state machine so it can be retrieved by ID
        com.alibaba.cola.statemachine.StateMachineFactory.register(sm);
        return sm;
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
