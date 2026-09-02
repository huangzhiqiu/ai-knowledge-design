# Usage Guide

> Version: 2.1 | Last Updated: 2026-09-03

## 1. Quick Start

### 1.1 Project Structure

This is a multi-module Maven project with three modules:

| Module | ArtifactId | Package | Responsibility |
|--------|-----------|---------|----------------|
| **statemachine-core** | `statemachine-core` | `com.selfdevelopment.statemachine` | Generic state machine engine + advanced features |
| **chat-engine** | `chat-engine` | `com.selfdevelopment.chatengine` | Conversation state machine (business layer) |
| **agent-connector** | `agent-connector` | `com.selfdevelopment.agentconnector` | Interaction state machine (channel layer) |

### 1.2 Add Dependency

Add the appropriate module to your `pom.xml`:

```xml
<!-- Core state machine engine (always needed) -->
<dependency>
    <groupId>com.selfdevelopment</groupId>
    <artifactId>statemachine-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Conversation state machine (chat-engine) -->
<dependency>
    <groupId>com.selfdevelopment</groupId>
    <artifactId>chat-engine</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Interaction state machine (agent-connector) -->
<dependency>
    <groupId>com.selfdevelopment</groupId>
    <artifactId>agent-connector</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1.3 Build and Register the State Machine

```java
import com.selfdevelopment.chatengine.statemachine.factory.ConversationStateMachineFactory;
import com.selfdevelopment.statemachine.api.StateMachine;

// Build and register (call once at application startup)
StateMachine<ConversationState, ConversationFact, CbolStateContext> machine =
    ConversationStateMachineFactory.build();
```

### 1.4 Fire an Event

```java
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.context.TraceContext;
import com.selfdevelopment.chatengine.config.StateMachineMarketConfig;
import com.selfdevelopment.chatengine.model.ConversationInstance;
import com.selfdevelopment.statemachine.core.StateContext;

// 1. Build context
CbolStateContext ctx = CbolStateContext.builder()
    .conversation(ConversationInstance.builder()
        .conversationId("conv-001")
        .state(ConversationState.INITIATED)
        .market("HK")
        .lastActivityTs(System.currentTimeMillis())
        .build())
    .marketConfig(StateMachineMarketConfig.defaultConfig())
    .traceContext(TraceContext.generate())
    .build();

// 2. Fire event
ChatEngineStateMachineService service = new ChatEngineStateMachineService();
StateContext<ConversationState, ConversationFact, CbolStateContext> result =
    service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

// 3. Use result
System.out.println("New state: " + result.getTargetState());  // IN_PROGRESS
```

## 2. Building a Custom State Machine

### 2.1 Using the Builder DSL

```java
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order-machine")
        .initialState(OrderState.CREATED)
        .endStates(OrderState.COMPLETED, OrderState.CANCELLED)

        // State with entry/exit actions
        .stateWithEntry(OrderState.PAID, ctx -> sendEmail(ctx))
        .stateWithExit(OrderState.PAID, ctx -> logExit(ctx))

        // Transition with guard and action
        .transition()
            .from(OrderState.CREATED)
            .on(OrderEvent.PAY)
            .to(OrderState.PAID)
            .guard(ctx -> ctx.getBusinessContext().isPaymentValid())
            .perform(ctx -> processPayment(ctx))
        .and()

        // Internal transition (state doesn't change)
        .transition()
            .from(OrderState.PAID)
            .on(OrderEvent.UPDATE_ADDRESS)
            .to(OrderState.PAID)
            .internal()
            .perform(ctx -> updateAddress(ctx))
        .and()

        .build();
```

### 2.2 Using Configurer Adapter (Spring Style)

```java
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<OrderState, OrderEvent, OrderContext> {

    @Override
    public void configure(StateConfigurer<OrderState, OrderEvent, OrderContext> states) {
        states.initial(OrderState.CREATED)
              .state(OrderState.PAID)
              .state(OrderState.SHIPPED)
              .end(OrderState.COMPLETED)
              .end(OrderState.CANCELLED);
    }

    @Override
    public void configure(TransitionConfigurer<OrderState, OrderEvent, OrderContext> transitions) {
        transitions.withExternal()
            .source(OrderState.CREATED)
            .event(OrderEvent.PAY)
            .target(OrderState.PAID)
            .guard(ctx -> ctx.getBusinessContext().isPaymentValid())
            .action(ctx -> processPayment(ctx))
        .and().withExternal()
            .source(OrderState.PAID)
            .event(OrderEvent.SHIP)
            .target(OrderState.SHIPPED)
        .and().withExternal()
            .source(OrderState.SHIPPED)
            .event(OrderEvent.DELIVER)
            .target(OrderState.COMPLETED);
    }
}

// Usage
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.fromConfigurer("order-machine", new OrderStateMachineConfig());
```

## 3. Working with Listeners

### 3.1 Audit Logging Listener

```java
machine.addListener(new StateMachineListener<OrderState, OrderEvent, OrderContext>() {
    @Override
    public void stateChanged(StateContext<OrderState, OrderEvent, OrderContext> ctx) {
        log.info("Order {}: {} -> {} (event={})",
            ctx.getBusinessContext().getOrderId(),
            ctx.getSourceState(),
            ctx.getTargetState(),
            ctx.getEvent());
    }

    @Override
    public void transitionError(StateContext<OrderState, OrderEvent, OrderContext> ctx) {
        log.error("Transition error: {} -> {} on {}",
            ctx.getSourceState(), ctx.getTargetState(), ctx.getEvent(),
            ctx.getException());
    }
});
```

### 3.2 Metrics Listener

```java
machine.addListener(new StateMachineListener<>() {
    @Override
    public void transitionEnded(Transition<...> t, StateContext<...> ctx) {
        metrics.increment("statemachine.transition.success",
            Tags.of("machine", ctx.getMachineId()));
    }

    @Override
    public void transitionDenied(StateContext<...> ctx, String reason) {
        metrics.increment("statemachine.transition.denied",
            Tags.of("reason", reason));
    }
});
```

## 4. Using ExtendedState

```java
// Create extended state for a conversation
ExtendedState ext = new ExtendedState();
ext.set("retryCount", 0);
ext.set("lastError", null);

// Fire first event
StateContext<...> result1 = machine.fireEvent(
    OrderState.CREATED, OrderEvent.PAY, context, ext);

// Read extended state after transition
int retryCount = result1.getExtendedState().get("retryCount", Integer.class);

// Use in guard
.guard(ctx -> {
    Integer retries = ctx.getExtendedState().get("retryCount", Integer.class);
    return retries != null && retries < 3;
})
```

## 5. Multi-Market Configuration

### 5.1 Using InMemoryProvider

```java
MarketConfigProvider.InMemoryProvider provider = new MarketConfigProvider.InMemoryProvider();

// Configure per-market settings
provider.put("HK", StateMachineMarketConfig.builder()
    .customerIdleSeconds(180)      // 3 min for HK market
    .transferTimeoutSeconds(120)    // 2 min
    .endingGraceSeconds(60)          // 1 min
    .surveyEnabled(true)
    .build());

provider.put("SG", StateMachineMarketConfig.builder()
    .customerIdleSeconds(300)
    .transferTimeoutSeconds(180)
    .build());

// Get config for a market (falls back to default if unknown)
StateMachineMarketConfig config = provider.getConfig("HK");
```

### 5.2 Building Context with Market Config

```java
CbolStateContext ctx = CbolStateContext.builder()
    .conversation(conversation)
    .marketConfig(marketConfigProvider.getConfig(conversation.market()))
    .traceContext(TraceContext.generate())
    .build();
```

## 6. Using Monitors

### 6.1 Setting Up Monitors

```java
ChatEngineStateMachineService service = new ChatEngineStateMachineService();

CustomerIdleMonitor idleMonitor = new CustomerIdleMonitor(service);
TransferMonitor transferMonitor = new TransferMonitor(service);
EndingGraceMonitor endingMonitor = new EndingGraceMonitor(service);
```

### 6.2 Scheduled Monitor Execution

```java
@Scheduled(fixedDelay = 30000)  // Every 30 seconds
public void checkCustomerIdle() {
    List<Conversation> activeConversations = repository.findActiveConversations();
    for (Conversation conv : activeConversations) {
        CbolStateContext ctx = buildContext(conv);
        idleMonitor.check(ctx, conv.getLastActivityTs());
    }
}

@Scheduled(fixedDelay = 15000)  // Every 15 seconds
public void checkTransferTimeout() {
    List<Conversation> transferring = repository.findTransferringConversations();
    for (Conversation conv : transferring) {
        CbolStateContext ctx = buildContext(conv);
        transferMonitor.check(ctx, conv.getTransferStartTs());
    }
}
```

## 7. Async Action Execution

### 7.1 Using ActionWorker

```java
ActionWorker worker = new ActionWorker();  // Default: core=CPU, max=CPU*2, queue=1000

// Or with custom configuration
ActionWorker customWorker = new ActionWorker(4, 8, 60, 500);

// Submit async action (implement core Action interface)
Action<ConversationState, ConversationFact, CbolStateContext> sendNotification = ctx -> {
    CbolStateContext businessCtx = ctx.getBusinessContext();
    notificationService.send(businessCtx.conversation().tenantId(), "Your conversation is IN_PROGRESS");
};

// Create StateContext wrapper
StateContext<ConversationState, ConversationFact, CbolStateContext> stateCtx = 
    StateContext.<ConversationState, ConversationFact, CbolStateContext>builder()
        .sourceState(ConversationState.IN_PROGRESS)
        .targetState(ConversationState.IN_PROGRESS)
        .event(ConversationFact.AGENT_ATTACHED)
        .businessContext(ctx)
        .build();

worker.submit(sendNotification, stateCtx);

// Shutdown at application exit
worker.shutdown();
```

### 7.2 Core Action Interface

Actions directly implement the core `Action<S, E, C>` interface from `statemachine-core`:

```java
@FunctionalInterface
public interface Action<S, E, C> {
    void execute(StateContext<S, E, C> context);
}
```

**Action-First Transition Principle**: Action executes BEFORE state change. If action fails, state does NOT change.

See [Concrete Action Implementations](../02-CBOL-Business-Layer-Design.md#75-concrete-action-implementations) for the 6 built-in actions in chat-engine.

## 8. Error Handling Patterns

### 8.1 Handling StateMachineException

```java
try {
    StateContext<...> result = service.fire(ctx, ConversationFact.TRANSFER_REQUEST);
    // Update conversation state
    conversation.setState(result.getTargetState());
    repository.save(conversation);
} catch (StateMachineException e) {
    String message = e.getMessage();
    if (message.contains("No transition found")) {
        // Invalid event for current state
        return ResponseEntity.badRequest().body("Invalid action");
    } else if (message.contains("guard condition failed")) {
        // Business condition not met
        return ResponseEntity.status(409).body("Transfer not available");
    } else if (message.contains("action failed")) {
        // Action execution error
        log.error("Action failed", e);
        return ResponseEntity.status(500).body("Processing error");
    }
    throw e;
}
```

### 8.2 Checking Before Firing

```java
// Check if transition exists (no guard evaluation)
if (machine.hasTransition(conversation.getState(), event)) {
    // Proceed
}

// Check if event can be fired (including guard evaluation)
if (machine.canFire(conversation.getState(), event, context)) {
    machine.fireEvent(conversation.getState(), event, context);
}
```

## 9. Testing

### 9.1 Unit Test Example

```java
@Test
void shouldTransitionFromInitiatedToActiveOnCustomerConnect() {
    // Given
    ConversationStateMachineFactory.build();
    ChatEngineStateMachineService service = new ChatEngineStateMachineService();
    CbolStateContext ctx = buildTestContext(ConversationState.INITIATED);

    // When
    StateContext<ConversationState, ConversationFact, CbolStateContext> result =
        service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

    // Then
    assertEquals(ConversationState.IN_PROGRESS, result.getTargetState());
    assertTrue(result.isTransitionAccepted());
}

@Test
void shouldThrowWhenNoTransitionExists() {
    ChatEngineStateMachineService service = new ChatEngineStateMachineService();
    CbolStateContext ctx = buildTestContext(ConversationState.CLOSED);

    assertThrows(StateMachineException.class,
        () -> service.fire(ctx, ConversationFact.CUSTOMER_CONNECT));
}
```

### 9.2 Test Isolation

```java
@AfterEach
void tearDown() {
    CbolStateMachineRegistry.clear();  // Clear registry between tests
}
```

## 10. Best Practices

### 10.1 DO

- **DO** manage state persistence in the caller (repository/service layer)
- **DO** capture market config as a snapshot in CbolStateContext
- **DO** use TraceContext.generate() at the entry point of each request
- **DO** wrap TraceMdcHelper.set() in try-finally with clear()
- **DO** use bounded thread pools (ActionWorker default is safe)
- **DO** register state machines once at application startup
- **DO** use listeners for cross-cutting concerns (logging, metrics, audit)
- **DO** keep actions idempotent where possible

### 10.2 DON'T

- **DON'T** store current state in the state machine engine (it's stateless by design)
- **DON'T** use `Executors.newCachedThreadPool()` (unbounded, OOM risk)
- **DON'T** forget to call `TraceMdcHelper.clear()` in finally blocks
- **DON'T** throw checked exceptions from actions (wrap in RuntimeException)
- **DON'T** mutate CbolStateContext during transitions (it's an immutable record)
- **DON'T** register the same machine ID twice (throws StateMachineException)
- **DON'T** rely on entry/exit action failures to block transitions (they're best-effort)

## 11. Integration with Spring Boot

### 11.1 Configuration Class

```java
@Configuration
public class StateMachineConfig {

    @Bean
    public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine() {
        return ConversationStateMachineFactory.build();
    }

    @Bean
    public ChatEngineStateMachineService ChatEngineStateMachineService() {
        return new ChatEngineStateMachineService();
    }

    @Bean
    public ActionWorker actionWorker() {
        return new ActionWorker();
    }

    @Bean
    public MarketConfigProvider marketConfigProvider() {
        MarketConfigProvider.InMemoryProvider provider =
            new MarketConfigProvider.InMemoryProvider();
        // Load from config center / Redis
        return provider;
    }

    @PreDestroy
    public void shutdown() {
        actionWorker().shutdown();
    }
}
```

## 12. Advanced Features

### 12.1 Build-Time Validation

Validate the state machine configuration at build time to catch errors early:

```java
// Validate during build (throws on ERROR-level issues)
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order")
        .initialState(OrderState.CREATED)
        .transition()
            .from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID)
        .and()
        .build(true);  // validate=true

// Or validate separately to get all errors
List<ValidationError> errors = StateMachineValidator.validate(machine);
errors.forEach(e -> System.out.println(e.level() + ": " + e.message()));
```

Validation rules: `NO_TRANSITIONS`, `INITIAL_STATE_DEFINED`, `INITIAL_STATE_REACHABLE`, `END_STATE_NO_OUTGOING`, `UNREACHABLE_STATE`, `DEAD_END_STATE`, `INTERNAL_TRANSITION_MATCH`, `DUPLICATE_TRANSITION_NO_GUARD`.

### 12.2 Persistence with Optimistic Locking

Use the built-in repository pattern with version-based optimistic locking:

```java
StateRepository<ConversationState> repository = new InMemoryStateRepository<>();

// Save initial state
repository.save("conv-123", ConversationState.INITIATED, 0);

// Load and transition with optimistic lock
ChatEngineStateMachineService service = new ChatEngineStateMachineService(machine, repository);
StateContext<...> result = service.fireWithLock("conv-123", ConversationFact.USER_MESSAGE, ctx);
// Automatically retries up to 3 times on version conflict
```

For production, implement `StateRepository` with JDBC/MongoDB:

```java
public class JdbcStateRepository<S> implements StateRepository<S> {
    // SELECT state, version FROM conversations WHERE id = ?
    // UPDATE conversations SET state = ?, version = version + 1 WHERE id = ? AND version = ?
}
```

### 12.3 Idempotent Event Processing

Prevent duplicate event processing with event ID deduplication:

```java
ProcessedEventStore store = new InMemoryProcessedEventStore();
IdempotentStateMachineDecorator<OrderState, OrderEvent, OrderContext> idempotent =
    new IdempotentStateMachineDecorator<>(machine, store);

// First call: processes the event
StateContext<...> result1 = idempotent.fireEventWithId(
    "evt-001", OrderState.CREATED, OrderEvent.PAY, ctx);

// Second call with same ID: returns cached result, does NOT re-process
StateContext<...> result2 = idempotent.fireEventWithId(
    "evt-001", OrderState.CREATED, OrderEvent.PAY, ctx);
```

### 12.4 Metrics with Micrometer

Auto-instrument the state machine with Micrometer metrics:

```java
MeterRegistry registry = new SimpleMeterRegistry();  // or Spring's auto-configured registry
StateMachine<OrderState, OrderEvent, OrderContext> monitored =
    new MonitoredStateMachine<>(machine, registry);

// All fireEvent calls are automatically instrumented
monitored.fireEvent(OrderState.CREATED, OrderEvent.PAY, ctx);

// Available metrics:
// - statemachine.transition.duration (Timer)
// - statemachine.transition.success (Counter)
// - statemachine.transition.error (Counter)
// - statemachine.transition.denied (Counter)
// - statemachine.event.received (Counter)
```

### 12.5 Event Sourcing / Audit Trail

Automatically record all transitions for audit and replay:

```java
StateTransitionStore<ConversationState, ConversationFact> store =
    new InMemoryStateTransitionStore<>();

StateMachine<ConversationState, ConversationFact, CbolStateContext> eventSourced =
    new EventSourcedStateMachine<>(machine, store, "conv-123");

// All transitions are automatically recorded
eventSourced.fireEvent(ConversationState.INITIATED, ConversationFact.USER_MESSAGE, ctx);

// Replay full history
List<StateTransitionEvent<...>> history = store.replay("conv-123");

// Reconstruct current state
Optional<ConversationState> current = store.reconstructState("conv-123");

// Time-travel query
List<...> stateAtTime = store.replayUpTo("conv-123", Instant.parse("2026-01-01T10:00:00Z"));
```

### 12.6 Resilience / Failure Handling

Choose a failure handling strategy based on your use case:

```java
// 1. Throw on failure (default)
StateMachine<...> resilient = new ResilientStateMachine<>(machine, new ThrowFailureHandler<>());

// 2. Return source state (no exceptions, check return value)
StateMachine<...> resilient = new ResilientStateMachine<>(machine, new ReturnSourceFailureHandler<>());
StateContext<...> result = resilient.fireEvent(state, event, ctx);
if (!result.isTransitionAccepted()) {
    // handle denial
}

// 3. Fallback to ERROR state
StateMachine<...> resilient = new ResilientStateMachine<>(machine,
    new FallbackStateFailureHandler<>(OrderState.ERROR));

// 4. Retry with exponential backoff, then fallback
FailureHandler<...> fallback = new FallbackStateFailureHandler<>(OrderState.ERROR);
RetryFailureHandler<...> retry = RetryFailureHandler.exponentialBackoff(
    3, fallback, 100, 5000);
StateMachine<...> resilient = new ResilientStateMachine<>(machine, retry);
```

### 12.7 Timeout Events / Scheduled Transitions

Automatically trigger events when an entity stays in a state too long:

```java
// Configure timeouts
Map<ConversationState, TimeoutConfig<ConversationState, ConversationFact>> timeouts = Map.of(
    ConversationState.IN_PROGRESS, TimeoutConfig.<ConversationState, ConversationFact>builder()
        .state(ConversationState.IN_PROGRESS)
        .timeoutEvent(ConversationFact.IDLE_TIMEOUT)
        .duration(30).timeUnit(TimeUnit.SECONDS).build(),
    ConversationState.TRANSFERRING, TimeoutConfig.<ConversationState, ConversationFact>builder()
        .state(ConversationState.TRANSFERRING)
        .timeoutEvent(ConversationFact.TRANSFER_TIMEOUT)
        .duration(60).timeUnit(TimeUnit.SECONDS).build()
);

// Create scheduler
StateMachineTimeoutScheduler<ConversationState, ConversationFact> scheduler =
    new InMemoryTimeoutScheduler<>("conversation-timeout", 4);

// Wrap the machine
StateMachine<ConversationState, ConversationFact, CbolStateContext> timeoutAware =
    new TimeoutAwareStateMachine<>(machine, scheduler, timeouts, "conv-123");

// Entering IN_PROGRESS automatically starts 30s timer
timeoutAware.fireEvent(ConversationState.INITIATED, ConversationFact.USER_MESSAGE, ctx);

// Leaving IN_PROGRESS automatically cancels the timer
timeoutAware.fireEvent(ConversationState.IN_PROGRESS, ConversationFact.AGENT_JOIN, ctx);

// Query timeout status
boolean IN_PROGRESS = timeoutAware.isTimeoutActive();
long remainingMs = timeoutAware.getRemainingTimeoutMs();
timeoutAware.cancelTimeout();  // manual cancel
```

This replaces the need for external Monitor classes (CustomerIdleMonitor, TransferMonitor, EndingGraceMonitor).

### 12.8 Diagram Generation

Generate documentation diagrams directly from the state machine configuration:

```java
// Mermaid (for GitHub / Markdown)
String mermaid = StateMachineDiagramGenerator.toMermaid(machine);

// PlantUML (for Confluence / enterprise docs)
String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);

// Transition table (Markdown)
String table = StateMachineDiagramGenerator.toTransitionTable(machine);

// Write to files
Files.writeString(Path.of("state-diagram.mmd"), mermaid);
Files.writeString(Path.of("state-diagram.puml"), plantUml);
Files.writeString(Path.of("transitions.md"), table);
```

### 12.9 Decorator Composition

Compose multiple decorators for a full-featured pipeline:

```java
StateMachine<OrderState, OrderEvent, OrderContext> pipeline =
    new TimeoutAwareStateMachine<>(          // 1. Outermost: timeout management
        new ResilientStateMachine<>(         // 2. Failure handling
            new EventSourcedStateMachine<>(  // 3. Audit trail
                new MonitoredStateMachine<>( // 4. Metrics
                    new IdempotentStateMachineDecorator<>( // 5. Innermost: deduplication
                        machine,
                        eventStore
                    ),
                    meterRegistry
                ),
                transitionStore,
                "order-123"
            ),
            new ThrowFailureHandler<>()
        ),
        timeoutScheduler,
        timeoutConfigs,
        "order-123"
    );
```

**Recommended order (outermost to innermost):** TimeoutAware → Resilient → EventSourced → Monitored → Idempotent → SimpleStateMachine

---

*For detailed design of each advanced feature, see [05-Advanced-Features.md](./05-Advanced-Features.md).*
