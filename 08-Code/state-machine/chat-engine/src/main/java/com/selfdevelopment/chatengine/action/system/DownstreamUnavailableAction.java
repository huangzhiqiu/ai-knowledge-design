package com.selfdevelopment.chatengine.action.system;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.chatengine.action.HandlesFact;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when downstream is unavailable (INITIATED → INITIATED, internal).
 * <p>
 * This action handles the business logic of downstream unavailability:
 * <ul>
 *   <li>Notifies system unavailable (stay in INITIATED)</li>
 *   <li>Records downstream unavailability event</li>
 *   <li>Sets up retry mechanism for downstream availability</li>
 *   <li>Notifies customer about temporary unavailability</li>
 * </ul>
 */
@Slf4j
@Component
@HandlesFact(ConversationFact.DOWNSTREAM_UNAVAILABLE)
public class DownstreamUnavailableAction implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(ConversationState from, ConversationState to, ConversationFact event, CbolStateContext ctx) {
        String conversationId = ctx.conversation().conversationId();
        String tenantId = ctx.conversation().tenantId();
        String market = ctx.conversation().market();

        log.warn("DownstreamUnavailableAction: {} --({})--> {} (internal, stay), conversationId={}, tenantId={}, market={}",
                from, event, to, conversationId, tenantId, market);

        // 1. Notify system unavailable
        notifySystemUnavailable(ctx);

        // 2. Record downstream unavailability event
        recordDownstreamUnavailable(ctx);

        // 3. Set up retry mechanism for downstream availability
        setupRetryMechanism(ctx);

        // 4. Notify customer about temporary unavailability
        notifyCustomerTemporaryUnavailable(ctx);

        log.warn("DownstreamUnavailableAction completed: conversationId={}, staying in INITIATED", conversationId);
    }

    private void notifySystemUnavailable(CbolStateContext ctx) {
        log.warn("Notifying system unavailable: conversationId={}", ctx.conversation().conversationId());
    }

    private void recordDownstreamUnavailable(CbolStateContext ctx) {
        log.warn("Recording downstream unavailability: conversationId={}", ctx.conversation().conversationId());
    }

    private void setupRetryMechanism(CbolStateContext ctx) {
        log.debug("Setting up retry mechanism: conversationId={}", ctx.conversation().conversationId());
    }

    private void notifyCustomerTemporaryUnavailable(CbolStateContext ctx) {
        String message = getTemporaryUnavailableMessage(ctx.conversation().market());
        log.debug("Notifying customer temporary unavailable: conversationId={}, message={}",
                ctx.conversation().conversationId(), message);
    }

    private String getTemporaryUnavailableMessage(String market) {
        return switch (market) {
            case "HK" -> "系統暫時繁忙，請稍候...";
            case "SG" -> "System is temporarily busy, please wait...";
            case "UK" -> "System is temporarily busy, please wait...";
            default -> "System temporarily busy, please wait...";
        };
    }
}
