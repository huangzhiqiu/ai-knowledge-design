# CBOL Business State Machine Layer

## Package Structure
```
com.selfdevelopment.ai.messaging
├─ statemachine/        # Generic lightweight state machine core (Spring-inspired)
│   ├─ core/            # StateMachine, SimpleStateMachine, Transition, StateContext
│   │                   # ExtendedState, Guard, Action, TransitionKind
│   ├─ builder/         # StateMachineBuilder (fluent DSL)
│   ├─ listener/        # StateMachineListener (lifecycle hooks)
│   ├─ registry/        # StateMachineRegistry (named machine lookup)
│   └─ exception/       # StateMachineException
└─ cbol/                # AI-Messaging-Hub business layer
    ├─ config           # Market config snapshot + provider
    ├─ context          # TraceContext, MDC helper, CbolStateContext
    ├─ enums            # Conversation/Interaction states, facts, reasons
    ├─ model            # ConversationInstance, InteractionInstance, audit record
    ├─ statemachine     # Factories + CbolStateMachineService facade + Registry
    ├─ action           # CbolAction + ActionWorker (async + MDC propagation)
    └─ monitor          # CustomerIdle / Transfer / EndingGrace monitors
```

## Core Engine Features (Spring StateMachine-inspired)

| Feature | Description |
|---------|-------------|
| **Stateless** | Current state injected per `fireEvent()` call; safe for concurrent use |
| **Table-driven** | Transitions in `ConcurrentHashMap`, O(1) lookup |
| **StateContext** | Rich context: source/target state, event, business context, extended state, event headers |
| **ExtendedState** | Key-value variables shared across transitions within an interaction |
| **Guard** | Guard condition receiving full `StateContext` (replaces simple Condition) |
| **Action** | Transition action receiving full `StateContext` |
| **TransitionKind** | `EXTERNAL` (state changes) and `INTERNAL` (action only, state stays) |
| **Listener** | `StateMachineListener`: stateChanged, transitionStarted/Ended/Denied, error, lifecycle |
| **Lifecycle** | `start()` / `stop()` / `isStarted()` |
| **Initial/End states** | Configured via builder for documentation and validation |
| **Zero dependencies** | Only JDK standard library for the core |

## Core Constraints (from design doc v6)
1. Dual-layer state machine: Conversation (business) + Interaction (channel)
2. Market-level config: snapshot bound at conversation creation; running conversations are not affected by hot reload
3. TraceId full-chain propagation: MDC auto-inject; audit log on every transition
4. Transfer failure/timeout goes directly to INITIATED (no rollback)
5. ENDING is irreversible, must converge to CLOSED; market-level timeout force-close
6. Monitors are external components, drive state via System Fact events

## Usage

### Building a state machine (Builder DSL)
```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
    StateMachineBuilder.<ConversationState, ConversationFact, CbolStateContext>builder("conversation")
        .initialState(ConversationState.INITIATED)
        .endStates(ConversationState.CLOSED)
        .stateWithEntry(ConversationState.ACTIVE, ctx -> log.info("Entering ACTIVE"))
        .stateWithExit(ConversationState.ACTIVE, ctx -> log.info("Leaving ACTIVE"))
        .transition()
            .from(ConversationState.INITIATED)
            .on(ConversationFact.CUSTOMER_CONNECT)
            .to(ConversationState.ACTIVE)
            .guard(ctx -> ctx.getBusinessContext().marketConfig().transferEnabled())
            .perform(ctx -> log.info("Customer connected: {}", ctx.getSourceState()))
        .and()
        .transition()
            .from(ConversationState.ACTIVE)
            .on(ConversationFact.SYS_CUSTOMER_IDLE)
            .to(ConversationState.ACTIVE)
            .internal()
            .perform(ctx -> ctx.getExtendedState().set("idleNotified", true))
        .and()
        .build();
```

### Building a state machine (Spring-style ConfigurerAdapter)
```java
public class ConversationStateMachineConfig
        extends StateMachineConfigurerAdapter<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void configure(StateConfigurer<ConversationState, ConversationFact, CbolStateContext> states) {
        states.withStates()
              .initial(ConversationState.INITIATED)
              .state(ConversationState.INITIATED, null, ctx -> log.info("exit INITIATED"))
              .stateWithEntry(ConversationState.ACTIVE, ctx -> log.info("enter ACTIVE"))
              .stateWithExit(ConversationState.TRANSFERRED, ctx -> log.info("exit TRANSFERRED"))
              .end(ConversationState.CLOSED);
    }

    @Override
    public void configure(TransitionConfigurer<ConversationState, ConversationFact, CbolStateContext> transitions) {
        transitions.withExternal()
                   .source(ConversationState.INITIATED)
                   .target(ConversationState.ACTIVE)
                   .event(ConversationFact.CUSTOMER_CONNECT)
                   .guard(ctx -> ctx.getBusinessContext().marketConfig().transferEnabled())
                   .action(ctx -> notifyCustomerConnected())
               .and().withExternal()
                   .source(ConversationState.ACTIVE)
                   .target(ConversationState.TRANSFERRED)
                   .event(ConversationFact.TRANSFER_REQUEST)
               .and().withInternal()
                   .source(ConversationState.ACTIVE)
                   .target(ConversationState.ACTIVE)
                   .event(ConversationFact.SYS_CUSTOMER_IDLE)
                   .action(ctx -> ctx.getExtendedState().set("idleNotified", true));
    }
}

// Build from configurer
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
    StateMachineBuilder.fromConfigurer("conversation", new ConversationStateMachineConfig());
```

**Entry/Exit action execution order** (for external transitions):
1. Exit action of source state
2. Transition action
3. Entry action of target state

Internal transitions do NOT execute entry/exit actions.

### Firing an event
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

// Full StateContext (contains target state, extended state, acceptance flag)
StateContext<ConversationState, ConversationFact, CbolStateContext> result =
        service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

// Or convenience: just the target state
ConversationState next = service.fireAndGetState(ctx, ConversationFact.CUSTOMER_CONNECT);
```

### Adding a listener (for auditing/monitoring)
```java
sm.addListener(new StateMachineListener<>() {
    @Override
    public void stateChanged(StateContext<S, E, C> ctx) {
        auditLog.info("{} -> {} via {}", ctx.getSourceState(), ctx.getTargetState(), ctx.getEvent());
    }
    @Override
    public void transitionDenied(StateContext<S, E, C> ctx, String reason) {
        log.warn("Transition denied: {} {} - {}", ctx.getSourceState(), ctx.getEvent(), reason);
    }
});
```

### Using ExtendedState (shared across transitions)
```java
ExtendedState ext = new ExtendedState();
sm.fireEvent(State.A, Event.E1, ctx, ext);
sm.fireEvent(State.B, Event.E2, ctx, ext); // ext persists across both transitions
```
