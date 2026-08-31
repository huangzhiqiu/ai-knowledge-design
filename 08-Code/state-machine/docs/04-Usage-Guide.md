# Usage Guide

> Version: 1.0 | Last Updated: 2026-09-01

## 1. Quick Start

### 1.1 Add Dependency

This is a local module. Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.selfdevelopment.ai</groupId>
    <artifactId>hub-statemachine-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1.2 Build and Register the State Machine

```java
import com.selfdevelopment.ai.messaging.cbol.statemachine.ConversationStateMachineFactory;
import com.selfdevelopment.ai.messaging.statemachine.core.StateMachine;

// Build and register (call once at application startup)
StateMachine<ConversationState, ConversationFact, CbolStateContext> machine =
    ConversationStateMachineFactory.build();
```

### 1.3 Fire an Event

```java
import com.selfdevelopment.ai.messaging.cbol.statemachine.CbolStateMachineService;
import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceContext;
import com.selfdevelopment.ai.messaging.cbol.config.StateMachineMarketConfig;
import com.selfdevelopment.ai.messaging.cbol.model.ConversationInstance;
import com.selfdevelopment.ai.messaging.statemachine.core.StateContext;

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
CbolStateMachineService service = new CbolStateMachineService();
StateContext<ConversationState, ConversationFact, CbolStateContext> result =
    service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

// 3. Use result
System.out.println("New state: " + result.getTargetState());  // ACTIVE
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
CbolStateMachineService service = new CbolStateMachineService();

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

// Submit async action
CbolAction sendNotification = ctx -> {
    notificationService.send(ctx.conversation().customerId(), "Your conversation is active");
};

worker.submit(sendNotification, ctx);

// Shutdown at application exit
worker.shutdown();
```

### 7.2 CbolAction Interface

```java
@FunctionalInterface
public interface CbolAction {
    void execute(CbolStateContext ctx);
}
```

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
    CbolStateMachineService service = new CbolStateMachineService();
    CbolStateContext ctx = buildTestContext(ConversationState.INITIATED);

    // When
    StateContext<ConversationState, ConversationFact, CbolStateContext> result =
        service.fire(ctx, ConversationFact.CUSTOMER_CONNECT);

    // Then
    assertEquals(ConversationState.ACTIVE, result.getTargetState());
    assertTrue(result.isTransitionAccepted());
}

@Test
void shouldThrowWhenNoTransitionExists() {
    CbolStateMachineService service = new CbolStateMachineService();
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
    public CbolStateMachineService cbolStateMachineService() {
        return new CbolStateMachineService();
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
