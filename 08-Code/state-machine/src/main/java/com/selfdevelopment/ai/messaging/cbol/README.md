# CBOL Business State Machine Layer

## Package Structure
```
com.selfdevelopment.ai.messaging
鈹溾攢 statemachine/        # Generic stateless table-driven state machine core
鈹斺攢 cbol/                # AI-Messaging-Hub business layer
    鈹溾攢 config           # Market config snapshot + provider
    鈹溾攢 context          # TraceContext, MDC helper, CbolStateContext
    鈹溾攢 enums            # Conversation/Interaction states, facts, reasons
    鈹溾攢 model            # ConversationInstance, InteractionInstance, audit record
    鈹溾攢 statemachine     # Factories + CbolStateMachineService facade
    鈹溾攢 action           # CbolAction + ActionWorker (async + MDC propagation)
    鈹斺攢 monitor          # CustomerIdle / Transfer / EndingGrace monitors
```

## Core Constraints (from design doc v6)
1. Dual-layer state machine: Conversation (business) + Interaction (channel)
2. Market-level config: snapshot bound at conversation creation; running conversations are not affected by hot reload
3. TraceId full-chain propagation: MDC auto-inject; audit log on every transition
4. Transfer failure/timeout goes directly to INITIATED (no rollback)
5. ENDING is irreversible, must converge to CLOSED; market-level timeout force-close
6. Monitors are external components, drive state via System Fact events

## Usage
```java
StateMachineMarketConfig config = marketConfigProvider.getConfig("SG");
TraceContext trace = TraceContext.generate();
CbolStateContext ctx = CbolStateContext.builder()
        .conversation(conv)
        .interaction(interaction)
        .marketConfig(config)
        .traceContext(trace)
        .build();
CbolStateMachineService service = new CbolStateMachineService();
ConversationState next = service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);
```