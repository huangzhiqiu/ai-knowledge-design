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
@ComponentScan(basePackages = "com.selfdevelopment.chatengine.action")
public class ChatEngineSpringConfig {

    /**
     * Creates the ConversationActions holder bean with all injected Action beans.
     * <p>
     * This holder centralizes all Action dependencies and can be passed to
     * the state machine builder for Spring-managed Action instances.
     *
     * @return the ConversationActions holder
     */
    @Bean
    public ConversationStateMachineFactory.ConversationActions conversationActions(
            SessionStartedAction sessionStartedAction,
            InteractionBecameActiveAction interactionBecameActiveAction,
            InboundMessageReceivedAction inboundMessageReceivedAction,
            SourceInteractionTransferredAction sourceInteractionTransferredAction,
            TargetInteractionInitiatedAction targetInteractionInitiatedAction,
            TargetInteractionConnectedAction targetInteractionConnectedAction,
            TargetInteractionConnectFailedAction targetInteractionConnectFailedAction,
            TransferTimeoutAction transferTimeoutAction,
            EndingStartedAction endingStartedAction,
            EndingTimeoutAction endingTimeoutAction,
            AllInteractionsEndedAction allInteractionsEndedAction,
            EndingActionsCompletedAction endingActionsCompletedAction,
            SurveySubmittedAction surveySubmittedAction,
            SurveyTimeoutAction surveyTimeoutAction,
            SurveySkippedAction surveySkippedAction,
            ConsultTransferStartedAction consultTransferStartedAction,
            ConsultTransferEndedAction consultTransferEndedAction,
            AgentTransferStartedAction agentTransferStartedAction,
            AgentTransferCompletedAction agentTransferCompletedAction,
            AgentTransferFailedAction agentTransferFailedAction,
            CustomerIdleTimeoutAction customerIdleTimeoutAction,
            SystemErrorAction systemErrorAction,
            DownstreamUnavailableAction downstreamUnavailableAction) {
        return new ConversationStateMachineFactory.ConversationActions(
                sessionStartedAction,
                interactionBecameActiveAction,
                inboundMessageReceivedAction,
                sourceInteractionTransferredAction,
                targetInteractionInitiatedAction,
                targetInteractionConnectedAction,
                targetInteractionConnectFailedAction,
                transferTimeoutAction,
                endingStartedAction,
                endingTimeoutAction,
                allInteractionsEndedAction,
                endingActionsCompletedAction,
                surveySubmittedAction,
                surveyTimeoutAction,
                surveySkippedAction,
                consultTransferStartedAction,
                consultTransferEndedAction,
                agentTransferStartedAction,
                agentTransferCompletedAction,
                agentTransferFailedAction,
                customerIdleTimeoutAction,
                systemErrorAction,
                downstreamUnavailableAction);
    }

    /**
     * Creates the conversation state machine bean.
     * <p>
     * Initializes the state machine on startup using the static factory method.
     * For Spring-managed Actions, use the conversationActions bean to build
     * a state machine with injected dependencies.
     *
     * @return the conversation state machine
     */
    @Bean
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine() {
        return ConversationStateMachineFactory.create();
    }

    /**
     * Creates the conversation state machine factory bean.
     *
     * @return the conversation state machine factory
     */
    @Bean
    public ConversationStateMachineFactory conversationStateMachineFactory() {
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
