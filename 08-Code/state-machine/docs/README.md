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

## Quick Reference

### Core Framework
- **Stateless engine** — current state injected per call
- **Table-driven** — O(1) transition lookup via ConcurrentHashMap
- **Zero dependencies** — JDK only
- **Spring-style config** — StateMachineConfigurerAdapter
- **Entry/exit actions** — best-effort (failures don't block transition)
- **Transition actions** — failures propagate as StateMachineException

### CBOL Business Layer
- **5 states** — INITIATED, ACTIVE, TRANSFERRED, ENDING, CLOSED
- **13 events** — lifecycle, transfer, ending, system
- **10 transitions** — including v6 transfer-failure-reset-to-INITIATED
- **3 monitors** — CustomerIdle, TransferTimeout, EndingGrace
- **Multi-market** — per-market timeouts and feature flags
- **TraceId** — full-chain via SLF4J MDC
- **Async actions** — bounded thread pool with MDC propagation

### Build & Test
```bash
cd 08-Code/state-machine
./mvnw.cmd clean test          # Run all tests
./mvnw.cmd jacoco:report       # Generate coverage report
```

**Current coverage:** 87% line / 71% branch (112 test cases)
