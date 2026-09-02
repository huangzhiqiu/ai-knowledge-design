# State Machine Documentation

> Design documents and usage guides for the Multi-Module State Machine project.

## Document Index

| # | Document | Description |
|---|----------|-------------|
| 00 | [Architecture Overview](./00-Architecture-Overview.md) | High-level architecture, multi-module structure, design principles, package structure, key decisions |
| 01 | [State Machine Core Design](./01-State-Machine-Core-Design.md) | Core framework: StateMachine interface, SimpleStateMachine, Transition, StateDef, Builder DSL, Listener, Registry, performance characteristics |
| 02 | [Business Layer Design](./02-CBOL-Business-Layer-Design.md) | chat-engine (Conversation SM) + agent-connector (Interaction SM): states/events, contexts, multi-market config, monitors, services, connectors |
| 03 | [State Transition Diagrams](./03-State-Transition-Diagrams.md) | Mermaid state diagrams, transition tables, monitor flowcharts, event classification, default config |
| 04 | [Usage Guide](./04-Usage-Guide.md) | Quick start, builder DSL, configurer adapter, listeners, extended state, multi-market, monitors, async actions, error handling, testing, best practices, Spring Boot integration, Demo usage |
| 05 | [Advanced Features](./05-Advanced-Features.md) | Persistence & optimistic locking, build-time validation, idempotency, metrics, event sourcing, resilience/failure handling, timeout events, diagram generation, failover, decorator composition |
| 06 | [Multi-Market Design](./06-Multi-Market-Design.md) | Multi-market architecture: config control vs per-market vs hybrid, market-aware guards/actions/extensions, implementation roadmap, risk assessment |
| 07 | [Multi-Market Best Practices](./07-Multi-Market-Best-Practices/README.md) | 8 detailed best practice guides: three-layer config inheritance, market diff visualization, routing & isolation, config-as-code GitOps, canary release, circuit breaker & degradation, schema validation, test matrix |

## Changelog

### v2.4 (2026-09-03)
- **Registry refactoring (Scheme A)**:
  - Added `getInstance()` static method to `StateMachineRegistry` in statemachine-core for global singleton access
  - Removed duplicate business-layer registry classes:
    * Deleted `CbolStateMachineRegistry` (chat-engine)
    * Deleted `AgentConnectorStateMachineRegistry` (agent-connector)
  - Updated all business code to use `StateMachineRegistry.getInstance()` directly
  - Preserved auto-initialization behavior in `AgentConnectorStateMachineService` via `getOrCreateStateMachine()` method
  - Updated 5 test files to use `StateMachineRegistry.getInstance().clear()`
  - Eliminated ~100 lines of duplicate code
  - Both business modules now share the same global registry (machineId ensures no conflicts)

### v2.3 (2026-09-03)
- **Code quality fixes (P0)**:
  - Fixed parameter naming in 3 Monitor classes: `ChatEngineStateMachineService` → `chatEngineStateMachineService`
  - Fixed outdated Javadoc in CustomerIdleMonitor: `ACTIVE` → `IN_PROGRESS`
  - Fixed fully qualified class name usage in ChatEngineStateMachineService (added import for StateMachineException)
- **ActionWorker improvements (P1)**:
  - Added `submitWithResult()` method returning `CompletableFuture<Void>` for result tracking
  - Added `submitWithCallback()` method with success/failure callbacks
  - Added `getExecutor()` method for advanced configuration and monitoring
  - Refactored original `submit()` to use `submitWithResult()` internally (backward compatible)
  - Added 15 new ActionWorker test cases
- **Code duplication elimination**:
  - Extracted `AbstractEventDispatcher<C>` to statemachine-core
  - Refactored ChatEngineEventDispatcher and AgentConnectorEventDispatcher to extend AbstractEventDispatcher
  - Eliminated ~30% duplicate code in event dispatchers

### v2.2 (2026-09-03)
- **State rename**: `ACTIVE` → `IN_PROGRESS`
- **Removed state**: `SURVEY_IN_PROGRESS` — survey is now an internal sub-phase within `IN_PROGRESS`
- **New state**: `NEW` — initial state, conversation record created but not initialized
- **New transition**: `NEW → INITIATED` via `CONVERSATION_INITIATED` event
- **New action**: `ConversationInitAction` — validates config, allocates resources, sets up routing
- **Survey redesign**: `SURVEY_START` is now an internal transition (`IN_PROGRESS → IN_PROGRESS`); `SURVEY_COMPLETE` transitions directly to `ENDING`
- **Architecture boundary**: Removed `InteractionInstance` from chat-engine — Conversation and Interaction are independent state machines

### v2.1 (2026-09-03)
- Added Action-First Transition design principle
- Added 6 concrete action implementations
- Updated all documentation to version 2.1

## Multi-Module Structure

```
state-machine/
├── pom.xml                          # Parent POM (packaging=pom)
├── statemachine-core/               # Shared core state machine engine
│   └── com.selfdevelopment.statemachine
├── chat-engine/                     # Conversation state machine (business layer)
│   └── com.selfdevelopment.chatengine
├── agent-connector/                 # Interaction state machine (channel layer)
│   └── com.selfdevelopment.agentconnector
└── docs/                            # This documentation
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

## Quick Reference

### Core Framework (statemachine-core)
- **Stateless engine** — current state injected per call
- **Table-driven** — O(1) transition lookup via ConcurrentHashMap
- **Zero dependencies** — JDK only (Micrometer optional for metrics)
- **Spring-style config** — StateMachineConfigurerAdapter
- **Entry/exit actions** — best-effort (failures don't block transition)
- **Transition actions** — failures propagate as StateMachineException
- **Lifecycle** — start/stop with listener notifications
- **Extended state** — key-value variables shared across transitions
- **Transition kinds** — EXTERNAL and INTERNAL

### Advanced Features (statemachine-core)
- **Persistence** — StateRepository with optimistic locking (version-based), auto-retry
- **Validation** — 8 build-time rules (ERROR/WARNING levels), validates on build
- **Idempotency** — event ID deduplication with cached results
- **Metrics** — Micrometer integration (Timer/Counter), 5 metrics with tags
- **Event Sourcing** — immutable transition records, replay, state reconstruction, time-travel
- **Resilience** — 4 failure handlers (THROW/RETURN_SOURCE/FALLBACK/RETRY with backoff)
- **Timeouts** — auto-schedule on state entry, auto-cancel on exit, one-shot/repeating
- **Diagrams** — auto-generate Mermaid, PlantUML, transition tables from config

### Chat Engine (chat-engine)
- **7 conversation states** — INITIATED, IN_PROGRESS, TRANSFERRED, IN_PROGRESS, ENDING, ERROR, CLOSED
- **18 events** — lifecycle, transfer, survey, ending, system, failover
- **23 transitions** — including v6 transfer-failure-reset, survey flow, failover flow
- **3 monitors** — CustomerIdle, TransferTimeout, EndingGrace
- **Multi-market** — per-market timeouts, feature flags, action mapping, extensions
- **TraceId** — full-chain via SLF4J MDC
- **Async actions** — bounded thread pool with MDC propagation
- **Failover** — action error → SYS_ACTION_FAILED → ERROR → retry/abort
- **Connectors** — AibotConnector, ChatHistoryOdsConnector

### Agent Connector (agent-connector)
- **6 interaction states** — CONNECTING, CONNECTED, RECONNECTING, HELD, TRANSFERRING, DISCONNECTED
- **14 events** — connection lifecycle, hold, transfer
- **Connectors** — GenesysConnector, CbolWebsocketConnector
- **Event normalizers** — GenesysEventNormalizer

### Demo Code
- **ChatEngineDemo** — 4 demos: basic flow, survey flow, multi-market config, transfer failure
- **AgentConnectorDemo** — 6 demos: connection, hold, reconnection, reconnection exhausted, transfer, connection failure

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

# Run demos
./mvnw.cmd exec:java -pl chat-engine -Dexec.mainClass="com.selfdevelopment.chatengine.demo.ChatEngineDemo"
./mvnw.cmd exec:java -pl agent-connector -Dexec.mainClass="com.selfdevelopment.agentconnector.demo.AgentConnectorDemo"
```

**Current stats:** 270+ test cases across all modules, BUILD SUCCESS

## Package Structure (statemachine-core)

```
com.selfdevelopment.statemachine/
├── api/              # Core interfaces (StateMachine, Action, Guard, Listener, Registry)
├── core/             # Core implementations (SimpleStateMachine, Transition, StateDef, StateContext, ExtendedState)
├── builder/          # StateMachineBuilder DSL
├── config/           # StateMachineConfigurerAdapter (Spring-style)
├── connector/        # Generic Connector interface
├── event/            # StandardEvent, EventNormalizer, EventDispatcher
├── persistence/      # StateRepository, InMemoryStateRepository, VersionedState, OptimisticLockException
├── validation/       # StateMachineValidator, ValidationError
├── idempotency/      # ProcessedEventStore, IdempotentStateMachineDecorator
├── metrics/          # StateMachineMetrics, MonitoredStateMachine (Micrometer optional)
├── eventsourcing/    # StateTransitionEvent, StateTransitionStore, EventSourcedStateMachine
├── resilience/       # FailureHandler, Throw/ReturnSource/Fallback/Retry handlers, ResilientStateMachine, FailoverStateMachine
├── timeout/          # TimeoutConfig, StateMachineTimeoutScheduler, TimeoutAwareStateMachine
├── diagram/          # StateMachineDiagramGenerator (Mermaid/PlantUML/table)
└── exception/        # StateMachineException
```

## Chinese Documentation

中文文档位于 [`zh/`](./zh/) 目录，包含所有文档的中文翻译版本。

---

*Last updated: 2026-09-02*
