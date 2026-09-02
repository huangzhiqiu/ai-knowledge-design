# Business Layer Design (Chat Engine + Agent Connector)

> Version: 2.1 | Last Updated: 2026-09-03

## 1. Overview

The business layer implements conversation lifecycle management using the core state machine framework. It is organized into **two separate modules** with clear system boundaries:

| Module | Package | Responsibility | External Systems |
|--------|---------|----------------|------------------|
| **chat-engine** | `com.selfdevelopment.chatengine` | Conversation state machine (business-level): 7 states, multi-market config, monitors, async actions | AIBot API, Chat History ODS |
| **agent-connector** | `com.selfdevelopment.agentconnector` | Interaction state machine (channel-level): 6 states, connector management | Genesys Cloud, Customer WebSocket |

**Key Characteristics:**
- **Dual-state model**: Conversation (business-level) + Interaction (channel-level), each in its own module
- **Multi-market support**: Per-market configuration for timeouts and feature flags
- **Full-chain tracing**: TraceId propagation via SLF4J MDC across async boundaries
- **v6 design**: Transfer failures/timeouts return to INITIATED (no rollback to IN_PROGRESS)
- **Automated monitors**: Three time-based monitors for idle detection, transfer timeout, and ending grace
- **Survey as in-progress**: IN_PROGRESS is a sub-state of IN_PROGRESS flow, controlled by flow
- **Failover mechanism**: Unhandled exceptions trigger FAIL event, routed to fail branch

## 2. Conversation State Model

### 2.1 States

```java
public enum ConversationState {
    INITIATED,          // Conversation created, waiting for customer connection
    IN_PROGRESS,             // Customer connected, AI or agent actively handling
    TRANSFERRED,        // Transfer to human agent in progress
    IN_PROGRESS, // Post-conversation survey in progress (controlled by flow)
    ENDING,             // Conversation ending, grace period for cleanup
    ERROR,              // Action failed, failover state (retry or abort)
    CLOSED              // Terminal state, conversation fully closed
}
```

### 2.2 State Descriptions

| State | Description | Entry Trigger | Exit Trigger |
|-------|-------------|---------------|--------------|
| INITIATED | Conversation created but customer not yet connected | System creates conversation | CUSTOMER_CONNECT / SYS_ACTION_FAILED |
| IN_PROGRESS | Customer connected, IN_PROGRESS conversation | CUSTOMER_CONNECT / SYS_RETRY | TRANSFER_REQUEST / SURVEY_START / CUSTOMER_CLOSE / SYS_CUSTOMER_IDLE / SYS_ACTION_FAILED |
| TRANSFERRED | Transfer to agent in progress | TRANSFER_REQUEST | TRANSFER_CONNECTED / TRANSFER_FAILED / TRANSFER_TIMEOUT / SURVEY_START / SYS_CUSTOMER_IDLE / SYS_ACTION_FAILED |
| IN_PROGRESS | Post-conversation survey IN_PROGRESS | SURVEY_START | SURVEY_COMPLETE / SYS_SURVEY_TIMEOUT / SYS_CUSTOMER_IDLE / CUSTOMER_CLOSE / SYS_ACTION_FAILED |
| ENDING | Grace period before closure | CUSTOMER_CLOSE / SYS_CUSTOMER_IDLE / SURVEY_COMPLETE / SYS_SURVEY_TIMEOUT | SYS_ENDING_GRACE_TIMEOUT |
| ERROR | Action failed, failover state | SYS_ACTION_FAILED | SYS_RETRY / SYS_ABORT |
| CLOSED | Terminal state | SYS_ENDING_GRACE_TIMEOUT / SYS_ABORT | (none) |

### 2.3 Events (ConversationFact)

```java
public enum ConversationFact {
    // LIFECYCLE
    CUSTOMER_CONNECT,      // Customer established connection
    AGENT_ATTACHED,        // Human agent joined (reserved)

    // TRANSFER
    TRANSFER_REQUEST,      // Request to transfer to human agent
    TRANSFER_CONNECTED,    // Agent successfully connected (reserved)
    TRANSFER_FAILED,       // Transfer failed (agent unavailable, rejected, etc.)
    TRANSFER_TIMEOUT,      // Transfer timed out waiting for agent

    // SURVEY
    SURVEY_START,          // Start post-conversation survey (surveyEnabled=true)
    SURVEY_COMPLETE,       // Survey completed by customer

    // ENDING
    CUSTOMER_CLOSE,        // Customer explicitly closed
    AGENT_CLOSE,           // Agent closed (reserved)

    // SYSTEM (fired by monitors)
    SYS_CUSTOMER_IDLE,          // Customer idle threshold exceeded
    SYS_TRANSFER_TIMEOUT,       // Transfer duration exceeded threshold
    SYS_ENDING_GRACE_TIMEOUT,   // Ending grace period exceeded
    SYS_SURVEY_TIMEOUT,         // Survey duration exceeded threshold

    // FAILOVER (action error → fail branch)
    SYS_ACTION_FAILED,    // Action threw unhandled exception → enter ERROR
    SYS_RETRY,            // Retry from ERROR → IN_PROGRESS
    SYS_ABORT             // Abort from ERROR → CLOSED
}
```

## 3. State Transition Diagram

```mermaid
stateDiagram-v2
    [*] --> INITIATED : Create conversation

    INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
    INITIATED --> ENDING : SYS_CUSTOMER_IDLE

    IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
    IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
    IN_PROGRESS --> ENDING : SYS_CUSTOMER_IDLE

    TRANSFERRED --> IN_PROGRESS : TRANSFER_CONNECTED (reserved)
    TRANSFERRED --> INITIATED : TRANSFER_FAILED
    TRANSFERRED --> INITIATED : TRANSFER_TIMEOUT
    TRANSFERRED --> INITIATED : SYS_TRANSFER_TIMEOUT
    TRANSFERRED --> ENDING : SYS_CUSTOMER_IDLE

    ENDING --> CLOSED : SYS_ENDING_GRACE_TIMEOUT

    CLOSED --> [*]
```

### 3.1 Transition Table

| # | From | Event | To | Guard | Action | Notes |
|---|------|-------|-----|-------|--------|-------|
| 1 | INITIATED | CUSTOMER_CONNECT | IN_PROGRESS | - | - | Customer connects |
| 2 | IN_PROGRESS | TRANSFER_REQUEST | TRANSFERRED | transferEnabled | - | Request agent transfer |
| 3 | TRANSFERRED | TRANSFER_FAILED | INITIATED | - | - | v6: no rollback to IN_PROGRESS |
| 4 | TRANSFERRED | TRANSFER_TIMEOUT | INITIATED | - | - | v6: no rollback to IN_PROGRESS |
| 5 | TRANSFERRED | SYS_TRANSFER_TIMEOUT | INITIATED | - | - | Monitor-driven |
| 6 | IN_PROGRESS | CUSTOMER_CLOSE | ENDING | - | - | Customer closes |
| 7 | INITIATED | SYS_CUSTOMER_IDLE | ENDING | - | - | Monitor-driven |
| 8 | IN_PROGRESS | SYS_CUSTOMER_IDLE | ENDING | - | - | Monitor-driven |
| 9 | TRANSFERRED | SYS_CUSTOMER_IDLE | ENDING | - | - | Monitor-driven |
| 10 | ENDING | SYS_ENDING_GRACE_TIMEOUT | CLOSED | - | - | Monitor-driven, terminal |

## 4. Core Components

### 4.1 CbolStateContext

The aggregate context object passed through every state transition.

```java
@Builder
public record CbolStateContext(
    ConversationInstance conversation,      // Current conversation data
    InteractionInstance interaction,         // Channel/device data (reserved)
    StateMachineMarketConfig marketConfig,   // Market-level configuration
    TraceContext traceContext                // Trace identifiers
) {}
```

**Design Rationale:**
- Immutable record ensures thread safety
- Aggregates all data needed by guards, actions, and monitors in one object
- Market config is snapshot-based (captured at event time) to avoid mid-transition config changes

### 4.2 ConversationInstance

```java
@Builder
public record ConversationInstance(
    String conversationId,          // Unique conversation identifier
    ConversationState state,        // Current state (managed by caller)
    String market,                  // Market code (e.g., "HK", "SG", "UK")
    String customerId,              // Customer identifier
    String agentId,                 // Assigned agent (null if AI-only)
    Long lastActivityTs,            // Last customer activity timestamp
    Long transferStartTs,           // Transfer start timestamp (null if not transferring)
    Long endingStartTs              // Ending period start timestamp (null if not ending)
) {}
```

### 4.3 TraceContext

```java
@Builder
public record TraceContext(
    String traceId,                 // Full-chain trace identifier (UUID)
    String spanId,                  // Current span identifier (UUID)
    String parentSpanId,            // Parent span (null for root)
    long startTimeMs,               // Trace start timestamp
    Map<String, String> tags        // Additional trace metadata
) {
    public static TraceContext generate() { ... }
}
```

### 4.4 TraceMdcHelper

Utility for propagating trace context to SLF4J MDC (Mapped Diagnostic Context).

```java
public final class TraceMdcHelper {
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    public static void set(TraceContext ctx) {
        if (ctx == null) return;
        MDC.put(MDC_TRACE_ID, ctx.traceId());
        MDC.put(MDC_SPAN_ID, ctx.spanId());
    }

    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }
}
```

**Usage Pattern (mandatory):**
```java
try {
    TraceMdcHelper.set(ctx.traceContext());
    // ... business logic ...
} finally {
    TraceMdcHelper.clear();  // MANDATORY: prevents memory leak in thread pools
}
```

## 5. Multi-Market Configuration

### 5.1 StateMachineMarketConfig

```java
@Builder
public record StateMachineMarketConfig(
    long customerIdleSeconds,       // Customer idle threshold (default: 300s = 5min)
    long transferTimeoutSeconds,     // Transfer timeout (default: 180s = 3min)
    long endingGraceSeconds,         // Ending grace period (default: 120s = 2min)
    boolean surveyEnabled,           // Whether post-conversation survey is enabled
    boolean transferEnabled,         // Whether human agent transfer is enabled
    boolean genesysEnabled,          // Whether Genesys integration is enabled
    String fallbackRoutingStrategy   // Strategy when transfer fails ("DROP", "RETRY", "QUEUE")
) {
    public static StateMachineMarketConfig defaultConfig() { ... }
}
```

### 5.2 MarketConfigProvider

```java
public interface MarketConfigProvider {
    StateMachineMarketConfig getConfig(String market);
    void invalidate(String market);

    class InMemoryProvider implements MarketConfigProvider {
        private final ConcurrentHashMap<String, StateMachineMarketConfig> cache;
        private final StateMachineMarketConfig fallback;

        public StateMachineMarketConfig getConfig(String market) {
            return cache.getOrDefault(market, fallback);
        }
        // ...
    }
}
```

**Design Notes:**
- `InMemoryProvider` is a reference implementation; production should use Redis-backed or config-center provider
- `invalidate(market)` allows cache refresh when config changes
- Fallback to `defaultConfig()` ensures no NPE for unknown markets

## 6. Monitors

### 6.1 AbstractTimeoutMonitor

Base class for all time-based monitors.

```java
public abstract class AbstractTimeoutMonitor {
    protected final ChatEngineStateMachineService ChatEngineStateMachineService;

    protected abstract boolean isApplicable(ConversationState state);
    protected abstract long timeoutSeconds(CbolStateContext ctx);
    protected abstract ConversationFact timeoutEvent();

    public void check(CbolStateContext ctx, long referenceTs) {
        if (!isApplicable(ctx.conversation().state())) return;
        long timeoutMs = TimeUnit.SECONDS.toMillis(timeoutSeconds(ctx));
        long elapsedMs = System.currentTimeMillis() - referenceTs;
        if (elapsedMs >= timeoutMs) {
            ChatEngineStateMachineService.fire(ctx, timeoutEvent());
        }
    }
}
```

### 6.2 Monitor Implementations

| Monitor | Applicable States | Timeout Config | Event Fired | Reference Timestamp |
|---------|-------------------|----------------|-------------|---------------------|
| CustomerIdleMonitor | INITIATED, IN_PROGRESS, TRANSFERRED | customerIdleSeconds | SYS_CUSTOMER_IDLE | lastActivityTs |
| TransferMonitor | TRANSFERRED | transferTimeoutSeconds | SYS_TRANSFER_TIMEOUT | transferStartTs |
| EndingGraceMonitor | ENDING | endingGraceSeconds | SYS_ENDING_GRACE_TIMEOUT | endingStartTs |

### 6.3 Monitor Execution Flow

```mermaid
flowchart TD
    A[Scheduler triggers monitor] --> B[Load conversation from DB]
    B --> C[Build CbolStateContext with market config]
    C --> D{isApplicable?}
    D -->|No| E[Skip]
    D -->|Yes| F[Calculate elapsed time]
    F --> G{elapsed >= timeout?}
    G -->|No| E
    G -->|Yes| H[fire SYS_* event]
    H --> I[State machine transitions]
    I --> J[Update conversation state in DB]
    J --> K[Log StateTransitionRecord]
```

## 7. ActionWorker (Async Execution)

### 7.1 Design

Executes state machine actions asynchronously with a bounded thread pool.

```java
public class ActionWorker {
    private final ExecutorService executor;

    public ActionWorker() {
        this(Runtime.getRuntime().availableProcessors(),  // core
             Runtime.getRuntime().availableProcessors() * 2,  // max
             60L,    // keepAlive seconds
             1000);  // queue capacity
    }

    public void submit(Action<ConversationState, ConversationFact, CbolStateContext> action,
                       StateContext<ConversationState, ConversationFact, CbolStateContext> ctx) {
        executor.submit(() -> {
            try {
                TraceMdcHelper.set(ctx.traceContext());  // MDC propagation
                action.execute(ctx);
            } catch (RuntimeException e) {
                log.error("Action execution failed, conversationId={}", ..., e);
            } finally {
                TraceMdcHelper.clear();  // MANDATORY cleanup
            }
        });
    }
}
```

### 7.2 Thread Pool Configuration

| Parameter | Default Value | Rationale |
|-----------|--------------|-----------|
| corePoolSize | CPU cores | Minimum threads for baseline load |
| maximumPoolSize | CPU cores * 2 | Handles bursty IO-bound action workloads |
| keepAliveTime | 60s | Idle thread reclamation |
| queueCapacity | 1000 | Bounded queue prevents OOM |
| rejectionPolicy | CallerRunsPolicy | Backpressure: caller thread executes task |
| threadFactory | NamedThreadFactory | Thread names for debugging ("cbol-action-worker-N") |

### 7.3 MDC Propagation

```mermaid
sequenceDiagram
    participant Caller
    participant Worker as ActionWorker
    participant Pool as Thread Pool
    participant MDC as SLF4J MDC

    Caller->>Worker: submit(action, ctx with traceId)
    Worker->>Pool: submit(Runnable)
    Pool->>MDC: put("traceId", ctx.traceId())
    Pool->>Pool: action.execute(ctx)
    Note over Pool: All logs in this thread carry traceId
    Pool->>MDC: remove("traceId") [finally]
```

### 7.4 Submission Modes

ActionWorker supports three submission modes for different use cases:

#### 7.4.1 Fire-and-Forget (`submit`)

Original mode for simple use cases where execution result is not needed. Exceptions are caught and logged only.

```java
// Fire-and-forget: exceptions are logged only
worker.submit(action, stateContext);
```

**Use case**: Non-critical background tasks where failure is acceptable (e.g., audit logging, metrics collection).

#### 7.4.2 Result Tracking (`submitWithResult`)

Returns a `CompletableFuture<Void>` that completes when the action finishes. Callers can track execution result and handle exceptions.

```java
// Result tracking: get CompletableFuture for result tracking
CompletableFuture<Void> future = worker.submitWithResult(action, stateContext);

// Chain operations
future.thenRun(() -> log.info("Action completed successfully"))
      .exceptionally(ex -> {
          log.error("Action failed", ex);
          // Handle failure (e.g., retry, alert, fallback)
          return null;
      });

// Or block and wait
try {
    future.get(3, TimeUnit.SECONDS);
} catch (ExecutionException e) {
    // Handle action exception
}
```

**Use case**: Critical business operations where failure needs to be handled (e.g., payment processing, state transitions that require confirmation).

#### 7.4.3 Callback-based (`submitWithCallback`)

Supports success and failure callbacks for event-driven programming style.

```java
// Callback-based: success/failure callbacks
worker.submitWithCallback(action, stateContext,
    ctx -> {
        // Success callback
        log.info("Action completed for conversation: {}", ctx.getBusinessContext().conversation().conversationId());
        // Trigger next step in workflow
    },
    ex -> {
        // Failure callback
        log.error("Action failed", ex);
        // Trigger error handling workflow
        alertService.notify("Action failed: " + ex.getMessage());
    });
```

**Use case**: Workflow orchestration where the next step depends on the execution result (e.g., saga pattern, event-driven architecture).

#### 7.4.4 Mode Comparison

| Mode | Return Value | Exception Handling | Use Case |
|------|-------------|-------------------|----------|
| `submit` | void | Logged only | Non-critical background tasks |
| `submitWithResult` | `CompletableFuture<Void>` | Propagated via Future | Critical operations needing result tracking |
| `submitWithCallback` | void | Via onFailure callback | Event-driven workflow orchestration |

### 7.5 Concrete Action Implementations

The chat-engine module provides **6 concrete action implementations** that directly implement the core `Action<ConversationState, ConversationFact, CbolStateContext>` interface. Each action encapsulates the business logic for a specific state transition.

> **Design principle**: Actions are the gatekeepers of state transitions. A transition from state A to state B only completes if the associated action executes successfully (action-first transition).

### 7.5.1 Action Overview

| Action Class | Transition | Business Logic |
|-------------|-----------|----------------|
| `CustomerConnectAction` | INITIATED → IN_PROGRESS | Create conversation record, send welcome message, initialize session, notify AI bot |
| `TransferRequestAction` | IN_PROGRESS → TRANSFERRED | Check agent availability, request routing to Genesys queue, notify customer, record transfer start |
| `TransferFailedAction` | TRANSFERRED → INITIATED | Record failure reason, cleanup transfer state, notify customer, trigger re-routing |
| `CustomerCloseAction` | IN_PROGRESS → ENDING | Mark conversation ending, send closing confirmation, release agent resources, record ending start |
| `SurveyStartAction` | IN_PROGRESS → IN_PROGRESS | Create survey record, send survey invitation, set survey timeout |
| `SurveyCompleteAction` | IN_PROGRESS → ENDING | Save survey results, calculate NPS/CSAT score, cancel survey timeout, trigger ending grace |

### 7.5.2 Action Implementation Pattern

All actions follow the same implementation pattern:

```java
@Slf4j
public class CustomerConnectAction 
    implements Action<ConversationState, ConversationFact, CbolStateContext> {

    @Override
    public void execute(StateContext<ConversationState, ConversationFact, CbolStateContext> context) {
        CbolStateContext ctx = context.getBusinessContext();
        
        // 1. Extract data from context
        String conversationId = ctx.conversation().conversationId();
        
        // 2. Execute business logic (simulated in demo, real implementation calls services)
        createConversationRecord(ctx);
        sendWelcomeMessage(ctx);
        
        // 3. Log completion
        log.info("CustomerConnectAction completed: conversationId={}", conversationId);
    }
}
```

### 7.5.3 Binding Actions to Transitions

Actions are bound to transitions in `ConversationStateMachineFactory` using the `.perform(action)` method:

```java
// Action instances (stateless, can be shared)
private static final CustomerConnectAction CUSTOMER_CONNECT_ACTION = new CustomerConnectAction();

// Bind action to transition
builder.transition()
    .from(ConversationState.INITIATED)
    .on(ConversationFact.CUSTOMER_CONNECT)
    .to(ConversationState.IN_PROGRESS)
    .perform(CUSTOMER_CONNECT_ACTION)  // Action executes before state change
    .and();
```

### 7.5.4 Action Failure Handling

When an action throws an unhandled `RuntimeException`:
1. The state does **NOT** change (source state is preserved)
2. `StateMachineException` is thrown with the cause
3. The caller can catch and handle (retry, escalate, or trigger failover)
4. Use `FailoverStateMachine` decorator for automatic failover (triggers `SYS_ACTION_FAILED` event → ERROR state)

```java
try {
    StateContext<...> result = machine.fireEvent(state, event, ctx);
} catch (StateMachineException e) {
    // Action failed, state unchanged
    log.error("Transition failed: {}", e.getMessage());
    // Option 1: Retry
    // Option 2: Trigger failover (use FailoverStateMachine)
    // Option 3: Escalate to human
}
```

## 8. ChatEngineStateMachineService

### 8.1 Main Entry Point

```java
public class ChatEngineStateMachineService {
    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> convSm;

    public ChatEngineStateMachineService() {
        this.convSm = StateMachineRegistry.getInstance().get(ConversationStateMachineFactory.MACHINE_ID);
    }

    public StateContext<ConversationState, ConversationFact, CbolStateContext> fire(
            CbolStateContext ctx, ConversationFact fact) {
        // Null validation
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(fact, "fact must not be null");

        TraceMdcHelper.set(ctx.traceContext());
        long start = System.currentTimeMillis();
        try {
            ConversationState from = ctx.conversation().state();
            StateContext<...> result = convSm.fireEvent(from, fact, ctx);

            // Audit logging
            StateTransitionRecord record = StateTransitionRecord.builder()
                .businessId(ctx.conversation().conversationId())
                .fromState(from.name())
                .toState(result.getTargetState().name())
                .fact(fact.name())
                .guardResult(result.isTransitionAccepted())
                .timestampMs(System.currentTimeMillis())
                .traceId(ctx.traceContext().traceId())
                .durationMs(System.currentTimeMillis() - start)
                .build();
            log.info("StateTransitionRecord: {}", record);
            return result;
        } finally {
            TraceMdcHelper.clear();
        }
    }
}
```

### 8.2 StateTransitionRecord (Audit)

```java
@Builder
public record StateTransitionRecord(
    String businessId,        // conversationId
    String fromState,         // source state name
    String toState,           // target state name
    String fact,              // event name
    boolean guardResult,      // whether transition was accepted
    long timestampMs,         // event timestamp
    String traceId,           // trace identifier
    long durationMs           // processing duration
) {}
```

## 9. Factory & Registry

### 9.1 ConversationStateMachineFactory

Builds and registers the conversation state machine with all 23 transition rules (T01-T23).

```java
public class ConversationStateMachineFactory {
    public static final String MACHINE_ID = "conversation";

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        StateMachineBuilder<...> builder = StateMachineBuilder.builder(MACHINE_ID);

        // 1. INITIATED -> IN_PROGRESS (customer connects)
        builder.transition().from(INITIATED).on(CUSTOMER_CONNECT).to(IN_PROGRESS).and();

        // 2. IN_PROGRESS -> TRANSFERRED (transfer requested)
        builder.transition().from(IN_PROGRESS).on(TRANSFER_REQUEST).to(TRANSFERRED).and();

        // 3. TRANSFERRED -> INITIATED (transfer failed) [v6: no rollback]
        builder.transition().from(TRANSFERRED).on(TRANSFER_FAILED).to(INITIATED).and();

        // ... (all 10 transitions)

        StateMachine<...> sm = builder.build();
        StateMachineRegistry.getInstance().register(sm);
        return sm;
    }
}
```

### 9.2 State Machine Registry

The chat-engine module uses the global singleton `StateMachineRegistry` from statemachine-core for state machine registration and lookup. This eliminates the need for a module-specific registry wrapper.

```java
// Register a state machine
StateMachineRegistry.getInstance().register(machine);

// Look up a state machine by ID
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
    StateMachineRegistry.getInstance().get(ConversationStateMachineFactory.MACHINE_ID);

// Clear all registered machines (for test isolation)
StateMachineRegistry.getInstance().clear();
```

**Design note**: Both chat-engine and agent-connector share the same global registry. Since each state machine has a unique machine ID (`conversation` vs `interaction`), there are no conflicts. The global singleton approach simplifies the API and eliminates duplicate registry holder classes.

## 10. Typical Usage Flow

### 10.1 Customer Connects

```mermaid
sequenceDiagram
    participant API as REST/WebSocket API
    participant Svc as ChatEngineStateMachineService
    participant SM as StateMachine
    participant Repo as Conversation Repository
    participant Log as Audit Log

    API->>Repo: Find or create conversation (state=INITIATED)
    Repo-->>API: conversation
    API->>API: Build CbolStateContext (with marketConfig, traceContext)
    API->>Svc: fire(ctx, CUSTOMER_CONNECT)
    Svc->>SM: fireEvent(INITIATED, CUSTOMER_CONNECT, ctx)
    SM-->>Svc: StateContext(target=IN_PROGRESS)
    Svc->>Log: info("StateTransitionRecord: INITIATED->IN_PROGRESS")
    Svc-->>API: StateContext
    API->>Repo: save(conversation with state=IN_PROGRESS)
```

### 10.2 Transfer Fails (v6 Behavior)

```mermaid
sequenceDiagram
    participant Svc as ChatEngineStateMachineService
    participant SM as StateMachine
    participant Repo as Repository

    Note over Svc: Current state = TRANSFERRED
    Svc->>SM: fireEvent(TRANSFERRED, TRANSFER_FAILED, ctx)
    Note over SM: Transition: TRANSFERRED -> INITIATED
    Note over SM: v6: Does NOT roll back to IN_PROGRESS
    SM-->>Svc: StateContext(target=INITIATED)
    Svc->>Repo: save(state=INITIATED)
    Note over Repo: Conversation returns to initial state<br/>Customer can reconnect or be re-routed
```

## 11. Error Handling

| Scenario | Exception | Handling |
|----------|-----------|----------|
| No transition for (state, event) | StateMachineException | Caller catches, returns 400 or logs warning |
| Guard condition fails | StateMachineException | Caller catches, returns 409 Conflict |
| Transition action fails | StateMachineException | Caller catches, retries or escalates |
| Entry/exit action fails | (none, best-effort) | Listener logs error, transition completes |
| Null context/event | NullPointerException | Fast-fail at service boundary |
| Async action fails | (logged only) | ActionWorker catches and logs with traceId |

## 12. Testing Strategy

| Level | Focus | Tools |
|-------|-------|-------|
| Unit | Individual transitions, guards, actions | JUnit 5 |
| Unit | Monitor timeout logic | JUnit 5 + Mock clock |
| Unit | MDC propagation in ActionWorker | JUnit 5 + CountDownLatch |
| Integration | Full conversation lifecycle | JUnit 5 + InMemoryProvider |
| Integration | Multi-market configuration | JUnit 5 + parameterized tests |

**Current Coverage:** 83% line / 71% branch (327 test cases)
