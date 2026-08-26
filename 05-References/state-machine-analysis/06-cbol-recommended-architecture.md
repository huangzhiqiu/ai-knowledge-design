# CBOL Recommended State Machine Architecture

> Synthesis of COLA, XState, squirrel-foundation, and Hypercell FSM best practices into a recommended architecture for CBOL's conversation state machine.
>
> **Last updated**: 2026-08-26
> **Philosophy**: COLA core + Hypercell runtime + squirrel observability + XState design patterns

---

## 1. Architecture Overview

```mermaid
flowchart TB
    subgraph API["API Layer"]
        WS["WebSocket Handler"]
        HTTP["HTTP API Controller"]
    end

    subgraph Manager["Orchestration Layer"]
        ConvMgr["ConversationStateManager<br/>load → lock → execute → save → unlock"]
    end

    subgraph Core["Core Engine Layer (COLA-based)"]
        Engine["ConversationStateMachine<br/>(stateless, shared singleton)<br/>- Map<State, StateNode> O(1)<br/>- fireEvent(currentState, event, ctx)<br/>- generateMermaid()"]
    end

    subgraph Runtime["Runtime Layer (Hypercell-inspired)"]
        Inst["ConversationInstance<br/>- currentState<br/>- context (extended state)<br/>- subStepProgress<br/>- snapshotStatus<br/>- retryCount, nextRetryAt<br/>- transitionHistory"]
    end

    subgraph Persistence["Persistence Layer"]
        Mongo["MongoDB<br/>ConversationInstance<br/>(optimistic locking)"]
        Redis["Redis<br/>- distributed lock<br/>- state cache<br/>- retry scheduler"]
    end

    subgraph Observability["Observability Layer (squirrel-inspired)"]
        Audit["@ConversationListener<br/>AuditListener"]
        Metrics["@ConversationListener<br/>MetricsListener"]
        Notifier["@ConversationListener<br/>WebSocketNotifier<br/>(@AsyncExecute)"]
        PerfMon["PerformanceMonitor<br/>transition latency / failure / decline"]
    end

    subgraph Advanced["Advanced Design (XState-inspired, design only)"]
        Parallel["Parallel Decomposition<br/>- ConnectionState<br/>- ConversationState<br/>- PresenceState"]
        Hierarchy["Hierarchical Grouping<br/>AGENT_HANDLING → typing/sending/awaiting"]
        History["History for Resume<br/>pause → resume returns to sub-state"]
    end

    WS --> ConvMgr
    HTTP --> ConvMgr
    ConvMgr -->|lock| Redis
    ConvMgr -->|load| Mongo
    ConvMgr -->|fireEvent| Engine
    Engine -->|produces| Inst
    ConvMgr -->|save| Mongo
    ConvMgr -->|unlock| Redis
    Engine -->|transition events| Audit
    Engine -->|transition events| Metrics
    Engine -->|transition events| Notifier
    Engine -->|transition events| PerfMon

    style Engine fill:#c8e6c9
    style ConvMgr fill:#f8bbd0
    style Inst fill:#bbdefb
    style Audit fill:#fff9c4
```

---

## 2. Core Engine (COLA-based)

### 2.1 Design

The core engine is **stateless** — it only stores transition rules, not current state. A single shared instance serves all conversations.

```mermaid
classDiagram
    class ConversationStateMachine {
        -stateMap: ConcurrentHashMap~ConversationState, StateNode~
        -machineId: String
        -failCallback: FailCallback
        +fireEvent(source: ConversationState, event: ConversationEvent, ctx: ConversationContext): ConversationState
        +verify(source: ConversationState, event: ConversationEvent): boolean
        +generateMermaid(): String
        +getAllStates(): Set~ConversationState~
        +getTransitionsFrom(state): List~Transition~
    }

    class StateNode {
        -id: ConversationState
        -transitions: Map~ConversationEvent, List~Transition~~
        +getId(): ConversationState
        +getEventTransitions(event): List~Transition~
        +addTransition(event, target, type): Transition
    }

    class Transition {
        -source: StateNode
        -target: StateNode
        -event: ConversationEvent
        -condition: Guard~ConversationContext~
        -action: Action~ConversationContext~
        -type: TransitionType
        +transit(ctx, checkCondition): StateNode
        +verify(): void
    }

    class Guard~T~ {
        <<interface>>
        +isSatisfied(ctx: T): boolean
    }

    class Action~T~ {
        <<interface>>
        +execute(from, to, event, ctx): void
    }

    ConversationStateMachine "1" --> "*" StateNode : stateMap
    StateNode "1" --> "*" Transition : transitions
    Transition "1" --> "1" Guard : condition
    Transition "1" --> "1" Action : action

    style ConversationStateMachine fill:#c8e6c9
```

### 2.2 Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Statefulness** | Stateless | Thread-safe, shareable, minimal memory — essential for millions of concurrent conversations |
| **Lookup** | ConcurrentHashMap O(1) | Fast transition lookup, no locks for reads |
| **Dependencies** | Zero | No external libs — pure JDK, easy to audit and maintain |
| **Generic types** | `<S, E, C>` | Type-safe state, event, context |
| **Builder** | Step-builder (phased) | Compile-time API correctness — can't skip from/to |
| **Fail behavior** | Fail callback + stay in current state | No exceptions for invalid transitions — graceful degradation |

---

## 3. Runtime Layer (Hypercell-inspired)

### 3.1 ConversationInstance

Each conversation has a mutable instance persisted in MongoDB.

```mermaid
classDiagram
    class ConversationInstance {
        -conversationId: String
        -currentState: ConversationState
        -context: ConversationContext
        -subStepProgress: Map~String, SubStepStatus~
        -snapshotStatus: SnapshotStatus
        -retryCount: int
        -nextRetryAt: Instant
        -transitionHistory: List~TransitionRecord~
        -version: long (optimistic locking)
        +isInitialState(): boolean
        +isTerminal(): boolean
        +getCompletedSubSteps(): Set~String~
        +getFailedSubStep(): String
    }

    class ConversationContext {
        -sessionId: String
        -userId: String
        -deviceType: DeviceType
        -aiSessionId: String
        -agentId: String
        -messageCount: int
        -retryCount: int
        -error: String
        -metadata: Map~String, Object~
    }

    class SubStepStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        FAILED
        SKIPPED
    }

    class SnapshotStatus {
        <<enumeration>>
        PENDING
        RUNNING
        AWAITING_RETRY
        COMPLETED
        FAILED
    }

    class TransitionRecord {
        -timestamp: Instant
        -fromState: ConversationState
        -toState: ConversationState
        -event: ConversationEvent
        -durationMs: long
        -success: boolean
        -error: String
    }

    ConversationInstance "1" --> "1" ConversationContext : context
    ConversationInstance "1" --> "*" TransitionRecord : history

    style ConversationInstance fill:#bbdefb
```

### 3.2 Sub-Step Checkpoint Pattern

For states that involve multiple service calls (e.g., AI processing, agent transfer), track each sub-step individually.

```mermaid
flowchart TB
    subgraph State["State: AI_PROCESSING"]
        SS1["subStep: validate-message"]
        SS2["subStep: call-ai-service"]
        SS3["subStep: save-ai-response"]
        SS4["subStep: notify-client"]
    end

    subgraph Attempt1["Attempt 1"]
        A1["validate ✅"] --> A2["call-ai ✅"] --> A3["save ❌<br/>(MongoDB timeout)"]
    end

    subgraph Snapshot["Snapshot at Failure"]
        S["completed: [validate, call-ai]<br/>failed: save<br/>status: AWAITING_RETRY"]
    end

    subgraph Attempt2["Attempt 2 (Resume)"]
        B1["skip validate"] --> B2["skip call-ai"] --> B3["save ✅<br/>(retry succeeded)"] --> B4["notify ✅"] --> B5["→ AGENT_CONNECTED"]
    end

    SS1 --> A1
    A3 -->|failure| S
    S -->|retry| Attempt2

    style S fill:#ffcdd2
    style B3 fill:#c8e6c9
```

### 3.3 ConversationStateManager

The manager encapsulates the full load → lock → execute → save → unlock cycle.

```mermaid
sequenceDiagram
    participant WS as WebSocket Handler
    participant Mgr as ConversationStateManager
    participant Redis as Redis
    participant Mongo as MongoDB
    participant Engine as StateMachine
    participant Listeners as Listeners

    WS->>Mgr: trigger(conversationId, event)
    Mgr->>Redis: SETNX conv:{id} (lock, TTL=30s)
    Redis-->>Mgr: OK
    Mgr->>Mongo: findById(conversationId)
    Mongo-->>Mgr: ConversationInstance
    Mgr->>Engine: fireEvent(instance.currentState, event, instance.context)
    Engine-->>Mgr: targetState + transitionEvents
    Mgr->>Mgr: execute sub-steps (with checkpoints + retry)
    Mgr->>Listeners: fire transition events (audit, metrics, notify)
    Mgr->>Mongo: save(instance) with optimistic locking (version++)
    Mongo-->>Mgr: OK
    Mgr->>Redis: DEL conv:{id} (unlock)
    Mgr-->>WS: result (targetState, context)
```

---

## 4. Observability Layer (squirrel-inspired)

### 4.1 Declarative Listeners

Cross-cutting concerns (audit, metrics, notifications) are implemented as declarative listeners with annotations.

```mermaid
flowchart TB
    subgraph Transition["Transition Lifecycle"]
        T1["beforeTransitionBegin"]
        T2["execute actions + sub-steps"]
        T3["afterTransitionComplete"]
        T4["afterTransitionDeclined"]
        T5["afterTransitionException"]
    end

    subgraph Listeners["Declarative Listeners"]
        direction TB
        Audit["AuditListener<br/>@OnTransitionBegin → log<br/>@OnTransitionComplete → log<br/>@OnTransitionDecline → warn"]
        Metrics["MetricsListener<br/>@OnTransitionComplete → record latency<br/>@OnTransitionDecline → increment counter<br/>@OnActionExecException → increment error"]
        Notifier["WebSocketNotifier<br/>@OnTransitionComplete → @AsyncExecute<br/>send state change to client"]
        ErrorHandler["ErrorHandlerListener<br/>@OnActionExecException → cleanup<br/>→ fire ERROR event"]
    end

    T1 --> Audit
    T1 --> Metrics
    T3 --> Audit
    T3 --> Metrics
    T3 --> Notifier
    T4 --> Audit
    T4 --> Metrics
    T5 --> ErrorHandler

    style Listeners fill:#fff9c4
```

### 4.2 Extension Methods (Intrinsic Logic)

Logic that's intrinsic to the state machine (not cross-cutting) uses the Template Method pattern via extension methods.

```java
public class ConversationStateMachine extends AbstractStateMachine<...> {
    @Override
    protected void afterTransitionCompleted(from, to, event, ctx) {
        // Intrinsic: notify WebSocket client of state change
        websocketService.notifyStateChange(ctx.getSessionId(), to);
    }

    @Override
    protected void afterTransitionDeclined(from, event, ctx) {
        // Intrinsic: return error to client (invalid operation in current state)
        websocketService.sendError(ctx.getSessionId(),
            "Invalid operation: " + event + " in state " + from);
    }

    @Override
    protected void afterTransitionCausedException(e, from, to, event, ctx) {
        // Intrinsic: cleanup + transition to ERROR state
        cleanup(ctx);
        fire(ConversationEvent.ERROR, ctx);
    }
}
```

### 4.3 Performance Monitor

Built-in performance tracking for every transition:

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `transition.count` | Total transitions per type | — |
| `transition.latency.p50` | Median transition latency | > 1ms |
| `transition.latency.p99` | P99 transition latency | > 10ms |
| `transition.failure.rate` | Failed transition rate | > 1% |
| `transition.decline.rate` | Declined (invalid) transition rate | > 5% |
| `substep.retry.count` | Sub-step retry count | > 3 per conversation |

---

## 5. Advanced Design (XState-inspired, design only)

### 5.1 Parallel Decomposition

Instead of one God state machine, maintain independent state for separate concerns.

```mermaid
stateDiagram-v2
    state "Connection State" as Conn {
        [*] --> Disconnected
        Disconnected --> Connecting : CONNECT
        Connecting --> Connected : CONNECTED
        Connecting --> Disconnected : CONNECT_FAILED
        Connected --> Reconnecting : NETWORK_ERROR
        Reconnecting --> Connected : RECONNECTED
        Reconnecting --> Disconnected : RECONNECT_FAILED
    }

    state "Conversation State" as Conv {
        [*] --> Idle
        Idle --> AI_Processing : USER_MESSAGE
        AI_Processing --> Agent_Connected : TRANSFER
        Agent_Connected --> Agent_Handling : AGENT_JOIN
        Agent_Handling --> Closed : CLOSE
        Agent_Handling --> AI_Processing : AGENT_LEAVE
    }

    state "Presence State" as Pres {
        [*] --> Online
        Online --> Away : IDLE_TIMEOUT
        Away --> Online : USER_ACTIVITY
        Online --> Offline : DISCONNECT
        Offline --> Online : CONNECT
    }

    note right of Conv
        All three states are
        independent and can
        change concurrently.
        No combinatorial explosion.
    end note
```

### 5.2 Hierarchical Grouping

Within complex states, use conceptual sub-states to reduce transition duplication.

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Agent_Handling : AGENT_JOIN

    state Agent_Handling {
        [*] --> Awaiting_Agent
        Awaiting_Agent --> Agent_Typing : AGENT_START_TYPING
        Agent_Typing --> Sending_Message : AGENT_SEND
        Sending_Message --> Awaiting_Response : MESSAGE_SENT
        Awaiting_Response --> Agent_Typing : AGENT_START_TYPING
        Awaiting_Response --> Awaiting_Agent : IDLE_TIMEOUT
    }

    Agent_Handling --> Closed : CLOSE
    Agent_Handling --> AI_Processing : AGENT_LEAVE

    note right of Agent_Handling
        CLOSE and AGENT_LEAVE work from
        ALL sub-states — defined once
        on the parent, no duplication.
    end note
```

### 5.3 History for Resume

When a conversation is paused (user disconnects) and resumed, return to the previous sub-state instead of resetting.

```mermaid
stateDiagram-v2
    [*] --> Active
    state Active {
        [*] --> AI_Processing
        AI_Processing --> Agent_Handling : TRANSFER
        state Agent_Handling {
            [*] --> Awaiting
            Awaiting --> Typing : TYPING
            Typing --> Sending : SEND
            Sending --> Awaiting : SENT
            state hist {
                [*] --> Awaiting
            }
        }
    }
    Active --> Paused : PAUSE (user disconnect)
    Paused --> Active.hist : RESUME (reconnect)

    note right of Paused
        Shallow history remembers
        the previous sub-state
        (e.g., Agent_Handling.Typing)
        and returns to it on resume.
    end note
```

---

## 6. Implementation Priority Roadmap

```mermaid
gantt
    title CBOL State Machine Implementation Roadmap
    dateFormat YYYY-MM-DD
    axisFormat %m/%d

    section P0 — Core Engine
    Stateless engine + O(1) lookup     :p01, 2026-09-01, 3d
    Fluent step-builder API            :p02, after p01, 2d
    Guards + Actions + Fail callback   :p03, after p02, 2d
    Unit tests (100% coverage)         :p04, after p03, 2d

    section P1 — Runtime & Persistence
    ConversationInstance model          :p11, after p04, 2d
    MongoDB persistence (optimistic lock) :p12, after p11, 3d
    Redis distributed lock (TTL takeover) :p13, after p12, 2d
    ConversationStateManager (load-execute-save) :p14, after p13, 3d

    section P2 — Resilience & Observability
    Sub-step checkpoints + resume       :p21, after p14, 3d
    Exponential backoff retry           :p22, after p21, 2d
    Startup recovery (recoverPendingRetries) :p23, after p22, 2d
    Declarative listeners (annotations) :p24, after p14, 3d
    Performance monitor + metrics        :p25, after p24, 2d
    Transition history / audit log       :p26, after p24, 2d

    section P3 — Advanced & Tooling
    Mermaid diagram generation (Visitor) :p31, after p26, 2d
    Parallel decomposition (connection + conversation) :p32, after p26, 3d
    Hierarchical grouping (AGENT_HANDLING sub-states) :p33, after p32, 2d
    History for pause/resume             :p34, after p33, 2d

    section P4 — Validation & Polish
    Design-time validation (unreachable states, etc.) :p41, after p34, 3d
    Integration tests (full workflow)    :p42, after p41, 3d
    Documentation + examples             :p43, after p42, 2d
    Performance benchmark + tuning       :p44, after p43, 2d
```

### Priority Summary

| Phase | Features | Source | Estimate |
|-------|----------|--------|----------|
| **P0 (Core)** | Stateless engine, O(1) lookup, fluent builder, guards, actions, fail callback | COLA | ~9 days |
| **P1 (Persistence)** | ConversationInstance, MongoDB persistence, Redis distributed lock, StateManager | Hypercell | ~10 days |
| **P2 (Resilience + Observability)** | Sub-step checkpoints, retry, startup recovery, declarative listeners, performance monitor, audit log | Hypercell + squirrel | ~14 days |
| **P3 (Advanced)** | Mermaid generation, parallel decomposition, hierarchical grouping, history | COLA + XState | ~9 days |
| **P4 (Validation)** | Design-time validation, integration tests, docs, benchmarks | leeoades/FunctionalStateMachine | ~10 days |
| **Total** | | | **~52 days** |

---

## 7. Key Design Principles

1. **Stateless core, stateful instance** — Engine is shared and stateless; each conversation's state lives in a persisted instance
2. **Checkpoint everything** — Every sub-step is tracked; failures resume from the last completed checkpoint
3. **Lock before execute** — Distributed lock prevents dual processing; TTL takeover handles crashed replicas
4. **Observe everything** — Declarative listeners for audit, metrics, notifications; transition history for debugging
5. **Retry with backoff** — Transient failures get exponential backoff retry; non-retryable errors fail fast
6. **Recover from restart** — In-flight retries are rescheduled after service restart
7. **Separate concerns** — Intrinsic logic in extension methods; cross-cutting concerns in declarative listeners
8. **Design for scale** — Parallel decomposition avoids combinatorial state explosion; hierarchical grouping reduces transition duplication

---

*CBOL Recommended Architecture — v1.0.0 — 2026-08-26*
*Synthesis of COLA (core) + Hypercell (runtime) + squirrel (observability) + XState (design patterns)*
