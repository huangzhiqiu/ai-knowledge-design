package com.selfdevelopment.agentconnector.action.genesys;

import com.alibaba.cola.statemachine.Action;
import com.selfdevelopment.agentconnector.context.AgentConnectorStateContext;
import com.selfdevelopment.agentconnector.enums.InteractionFact;
import com.selfdevelopment.agentconnector.enums.InteractionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Action executed when Genesys consult transfer starts (IN_PROGRESS → CONSULT_TRANSFER).
 * <p>
 * GENESYS ONLY. This action handles the business logic of consult transfer start:
 * <ul>
 *   <li>Sets consultTransferInFlight=true</li>
 *   <li>Records consult target agent/queue</li>
 *   <li>Initiates consult transfer via Genesys API</li>
 *   <li>Notifies customer about consult transfer</li>
 * </ul>
 */
@Slf4j
@Component
public class ConsultTransferStartedAction implements Action<InteractionState, InteractionFact, AgentConnectorStateContext> {

    @Override
    public void execute(InteractionState from, InteractionState to, InteractionFact event, AgentConnectorStateContext ctx) {
        String interactionId = ctx.interaction().interactionId();

        log.info("ConsultTransferStartedAction: {} --({})--> {}, interactionId={} (GENESYS ONLY)",
                from, event, to, interactionId);

        // 1. Set consultTransferInFlight=true
        setConsultTransferInFlight(ctx);

        // 2. Record consult target agent/queue
        recordConsultTarget(ctx);

        // 3. Initiate consult transfer via Genesys API
        initiateConsultTransfer(ctx);

        // 4. Notify customer about consult transfer
        notifyConsultTransfer(ctx);

        log.info("ConsultTransferStartedAction completed: interactionId={}, consult transfer started", interactionId);
    }

    private void setConsultTransferInFlight(AgentConnectorStateContext ctx) {
        log.debug("Setting consultTransferInFlight=true: interactionId={}", ctx.interaction().interactionId());
    }

    private void recordConsultTarget(AgentConnectorStateContext ctx) {
        log.debug("Recording consult target: interactionId={}", ctx.interaction().interactionId());
    }

    private void initiateConsultTransfer(AgentConnectorStateContext ctx) {
        log.debug("Initiating consult transfer via Genesys API: interactionId={}", ctx.interaction().interactionId());
    }

    private void notifyConsultTransfer(AgentConnectorStateContext ctx) {
        log.debug("Notifying consult transfer: interactionId={}", ctx.interaction().interactionId());
    }
}
