# State Machine Documentation

> Design documents and usage guides for the CBOL State Machine project.

## Document Index

| # | Document | Description |
|---|----------|-------------|
| 00 | [Architecture Overview](./00-Architecture-Overview.md) | High-level architecture, design principles, package structure, key decisions |
| 01 | [State Machine Core Design](./01-State-Machine-Core-Design.md) | Core framework: StateMachine interface, SimpleStateMachine, Transition, StateDef, Builder DSL, Listener, Registry, performance characteristics |
| 02 | [CBOL Business Layer Design](./02-CBOL-Business-Layer-Design.md) | CBOL-specific: conversation states/events, CbolStateContext, multi-market config, monitors, ActionWorker, service layer, error handling |
| 03 | [State Transition Diagrams](./03-State-Transition-Diagrams.md) | Mermaid state diagrams, transition tables, monitor flowcharts, event classification, default config |
| 04 | [Usage Guide](./04-Usage-Guide.md) | Quick start, builder DSL, configurer adapter, listeners, extended state, multi-market, monitors, async actions, error handling, testing, best practices, Spring Boot integration |
| 05 | [Advanced Features](./05-Advanced-Features.md) | Persistence & optimistic locking, build-time validation, idempotency, metrics, event sourcing, resilience/failure handling, timeout events, diagram generation, failover, decorator composition |
| 06 | [Multi-Market Design](./06-Multi-Market-Design.md) | Multi-market architecture: config control vs per-market vs hybrid, market-aware guards/actions/extensions, implementation roadmap, risk assessment |

## Quick Reference

### Core Framework
- **Stateless engine** — current state injected per call
- **Table-driven** — O(1) transition lookup via ConcurrentHashMap
- **Zero dependencies** — JDK only (Micrometer optional for metrics)
- **Spring-style config** — StateMachineConfigurerAdapter
- **Entry/exit actions** — best-effort (failures don't block transition)
- **Transition actions** — failures propagate as StateMachineException
- **Lifecycle** — start/stop with listener notifications
- **Extended state** — key-value variables shared across transitions
- **Transition kinds** — EXTERNAL and INTERNAL

### Advanced Features
- **Persistence** — StateRepository with optimistic locking (version-based), auto-retry
- **Validation** — 8 build-time rules (ERROR/WARNING levels), validates on build
- **Idempotency** — event ID deduplication with cached results
- **Metrics** — Micrometer integration (Timer/Counter), 5 metrics with tags
- **Event Sourcing** — immutable transition records, replay, state reconstruction, time-travel
- **Resilience** — 4 failure handlers (THROW/RETURN_SOURCE/FALLBACK/RETRY with backoff)
- **Timeouts** — auto-schedule on state entry, auto-cancel on exit, one-shot/repeating
- **Diagrams** — auto-generate Mermaid, PlantUML, transition tables from config

### CBOL Business Layer
- **7 states** — INITIATED, ACTIVE, TRANSFERRED, SURVEY_IN_PROGRESS, ENDING, ERROR, CLOSED
- **18 events** — lifecycle, transfer, survey, ending, system, failover
- **23 transitions** — including v6 transfer-failure-reset, survey flow, failover flow
- **3 monitors** — CustomerIdle, TransferTimeout, EndingGrace (can be replaced by timeout feature)
- **Multi-market** — per-market timeouts, feature flags, action mapping, extensions
- **TraceId** — full-chain via SLF4J MDC
- **Async actions** — bounded thread pool with MDC propagation
- **Failover** — action error → SYS_ACTION_FAILED → ERROR → retry/abort

### Build & Test
```bash
cd 08-Code/state-machine
./mvnw.cmd clean test          # Run all tests
./mvnw.cmd jacoco:report       # Generate coverage report
```

**Current stats:** 327 test cases, 83% line / 71% branch coverage

### Package Structure
```
statemachine/
├── core/           # StateMachine, SimpleStateMachine, Transition, StateContext, ExtendedState, StateDef
├── builder/        # StateMachineBuilder DSL
├── config/         # StateMachineConfigurerAdapter
├── listener/       # StateMachineListener (8 callbacks)
├── registry/       # StateMachineRegistry
├── exception/      # StateMachineException
├── persistence/    # StateRepository, InMemoryStateRepository, VersionedState, OptimisticLockException
├── validation/     # StateMachineValidator, ValidationError
├── idempotency/    # ProcessedEventStore, IdempotentStateMachineDecorator
├── metrics/        # StateMachineMetrics, MonitoredStateMachine (Micrometer optional)
├── eventsourcing/  # StateTransitionEvent, StateTransitionStore, EventSourcedStateMachine
├── resilience/     # FailureHandler, Throw/ReturnSource/Fallback/Retry handlers, ResilientStateMachine, FailoverStateMachine, FailoverContext
├── timeout/        # TimeoutConfig, StateMachineTimeoutScheduler, InMemoryTimeoutScheduler, TimeoutAwareStateMachine
├── event/          # StandardEvent, EventNormalizer, EventDispatcher (event-driven infrastructure)
└── diagram/        # StateMachineDiagramGenerator (Mermaid/PlantUML/table)
```
