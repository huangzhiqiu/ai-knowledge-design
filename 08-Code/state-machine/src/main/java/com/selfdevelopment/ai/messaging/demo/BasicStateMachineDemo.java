package com.selfdevelopment.ai.messaging.demo;

import com.selfdevelopment.ai.messaging.statemachine.builder.StateMachineBuilder;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;

/**
 * Basic state machine usage demo.
 * <p>
 * Demonstrates the core concepts:
 * <ul>
 *   <li>Building a state machine with the fluent Builder DSL</li>
 *   <li>Defining states, transitions, guards, and actions</li>
 *   <li>Firing events and inspecting the resulting StateContext</li>
 *   <li>Handling transition failures (no rule / guard rejected)</li>
 *   <li>Using ExtendedState to pass data between transitions</li>
 * </ul>
 *
 * <pre>
 * Order flow: CREATED → PAID → SHIPPED → DELIVERED
 *                        ↘ CANCELLED (from CREATED or PAID)
 * </pre>
 */
public class BasicStateMachineDemo {

    // --- Domain model ---

    enum OrderState { CREATED, PAID, SHIPPED, DELIVERED, CANCELLED }
    enum OrderEvent { PAY, SHIP, DELIVER, CANCEL }

    static class OrderContext {
        final String orderId;
        double amount;
        String paymentMethod;

        OrderContext(String orderId, double amount) {
            this.orderId = orderId;
            this.amount = amount;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Basic State Machine Demo ===\n");

        // 1. Build the state machine
        StateMachine<OrderState, OrderEvent, OrderContext> machine = buildOrderMachine();

        // 2. Fire events and observe transitions
        OrderContext ctx = new OrderContext("ORD-001", 99.99);

        System.out.println("--- Normal flow: CREATED → PAID → SHIPPED → DELIVERED ---");
        fireAndPrint(machine, OrderState.CREATED, OrderEvent.PAY, ctx);
        fireAndPrint(machine, OrderState.PAID, OrderEvent.SHIP, ctx);
        fireAndPrint(machine, OrderState.SHIPPED, OrderEvent.DELIVER, ctx);

        // 3. Guard condition demo
        System.out.println("\n--- Guard demo: CANCEL from SHIPPED should be rejected ---");
        try {
            machine.fireEvent(OrderState.SHIPPED, OrderEvent.CANCEL, ctx);
        } catch (Exception e) {
            System.out.println("  Rejected: " + e.getMessage());
        }

        // 4. Cancel from PAID (allowed)
        System.out.println("\n--- Cancel from PAID (allowed) ---");
        OrderContext ctx2 = new OrderContext("ORD-002", 49.99);
        fireAndPrint(machine, OrderState.CREATED, OrderEvent.PAY, ctx2);
        fireAndPrint(machine, OrderState.PAID, OrderEvent.CANCEL, ctx2);

        // 5. ExtendedState demo
        System.out.println("\n--- ExtendedState demo: pass data between transitions ---");
        StateMachine<OrderState, OrderEvent, OrderContext> extMachine = buildExtendedStateMachine();
        OrderContext ctx3 = new OrderContext("ORD-003", 199.99);
        StateContext<OrderState, OrderEvent, OrderContext> result =
                extMachine.fireEvent(OrderState.CREATED, OrderEvent.PAY, ctx3);
        System.out.println("  ExtendedState after PAY: " + result.getExtendedState().getVariables());

        System.out.println("\n=== Demo complete ===");
    }

    /**
     * Builds an order state machine with guard and action.
     */
    private static StateMachine<OrderState, OrderEvent, OrderContext> buildOrderMachine() {
        return StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order-demo")
                .initialState(OrderState.CREATED)
                .endStates(OrderState.DELIVERED, OrderState.CANCELLED)

                // CREATED → PAID
                .transition()
                    .from(OrderState.CREATED)
                    .on(OrderEvent.PAY)
                    .to(OrderState.PAID)
                    .perform(ctx -> System.out.println("  [action] Processing payment for " + ctx.getBusinessContext().orderId))
                .and()

                // PAID → SHIPPED
                .transition()
                    .from(OrderState.PAID)
                    .on(OrderEvent.SHIP)
                    .to(OrderState.SHIPPED)
                    .perform(ctx -> System.out.println("  [action] Shipping order " + ctx.getBusinessContext().orderId))
                .and()

                // SHIPPED → DELIVERED
                .transition()
                    .from(OrderState.SHIPPED)
                    .on(OrderEvent.DELIVER)
                    .to(OrderState.DELIVERED)
                .and()

                // CREATED → CANCELLED
                .transition()
                    .from(OrderState.CREATED)
                    .on(OrderEvent.CANCEL)
                    .to(OrderState.CANCELLED)
                .and()

                // PAID → CANCELLED (with refund action)
                .transition()
                    .from(OrderState.PAID)
                    .on(OrderEvent.CANCEL)
                    .to(OrderState.CANCELLED)
                    .perform(ctx -> System.out.println("  [action] Refunding " + ctx.getBusinessContext().amount + " for " + ctx.getBusinessContext().orderId))
                .and()

                .build();
    }

    /**
     * Builds a state machine that uses ExtendedState to share data.
     */
    private static StateMachine<OrderState, OrderEvent, OrderContext> buildExtendedStateMachine() {
        return StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("extended-demo")
                .initialState(OrderState.CREATED)
                .transition()
                    .from(OrderState.CREATED)
                    .on(OrderEvent.PAY)
                    .to(OrderState.PAID)
                    .perform(ctx -> {
                        // Store payment reference in ExtendedState for later use
                        ctx.getExtendedState().set("paymentRef", "PAY-" + System.currentTimeMillis());
                        ctx.getExtendedState().set("paidAt", System.currentTimeMillis());
                    })
                .and()
                .build();
    }

    /**
     * Helper: fire an event and print the result.
     */
    private static void fireAndPrint(StateMachine<OrderState, OrderEvent, OrderContext> machine,
                                       OrderState from, OrderEvent event, OrderContext ctx) {
        try {
            StateContext<OrderState, OrderEvent, OrderContext> result =
                    machine.fireEvent(from, event, ctx);
            System.out.printf("  %s --[%s]--> %s (accepted=%s)%n",
                    from, event, result.getTargetState(), result.isTransitionAccepted());
        } catch (Exception e) {
            System.out.printf("  %s --[%s]--> FAILED: %s%n", from, event, e.getMessage());
        }
    }
}
