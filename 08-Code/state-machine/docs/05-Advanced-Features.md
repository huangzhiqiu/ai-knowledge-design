# 05 — Advanced Features

> Production-ready capabilities built on Alibaba COLA StateMachine: multi-market configuration, monitors, trace context, idempotency, observability, PlantUML generation, and extensibility patterns.

---

## 1. Multi-Market Configuration

### Problem

The project deploys to multiple markets (HK, SG, UK, etc.) with similar but slightly different state flows. Each market may have different timeouts, feature toggles, and routing strategies.

### Solution

A `StateMachineMarketConfig` record that captures market-specific behavior as an immutable snapshot. The config is injected into `CbolStateContext` at the start of each state transition.

```java
@Builder
public record StateMachineMarketConfig(
    int customerIdleSeconds,       // Default: 300
    int transferTimeoutSeconds,    // Default: 120
    int endingGraceSeconds,        // Default: 30
    boolean surveyEnabled,         // Default: true
    boolean transferEnabled,       // Default: true
    boolean genesysEnabled,        // Default: true
    String fallbackRoutingStrategy // Default: "DROP"
) {
    public static StateMachineMarketConfig defaultConfig() { ... }
}
```

### Market Config Provider

```java
public interface MarketConfigProvider {
    StateMachineMarketConfig getConfig(String market);

    class InMemoryProvider implements MarketConfigProvider {
        private final ConcurrentHashMap<String, StateMachineMarketConfig> cache = new ConcurrentHashMap<>();

        public void put(String market, StateMachineMarketConfig config) {
            cache.put(market, config);
        }

        @Override
        public StateMachineMarketConfig getConfig(String market) {
            return cache.getOrDefault(market, StateMachineMarketConfig.defaultConfig());
        }
    }
}
```

### Usage in Transition

```java
CbolStateContext ctx = CbolStateContext.builder()
        .conversation(conversation)
        .marketConfig(marketConfigProvider.getConfig("HK"))
        .traceContext(TraceContext.generate())
        .build();

ConversationState newState = sm.fireEvent(
        conversation.state(),
        ConversationFact.INTERACTION_BECAME_ACTIVE,
        ctx);
```

### Design Principles

- **Config as snapshot**: Market config is captured at transition start, not read dynamically during action execution
- **Default-first**: All config fields have sensible defaults; markets only override what differs
- **Immutable**: Config is a record, cannot be mutated during transition
- **Feature toggles**: Boolean fields (surveyEnabled, transferEnabled, genesysEnabled) control which transitions are active

---

## 2. Monitors (Timeout & Health Checks)

### Problem

State machines need to detect and handle timeouts: customer idle, transfer timeout, ending grace period. These are time-based events that should trigger automatic state transitions.

### Solution

Three monitor classes that check elapsed time and fire system events when thresholds are exceeded.

### 2.1 Customer Idle Monitor

```java
public class CustomerIdleMonitor {
    private final ChatEngineStateMachineService service;

    public void check(CbolStateContext ctx, long lastActivityTimestamp) {
        long idleMs = System.currentTimeMillis() - lastActivityTimestamp;
        int threshold = ctx.marketConfig().customerIdleSeconds() * 1000;

        if (idleMs > threshold) {
            service.fire(ctx, ConversationFact.CUSTOMER_IDLE_TIMEOUT);
        }
    }
}
```

### 2.2 Transfer Monitor

```java
public class TransferMonitor {
    private final ChatEngineStateMachineService service;

    public void check(CbolStateContext ctx, long transferStartTimestamp) {
        // Only active in TRANSFERRED state
        if (ctx.conversation().state() != ConversationState.TRANSFERRED) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - transferStartTimestamp;
        int threshold = ctx.marketConfig().transferTimeoutSeconds() * 1000;

        if (elapsedMs > threshold) {
            service.fire(ctx, ConversationFact.TRANSFER_TIMEOUT);
        }
    }
}
```

### 2.3 Ending Grace Monitor

```java
public class EndingGraceMonitor {
    private final ChatEngineStateMachineService service;

    public void check(CbolStateContext ctx, long enterEndingTimestamp) {
        // Only active in ENDING state
        if (ctx.conversation().state() != ConversationState.ENDING) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - enterEndingTimestamp;
        int threshold = ctx.marketConfig().endingGraceSeconds() * 1000;

        if (elapsedMs > threshold) {
            service.fire(ctx, ConversationFact.ENDING_TIMEOUT);
        }
    }
}
```

### Monitor Integration Pattern

```java
// Scheduled task (e.g., @Scheduled every 30 seconds)
@Scheduled(fixedDelay = 30000)
public void runMonitors() {
    List<Conversation> activeConversations = repository.findActive();

    for (Conversation conv : activeConversations) {
        CbolStateContext ctx = buildContext(conv);

        customerIdleMonitor.check(ctx, conv.getLastActivityAt());
        transferMonitor.check(ctx, conv.getTransferStartedAt());
        endingGraceMonitor.check(ctx, conv.getEnteredEndingAt());
    }
}
```

---

## 3. Trace Context & Observability

### Problem

In a distributed system, state transitions need to be traceable across services. Each transition should carry a trace ID for logging and debugging.

### Solution

A `TraceContext` record with UUID-based trace ID, plus `TraceMdcHelper` for SLF4J MDC propagation.

### 3.1 Trace Context

```java
public record TraceContext(
    String traceId,
    long timestamp
) {
    public static TraceContext generate() {
        return new TraceContext(UUID.randomUUID().toString(), System.currentTimeMillis());
    }
}
```

### 3.2 MDC Propagation

```java
public class TraceMdcHelper {
    private static final String TRACE_ID_KEY = "traceId";

    public static void set(TraceContext ctx) {
        MDC.put(TRACE_ID_KEY, ctx.traceId());
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
```

### 3.3 Usage in Service

```java
public ConversationState fire(CbolStateContext ctx, ConversationFact fact) {
    TraceMdcHelper.set(ctx.traceContext());
    long start = System.currentTimeMillis();
    try {
        ConversationState from = ctx.conversation().state();
        ConversationState to = convSm.fireEvent(from, fact, ctx);

        log.info("State transition: {} --({})--> {}, conversationId={}, durationMs={}",
                from, fact, to,
                ctx.conversation().conversationId(),
                System.currentTimeMillis() - start);
        return to;
    } finally {
        TraceMdcHelper.clear();
    }
}
```

### 3.4 Audit Logging Pattern

Every state transition should produce an audit log entry with:
- Business ID (conversationId / interactionId)
- From state / To state
- Event / Fact
- Market
- Trace ID
- Duration
- Timestamp

---

## 4. Idempotency

### Problem

In a distributed system, events may be delivered multiple times (at-least-once delivery). The state machine should handle duplicate events gracefully without corrupting state.

### Solution

Use `conversationId + event` as an idempotency key, and check before firing.

```java
public class IdempotentStateMachineService {
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final ChatEngineStateMachineService delegate;

    public ConversationState fire(CbolStateContext ctx, ConversationFact event) {
        String idempotencyKey = ctx.conversation().conversationId() + ":" + event;

        if (processedEvents.contains(idempotencyKey)) {
            log.warn("Duplicate event detected: {}", idempotencyKey);
            return ctx.conversation().state(); // Return current state, no-op
        }

        processedEvents.add(idempotencyKey);
        return delegate.fire(ctx, event);
    }
}
```

### COLA-Level Idempotency

COLA StateMachine itself is idempotent in the sense that:
- If no transition matches `(sourceState, event)`, it throws `StateMachineException`
- The state does NOT change on exception (action-first principle)
- Firing the same event from the same state repeatedly will either succeed repeatedly (if action is idempotent) or fail consistently

**Best practice**: Make your Action implementations idempotent. Use database unique constraints or optimistic locking to prevent duplicate side effects.

---

## 5. PlantUML Diagram Generation

### Problem

State machines can become complex with many states and transitions. Visual documentation helps developers understand the flow.

### Solution

COLA StateMachine has a built-in `generatePlantUML()` method that produces a PlantUML state diagram.

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        ConversationStateMachineFactory.build();

String plantUml = sm.generatePlantUML();
System.out.println(plantUml);
```

### Output Example

```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
TRANSFERRED --> IN_PROGRESS : TRANSFER_FAILED
TRANSFERRED --> ENDING : TRANSFER_COMPLETE
ENDING --> CLOSED : SYS_ENDING_GRACE_TIMEOUT
@enduml
```

### Rendering

Use any PlantUML renderer:
- Online: https://www.plantuml.com/plantuml/
- VS Code: PlantUML extension
- IntelliJ: PlantUML integration plugin

### CI/CD Integration

```bash
# Generate PlantUML and render to PNG in CI
java -jar plantuml.jar -tpng state-machine.puml
```

---

## 6. Factory Caching Pattern

### Problem

COLA StateMachine does NOT allow rebuilding a state machine with the same ID. Attempting to build twice throws:
```
The state machine with id [conversation] is already built, no need to build again
```

### Solution

Use a factory caching pattern with double-checked locking.

```java
public class ConversationStateMachineFactory {
    public static final String MACHINE_ID = "conversation";
    private static volatile StateMachine<ConversationState, ConversationFact, CbolStateContext> instance;

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        // Fast path: check if already built
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (Exception ignored) {
            // Not built yet
        }

        // Slow path: build with synchronization
        synchronized (ConversationStateMachineFactory.class) {
            // Double-check
            try {
                StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                        StateMachineFactory.get(MACHINE_ID);
                if (existing != null) {
                    return existing;
                }
            } catch (Exception ignored) {
                // Not built yet
            }

            // Build
            StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                    StateMachineBuilderFactory.create();

            // ... define transitions ...

            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                    builder.build(MACHINE_ID);
            StateMachineFactory.register(sm);
            return sm;
        }
    }
}
```

### Test Isolation

For tests that need a fresh state machine, use a unique machine ID per test class:

```java
class MyTest {
    private static final String TEST_MACHINE_ID = "conversation-test-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Build with unique ID
    }
}
```

---

## 7. Action-First Principle & Error Handling

### Problem

What happens when an Action throws an exception during a state transition?

### Solution

COLA StateMachine follows the **action-first principle**:
1. Action executes **before** state change
2. If action succeeds → state changes to target state
3. If action fails → `StateMachineException` is thrown, state remains unchanged

```java
try {
    ConversationState newState = sm.fireEvent(
            ConversationState.IN_PROGRESS,
            ConversationFact.CUSTOMER_CLOSE,
            ctx);
    // State changed successfully
} catch (StateMachineException e) {
    // Action failed OR no transition matched
    // State remains IN_PROGRESS
    log.error("Transition failed", e);

    // Business layer can decide: retry, failover, or alert
    handleFailure(ctx, e);
}
```

### Failover Pattern (Business Layer)

While COLA doesn't have a built-in failover state machine, the business layer can implement one:

```java
public ConversationState fireWithFailover(CbolStateContext ctx, ConversationFact event) {
    try {
        return sm.fireEvent(ctx.conversation().state(), event, ctx);
    } catch (StateMachineException e) {
        log.warn("Primary transition failed, attempting failover: {}", e.getMessage());

        // Try failover event (e.g., SYSTEM_ERROR)
        try {
            return sm.fireEvent(ctx.conversation().state(), ConversationFact.SYSTEM_ERROR, ctx);
        } catch (StateMachineException e2) {
            log.error("Failover also failed", e2);
            throw e2;
        }
    }
}
```

---

## 8. Extensibility Patterns

### 8.1 Custom Action Composition

Compose multiple actions into one:

```java
public class CompositeAction<S, E, C> implements Action<S, E, C> {
    private final List<Action<S, E, C>> actions;

    @Override
    public void execute(S from, S to, E event, C context) {
        for (Action<S, E, C> action : actions) {
            action.execute(from, to, event, context);
        }
    }
}
```

### 8.2 Action with Retry

Wrap an action with retry logic:

```java
public class RetryAction<S, E, C> implements Action<S, E, C> {
    private final Action<S, E, C> delegate;
    private final int maxRetries;
    private final long backoffMs;

    @Override
    public void execute(S from, S to, E event, C context) {
        int attempts = 0;
        while (true) {
            try {
                delegate.execute(from, to, event, context);
                return;
            } catch (Exception e) {
                if (++attempts >= maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(backoffMs * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
```

### 8.3 Async Action Worker (Reserved)

`ActionWorker` is a reserved utility class for future async action execution. Current design uses synchronous action-first transitions.

```java
// RESERVED: For future async action execution
public class ActionWorker {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CompletableFuture<Void> submit(
            Action<ConversationState, ConversationFact, CbolStateContext> action,
            ConversationState from, ConversationState to,
            ConversationFact event, CbolStateContext ctx) {
        return CompletableFuture.runAsync(() -> {
            TraceMdcHelper.set(ctx.traceContext());
            try {
                action.execute(from, to, event, ctx);
            } finally {
                TraceMdcHelper.clear();
            }
        }, executor);
    }
}
```

---

## 9. Summary Table

| Feature | Implementation | Location |
|---------|---------------|----------|
| Multi-market config | `StateMachineMarketConfig` record | chat-engine/config |
| Customer idle monitor | `CustomerIdleMonitor` | chat-engine/monitor |
| Transfer monitor | `TransferMonitor` | chat-engine/monitor |
| Ending grace monitor | `EndingGraceMonitor` | chat-engine/monitor |
| Trace context | `TraceContext` + `TraceMdcHelper` | chat-engine/context |
| Idempotency | Business layer pattern | Application code |
| PlantUML generation | COLA built-in `generatePlantUML()` | statemachine-core |
| Factory caching | Double-checked locking pattern | chat-engine/statemachine/factory |
| Action-first error handling | COLA built-in | statemachine-core |
| Failover | Business layer pattern | Application code |
| Async action worker | Reserved utility class | chat-engine/action |

---

## 10. References

- Alibaba COLA GitHub: https://github.com/alibaba/COLA
- COLA StateMachine module: `cola-components/cola-component-statemachine`
- COLA StateMachine tests: `cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`

---

*Last updated: 2026-09-05 (v3.0 — rewritten for Alibaba COLA StateMachine)*
