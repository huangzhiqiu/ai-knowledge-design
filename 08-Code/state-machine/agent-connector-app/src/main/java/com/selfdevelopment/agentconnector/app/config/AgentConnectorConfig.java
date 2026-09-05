package com.selfdevelopment.agentconnector.app.config;

import com.selfdevelopment.agentconnector.service.AgentConnectorStateMachineService;
import com.selfdevelopment.agentconnector.statemachine.factory.InteractionStateMachineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for agent connector state machine.
 */
@Configuration
public class AgentConnectorConfig {

    /**
     * Creates the interaction state machine service bean.
     * Ensures the state machine is initialized on application startup.
     *
     * @return the agent connector state machine service
     */
    @Bean
    public AgentConnectorStateMachineService agentConnectorStateMachineService() {
        // Initialize state machine on startup
        InteractionStateMachineFactory.create();
        return new AgentConnectorStateMachineService();
    }
}
