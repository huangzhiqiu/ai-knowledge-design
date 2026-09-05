package com.selfdevelopment.chatengine.app.config;

import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for chat engine state machine.
 */
@Configuration
public class ChatEngineConfig {

    /**
     * Creates the conversation state machine service bean.
     * Ensures the state machine is initialized on application startup.
     *
     * @return the chat engine state machine service
     */
    @Bean
    public ChatEngineStateMachineService chatEngineStateMachineService() {
        // Initialize state machine on startup
        ConversationStateMachineFactory.create();
        return new ChatEngineStateMachineService();
    }
}
