# State Machine Architecture Design

> Version: 4.0 | Last Updated: 2026-09-05
> Based on Alibaba COLA StateMachine: https://github.com/alibaba/COLA
> Aligned with Event-Driven Orchestration Design (v4.0)

## 1. Overview

This project implements a **lightweight, stateless, table-driven state machine framework** for the CBOL (AI Messaging Hub) system, powered by **Alibaba COLA StateMachine**. The framework is optimized for simplicity, high performance, and type safety.

The project is organized as a **multi-module Maven project** with three modules:

| Module | Package | Responsibility |
|--------|---------|----------------|
| **statemachine-core** | `com.alibaba.cola.statemachine` | Alibaba COLA StateMachine core engine: Action, Condition, State, Transition, Builder DSL, StateMachineFactory, PlantUML generation |
| **chat-engine** | `com.selfdevelopment.chatengine` | Conversation state machine (business-level): 7 states (NEW, INITIATED, ACTIVE, IN_PROGRESS, TRANSFERRED, ENDING, CLOSED), 25+ events, 13 actions, multi-market config, monitors, repository, demo |
| **agent-connector** | `com.selfdevelopment.agentconnector` | Interaction state machine (channel-level): 8 states (INITIATED, CONNECTED, IN_PROGRESS, DEGRADED, RECONNECTING, CONSULT_TRANSFER, TRANSFERRED, CLOSED), 20+ events, 14 actions, channel connectors, event normalizers, demo |

### Module Dependencies

```
chat-engine ──► statemachine-core
agent-connector ──► statemachine-core
```

`chat-engine` and `agent-connector` have **no direct dependency** on each other. This separation ensures:
- Channel-level concerns (connection, hold, transfer) are isolated from business-level concerns (conversation lifecycle)
- Each module can be developed, tested, and deployed independently
- Clear system boundaries: chat-engine connects to AIBot and ChatHistory; agent-connector connects to Genesys and WebSocket

## 2. Design Principles

### 2.1 Stateless Engine (Mandatory)

The state machine engine itself does **not** store the current state. The caller injects the current state on each `fireEvent` call. This design:

- Allows a single machine instance to serve thousands of concurrent conversations
- Eliminates thread-safety concerns for state storage
- Enables horizontal scaling without session affinity
- Simplifies state persistence (caller manages DB/cache)

```java
// Caller manages state; engine only stores transition rules
ConversationState newState = stateMachine.fireEvent(
    conversation.getCurrentState(),  // injected by caller
    ConversationFact.INTERACTION_BECAME_ACTIVE,
    context
);
conversation.setCurrentState(newState);
repository.save(conversation);
```

### 2.2 Table-Driven Transitions (O(1) Lookup)

Transitions are stored in a `ConcurrentHashMap` keyed by `(sourceState, event)`, enabling O(1) lookup. Multiple transitions with the same key (different guards) are stored as a list and evaluated in order.

### 2.3 Action-First Transition (Core Principle)

Actions execute **before** state change. If an action fails, the state does NOT change. This ensures business logic is the gatekeeper for state transitions.

**Execution order:**
1. Guard/Condition check (`when()`) — if false, transition rejected
2. **Transition action (`perform()`) — failure → `StateMachineException`, state unchanged**
3. State transition completes

### 2.4 COLA Builder DSL

The configuration API uses Alibaba COLA StateMachine's Builder DSL:

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();

builder.externalTransition()
        .from(ConversationState.NEW)
        .to(ConversationState.INITIATED)
        .on(ConversationFact.SESSION_STARTED)
        .perform(new SessionStartedAction());

StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

**Builder API order:** `from() → to() → on() → when() → perform()`

### 2.5 Generic Type-Safe

COLA StateMachine uses Java generics for type-safe states, events, and contexts. No reflection, no runtime type errors.

### 2.6 Independent State Machines

Conversation and Interaction are **independent** state machines with separate contexts. They do NOT share state or context.

## 3. Package Structure

### 3.1 statemachine-core (Alibaba COLA StateMachine)

```
com.alibaba.cola.statemachine/
├── Action.java                    # Functional interface for transition actions
├── Condition.java                 # Functional interface for guards
├── State.java                     # State interface
├── StateContext.java              # Context interface
├── StateMachine.java              # Core state machine interface
├── StateMachineFactory.java       # Global registry
├── Transition.java                # Transition interface
├── builder/
│   ├── StateMachineBuilder.java   # Builder DSL entry
│   ├── StateMachineBuilderFactory.java
│   ├── From.java, To.java, On.java, When.java, Perform.java
│   └── TransitionBuilder.java
├── impl/
│   ├── StateMachineImpl.java
│   ├── StateImpl.java
│   ├── TransitionImpl.java
│   └── StateContextImpl.java
└── exception/
    └── StateMachineException.java
```

### 3.2 chat-engine

```
com.selfdevelopment.chatengine/
├── action/
│   ├── ActionWorker.java          # RESERVED: async action executor
│   └── impl/
│       ├── ConversationInitAction.java
│       ├── CustomerConnectAction.java
│       ├── TransferRequestAction.java
│       ├── TransferFailedAction.java
│       ├── CustomerCloseAction.java
│       ├── SurveyStartAction.java
│       └── SurveyCompleteAction.java
├── config/
│   ├── MarketConfigProvider.java
│   └── StateMachineMarketConfig.java
├── context/
│   ├── CbolStateContext.java
│   ├── TraceContext.java
│   └── TraceMdcHelper.java
├── demo/
│   ├── ChatEngineDemo.java
│   └── DemoLogger.java
├── enums/
│   ├── ConversationFact.java
│   └── ConversationState.java
├── ingress/
│   ├── AibotEvent.java
│   ├── AibotEventNormalizer.java
│   └── ChatEngineEventDispatcher.java
├── model/
│   └── ConversationInstance.java
├── monitor/
│   ├── CustomerIdleMonitor.java
│   ├── TransferMonitor.java
│   └── EndingGraceMonitor.java
├── repository/
│   └── ConversationRepository.java
├── service/
│   └── ChatEngineStateMachineService.java
└── statemachine/
    └── factory/
        └── ConversationStateMachineFactory.java
```

### 3.3 agent-connector

```
com.selfdevelopment.agentconnector/
├── action/
│   └── impl/
│       ├── ConnectionEstablishedAction.java
│       ├── ConnectionFailedAction.java
│       ├── ConnectionDroppedAction.java
│       ├── CloseRequestAction.java
│       ├── ReconnectSuccessAction.java
│       ├── HoldRequestAction.java
│       ├── HoldResumeAction.java
│       ├── TransferStartAction.java
│       └── TransferCompleteAction.java
├── context/
│   └── AgentConnectorStateContext.java
├── demo/
│   ├── AgentConnectorDemo.java
│   └── DemoLogger.java
├── enums/
│   ├── InteractionFact.java
│   └── InteractionState.java
├── ingress/
│   ├── AgentConnectorEventDispatcher.java
│   ├── GenesysEvent.java
│   └── GenesysEventNormalizer.java
├── model/
│   └── InteractionInstance.java
├── service/
│   └── AgentConnectorStateMachineService.java
└── statemachine/
    └── factory/
        └── InteractionStateMachineFactory.java
```

## 4. Key Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| **Use Alibaba COLA StateMachine** | Battle-tested, lightweight, type-safe, zero external dependencies, active community |
| **Stateless engine** | Horizontal scaling, thread-safety, simplified persistence |
| **Action-first transition** | Business logic is the gatekeeper for state changes |
| **Multi-module Maven** | Clear separation of concerns, independent development/deployment |
| **Independent state machines** | Conversation (business) and Interaction (channel) have different lifecycles |
| **Multi-market config** | Configuration-driven per-market behavior (HK, SG, UK, etc.) |
| **Survey as sub-phase** | SURVEY_START is internal transition (IN_PROGRESS → IN_PROGRESS), not a separate state |
| **NEW initial state** | Conversation record created but not yet initialized |
| **Factory caching pattern** | COLA StateMachine does not allow rebuilding; cache to prevent duplicate builds |

## 5. Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 21 |
| Build Tool | Maven | 3.x |
| State Machine | Alibaba COLA StateMachine | 4.x |
| Logging | SLF4J + Logback | 1.x |
| Testing | JUnit 5 | 5.x |
| Code Generation | Lombok | 1.x |

## 6. References

- Alibaba COLA GitHub: https://github.com/alibaba/COLA
- COLA StateMachine module: `cola-components/cola-component-statemachine`
- COLA StateMachine design philosophy: lightweight, stateless, table-driven

---

*Last updated: 2026-09-04 (v3.0 — migrated to Alibaba COLA StateMachine)*
