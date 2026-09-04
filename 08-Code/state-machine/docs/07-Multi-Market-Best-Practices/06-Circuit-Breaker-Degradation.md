# 06. Circuit Breaker & Degradation Strategy

> Version: 1.0 | Last Updated: 2026-09-01
> Priority: P2 | Estimated Effort: 2-3 days

## 1. Problem Statement

### 1.1 Current Issue
When the state machine or its dependencies (AIBot, Genesys, WebSocket, database) experience issues, the system has no structured way to degrade gracefully. It either:
- Continues processing and times out (bad user experience, resource exhaustion)
- Crashes entirely (cascading failure)
- Returns generic errors without context

There's no concept of "the system is degraded but still functional" or "this market is down but others are fine."

### 1.2 Scenarios
- **AIBot API slow**: AI response takes 30s instead of 3s, threads block, pool depletes
- **Genesys outage**: Transfer requests all fail, system keeps retrying, wasting resources
- **Database connection pool exhausted**: State persistence fails, conversations get stuck
- **Market-specific issue**: HK's Genesys org is down, but SG/UK work fine
- **Global traffic spike**: System at 90% capacity, need to shed load gracefully

### 1.3 Goals
- Detect failures early and fail fast (don't wait for timeout)
- Prevent cascading failures (one component's failure doesn't crash the whole system)
- Provide graceful degradation (reduce functionality rather than crash)
- Per-market isolation (one market's degradation doesn't affect others)
- Automatic recovery (when dependency recovers, circuit closes automatically)

---

## 2. Design Overview

### 2.1 Degradation Levels

```
┌─────────────────────────────────────────────────────────────────┐
│                    Degradation Levels (L0 → L4)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  L0 NORMAL                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Full functionality: all events, all actions, all connectors│  │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (dependency slow / error rate ↑)  │
│  L1 WEAK_DEGRADATION                                               │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Skip non-critical actions: audit logging, notifications,  │    │
│  │ metrics collection. Core transitions and connectors work.  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (error rate ↑ more / latency ↑)   │
│  L2 MEDIUM_DEGRADATION                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Only critical events: CUSTOMER_CONNECT, CUSTOMER_CLOSE,  │    │
│  │ SURVEY_COMPLETE, SYS_*. Non-critical events queued/dropped.│  │
│  │ Connectors: use cached/fallback responses.                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (dependency down / resource critical)│
│  L3 STRONG_DEGRADATION                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Reject new conversations. Existing conversations: only    │    │
│  │ CUSTOMER_CLOSE allowed. All other events rejected with 503.│  │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                    │
│                              ▼ (state machine itself failing)      │
│  L4 CIRCUIT_OPEN                                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ All events fail fast with 503. No state machine execution. │  │
│  │ Static error response returned.                             │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Circuit Breaker per Dependency

```
                    ┌──────────────────────────────┐
                    │     Event Processing          │
                    └──────────┬───────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
┌─────────▼─────────┐ ┌──────▼──────────┐ ┌──────▼──────────┐
│ AIBot Circuit      │ │ Genesys Circuit  │ │ DB Circuit       │
│ (per market)       │ │ (per market)     │ │ (global)         │
│                    │ │                   │ │                  │
│ States:            │ │ States:           │ │ States:          │
│  • CLOSED (normal) │ │  • CLOSED         │ │  • CLOSED        │
│  • OPEN (fail fast)│ │  • OPEN           │ │  • OPEN          │
│  • HALF_OPEN (test)│ │  • HALF_OPEN      │ │  • HALF_OPEN     │
└─────────┬──────────┘ └──────┬──────────┘ └──────┬──────────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
                    ┌──────────▼───────────────────┐
                    │     Degradation Controller     │
                    │  (combines circuit states →    │
                    │   overall degradation level)    │
                    └────────────────────────────────┘
```

---

## 3. Detailed Design

### 3.1 Circuit Breaker Implementation

```java
public class MarketCircuitBreaker {

    private final String market;
    private final String dependency;  // "aibot", "genesys", "db", "websocket"
    private final CircuitBreakerConfig config;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile long openTime;

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public MarketCircuitBreaker(String market, String dependency, CircuitBreakerConfig config) {
        this.market = market;
        this.dependency = dependency;
        this.config = config;
    }

    /**
     * Try to acquire permission to execute.
     * Returns true if execution is allowed, false if circuit is open.
     */
    public boolean tryAcquire() {
        State current = state.get();

        if (current == State.CLOSED) return true;

        if (current == State.OPEN) {
            // Check if cooling period has elapsed
            if (System.currentTimeMillis() - openTime >= config.getOpenDurationMs()) {
                // Transition to HALF_OPEN (allow one probe request)
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker {}/{} transitioning OPEN → HALF_OPEN", market, dependency);
                    return true;  // Allow the probe
                }
            }
            return false;  // Still open, fail fast
        }

        // HALF_OPEN: only allow a limited number of probe requests
        return successCount.get() < config.getHalfOpenMaxProbes();
    }

    /**
     * Record a successful execution.
     */
    public void recordSuccess() {
        State current = state.get();

        if (current == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= config.getHalfOpenSuccessThreshold()) {
                // Close the circuit
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    log.info("Circuit breaker {}/{} recovered, HALF_OPEN → CLOSED", market, dependency);
                    resetCounters();
                }
            }
        } else {
            // CLOSED: reset failure count on success (sliding window)
            failureCount.set(0);
        }
    }

    /**
     * Record a failed execution.
     */
    public void recordFailure(Exception e) {
        State current = state.get();

        if (current == State.HALF_OPEN) {
            // Any failure in HALF_OPEN → back to OPEN
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openTime = System.currentTimeMillis();
                log.warn("Circuit breaker {}/{} probe failed, HALF_OPEN → OPEN: {}",
                        market, dependency, e.getMessage());
            }
            return;
        }

        if (current == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= config.getFailureThreshold()) {
                // Open the circuit
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openTime = System.currentTimeMillis();
                    log.warn("Circuit breaker {}/{} opened after {} failures: {}",
                            market, dependency, failures, e.getMessage());
                }
            }
        }
    }

    public State getState() { return state.get(); }

    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
    }
}
```

### 3.2 Circuit Breaker Config

```java
public record CircuitBreakerConfig(
    int failureThreshold,           // e.g., 5 (5 failures → open)
    long openDurationMs,             // e.g., 30000 (30s open)
    int halfOpenMaxProbes,           // e.g., 3 (max 3 probe requests)
    int halfOpenSuccessThreshold,    // e.g., 2 (2 successes → close)
    long slidingWindowMs              // e.g., 60000 (1 minute window for failure counting)
) {
    public static CircuitBreakerConfig defaults() {
        return new CircuitBreakerConfig(5, 30000, 3, 2, 60000);
    }

    public static CircuitBreakerConfig aggressive() {
        return new CircuitBreakerConfig(3, 60000, 2, 1, 30000);
    }

    public static CircuitBreakerConfig lenient() {
        return new CircuitBreakerConfig(10, 15000, 5, 3, 120000);
    }
}
```

### 3.3 Degradation Controller

```java
public class DegradationController {

    private final Map<String, Map<String, MarketCircuitBreaker>> circuitBreakers = new ConcurrentHashMap<>();
    private final DegradationConfig config;

    public enum DegradationLevel {
        L0_NORMAL, L1_WEAK, L2_MEDIUM, L3_STRONG, L4_CIRCUIT_OPEN
    }

    /**
     * Determines the overall degradation level for a market by combining
     * all dependency circuit breaker states.
     */
    public DegradationLevel getLevel(String market) {
        Map<String, MarketCircuitBreaker> breakers = circuitBreakers.get(market);
        if (breakers == null) return DegradationLevel.L0_NORMAL;

        long openCount = breakers.values().stream()
                .filter(b -> b.getState() == MarketCircuitBreaker.State.OPEN)
                .count();
        long halfOpenCount = breakers.values().stream()
                .filter(b -> b.getState() == MarketCircuitBreaker.State.HALF_OPEN)
                .count();
        int totalDeps = breakers.size();

        // L4: All critical dependencies open
        if (openCount >= config.getCriticalDependencyCount()) {
            return DegradationLevel.L4_CIRCUIT_OPEN;
        }

        // L3: Majority of dependencies open
        if (openCount > totalDeps / 2) {
            return DegradationLevel.L3_STRONG;
        }

        // L2: At least one critical dependency open
        if (openCount > 0) {
            return DegradationLevel.L2_MEDIUM;
        }

        // L1: At least one dependency half-open (degraded)
        if (halfOpenCount > 0) {
            return DegradationLevel.L1_WEAK;
        }

        return DegradationLevel.L0_NORMAL;
    }

    /**
     * Checks if an event should be processed at the current degradation level.
     */
    public boolean isEventAllowed(String market, ConversationFact event) {
        DegradationLevel level = getLevel(market);

        return switch (level) {
            case L0_NORMAL -> true;  // All events allowed
            case L1_WEAK -> !isNonCriticalAction(event);
            case L2_MEDIUM -> isCriticalEvent(event);
            case L3_STRONG -> event == ConversationFact.CUSTOMER_CLOSE;
            case L4_CIRCUIT_OPEN -> false;  // No events allowed
        };
    }

    private boolean isCriticalEvent(ConversationFact event) {
        return switch (event) {
            case CUSTOMER_CONNECT, CUSTOMER_CLOSE, SURVEY_COMPLETE,
                 SYS_ACTION_FAILED, SYS_RETRY, SYS_ABORT -> true;
            default -> false;
        };
    }

    private boolean isNonCriticalAction(ConversationFact event) {
        return switch (event) {
            // Events that trigger non-critical actions (audit, notifications)
            case TRANSFER_CONNECTED, SURVEY_START -> true;
            default -> false;
        };
    }
}
```

### 3.4 Connector with Circuit Breaker

```java
public class CircuitBreakerAwareConnector {

    private final AibotConnector delegate;
    private final MarketCircuitBreaker circuitBreaker;

    public Optional<Connector.ConnectorResponse> sendMessage(String market,
                                                                String conversationId,
                                                                String message,
                                                                String userId) {
        // 1. Check circuit breaker
        if (!circuitBreaker.tryAcquire()) {
            log.warn("AIBot circuit breaker open for market {}, returning cached response", market);
            return Optional.of(cachedResponse(conversationId));
        }

        // 2. Execute with timeout
        try {
            Optional<Connector.ConnectorResponse> response =
                    delegate.sendMessage(conversationId, message, userId);
            circuitBreaker.recordSuccess();
            return response;
        } catch (Exception e) {
            circuitBreaker.recordFailure(e);
            throw e;
        }
    }

    private Connector.ConnectorResponse cachedResponse(String conversationId) {
        return Connector.ConnectorResponse.success(Map.of(
                "conversationId", conversationId,
                "cached", true,
                "message", "Service temporarily degraded, using cached response"
        ));
    }
}
```

### 3.5 Event Processing with Degradation

```java
public class DegradationAwareEventProcessor {

    private final DegradationController degradationController;
    private final StateMachineProcessor processor;

    public ConversationState process(String market, Event event, Conversation conversation) {
        // 1. Check degradation level
        DegradationController.DegradationLevel level = degradationController.getLevel(market);

        // 2. Check if event is allowed
        ConversationFact fact = mapToFact(event);
        if (!degradationController.isEventAllowed(market, fact)) {
            return rejectedResponse(level, fact, market);
        }

        // 3. Apply degradation-specific behavior
        return switch (level) {
            case L0_NORMAL -> processor.process(event, conversation);
            case L1_WEAK -> processWithSkippedActions(event, conversation);
            case L2_MEDIUM -> processCriticalOnly(event, conversation);
            case L3_STRONG -> processCloseOnly(event, conversation);
            case L4_CIRCUIT_OPEN -> rejectedResponse(level, fact, market);
        };
    }

    private ConversationState processWithSkippedActions(Event event, Conversation conversation) {
        // Skip non-critical actions (audit, notifications) but still do state transition
        return processor.process(event, conversation, ProcessingOptions.skipNonCriticalActions());
    }
}
```

### 3.6 Degradation-Aware State Machine

For L1 (weak degradation), the state machine can skip non-critical actions:

```java
// Note: COLA StateMachine's fireEvent returns ConversationState, not StateContext
// Degradation awareness can be implemented at the business layer (Service layer),
// rather than wrapping the StateMachine interface
public class DegradationAwareStateMachineService {

    private final StateMachine<ConversationState, ConversationFact, CbolStateContext> delegate;
    private final DegradationController degradationController;
    private final String market;

    public ConversationState fireEvent(ConversationState source, ConversationFact event, CbolStateContext ctx) {
        DegradationController.DegradationLevel level = degradationController.getLevel(market);

        if (level == DegradationController.DegradationLevel.L1_WEAK) {
            // L1 weak degradation: skip non-critical actions, but still do state transition
            // This can be implemented by setting a flag in CbolStateContext
            CbolStateContext degradedCtx = CbolStateContext.builder()
                    .conversation(ctx.conversation())
                    .marketConfig(ctx.marketConfig())
                    .traceContext(ctx.traceContext())
                    .skipNonCriticalActions(true)  // custom flag
                    .build();
            return delegate.fireEvent(source, event, degradedCtx);
        }

        return delegate.fireEvent(source, event, ctx);
    }
}
```

---

## 4. Monitoring & Alerting

### 4.1 Circuit Breaker Metrics

```
# HELP circuit_breaker_state Current state of circuit breaker (0=CLOSED, 1=HALF_OPEN, 2=OPEN)
# TYPE circuit_breaker_state gauge
circuit_breaker_state{market="HK",dependency="aibot"} 0
circuit_breaker_state{market="HK",dependency="genesys"} 2
circuit_breaker_state{market="SG",dependency="aibot"} 0

# HELP circuit_breaker_failures_total Total failures recorded
# TYPE circuit_breaker_failures_total counter
circuit_breaker_failures_total{market="HK",dependency="genesys"} 42

# HELP degradation_level Current degradation level (0-4)
# TYPE degradation_level gauge
degradation_level{market="HK"} 2
degradation_level{market="SG"} 0
degradation_level{market="UK"} 1
```

### 4.2 Alert Rules

| Alert | Condition | Severity |
|-------|-----------|----------|
| Circuit opened | circuit_breaker_state == 2 for > 1 min | Warning |
| Market degraded | degradation_level >= 2 for > 5 min | Critical |
| Multiple circuits open | > 2 dependencies open in same market | Critical |
| Global degradation | > 50% markets at L2+ | Critical |
| Circuit flapping | state changes > 5 times in 10 min | Warning |
| Recovery | circuit transitions OPEN → CLOSED | Info |

---

## 5. Implementation Roadmap

### Phase 1: Circuit Breaker Core (1 day)
- [ ] Implement `MarketCircuitBreaker` with CLOSED/OPEN/HALF_OPEN states
- [ ] Implement `CircuitBreakerConfig` with defaults/aggressive/lenient presets
- [ ] Implement per-market per-dependency circuit breaker registry
- [ ] Unit tests for state transitions, threshold, recovery

### Phase 2: Connector Integration (1 day)
- [ ] Wrap AIBot connector with circuit breaker
- [ ] Wrap Genesys connector with circuit breaker
- [ ] Implement cached/fallback responses for open circuits
- [ ] Implement timeout enforcement
- [ ] Integration tests for circuit breaker behavior

### Phase 3: Degradation Controller (1 day)
- [ ] Implement `DegradationController` with 5 levels
- [ ] Implement event allow/deny per level
- [ ] Implement degradation-aware event processor
- [ ] Implement L1 action skipping
- [ ] Unit tests for each degradation level

### Phase 4: Monitoring & Operations (0.5 day)
- [ ] Implement circuit breaker metrics (Micrometer)
- [ ] Implement degradation level metrics
- [ ] Implement health endpoint with circuit breaker status
- [ ] Implement alert rules
- [ ] Implement manual circuit breaker control (admin API: force open/close/reset)

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Circuit breaker too aggressive (opens on transient errors) | Medium | Medium | Configurable threshold; sliding window; minimum failure count; lenient defaults for non-critical deps |
| Circuit breaker too lenient (doesn't open when it should) | Medium | Medium | Monitor time-to-open metric; alert on high error rate without circuit opening; tune thresholds in staging |
| Cached responses become stale | Medium | Low | Short TTL on cached responses; mark cached responses clearly; only cache idempotent/read-only operations |
| Degradation level flapping (oscillates between levels) | Low | Medium | Hysteresis: require sustained state change (e.g., 3 consecutive checks) before changing level |
| L1 action skipping causes data loss (audit logs missing) | Low | Medium | Audit logs are best-effort; critical audit (financial) is never skipped; L1 only skips non-critical actions |
| Circuit breaker state lost on restart | Low | Low | Circuit breaker is in-memory; resets to CLOSED on restart (safe default); persistent state optional |
| Manual circuit control abused (someone forces open and forgets) | Low | Medium | Manual overrides have TTL (auto-expire after 1 hour); audit log of all manual operations; alert on forced-open circuits |

---

## 7. Success Criteria

- [ ] Circuit breaker opens within 30 seconds of dependency failure threshold breach
- [ ] Circuit breaker closes within 60 seconds of dependency recovery
- [ ] Open circuit returns response in < 100ms (fail fast, not wait for timeout)
- [ ] One market's open circuit does not affect other markets
- [ ] Degradation level is visible in real-time (health endpoint + metrics)
- [ ] L1 degradation skips non-critical actions but completes all state transitions
- [ ] L4 degradation rejects all events with clear 503 response
- [ ] Manual circuit breaker control (open/close/reset) available via admin API
- [ ] Circuit breaker state changes are logged and alerted
- [ ] Recovery from degradation is automatic (no manual intervention needed)
