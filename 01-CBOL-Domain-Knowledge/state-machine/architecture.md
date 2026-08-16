# State Machine Architecture

> Core architecture, data flow, and thread safety design.

## Component Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Business Layer                        │
│  (Redis state retrieval, persistence, orchestration)    │
└───────────────────────┬─────────────────────────────────┘
                        │ fireEvent(currentState, event, context)
                        v
┌─────────────────────────────────────────────────────────┐
│              SimpleStateMachine<S, E, C>                 │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │  Transition Map (ConcurrentHashMap)             │    │
│  │  Key: (sourceState, event) -> Transition        │    │
│  └─────────────────────────────────────────────────┘    │
│                        │                                │
│                        v                                │
│  1. O(1) lookup for transition rule                     │
│  2. Evaluate guard condition (if present)               │
│  3. Execute action (if condition passes)                │
│  4. Return target state                                 │
└───────────────────────┬─────────────────────────────────┘
                        │ returns newState
                        v
┌─────────────────────────────────────────────────────────┐
│                    Business Layer                        │
│         (Persist new state to Redis)                    │
└─────────────────────────────────────────────────────────┘
```

## Data Flow

1. **Business layer** retrieves current state from Redis (or other persistent store)
2. **Business layer** invokes `fireEvent(currentState, event, context)`
3. **State machine** performs O(1) lookup for transition rule in internal Map
4. **Guard condition** evaluated (if present)
5. **Action** executed (if present and condition passes)
6. **New target state** returned to business layer
7. **Business layer** persists new state to Redis

## Transition Rule Storage

### Key Design
```java
// Key: composite of source state + event
// Value: Transition object containing target state, condition, action
Map<StateEventKey<S, E>, Transition<S, E, C>> transitionMap;
```

### Transition Object
```java
class Transition<S, E, C> {
    S targetState;
    Condition<C> condition;  // nullable
    Action<C> action;        // nullable
}
```

### O(1) Lookup
```
fireEvent(source, event, context):
    key = StateEventKey(source, event)
    transition = transitionMap.get(key)    // O(1)
    if transition == null: throw IllegalStateTransitionException
    if transition.condition != null && !condition.test(context):
        throw GuardConditionFailedException (optional)
    if transition.action != null:
        transition.action.execute(context)
    return transition.targetState
```

## Thread Safety

### Immutable After Build
- Transition rules stored in `ConcurrentHashMap`
- State machine instance is **strictly immutable after build phase**
- No mutable shared state within engine itself
- **Inherently safe for concurrent use** — no locks needed for reads

### Build Phase
```java
// During build: mutations allowed
builder.externalTransition()...
    
// After build(): transitionMap frozen, no more modifications
StateMachine<DialogState, DialogEvent, DialogContext> sm = builder.build();
```

### Concurrency Model
| Phase | Thread Safety | Mechanism |
|-------|--------------|-----------|
| Build (single thread) | Not required | Single-threaded configuration |
| After build (multi-thread) | Guaranteed | Immutable state, ConcurrentHashMap |
| fireEvent calls | Lock-free | Pure read operations on immutable map |

## Stateless Design

### What "Stateless" Means
- Engine does **not** store current state of any session
- Engine stores only the **transition graph** (rules)
- Current state is always **injected** by caller via `fireEvent(currentState, ...)`

### Benefits
1. **No shared mutable state** → no race conditions
2. **Single instance serves all sessions** → no per-session object creation
3. **Easy to test** → pure function of (state, event, context)
4. **Horizontal scaling** → any instance can process any session
5. **Persistence decoupled** → business layer chooses storage (Redis, DB, etc.)

### State Persistence (Delegated to Business Layer)
```
Business layer responsibility:
- Load current state from Redis before fireEvent
- Save new state to Redis after fireEvent
- Handle TTL/expiration
- Handle concurrent state updates (optimistic locking if needed)
```

## Error Handling

| Exception | When Thrown |
|-----------|-------------|
| `IllegalStateTransitionException` | No transition rule matches (source, event) |
| `GuardConditionFailedException` | Guard condition evaluates to false (optional) |

### Design Decisions
- **Timeout handling**: Engine is time-agnostic; timeouts handled by business layer
- **Retry policy**: Engine is idempotent; retry logic managed externally
- **No swallowed exceptions**: All error conditions explicitly thrown

## Circuit Breaker Integration

The stateless nature makes the engine fully compatible with Resilience4j circuit breakers:

```
Circuit breaker wraps downstream calls (AI Bot / Human Agent platform),
NOT the state machine itself.

If circuit opens:
  → State machine can still safely transition to DEGRADED or ERROR state
  → No state corruption because engine is stateless
```

## Testing Strategy

| Test Type | Focus |
|-----------|-------|
| Unit Tests | Every transition rule (positive + negative cases) |
| Guard Edge Cases | Null handling, timeout simulation, concurrent evaluation |
| Integration Tests | State persistence validation with Redis |
| Concurrency Stress Tests | Multi-threaded fireEvent calls to verify thread safety |
