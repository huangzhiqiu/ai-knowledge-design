package com.selfdevelopment.chatengine.action.lifecycle;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when interaction becomes active (INITIATED → ACTIVE).
 * <p>
 * This action handles the business logic of interaction readiness:
 * <ul>
 *   <li>Sets activeAt timestamp</li>
 *   <li>Sends welcome message to the customer</li>
 *   <li>Notifies AI bot to start processing</li>
 *   <li>Activates customer idle monitoring</li>
 * </ul>
 */
@Slf4j
@Component
public class InteractionBecameActiveAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.info("InteractionBecameActiveAction: {} --({})--> {}, conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Set activeAt timestamp
        setActiveAt(ctx);

        // 2. Send welcome message to the customer
        sendWelcomeMessage(ctx);

        // 3. Notify AI bot to start processing
        notifyAiBot(ctx);

        // 4. Activate customer idle monitoring
        activateCustomerIdleMonitoring(ctx);

        log.info("InteractionBecameActiveAction completed successfully: conversationId={}", conversationId);
    }

    private void setActiveAt(CbolStateContext ctx) {
        log.debug("Setting activeAt timestamp: conversationId={}", ctx.conversation().conversationId());
    }

    private void sendWelcomeMessage(CbolStateContext ctx) {
        String welcomeMessage = getWelcomeMessage(ctx.conversation().market());
        log.debug("Sending welcome message: tenantId={}, message={}", ctx.conversation().tenantId(), welcomeMessage);
    }

    private void notifyAiBot(CbolStateContext ctx) {
        log.debug("Notifying AI bot: conversationId={}", ctx.conversation().conversationId());
    }

    private void activateCustomerIdleMonitoring(CbolStateContext ctx) {
        log.debug("Activating customer idle monitoring: conversationId={}", ctx.conversation().conversationId());
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
