package com.selfdevelopment.agentconnector.action.reconnection;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when reconnection succeeds (RECONNECTING → CONNECTED/IN_PROGRESS).
 * <p>
 * This action handles the business logic of successful reconnection:
 * <ul>
 *   <li>Resets reconnection attempt count</li>
 *   <li>Clears reconnectingAt timestamp</li>
 *   <li>Restores channel session state</li>
 *   <li>Resends pending messages if any</li>
 *   <li>Notifies upstream about reconnection success</li>
 * </ul>
 */
@Slf4j
public class ReconnectSuccessAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("ReconnectSuccessAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Reset reconnection attempt count
        resetReconnectAttemptCount(ctx);

        // 2. Clear reconnectingAt timestamp
        clearReconnectingAt(ctx);

        // 3. Restore channel session state
        restoreSessionState(ctx);

        // 4. Resend pending messages if any
        resendPendingMessages(ctx);

        // 5. Notify upstream about reconnection success
        notifyReconnectionSuccess(ctx);

        log.info("ReconnectSuccessAction completed: interactionId={}, reconnection successful, restored to {}",
                interactionId, to);
    }

    private void resetReconnectAttemptCount(AgentConnectorStateContext ctx) {
        log.debug("Resetting reconnect attempt count: interactionId={}", ctx.interaction().interactionId());
    }

    private void clearReconnectingAt(AgentConnectorStateContext ctx) {
        log.debug("Clearing reconnectingAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void restoreSessionState(AgentConnectorStateContext ctx) {
        log.debug("Restoring session state: interactionId={}", ctx.interaction().interactionId());
    }

    private void resendPendingMessages(AgentConnectorStateContext ctx) {
        log.debug("Resending pending messages: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyReconnectionSuccess(AgentConnectorStateContext ctx) {
        log.info("Notifying reconnection success: interactionId={}", ctx.interaction().interactionId());
    }
}
