# Self-Developed Lightweight State Machine

> Proprietary, lightweight, stateless state machine engine for AI Messaging Hub (HSBC).

## Why Self-Developed?

Mature open-source libraries exist (Spring StateMachine, COLA StateMachine), but HSBC's strict open-source compliance and security policies require a self-developed solution:
- Zero external dependencies → 100% compliance with Snyk, Black Duck, SonarQube
- No third-party CVE risk
- Full control over security and performance

## Design Goals & Principles

| Principle | Description |
|-----------|-------------|
| **Stateless Engine** | Stores only transition rules (the "graph"). Current state is always injected by business layer |
| **Table-Driven** | Map-based lookups for O(1) transitions, no reflection, no Spring container |
| **Single Responsibility** | Only handles: validate condition → transition state → execute action. No DB, cache, or transaction logic |
| **Zero External Dependencies** | Core engine relies solely on standard JDK libraries |
| **Type Safety** | Generic Java types for compile-time checking of States, Events, Contexts |
| **Testability** | DSL-style configuration serves as living documentation, inherently unit-testable |

## Inspiration from COLA StateMachine

### Borrowed Principles
- **Builder Pattern**: Fluent API for intuitive configuration
- **External Transition Model**: Source State → Event → Target State → Action
- **Guard Conditions**: Boolean predicates that must pass for transition
- **Action/Perform Hooks**: Executable logic triggered on successful transition
- **Pure POJO**: Completely decoupled from any framework

### Intentionally Excluded
- No Spring Framework coupling (no ApplicationContext, no Spring Beans)
- No built-in persistence (Redis/DB delegated to business layer)
- No visual monitoring (external observability can be added later)

## Comparison with Open-Source Alternatives

| Feature | Self-Developed Engine | COLA StateMachine | Spring StateMachine |
|---------|----------------------|-------------------|---------------------|
| External Dependencies | None | Alibaba COLA Framework | Spring Framework |
| State Persistence | External (Redis) | External | Built-in |
| Compliance Risk | Zero | Medium | Medium |
| Learning Curve | Low | Low | High |
| Performance | High (O(1)) | High (O(1)) | Moderate |
| Reflection Usage | None | Minimal | Heavy |

## Core Components

| Component | Description |
|-----------|-------------|
| `SimpleStateMachine<S, E, C>` | Main engine, parameterized by State (S), Event (E), Context (C) |
| `Transition<S, E, C>` | Internal transition rule: target state, guard condition, action |
| `Condition<C>` | Functional interface for guard predicates (returns boolean) |
| `Action<C>` | Functional interface for transition actions (void execution) |
| `StateMachineRegistry` | Optional registry for managing multiple state machine instances |

## Performance Characteristics

| Metric | Characteristic |
|--------|---------------|
| Time Complexity | O(1) transition lookup (Map-based) |
| Space Complexity | O(n) where n = transition rules (typically < 50) |
| Thread Safety | Lock-free reads after initialization (ConcurrentHashMap) |
| Expected Throughput | 10M+ transitions/sec on single thread |
| Memory Footprint | < 100KB for typical dialog state machine |

## Security & Compliance

- **Zero Dependencies**: 100% self-developed, no third-party state machine libraries
- **Network Isolation**: No external network calls from engine
- **No Reflection**: Eliminates dynamic class loading risks
- **Code Review Friendly**: Core code < 200 lines, highly readable
- **Scanning Compliance**: Fully compatible with HSBC's Snyk, Black Duck, SonarQube (zero CVE risk)

## Maven Dependency

```xml
<!-- Internal only, no external state machine library -->
<dependency>
    <groupId>com.hsbc.ai.messaging</groupId>
    <artifactId>hub-statemachine-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Reference
COLA StateMachine: https://github.com/alibaba/COLA (design inspiration only; no code or binaries imported)
