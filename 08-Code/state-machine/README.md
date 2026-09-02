# state-machine — Self-Developed Lightweight State Machine Engine

> A production-ready, stateless, table-driven state machine framework with zero core dependencies.
> Multi-module Maven project: `statemachine-core` (shared engine), `chat-engine` (Conversation SM), `agent-connector` (Interaction SM).

## Design Principles

| Principle | Description |
|-----------|-------------|
| **Stateless engine** | Stores only transition rules; current state is injected by the business layer per call |
| **Table-driven** | O(1) transition lookup via ConcurrentHashMap, no reflection, no Spring container required |
| **Single responsibility** | Only handles: guard evaluation → state transition → action execution |
| **Zero core dependencies** | Core engine depends only on JDK standard library (Micrometer optional for metrics) |
| **Type-safe** | Generic Java types with compile-time checking for State/Event/Context |
| **Testable** | DSL-style configuration is living documentation, naturally unit-testable |
| **Decorator-based** | All advanced features are composable decorators — use only what you need |
| **Multi-module** | Clean separation: shared core vs. business-specific state machines |

## Tech Stack

- Java 21
- Maven 3.8+ (with Maven Wrapper, multi-module)
- JUnit 5 (testing)
- SLF4J (logging API)
- Micrometer (optional, for metrics)
- Lombok (provided, for business model records)
- JaCoCo (code coverage)
- Zero runtime core dependencies

## Modules

```
state-machine/
├── pom.xml                          # Parent POM (packaging=pom)
├── statemachine-core/               # Shared core state machine engine
│   └── com.selfdevelopment.statemachine
├── chat-engine/                     # Conversation state machine (business layer)
│   └── com.selfdevelopment.chatengine
└── agent-connector/                 # Interaction state machine (channel layer)
    └── com.selfdevelopment.agentconnector
```

### Module Responsibilities

| Module | Package | Responsibility |
|--------|---------|----------------|
| **statemachine-core** | `com.selfdevelopment.statemachine` | Generic state machine engine, Builder, ConfigurerAdapter, persistence, event sourcing, idempotency, timeout, resilience, metrics, validation, diagram generation, Connector generic interface |
| **chat-engine** | `com.selfdevelopment.chatengine` | Conversation state machine (7 states), Aibot connector, ChatHistory ODS connector, monitors, market configuration, trace context, async action worker |
| **agent-connector** | `com.selfdevelopment.agentconnector` | Interaction state machine (6 states), Genesys connector, WebSocket connector, event normalizers |

### Module Dependencies

```
chat-engine ──► statemachine-core
agent-connector ──► statemachine-core
```

`chat-engine` and `agent-connector` have **no direct dependency** on each other.

## Features

### Core Engine (statemachine-core)
- `StateMachine` interface with lifecycle (start/stop)
- `SimpleStateMachine` — thread-safe, immutable after construction
- `Transition` with guard conditions, actions, and EXTERNAL/INTERNAL kinds
- `StateDef` with entry/exit actions (best-effort)
- `ExtendedState` — key-value variables shared across transitions
- `StateMachineListener` — 8 callback hooks for auditing/monitoring
- `StateMachineBuilder` — fluent DSL
- `StateMachineConfigurerAdapter` — Spring-style configuration
- `StateMachineRegistry` — registry for named state machines

### Advanced Features (Decorators)

| Feature | Package | Key Class | Description |
|---------|---------|-----------|-------------|
| **Persistence** | `persistence` | `StateRepository` | State storage with optimistic locking (version-based), auto-retry |
| **Validation** | `validation` | `StateMachineValidator` | 8 build-time rules with ERROR/WARNING levels |
| **Idempotency** | `idempotency` | `IdempotentStateMachineDecorator` | Event ID deduplication with cached results |
| **Metrics** | `metrics` | `MonitoredStateMachine` | Micrometer integration: Timer/Counter, 5 metrics with tags |
| **Event Sourcing** | `eventsourcing` | `EventSourcedStateMachine` | Immutable transition records, replay, state reconstruction, time-travel |
| **Resilience** | `resilience` | `ResilientStateMachine` | 4 failure handlers: THROW / RETURN_SOURCE / FALLBACK / RETRY (with backoff) |
| **Timeouts** | `timeout` | `TimeoutAwareStateMachine` | Auto-schedule on state entry, auto-cancel on exit, one-shot/repeating |
| **Diagrams** | `diagram` | `StateMachineDiagramGenerator` | Auto-generate Mermaid, PlantUML, transition tables from config |

### Chat Engine (chat-engine)
- **7 conversation states**: INITIATED, ACTIVE, TRANSFERRED, SURVEY_IN_PROGRESS, ENDING, ERROR, CLOSED
- 13+ events across lifecycle, transfer, ending, survey, and system categories
- 10+ transitions including v6 transfer-failure-reset-to-INITIATED
- Multi-market configuration with per-market timeouts and feature flags (HK, SG, UK, etc.)
- TraceId full-chain propagation via SLF4J MDC
- Async actions with bounded thread pool and MDC propagation
- 3 monitors: CustomerIdle, TransferTimeout, EndingGrace
- Aibot connector, ChatHistory ODS connector
- Conversation repository with optimistic locking

### Agent Connector (agent-connector)
- **6 interaction states**: CONNECTING, CONNECTED, RECONNECTING, HELD, TRANSFERRING, DISCONNECTED
- 14 channel-level events (connection lifecycle, hold, transfer)
- Genesys connector, WebSocket connector
- Genesys event normalizer
- Agent connector state machine service with audit logging

## Project Structure

```
state-machine/
├── pom.xml                              # Parent POM
├── README.md
├── docs/
│   ├── 00-Architecture-Overview.md
│   ├── 01-State-Machine-Core-Design.md
│   ├── 02-CBOL-Business-Layer-Design.md
│   ├── 03-State-Transition-Diagrams.md
│   ├── 04-Usage-Guide.md
│   ├── 05-Advanced-Features.md
│   ├── multi-market-design/             # Multi-market design documents
│   └── zh/                              # Chinese translations
├── statemachine-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/selfdevelopment/statemachine/
│       │   ├── api/                     # Core interfaces
│       │   ├── core/                    # Core implementations
│       │   ├── builder/                 # Builder DSL
│       │   ├── config/                  # ConfigurerAdapter
│       │   ├── connector/               # Generic Connector interface
│       │   ├── event/                   # Event dispatcher/normalizer
│       │   ├── persistence/             # State repository + optimistic lock
│       │   ├── validation/              # Build-time validator
│       │   ├── idempotency/             # Idempotent decorator
│       │   ├── metrics/                 # Micrometer integration
│       │   ├── eventsourcing/           # Event sourcing
│       │   ├── resilience/              # Resilience + failure handlers
│       │   ├── timeout/                 # Timeout scheduler
│       │   ├── diagram/                 # Mermaid/PlantUML generator
│       │   └── exception/               # StateMachineException
│       └── test/java/                   # 219+ test cases
├── chat-engine/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/selfdevelopment/chatengine/
│       │   ├── enums/                   # ConversationState, ConversationFact, etc.
│       │   ├── model/                   # ConversationInstance, InteractionInstance (simplified)
│       │   ├── config/                  # Market config provider
│       │   ├── context/                 # CbolStateContext, TraceContext
│       │   ├── action/                  # Async action worker
│       │   ├── monitor/                 # CustomerIdle, Transfer, EndingGrace
│       │   ├── repository/              # Conversation repository
│       │   ├── connector/               # Aibot, ChatHistory ODS connectors
│       │   ├── ingress/                 # Event dispatcher, normalizers
│       │   ├── service/                 # ChatEngineStateMachineService
│       │   └── statemachine/            # Factory, Registry
│       └── test/java/                   # 50+ test cases
└── agent-connector/
    ├── pom.xml
    └── src/
        ├── main/java/com/selfdevelopment/agentconnector/
        │   ├── enums/                   # InteractionState, InteractionFact
        │   ├── model/                   # InteractionInstance
        │   ├── context/                 # AgentConnectorStateContext
        │   ├── connector/               # Genesys, WebSocket connectors
        │   ├── ingress/                 # Event dispatcher, Genesys normalizer
        │   ├── service/                 # AgentConnectorStateMachineService
        │   └── statemachine/            # Factory, Registry
        └── test/java/                   # Test cases
```

## Quick Start

### 1. Define State, Event, Context

```java
public enum OrderState { NEW, PAID, SHIPPED, COMPLETED, CANCELLED }
public enum OrderEvent { PAY, SHIP, COMPLETE, CANCEL }
public class OrderContext { /* business data */ }
```

### 2. Build the State Machine

```java
StateMachine<OrderState, OrderEvent, OrderContext> machine =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>builder("order-machine")
        .initialState(OrderState.NEW)
        .endStates(OrderState.COMPLETED, OrderState.CANCELLED)
        .transition()
            .from(OrderState.NEW).on(OrderEvent.PAY).to(OrderState.PAID)
            .guard(ctx -> ctx.isPaymentValid())
            .perform(ctx -> System.out.println("Payment processed"))
        .and()
        .transition()
            .from(OrderState.PAID).on(OrderEvent.SHIP).to(OrderState.SHIPPED)
        .and()
        .build(true);  // validate=true
```

### 3. Fire Events

```java
OrderContext ctx = new OrderContext();
StateContext<OrderState, OrderEvent, OrderContext> result =
    machine.fireEvent(OrderState.NEW, OrderEvent.PAY, ctx);

if (result.isTransitionAccepted()) {
    System.out.println("New state: " + result.getTargetState());
}
```

### 4. Add Advanced Features (Decorators)

```java
// Compose: timeout-aware + resilient + event-sourced + monitored + idempotent
StateMachine<OrderState, OrderEvent, OrderContext> pipeline =
    new TimeoutAwareStateMachine<>(
        new ResilientStateMachine<>(
            new EventSourcedStateMachine<>(
                new MonitoredStateMachine<>(
                    new IdempotentStateMachineDecorator<>(machine, eventStore),
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

### 5. Generate Diagrams

```java
String mermaid = StateMachineDiagramGenerator.toMermaid(machine);
String plantUml = StateMachineDiagramGenerator.toPlantUml(machine);
String table = StateMachineDiagramGenerator.toTransitionTable(machine);
```

## Performance

| Metric | Characteristic |
|--------|---------------|
| Time complexity | O(1) transition lookup (Map-based) |
| Space complexity | O(n), n = number of transition rules (typically < 50) |
| Thread safety | Lock-free reads after initialization (ConcurrentHashMap) |
| Expected throughput | 10M+ transitions/sec single-threaded |
| Memory footprint | Typical conversation state machine < 100KB |

## Build & Test

```bash
cd 08-Code/state-machine

# Build all modules
./mvnw.cmd clean install

# Compile all modules
./mvnw.cmd clean compile

# Run all tests
./mvnw.cmd clean test

# Run tests for a specific module
./mvnw.cmd clean test -pl statemachine-core
./mvnw.cmd clean test -pl chat-engine
./mvnw.cmd clean test -pl agent-connector

# Generate coverage report
./mvnw.cmd test jacoco:report

# Package all modules
./mvnw.cmd package
```

**Current stats:** 270+ test cases across all modules, BUILD SUCCESS

## Quality Gates

- [x] All tests pass (`mvn test`)
- [x] Line coverage >= 80%
- [x] Branch coverage >= 70%
- [x] No Sonar critical/blocker issues
- [x] Follows `04-Coding-Guidelines/` (all documents)
- [x] Security guidelines followed
- [x] Concurrency guidelines followed
- [x] Multi-module dependency rules enforced

## Security & Compliance

- **Zero core dependencies**: 100% self-developed, no third-party state machine library
- **Network isolation**: Engine has no external network calls
- **No reflection**: Eliminates dynamic class loading risk
- **Code review friendly**: Core code is concise and highly readable
- **Scan compliant**: Fully compatible with Snyk, Black Duck, SonarQube (zero CVE risk)
- **Optional dependencies**: Micrometer and Lombok are optional/provided only

## Reference Documents

- [Architecture Overview](./docs/00-Architecture-Overview.md)
- [Core Design](./docs/01-State-Machine-Core-Design.md)
- [CBOL Business Layer](./docs/02-CBOL-Business-Layer-Design.md)
- [State Transition Diagrams](./docs/03-State-Transition-Diagrams.md)
- [Usage Guide](./docs/04-Usage-Guide.md)
- [Advanced Features](./docs/05-Advanced-Features.md)
- [Multi-Market Design](./docs/multi-market-design/)
- [State Machine Design (Domain Knowledge)](../../01-CBOL-Domain-Knowledge/state-machine/README.md)
- [Event-Driven Orchestration Design](../../01-CBOL-Domain-Knowledge/state-machine/event-driven-orchestration-design.md)

---

*state-machine — Self-Development AI Messaging Hub — Multi-Module — 2026-09-02*
