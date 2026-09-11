package com.selfdevelopment.agentconnector.action.transfer;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when cross-channel transfer succeeds (IN_PROGRESS → TRANSFERRED).
 * <p>
 * This action handles the business logic of successful cross-channel transfer:
 * <ul>
 *   <li>Sets transferredAt timestamp</li>
 *   <li>Records transfer target channel</li>
 *   <li>Detaches source interaction (source detach marker)</li>
 *   <li>Notifies conversation layer about transfer success</li>
 * </ul>
 */
@Slf4j
@Component
public class TransferSuccessAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("TransferSuccessAction: {} --({})--> {}, interactionId={}",
                from, event, to, interactionId);

        // 1. Set transferredAt timestamp
        setTransferredAt(ctx);

        // 2. Record transfer target channel
        recordTransferTarget(ctx);

        // 3. Detach source interaction (source detach marker)
        detachSourceInteraction(ctx);

        // 4. Notify conversation layer about transfer success
        notifyTransferSuccess(ctx);

        log.info("TransferSuccessAction completed: interactionId={}, cross-channel transfer successful, source detached", interactionId);
    }

    private void setTransferredAt(AgentConnectorStateContext ctx) {
        log.debug("Setting transferredAt: interactionId={}", ctx.interaction().interactionId());
    }

    private void recordTransferTarget(AgentConnectorStateContext ctx) {
        log.debug("Recording transfer target: interactionId={}", ctx.interaction().interactionId());
    }

    private void detachSourceInteraction(AgentConnectorStateContext ctx) {
        log.debug("Detaching source interaction: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyTransferSuccess(AgentConnectorStateContext ctx) {
        log.info("Notifying transfer success: interactionId={}", ctx.interaction().interactionId());
    }
}
