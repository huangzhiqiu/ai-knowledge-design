# state-machine — Self-Developed Lightweight State Machine Engine

> A production-ready, stateless, table-driven state machine framework with zero core dependencies.
> Based on the design documents in [01-CBOL-Domain-Knowledge/state-machine/](../../01-CBOL-Domain-Knowledge/state-machine/).

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

## Tech Stack

- Java 17+
- Maven 3.8+ (with Maven Wrapper)
- JUnit 5 (testing)
- SLF4J (logging API)
- Micrometer (optional, for metrics)
- Lombok (provided, for business model records)
- Zero runtime core dependencies

## Features

### Core Engine
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

### CBOL Business Layer
- 5 conversation states: INITIATED, ACTIVE, TRANSFERRED, ENDING, CLOSED
- 13 events across lifecycle, transfer, ending, and system categories
- 10 transitions including v6 transfer-failure-reset-to-INITIATED
- Multi-market configuration with per-market timeouts and feature flags
- TraceId full-chain propagation via SLF4J MDC
- Async actions with bounded thread pool and MDC propagation
- 3 monitors: CustomerIdle, TransferTimeout, EndingGrace (replaceable by timeout feature)

## Project Structure

```
state-machine/
├── pom.xml
├── README.md
├── docs/
│   ├── 00-Architecture-Overview.md
│   ├── 01-State-Machine-Core-Design.md
│   ├── 02-CBOL-Business-Layer-Design.md
│   ├── 03-State-Transition-Diagrams.md
│   ├── 04-Usage-Guide.md
│   └── 05-Advanced-Features.md
└── src/
    ├── main/java/com/selfdevelopment/ai/messaging/
    │   ├── statemachine/
    │   │   ├── core/           # StateMachine, SimpleStateMachine, Transition, StateContext, etc.
    │   │   ├── builder/        # StateMachineBuilder DSL
    │   │   ├── config/         # StateMachineConfigurerAdapter
    │   │   ├── listener/       # StateMachineListener
    │   │   ├── registry/       # StateMachineRegistry
    │   │   ├── exception/      # StateMachineException
    │   │   ├── persistence/    # StateRepository, optimistic locking
    │   │   ├── validation/     # StateMachineValidator (8 rules)
    │   │   ├── idempotency/    # IdempotentStateMachineDecorator
    │   │   ├── metrics/        # MonitoredStateMachine (Micrometer)
    │   │   ├── eventsourcing/  # EventSourcedStateMachine
    │   │   ├── resilience/     # ResilientStateMachine + 4 failure handlers
    │   │   ├── timeout/        # TimeoutAwareStateMachine + scheduler
    │   │   └── diagram/        # StateMachineDiagramGenerator
    │   └── cbol/               # CBOL business layer (conversation state machine)
    └── test/java/              # 229 test cases
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

# Compile
./mvnw.cmd clean compile

# Run all tests
./mvnw.cmd clean test

# Generate coverage report
./mvnw.cmd test jacoco:report

# Package
./mvnw.cmd package
```

**Current stats:** 229 test cases, 84% line / 71% branch coverage

## Quality Gates

- [x] Line coverage >= 80%
- [x] Branch coverage >= 70%
- [x] All tests pass
- [x] No Sonar critical/blocker issues
- [x] Follows 04-Coding-Guidelines
- [x] Security guidelines followed
- [x] Concurrency guidelines followed

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
- [State Machine Design (Domain Knowledge)](../../01-CBOL-Domain-Knowledge/state-machine/README.md)
- [Event-Driven Orchestration Design](../../01-CBOL-Domain-Knowledge/state-machine/event-driven-orchestration-design.md)

---

*state-machine — Self-Development AI Messaging Hub — 2026-09-01*
