package com.selfdevelopment.ai.messaging.demo;

import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationFact;
import com.selfdevelopment.ai.messaging.cbol.enums.ConversationState;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.cbol.statemachine.factory.ConversationStateMachineFactory;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.api.StateMachine;

/**
 * CBOL (AI Messaging Hub) business layer demo.
 * <p>
 * Demonstrates the full conversation lifecycle using the CBOL state machine:
 * <ul>
 *   <li>Customer connects → ACTIVE</li>
 *   <li>Transfer to human agent → TRANSFERRED</li>
 *   <li>Transfer failure → INITIATED (v6: no rollback to ACTIVE)</li>
 *   <li>Customer closes → ENDING → CLOSED</li>
 *   <li>Survey flow: SURVEY_IN_PROGRESS → ENDING</li>
 *   <li>Multi-market configuration (HK vs SG)</li>
 *   <li>TraceId propagation via SLF4J MDC</li>
 * </ul>
 */
public class CbolConversationDemo {

    public static void main(String[] args) {
        System.out.println("=== CBOL Conversation State Machine Demo ===\n");

        // 1. Build the conversation state machine
        StateMachine<ConversationState, ConversationFact, CbolStateContext> machine =
                ConversationStateMachineFactory.build();

        System.out.println("Machine ID: " + machine.getMachineId());
        System.out.println("Transition count: " + machine.getTransitionCount());
        System.out.println("Initial state: " + machine.getInitialState());
        System.out.println("End states: " + machine.getEndStates());

        // 2. Demo 1: Normal conversation flow
        System.out.println("\n--- Demo 1: Normal flow (HK market) ---");
        CbolStateContext hkCtx = buildContext("conv-hk-001", "HK", false);
        runFlow(machine, hkCtx,
                ConversationFact.CUSTOMER_CONNECT,   // INITIATED → ACTIVE
                ConversationFact.TRANSFER_REQUEST,   // ACTIVE → TRANSFERRED
                ConversationFact.TRANSFER_CONNECTED, // TRANSFERRED → ACTIVE (reserved)
                ConversationFact.CUSTOMER_CLOSE,     // ACTIVE → ENDING
                ConversationFact.SYS_ENDING_GRACE_TIMEOUT // ENDING → CLOSED
        );

        // 3. Demo 2: Transfer failure (v6 behavior)
        System.out.println("\n--- Demo 2: Transfer failure → INITIATED (v6: no rollback) ---");
        CbolStateContext hkCtx2 = buildContext("conv-hk-002", "HK", false);
        runFlow(machine, hkCtx2,
                ConversationFact.CUSTOMER_CONNECT,   // INITIATED → ACTIVE
                ConversationFact.TRANSFER_REQUEST,   // ACTIVE → TRANSFERRED
                ConversationFact.TRANSFER_FAILED      // TRANSFERRED → INITIATED (v6!)
        );

        // 4. Demo 3: Survey flow (SG market with survey enabled)
        System.out.println("\n--- Demo 3: Survey flow (SG market, surveyEnabled=true) ---");
        CbolStateContext sgCtx = buildContext("conv-sg-001", "SG", true);
        runFlow(machine, sgCtx,
                ConversationFact.CUSTOMER_CONNECT,   // INITIATED → ACTIVE
                ConversationFact.SURVEY_START,       // ACTIVE → SURVEY_IN_PROGRESS
                ConversationFact.SURVEY_COMPLETE,    // SURVEY_IN_PROGRESS → ENDING
                ConversationFact.SYS_ENDING_GRACE_TIMEOUT // ENDING → CLOSED
        );

        // 5. Demo 4: Failover (action error → ERROR)
        System.out.println("\n--- Demo 4: Failover flow (action error → ERROR → retry/abort) ---");
        CbolStateContext hkCtx3 = buildContext("conv-hk-003", "HK", false);
        runFlow(machine, hkCtx3,
                ConversationFact.CUSTOMER_CONNECT,   // INITIATED → ACTIVE
                ConversationFact.SYS_ACTION_FAILED,  // ACTIVE → ERROR (failover)
                ConversationFact.SYS_RETRY,          // ERROR → ACTIVE (retry)
                ConversationFact.SYS_ACTION_FAILED,  // ACTIVE → ERROR (fail again)
                ConversationFact.SYS_ABORT           // ERROR → CLOSED (abort)
        );

        // 6. Demo 5: Invalid transition
        System.out.println("\n--- Demo 5: Invalid transition (CLOSED → any event rejected) ---");
        try {
            machine.fireEvent(ConversationState.CLOSED, ConversationFact.CUSTOMER_CONNECT, hkCtx);
        } catch (Exception e) {
            System.out.println("  Rejected (expected): " + e.getMessage());
        }

        System.out.println("\n=== Demo complete ===");
    }

    /**
     * Builds a CbolStateContext with the given market and survey config.
     */
    private static CbolStateContext buildContext(String conversationId, String market, boolean surveyEnabled) {
        ConversationInstance conversation = ConversationInstance.builder()
                .conversationId(conversationId)
                .market(market)
                .tenantId("tenant-001")
                .state(ConversationState.INITIATED)
                .surveyEnabled(surveyEnabled)
                .surveyCompleted(false)
                .build();

        StateMachineMarketConfig marketConfig = StateMachineMarketConfig.builder()
                .customerIdleSeconds(300)
                .transferTimeoutSeconds(180)
                .endingGraceSeconds(120)
                .surveyEnabled(surveyEnabled)
                .transferEnabled(true)
                .genesysEnabled(false)
                .fallbackRoutingStrategy("DROP")
                .build();

        TraceContext traceContext = TraceContext.generate();

        return CbolStateContext.builder()
                .conversation(conversation)
                .interaction(null)
                .marketConfig(marketConfig)
                .traceContext(traceContext)
                .build();
    }

    /**
     * Runs a sequence of events and prints each transition.
     */
    private static void runFlow(StateMachine<ConversationState, ConversationFact, CbolStateContext> machine,
                                 CbolStateContext ctx,
                                 ConversationFact... events) {
        ConversationState current = ctx.conversation().state();

        for (ConversationFact event : events) {
            try {
                StateContext<ConversationState, ConversationFact, CbolStateContext> result =
                        machine.fireEvent(current, event, ctx);
                System.out.printf("  %-20s --[%-25s]--> %-20s%n",
                        current, event, result.getTargetState());
                current = result.getTargetState();
            } catch (Exception e) {
                System.out.printf("  %-20s --[%-25s]--> FAILED: %s%n",
                        current, event, e.getMessage());
                break;
            }
        }
    }
}
