package com.selfdevelopment.agentconnector.spring;

import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.spring.service.SpringAgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for agent connector state machine.
 * <p>
 * Registers agent connector components as Spring Beans for dependency injection.
 * This configuration is optional - the library can also be used without Spring
 * via the static factory methods.
 * <p>
 * Usage:
 * <pre>
 * {@code @Import(AgentConnectorSpringConfig.class)}
 * public class MyAppConfig { ... }
 * </pre>
 */
@Configuration
public class AgentConnectorSpringConfig {

    /**
     * Creates the interaction state machine factory bean.
     * Initializes the state machine on startup.
     *
     * @return the interaction state machine factory
     */
    @Bean
    public InteractionStateMachineFactory interactionStateMachineFactory() {
        // Initialize state machine on startup
        InteractionStateMachineFactory.create();
        return new InteractionStateMachineFactory();
    }

    /**
     * Creates the Spring-aware agent connector state machine service bean.
     * This service publishes Spring events on state transitions.
     *
     * @return the Spring-aware agent connector state machine service
     */
    @Bean
    public SpringAgentConnectorStateMachineService springAgentConnectorStateMachineService() {
        return new SpringAgentConnectorStateMachineService();
    }

    /**
     * Creates the agent connector state machine service bean (alias for Spring-aware service).
     *
     * @param springAgentConnectorStateMachineService the Spring-aware service
     * @return the agent connector state machine service
     */
    @Bean
    public AgentConnectorStateMachineService agentConnectorStateMachineService(
            SpringAgentConnectorStateMachineService springAgentConnectorStateMachineService) {
        return springAgentConnectorStateMachineService;
    }
}
