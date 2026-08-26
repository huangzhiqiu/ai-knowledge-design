# XState — The SCXML Gold Standard

> Deep analysis of XState v5 — the most sophisticated state machine library in existence. While TypeScript, its design philosophy represents the state of the art in state machine theory.
>
> **Repository**: [statelyai/xstate](https://github.com/statelyai/xstate)
> **Stars**: ~29.5k
> **License**: MIT
> **Language**: TypeScript
> **Standard**: Implements SCXML (W3C State Chart XML)
> **Dependencies**: Zero runtime dependencies

---

## 1. Architecture Overview

XState's fundamental data structure is a **tree of `StateNode` objects**, not a flat map. This tree structure enables hierarchical states, parallel regions, and the full statechart specification.

```mermaid
flowchart TB
    subgraph Machine["StateMachine (ActorLogic)"]
        Root["root StateNode<br/>id: 'light'"]
        Root -->|"states"| Green["StateNode: 'green'<br/>type: atomic"]
        Root -->|"states"| Yellow["StateNode: 'yellow'<br/>type: atomic"]
        Root -->|"states"| Red["StateNode: 'red'<br/>type: compound"]
        Red -->|"states"| RedWalk["StateNode: 'walk'<br/>type: atomic"]
        Red -->|"states"| RedWait["StateNode: 'wait'<br/>type: atomic"]
        Root -->|"context"| Ctx["{ count: 0 }<br/>(extended state)"]
        Root -->|"initial"| Init["'green'"]
    end

    subgraph Runtime["Runtime"]
        Actor["createActor(machine)<br/>ActorRef"]
        Snapshot["MachineSnapshot<br/>{ value, context, _nodes, status }"]
    end

    Machine -->|createActor| Actor
    Actor -->|getSnapshot| Snapshot
    Actor -->|send event| Actor

    style Root fill:#bbdefb
    style Red fill:#fff9c4
    style Actor fill:#c8e6c9
```

### Core Class Hierarchy

```mermaid
classDiagram
    class ActorLogic~TSnapshot,TEvent~ {
        <<interface>>
        +transition(snapshot, event, actorScope) TSnapshot
        +getInitialSnapshot(actorScope, input) TSnapshot
        +getPersistedSnapshot(snapshot) Snapshot
        +restoreSnapshot(snapshot, actorScope) TSnapshot
        +start(snapshot) void
    }

    class StateMachine~TContext,TEvent,...~ {
        -id string
        -root StateNode
        -config MachineConfig
        -implementations MachineImplementations
        -idMap Map~string, StateNode~
        +transition(snapshot, event, actorScope) MachineSnapshot
        +getInitialSnapshot(actorScope, input) MachineSnapshot
        +getStateNodeById(stateId) StateNode
        +provide(implementations) StateMachine
    }

    class StateNode~TContext,TEvent~ {
        -key string
        -type 'atomic'|'compound'|'parallel'|'final'|'history'
        -states Map~string, StateNode~
        -on Map~string, TransitionDefinition~
        -initial string
        -entry Action[]
        -exit Action[]
        -meta object
        +_initialize() void
    }

    class MachineSnapshot~TContext,TEvent~ {
        -value StateValue
        -context TContext
        -_nodes Set~StateNode~
        -children Record~string, ActorRef~
        -status 'active'|'done'|'error'|'stopped'
        -historyValue HistoryValue
        -error unknown
    }

    ActorLogic <|.. StateMachine
    StateMachine "1" --> "1" StateNode : root
    StateNode "1" --> "*" StateNode : states (children)
    StateMachine "1" --> "*" MachineSnapshot : produces

    style StateMachine fill:#bbdefb
    style StateNode fill:#fff9c4
    style MachineSnapshot fill:#c8e6c9
```

---

## 2. The Transition Algorithm — Microsteps and Macrosteps

XState's transition logic is more complex than COLA's because it handles hierarchical states, parallel regions, and internal events.

```mermaid
flowchart TB
    subgraph External["External Event (e.g., user sends message)"]
        EV["event: { type: 'SUBMIT' }"]
    end

    subgraph Macrostep["macrostep() — full atomic transition cycle"]
        direction TB
        MS1["1. transitionNode()<br/>Find matching transitions in current state nodes"]
        MS2["2. Compute exit set<br/>All states to exit (including parents)"]
        MS3["3. Execute exit actions<br/>in reverse order"]
        MS4["4. Execute transition actions"]
        MS5["5. Compute entry set<br/>All states to enter (including initial sub-states)"]
        MS6["6. Execute entry actions<br/>in order"]
        MS7["7. Resolve context updates<br/>(assign actions)"]
        MS8["8. Collect internal events<br/>from actions (raise, send)"]
        MS9{"9. Any internal events?"}
        MS10["10. Process internal event<br/>(microstep, no exit/entry of top-level)"]
        MS9 -->|Yes| MS10
        MS10 --> MS9
        MS9 -->|No| MS11["11. Return stable snapshot"]
    end

    EV --> MS1
    MS1 --> MS2 --> MS3 --> MS4 --> MS5 --> MS6 --> MS7 --> MS8 --> MS9

    style Macrostep fill:#e3f2fd
    style MS11 fill:#c8e6c9
```

### Core Method: `StateMachine.transition()`

```typescript
public transition(snapshot, event, actorScope): MachineSnapshot {
    return macrostep(snapshot, event, actorScope, []).snapshot;
}
```

**Two-level semantics**:

| Level | Function | What it does |
|-------|----------|-------------|
| **Microstep** | `transitionNode()` | Single transition: exit source states, execute actions, enter target states |
| **Macrostep** | `macrostep()` | Process event + all internal events generated by actions, until stable |

**Why macrosteps matter**: When a transition's action raises an internal event (via `raise()`), that event is processed in the same macrostep. This means a single external event can cause multiple state changes in one atomic operation. Essential for complex statecharts, overkill for simple FSMs.

---

## 3. Five Advanced Statechart Concepts

XState implements the full SCXML specification. These are the 5 most important concepts beyond basic FSM.

### 3.1 Hierarchical (Compound) States

A state can contain sub-states. When in the parent state, you're also in exactly one sub-state.

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Processing : START
    state Processing {
        [*] --> Validating
        Validating --> Executing : VALID
        Validating --> Error : INVALID
    }
    Processing --> Idle : CANCEL
    Processing --> Done : DONE
    Error --> [*]
    Done --> [*]
```

```typescript
const machine = createMachine({
  initial: 'idle',
  states: {
    idle: { on: { START: 'processing' } },
    processing: {           // COMPOUND state
      initial: 'validating',
      states: {
        validating: { on: { VALID: 'executing', INVALID: '#error' } },
        executing: { on: { DONE: '#done' } },
      },
      on: { CANCEL: 'idle' }  // transition from parent applies to ALL sub-states
    },
    done: { id: 'done', type: 'final' },
    error: { id: 'error', type: 'final' }
  }
});
```

**Key benefit**: `CANCEL` works from both `processing.validating` and `processing.executing` — defined once on the parent. This **eliminates transition duplication**.

**For CBOL**: Group related sub-states (e.g., `AGENT_HANDLING` has sub-states `typing`, `sending`, `awaiting_response`) to reduce transition duplication.

### 3.2 Parallel (Orthogonal) Regions

A state can contain multiple independent sub-state machines that run concurrently.

```mermaid
stateDiagram-v2
    state "Active Session" as Active {
        state "Connection" as Conn {
            [*] --> Disconnected
            Disconnected --> Connected : CONNECT
            Connected --> Disconnected : DISCONNECT
            Connected --> Reconnecting : NETWORK_ERROR
            Reconnecting --> Connected : RECONNECTED
            Reconnecting --> Disconnected : RECONNECT_FAILED
        }
        state "Conversation" as Conv {
            [*] --> Idle
            Idle --> AI_Processing : USER_MESSAGE
            AI_Processing --> Agent_Connected : TRANSFER
            Agent_Connected --> Agent_Handling : AGENT_JOIN
            Agent_Handling --> Closed : CLOSE
        }
    }
```

```typescript
const machine = createMachine({
  type: 'parallel',          // PARALLEL state
  states: {
    connection: {            // Region 1: connection status
      initial: 'disconnected',
      states: {
        disconnected: { on: { CONNECT: 'connected' } },
        connected: { on: { DISCONNECT: 'disconnected', NETWORK_ERROR: 'reconnecting' } },
        reconnecting: { on: { RECONNECTED: 'connected', RECONNECT_FAILED: 'disconnected' } }
      }
    },
    conversation: {          // Region 2: conversation status
      initial: 'idle',
      states: {
        idle: { on: { USER_MESSAGE: 'ai_processing' } },
        ai_processing: { on: { TRANSFER: 'agent_connected' } },
        agent_connected: { on: { AGENT_JOIN: 'agent_handling' } },
        agent_handling: { on: { CLOSE: 'closed' } },
        closed: { type: 'final' }
      }
    }
  }
});
// State value: { connection: 'connected', conversation: 'ai_processing' }
```

**For CBOL**: This is **directly applicable**. A WebSocket connection has independent concerns:
- Connection state (connected / reconnecting / disconnected)
- Conversation state (idle / AI processing / agent connected / closed)
- Presence state (online / away / offline)

Instead of one God state machine with combinatorial states (e.g., `CONNECTED_AI_PROCESSING_ONLINE`), use parallel regions.

### 3.3 Guards (Conditional Transitions)

Transitions can have guard predicates. The first transition whose guard returns true is taken.

```mermaid
flowchart LR
    A["current state"] -->|SUBMIT event| B{Check guards in order}
    B -->|isValid = true| C["target: success"]
    B -->|isValid = false<br/>hasCriticalError = true| D["target: error"]
    B -->|all guards false<br/>unguarded fallback| E["target: review"]

    style C fill:#c8e6c9
    style D fill:#ffcdd2
    style E fill:#fff9c4
```

```typescript
on: {
  SUBMIT: [
    { target: 'success', guard: 'isValid' },           // guarded
    { target: 'error', guard: 'hasCriticalError' },     // guarded
    { target: 'review' }                                  // unguarded fallback
  ]
}
```

This is the same pattern as COLA's `routeTransition()` — guarded first, unguarded fallback. XState formalizes it as an array of transition definitions with explicit ordering.

### 3.4 History Pseudo-States

When re-entering a compound state, history states remember the previous sub-state.

```mermaid
stateDiagram-v2
    [*] --> Processing
    state Processing {
        [*] --> Validating
        Validating --> Executing : VALID
        Executing --> Validating : EDIT
        state hist {
            [*] --> Validating
        }
    }
    Processing --> Paused : SUSPEND
    Paused --> Processing.hist : RESUME
    note right of Processing.hist
        Shallow history:
        remembers 'executing'
        after suspend/resume
    end note
```

```typescript
processing: {
  initial: 'validating',
  states: {
    validating: { ... },
    executing: { ... },
    hist: { type: 'history', history: 'shallow' }  // or 'deep'
  },
  on: {
    SUSPEND: 'paused',
    RESUME: 'processing.hist'  // re-enter the PREVIOUS sub-state
  }
}
```

| History Type | Remembers | Use When |
|-------------|-----------|----------|
| **Shallow** | Only the immediate sub-state | Simple nesting (1 level) |
| **Deep** | Entire nested configuration (all levels) | Complex multi-level nesting |

**For CBOL**: Useful for conversation pause/resume. If a user pauses a conversation mid-AI-processing, resuming should return to the exact sub-state, not reset to the initial.

### 3.5 Actor Model (Invoked Actors)

A state can invoke other state machines as child actors. This is the most powerful XState concept and the #1 source of architectural confusion.

```mermaid
flowchart TB
    subgraph Parent["Parent Machine: conversation"]
        PState["state: 'ai_processing'"]
        PState -->|invoke| ChildActor["Actor: aiProcessor<br/>(child state machine)"]
    end

    subgraph Child["Child Machine: aiProcessor"]
        CInit["initial: 'thinking'"]
        CInit --> CResp["'responding'"]
        CResp --> CDone["'done' (final)"]
    end

    ChildActor -->|onDone| PDone["parent → 'success'"]
    ChildActor -->|onError| PErr["parent → 'error'"]
    Parent -.->|send message| ChildActor

    style ChildActor fill:#f8bbd0
    style PDone fill:#c8e6c9
```

```typescript
processing: {
  invoke: {
    src: 'aiProcessingMachine',  // child state machine definition
    id: 'ai-processor',
    onDone: {
      target: 'success',
      actions: assign({ result: (_, e) => e.data })
    },
    onError: {
      target: 'error',
      actions: assign({ error: (_, e) => e.data })
    }
  }
}
```

**Compound states vs invoked actors** (the #1 XState architectural mistake):

| Aspect | Compound State | Invoked Actor |
|--------|---------------|---------------|
| **Lifecycle** | Tied to parent — enters/exits with parent | Independent — can outlive parent, spawned/destroyed explicitly |
| **Communication** | Internal events only | Send/receive messages via actor system |
| **State visibility** | Parent can see sub-state | Parent only sees done/error, not internal state |
| **Use when** | Sub-flow is part of parent's lifecycle | Sub-flow is independent, may run concurrently, may need to communicate |

---

## 4. Context (Extended State)

Unlike basic FSMs where all information is encoded in the state, XState separates:
- **Finite state** — the current state node (e.g., `processing`)
- **Extended state (context)** — arbitrary data (e.g., `{ userId, messageCount, retryCount }`)

```mermaid
flowchart LR
    subgraph Snapshot["MachineSnapshot"]
        State["value: 'processing'<br/>(finite state)"]
        Context["context: { count: 3, userId: '123' }<br/>(extended state)"]
    end

    State -->|guard uses context| Guard{"guard: ({ count }) => count > 0"}
    Context --> Guard
    Guard -->|true| Transition["transition to 'success'"]
    Transition -->|assign action updates context| NewCtx["context: { count: 4, ... }"]

    style State fill:#bbdefb
    style Context fill:#fff9c4
```

```typescript
const machine = createMachine({
  context: { count: 0, maxRetries: 3 },
  initial: 'idle',
  states: {
    idle: {
      on: {
        INCREMENT: {
          actions: assign({ count: (ctx) => ctx.count + 1 })  // update context
        },
        SUBMIT: {
          target: 'processing',
          guard: ({ count }) => count > 0  // guard uses context
        }
      }
    }
  }
});
```

This is equivalent to COLA's `C` (context) generic parameter. XState formalizes context updates via `assign()` actions and makes context a first-class citizen of the snapshot.

---

## 5. Strengths

| Strength | Detail |
|----------|--------|
| **Full SCXML compliance** | Implements the W3C statechart standard — hierarchical, parallel, history, guards, actions |
| **Actor model** | Invoked actors for independent sub-flows with message-based communication |
| **Zero runtime dependencies** | Pure TypeScript, no external libs |
| **Excellent visualizer** | XState Visualizer (web) for real-time state diagram visualization |
| **Type-safe** | Full TypeScript type inference for states, events, context, guards, actions |
| **Macrostep semantics** | Internal events processed atomically — no intermediate inconsistent states |
| **Mature ecosystem** | 29.5k stars, used by many production systems (Chakra UI, etc.) |
| **Snapshot persistence** | `getPersistedSnapshot()` / `restoreSnapshot()` for state serialization |

---

## 6. Limitations

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| **TypeScript only** | Can't use directly in Java | Borrow design patterns, not code |
| **Steep learning curve** | Actor model + statechart concepts are complex | Start with flat FSM, add features incrementally |
| **Verbose config** | Machine definitions can be long | Use `setup()` for shared implementations |
| **Overkill for simple flows** | Full statechart is unnecessary for basic state transitions | Use COLA-style minimal FSM for simple cases |
| **Macrostep complexity** | Internal event chains can be hard to debug | Use XState Visualizer for tracing |
| **No built-in distributed support** | Actors are in-process only | Add distributed layer separately (Hypercell pattern) |

---

## 7. CBOL Takeaways

### High-Value Patterns to Adopt 📋

1. **Parallel decomposition** — split connection state, conversation state, and presence state into independent orthogonal regions instead of one combinatorial FSM
2. **Hierarchical states** — group related sub-states (e.g., `AGENT_HANDLING` has sub-states `typing`, `sending`, `awaiting_response`) to eliminate transition duplication
3. **History pseudo-states** — for pause/resume scenarios, remember the previous sub-state instead of resetting
4. **Context separation** — clearly separate finite state (enum) from extended state (context data)
5. **Macrostep semantics** — when an action raises an internal event, process it atomically in the same transition cycle

### Medium-Value Patterns 📋

6. **Final states with output** — terminal states can carry output data (e.g., `CLOSED` carries `closeReason`, `closeTimestamp`)
7. **Actor model for sub-flows** — AI processing could be an invoked actor that communicates via messages
8. **StateNode tree traversal** — for validation and visualization, traverse the state tree rather than a flat map

### Overkill for CBOL ❌

- Full SCXML compliance — we don't need all SCXML features
- Actor system with message passing — too complex for our use case
- Macrosteps with internal event chains — simple FSM transitions suffice
- TypeScript-specific type inference magic

---

## 8. Key Source Files Reference

| File | Path | Purpose |
|------|------|---------|
| `createMachine.ts` | `packages/core/src/createMachine.ts` | Factory function, entry point |
| `StateMachine.ts` | `packages/core/src/StateMachine.ts` | Core class, implements ActorLogic |
| `StateNode.ts` | `packages/core/src/StateNode.ts` | State node (tree structure) |
| `stateUtils.ts` | `packages/core/src/stateUtils.ts` | transitionNode, macrostep, microstep |
| `State.ts` | `packages/core/src/State.ts` | MachineSnapshot, createMachineSnapshot |
| `types.ts` | `packages/core/src/types.ts` | All type definitions |

---

*XState Deep Analysis — v1.0.0 — 2026-08-26*
