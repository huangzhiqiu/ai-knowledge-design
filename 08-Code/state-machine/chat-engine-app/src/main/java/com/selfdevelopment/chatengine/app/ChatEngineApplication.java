package com.selfdevelopment.chatengine.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chat Engine Spring Boot Application.
 * <p>
 * Provides REST API for conversation state machine operations.
 */
@SpringBootApplication
public class ChatEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatEngineApplication.class, args);
    }
}
