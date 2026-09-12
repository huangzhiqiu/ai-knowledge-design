package com.selfdevelopment.agentconnector.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Connector Spring Boot Application.
 * <p>
 * Provides REST API for interaction state machine operations.
 */
@SpringBootApplication
public class AgentConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentConnectorApplication.class, args);
    }
}
