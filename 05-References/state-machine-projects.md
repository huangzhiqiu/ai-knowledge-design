# State Machine Projects & References

> Curated collection of state machine frameworks, design patterns, and best practices from GitHub and the community. Focused on Java ecosystem, lightweight design, and IM/chat domain applicability.
>
> **Last updated**: 2026-08-26

---

## 1. Java State Machine Frameworks

### 1.1 COLA State Machine (Alibaba)

| Field | Value |
|-------|-------|
| **Repository** | [alibaba/COLA](https://github.com/alibaba/COLA) (component: `cola-component-statemachine`) |
| **Stars** | ~10k+ (COLA framework) |
| **License** | GPL-2.0 |
| **Key Design** | Stateless, table-driven (ConcurrentHashMap O(1) lookup), fluent API, zero external dependencies |
| **Maven** | `com.alibaba.cola:cola-component-statemachine:4.x` |

**Why it matters to CBOL**: This is the philosophical reference for our custom lightweight state machine. COLA's stateless design — where the engine only stores transition rules and the current state is injected by the business layer — is exactly the pattern we adopted. The table-driven approach with O(1) transition lookup is ideal for high-concurrency IM systems.

**Core concepts**:
- `StateMachineBuilder` — fluent builder for defining transitions
- `StateMachine` — stateless engine, fire event with current state
- `Condition` — guard predicate for conditional transitions
- `Action` — callback executed on successful transition

---

### 1.2 Spring Statemachine

| Field | Value |
|-------|-------|
| **Repository** | [spring-attic/spring-statemachine](https://github.com/spring-attic/spring-statemachine) |
| **Stars** | ~2.7k |
| **License** | Apache-2.0 |
| **Status** | Archived (moved to attic), but still widely used |
| **Key Design** | Full-featured state machine with hierarchical states, regions, persistence, distributed lock |

**Why it matters**: The industry-standard Spring ecosystem state machine. Feature-rich but heavyweight. Good reference for understanding advanced state machine concepts (hierarchical states, orthogonal regions, persistence). We chose NOT to use it because of its stateful design and complexity overhead — our IM system needs stateless, lock-free concurrency.

**Useful concepts to borrow**:
- Hierarchical state nesting (compound states)
- Orthogonal regions (parallel state machines)
- State machine persistence via `StateMachinePersist`
- Distributed state machine with Zookeeper/Redis lock

---

### 1.3 stateless4j

| Field | Value |
|-------|-------|
| **Repository** | [stateless4j/stateless4j](https://github.com/stateless4j/stateless4j) |
| **Stars** | ~300 |
| **License** | Apache-2.0 |
| **Key Design** | Lightweight, fluent configuration, inspired by C# Stateless |
| **Core file** | `StateMachine.java` — only 334 lines, 301 LOC |

**Why it matters**: Minimal, clean implementation. Good reference for how to build a state machine with minimal code. The `StateMachineConfig` and `Transition` classes show a clean separation of configuration and runtime.

---

### 1.4 squirrel-foundation

| Field | Value |
|-------|-------|
| **Repository** | [hekailiang/squirrel](https://github.com/hekailiang/squirrel) (fork: [forwjp/squirrel](https://github.com/forwjp/squirrel)) |
| **Stars** | ~1.5k |
| **License** | Apache-2.0 |
| **Key Design** | Lightweight, highly flexible, extensible, diagnosable, type-safe, enterprise-grade |
| **Features** | Declarative configuration, MVEL/OGNL expression, state machine diagnostics, event listener, partial transition |

**Why it matters**: Enterprise-focused Java state machine with excellent diagnostics. The "diagnosable" feature — being able to inspect state machine configuration, transition history, and current state at runtime — is valuable for debugging complex conversation flows in IM systems.

---

### 1.5 pnavais/state-machine

| Field | Value |
|-------|-------|
| **Repository** | [pnavais/state-machine](https://github.com/pnavais/state-machine) |
| **Stars** | ~50 |
| **License** | MIT |
| **Key Design** | Generic, zero-dependency, Java 8+, fluent builder |
| **Maven** | `com.github.pnavais:state-machine:1.2.0` |

**Why it matters**: Ultra-minimal implementation. Good reference for the absolute minimum viable state machine API.

---

### 1.6 j-easy/easy-states

| Field | Value |
|-------|-------|
| **Repository** | [j-easy/easy-states](https://github.com/j-easy/easy-states) |
| **Stars** | ~500 |
| **License** | MIT |
| **Key Design** | Simple, lightweight, Java 8+, FSM + BPM (workflow engine) |
| **Maven** | `org.jeasy:easy-states:2.0.0` |

**Why it matters**: Combines FSM with a simple workflow engine. The `Workflow` API shows how to compose multiple state machines into a larger workflow — relevant for our multi-stage conversation pipeline.

---

### 1.7 Hypercell FSM Library (Distributed)

| Field | Value |
|-------|-------|
| **Repository** | [Hypercell-IT-Solutions/fsm-library](https://github.com/Hypercell-IT-Solutions/fsm-library) |
| **Stars** | ~50 |
| **License** | MIT |
| **Key Design** | Java 17, type-safe, distributed, resumable workflows, failure recovery, automatic retry |
| **Use case** | Stateful workflows spanning multiple HTTP requests, process restarts, service replicas |

**Why it matters**: Directly relevant to distributed IM systems. Shows how to build a state machine that survives process restarts and can be driven across multiple HTTP requests with full failure recovery. The "resumable workflow" pattern is exactly what we need for conversation state that spans WebSocket reconnections.

---

### 1.8 Spring Boot Simple State Machine

| Field | Value |
|-------|-------|
| **Repository** | [nilskasseckert/spring-boot-simple-state-machine](https://github.com/nilskasseckert/spring-boot-simple-state-machine) |
| **Stars** | ~30 |
| **License** | MIT |
| **Key Design** | Spring Boot auto-configuration, 3 transition types (SUCCESS/ERROR/CONDITIONAL), SpEL guards, action-based authorization, Spring Application Events |

**Why it matters**: Shows how to integrate a state machine with Spring Boot's event system and auto-configuration. The `CONDITIONAL` transition type via SpEL is a clean pattern for guard conditions.

---

## 2. Cross-Language / Industry-Standard Projects

### 2.1 XState (Gold Standard)

| Field | Value |
|-------|-------|
| **Repository** | [statelyai/xstate](https://github.com/statelyai/xstate) |
| **Stars** | ~29.5k |
| **License** | MIT |
| **Language** | TypeScript/JavaScript |
| **Standard** | Implements SCXML (State Chart XML) specification |
| **Key Features** | Hierarchical states, parallel regions, guards, actions, actor model, zero dependencies, visualizer |

**Why it matters**: The de facto gold standard for state machine/statechart libraries. Even though it's TypeScript, its design philosophy and API patterns are the best reference for any state machine implementation. Key concepts to borrow:

1. **SCXML compliance** — follows the W3C statechart standard
2. **Actor model** — each state machine can invoke other state machines as actors
3. **Compound states vs invoked actors** — two fundamentally different composition models (nested lifecycle vs independent communication)
4. **Guards** — conditional transitions with predicate functions
5. **Actions** — entry/exit/transition side effects
6. **Parallel regions** — orthogonal states that run concurrently
7. **History pseudo-states** — shallow and deep history for remembering previous sub-states
8. **Visualizer** — automatic state diagram generation

**Common architectural mistake (from XState community)**: Conflating compound states with invoked actors. They look similar but have radically different semantics around lifecycle, communication, and state visibility.

---

### 2.2 javascript-state-machine

| Field | Value |
|-------|-------|
| **Repository** | [jakesgordon/javascript-state-machine](https://github.com/jakesgordon/javascript-state-machine) |
| **Stars** | ~8.7k |
| **License** | MIT |
| **Key Design** | Simple, finite state machine, no statecharts (simpler than XState) |

**Why it matters**: The "simple but powerful" alternative to XState. Good reference for when a full statechart is overkill and a flat FSM suffices.

---

### 2.3 Zag (Chakra UI)

| Field | Value |
|-------|-------|
| **Repository** | [chakra-ui/zag](https://github.com/chakra-ui/zag) |
| **Stars** | ~4.9k |
| **License** | MIT |
| **Key Design** | Headless component logic powered by finite state machines, framework-agnostic (React/Solid/Vue/Svelte) |

**Why it matters**: Shows how state machines can power UI component logic in a framework-agnostic way. The "headless" pattern — separating state machine logic from rendering — is applicable to our conversation flow logic.

---

## 3. Design Patterns & Best Practices

### 3.1 Functional State Machine (leeoades)

| Field | Value |
|-------|-------|
| **Repository** | [leeoades/FunctionalStateMachine](https://github.com/leeoades/FunctionalStateMachine) |
| **Key Design** | Functional, design-time analysis, actor-model friendly, automatic Mermaid diagram generation |
| **Features** | Detect unreachable states, missing transitions, configuration errors at build time |

**Why it matters**: The "design-time analysis" concept — validating state machine configuration at build time rather than runtime — is excellent for catching errors early. Automatic Mermaid diagram generation is also valuable for documentation. The actor-model friendly design (load state → fire trigger → execute commands → save state) aligns with our stateless approach.

---

### 3.2 State Machine Pattern Selection Guide

Based on community best practices, choose the right pattern:

| Pattern | Use When | State Count | Complexity |
|---------|----------|-------------|------------|
| **Flat FSM** | Single concern, simple flows | ≤ 8 states | Low |
| **Hierarchical (Nested)** | States naturally group, transitions between groups | 8-20 states | Medium |
| **Parallel (Orthogonal)** | Independent concerns that coexist (e.g., movement + combat + animation) | Any | High |
| **Multiple FSMs** | Separate domains with independent lifecycles | Any | Medium-High |
| **Actor Model** | State machines that communicate and spawn each other | Any | High |

**Key rules**:
- When a single flat FSM grows beyond 8-10 states, consider hierarchical or parallel decomposition
- Over-nesting (depth > 3 levels) causes maintenance problems — limit to 2-3 levels
- Parallel regions need careful synchronization to avoid race conditions
- Composition over inheritance: never use deep inheritance hierarchies for states (fragile base class problem)

---

### 3.3 Anti-Patterns to Avoid

| Anti-Pattern | Problem | Fix |
|-------------|---------|-----|
| **God State Machine** | One FSM handles everything, combinatorial explosion of states | Split into multiple parallel FSMs by domain |
| **Boolean Flag Hell** | Using `isProcessing`, `isTransferring`, `isError` booleans instead of states | Replace with proper state enumeration |
| **Unguarded Transitions First** | An unguarded transition matches everything, making subsequent guarded transitions unreachable | Order guarded transitions before unguarded catch-alls |
| **Deep Inheritance** | 11-level state class hierarchy, fragile base class, diamond problem | Use composition over inheritance |
| **Stateful Engine in Distributed System** | State machine holds current state in memory, lost on restart | Use stateless engine + external state storage (DB/Redis) |
| **Missing History State** | Re-entering a composite state resets to default sub-state, losing context | Use shallow/deep history pseudo-states when needed |
| **Conflating Compound States with Invoked Actors** | Wrong composition model causes lifecycle/communication bugs | Understand the difference: compound = nested lifecycle, actor = independent communication |
| **No Design-Time Validation** | Invalid transitions only caught at runtime | Add build-time analysis for unreachable states, missing transitions |

---

## 4. IM / Chat Domain State Machines

### 4.1 Call State Machine (nself-org/chat)

| Field | Value |
|-------|-------|
| **Repository** | [nself-org/chat](https://github.com/nself-org/chat) (wiki: Call State Machine Diagram) |
| **Domain** | Voice/video call in chat application |
| **States** | connected, held, transferring, reconnecting, ending, ended |
| **Key transitions** | connected→transferring (user transfer), connected→reconnecting (network issue), transferring→connected (transfer complete), reconnecting→ended (reconnection failed) |

**Why it matters**: Directly relevant to our conversation transfer feature. The state model for call transfer (connected → transferring → connected/failed) maps closely to our AI-to-agent conversation transfer flow. The `reconnecting` state is also relevant for WebSocket reconnection handling.

**State transition table**:

| From State | Valid Next States | Triggers |
|-----------|-------------------|----------|
| connected | transferring, reconnecting, ending | User transfers / Network issue / User hangs up |
| held | connected, transferring, ending | User resumes / Transfer while held / User ends |
| transferring | connected, ending | Transfer complete / Transfer cancelled |
| reconnecting | connected, ending | Reconnected / Reconnection failed |
| ending | ended | Cleanup complete |

---

### 4.2 CBOL Conversation State Machine (Our Design)

Our custom lightweight state machine (see `01-CBOL-Domain-Knowledge/state-machine/`) defines these conversation states:

```
INIT → AI_PROCESSING → TRANSFERRING → AGENT_CONNECTED → AGENT_HANDLING → CLOSED
                    ↘                ↘                    ↘
                     ERROR            TRANSFER_FAILED       TIMEOUT
```

**Design decisions** (informed by the references above):
- **Stateless engine** (COLA pattern): engine only stores transition rules, current state injected by business layer
- **Table-driven** (ConcurrentHashMap O(1) lookup): high concurrency, no locks
- **Zero external dependencies**: lightweight, no Spring Statemachine overhead
- **Generic type-safe**: `StateMachine<S, E, C>` where S=state, E=event, C=context
- **No persistence in engine**: conversation state stored in MongoDB/Redis, engine is pure computation

---

## 5. Saga Pattern + State Machine

For distributed transactions across services (e.g., message forwarding across multiple IM nodes), Saga pattern combined with state machine is a powerful approach.

### 5.1 spring-saga-kt (BK202503)

| Field | Value |
|-------|-------|
| **Repository** | [BK202503/bk-spring-saga](https://github.com/BK202503/bk-spring-saga) |
| **Language** | Kotlin (coroutine-native) |
| **Key Design** | Saga orchestrator for Spring Boot, compensating transactions, exponential retry |
| **Pattern** | Orchestration-based Saga (central coordinator) |

### 5.2 Saga-pattern-with-state-machine (danepham2204)

| Field | Value |
|-------|-------|
| **Repository** | [danepham2204/Saga-pattern-with-state-machine](https://github.com/danepham2204/Saga-pattern-with-state-machine) |
| **Stack** | Java 17, Spring Boot 3.0.4, Maven |
| **Key Design** | State machine for entity status + Saga orchestration for long-running transactions |
| **Pattern** | State machine ensures valid state sequence; Saga coordinator handles compensation |

**Why it matters**: Shows how to combine state machine (for entity state validity) with Saga pattern (for distributed transaction coordination). This is relevant for our message forwarding across services — the forwarding state machine ensures valid transitions, while Saga handles compensation if a downstream service fails.

---

## 6. Summary: What CBOL Should Borrow

Based on this research, here are the key takeaways for our state machine design:

### Already Adopted ✅
- [x] Stateless engine (COLA pattern) — current state injected by business layer
- [x] Table-driven O(1) lookup (ConcurrentHashMap)
- [x] Zero external dependencies
- [x] Generic type-safe API
- [x] Fluent builder for transition definition
- [x] Guard conditions (Condition) for conditional transitions
- [x] Action callbacks for transition side effects

### Consider Adding 📋
- [ ] **Design-time validation**: Build-time check for unreachable states, missing transitions, duplicate transitions (reference: leeoades/FunctionalStateMachine)
- [ ] **Automatic Mermaid diagram generation**: Generate state diagrams from transition definitions (reference: leeoades, XState visualizer)
- [ ] **Transition history/audit log**: Record every state transition with timestamp, event, source state, target state for debugging (reference: squirrel-foundation diagnostics)
- [ ] **State machine snapshot/serialization**: Support for persisting and restoring state machine context (reference: Hypercell FSM resumable workflows)
- [ ] **Composite/history states**: For complex sub-flows (e.g., within AGENT_HANDLING, sub-states for typing/sending/awaiting response) (reference: XState, Spring Statemachine)
- [ ] **Parallel regions**: For independent concerns (e.g., connection state + conversation state running in parallel) (reference: XState orthogonal regions)
- [ ] **Error state with retry policy**: Dedicated ERROR state with configurable retry/backoff before transitioning to CLOSED (reference: spring-saga-kt exponential retry)

### Explicitly Rejected ❌
- [ ] **Spring Statemachine**: Too heavyweight, stateful design, archived status — our custom stateless engine is better for high-concurrency IM
- [ ] **Deep inheritance for states**: Fragile base class problem — use composition/table-driven instead
- [ ] **God state machine**: One FSM for everything — split by domain (conversation FSM, connection FSM, forwarding FSM)
- [ ] **Boolean flags instead of states**: `isProcessing` / `isTransferring` anti-pattern — use proper state enumeration

---

## 7. Quick Reference: Framework Comparison

| Framework | Stateless | Deps | Hierarchical | Parallel | Persistence | Diagnostics | Java Version |
|-----------|-----------|------|-------------|----------|-------------|-------------|-------------|
| **COLA StateMachine** | ✅ | 0 | ❌ | ❌ | ❌ | ❌ | Java 8+ |
| **Spring Statemachine** | ❌ | Many | ✅ | ✅ | ✅ | ✅ | Java 8+ |
| **stateless4j** | ❌ | 0 | ❌ | ❌ | ❌ | ❌ | Java 8+ |
| **squirrel-foundation** | ❌ | Few | ✅ | ❌ | ✅ | ✅ | Java 8+ |
| **easy-states** | ❌ | 0 | ❌ | ❌ | ❌ | ❌ | Java 8+ |
| **Hypercell FSM** | ✅ | Few | ❌ | ❌ | ✅ | ✅ | Java 17+ |
| **XState (TS)** | ❌ | 0 | ✅ | ✅ | ✅ | ✅ | TypeScript |
| **Our Custom** | ✅ | 0 | ❌ | ❌ | ❌ | ❌ | Java 17+ |

---

*State Machine Projects Reference — v1.0.0 — 2026-08-26*
*Curated from GitHub search, focused on Java ecosystem, lightweight design, and IM/chat domain applicability*
