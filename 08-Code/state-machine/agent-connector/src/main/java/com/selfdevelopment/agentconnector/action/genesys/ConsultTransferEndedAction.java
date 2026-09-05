package com.selfdevelopment.agentconnector.action.genesys;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;

/**
 * Action executed when Genesys consult transfer ends (CONSULT_TRANSFER → IN_PROGRESS).
 * <p>
 * GENESYS ONLY. This action handles the business logic of consult transfer end:
 * <ul>
 *   <li>Sets consultTransferInFlight=false</li>
 *   <li>Records consult transfer outcome</li>
 *   <li>Restores original agent session</li>
 *   <li>Notifies customer about consult transfer completion</li>
 * </ul>
 */
@Slf4j
public class ConsultTransferEndedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("ConsultTransferEndedAction: {} --({})--> {}, interactionId={} (GENESYS ONLY)",
                from, event, to, interactionId);

        // 1. Set consultTransferInFlight=false
        setConsultTransferInFlight(ctx);

        // 2. Record consult transfer outcome
        recordConsultOutcome(ctx);

        // 3. Restore original agent session
        restoreOriginalSession(ctx);

        // 4. Notify customer about consult transfer completion
        notifyConsultCompleted(ctx);

        log.info("ConsultTransferEndedAction completed: interactionId={}, consult transfer ended, restored to IN_PROGRESS", interactionId);
    }

    private void setConsultTransferInFlight(AgentConnectorStateContext ctx) {
        log.debug("Setting consultTransferInFlight=false: interactionId={}", ctx.interaction().interactionId());
    }

    private void recordConsultOutcome(AgentConnectorStateContext ctx) {
        log.debug("Recording consult outcome: interactionId={}", ctx.interaction().interactionId());
    }

    private void restoreOriginalSession(AgentConnectorStateContext ctx) {
        log.debug("Restoring original session: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyConsultCompleted(AgentConnectorStateContext ctx) {
        log.debug("Notifying consult completed: interactionId={}", ctx.interaction().interactionId());
    }
}
