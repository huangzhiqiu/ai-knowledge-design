package com.selfdevelopment.chatengine.action.impl;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when a customer connects (INITIATED → IN_PROGRESS).
 * <p>
 * This action handles the actual business logic of establishing a connection:
 * <ul>
 *   <li>Creates a conversation record in the database</li>
 *   <li>Sends a welcome message to the customer</li>
 *   <li>Initializes session data</li>
 *   <li>Notifies the AI bot to start processing</li>
 * </ul>
 */
@Slf4j
public class CustomerConnectAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("CustomerConnectAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Create conversation record in database (simulated)
        createConversationRecord(ctx);

        // 2. Send welcome message to customer (simulated)
        sendWelcomeMessage(ctx);

        // 3. Initialize session data (simulated)
        initializeSessionData(ctx);

        // 4. Notify AI bot to start processing (simulated)
        notifyAiBot(ctx);

        log.info("CustomerConnectAction completed successfully: conversationId={}", conversationId);
    }

    private void createConversationRecord(CbolStateContext ctx) {
        // In production: call repository.save(conversation)
        log.debug("Creating conversation record: conversationId={}", ctx.conversation().conversationId());
    }

    private void sendWelcomeMessage(CbolStateContext ctx) {
        // In production: call messageService.sendWelcomeMessage(tenantId)
        String welcomeMessage = getWelcomeMessage(ctx.conversation().market());
        log.debug("Sending welcome message: tenantId={}, message={}",
                ctx.conversation().tenantId(), welcomeMessage);
    }

    private void initializeSessionData(CbolStateContext ctx) {
        // In production: initialize session in Redis or session store
        log.debug("Initializing session data: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyAiBot(CbolStateContext ctx) {
        // In production: call aiBotService.startConversation(conversationId)
        log.debug("Notifying AI bot: conversationId={}", ctx.conversation().conversationId());
    }

    private String getWelcomeMessage(String market) {
        return switch (market) {
            case "HK" -> "歡迎使用我們的服務！";
            case "SG" -> "Welcome to our service!";
            case "UK" -> "Welcome to our service!";
            default -> "Welcome!";
        };
    }
}
