package com.selfdevelopment.chatengine.app.config;

import com.selfdevelopment.chatengine.spring.ChatEngineSpringConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring configuration for chat engine application.
 * <p>
 * Imports the ChatEngineSpringConfig from the chat-engine module, which provides:
 * <ul>
 *   <li>Conversation state machine bean (built with Spring-managed Actions)</li>
 *   <li>Chat engine state machine service bean</li>
 *   <li>Action registry for automatic Action discovery and binding</li>
 *   <li>AOP aspects for logging and performance monitoring</li>
 * </ul>
 */
@Configuration
@Import(ChatEngineSpringConfig.class)
public class ChatEngineConfig {
    // All beans are provided by ChatEngineSpringConfig
}
