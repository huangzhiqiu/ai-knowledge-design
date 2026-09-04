# State Machine Core Framework Design

> Version: 3.0 | Last Updated: 2026-09-04
> Based on Alibaba COLA StateMachine: https://github.com/alibaba/COLA

## 0. Design Principles

### Action-First Transition (Core Principle)

The state machine follows the **action-first transition** principle:

> **Action executes BEFORE state change. If action fails, state does NOT change.**

This ensures that business logic (action) is the gatekeeper for state transitions. A transition from state A to state B only completes if the associated action executes successfully.

**Execution order for EXTERNAL transitions (COLA StateMachine):**
1. Guard/Condition check (`when()`) — if false, transition is rejected
2. **Transition action (`perform()`) — failure → `StateMachineException`, state does NOT change**
3. State transition completes

**Key points:**
- Transition action is the only action that can block a state transition
- When transition action fails, the source state is preserved and the exception propagates
- COLA StateMachine throws `StateMachineException` when no transition matches or when action fails

## 1. Core Abstractions (Alibaba COLA StateMachine)

### Package Structure

The core framework is based on Alibaba COLA StateMachine:

| Package | Responsibility |
|---------|----------------|
| `com.alibaba.cola.statemachine` | Core interfaces: `StateMachine`, `Action`, `Condition`, `State`, `Transition`, `StateMachineFactory`, `StateContext` |
| `com.alibaba.cola.statemachine.builder` | Builder DSL: `StateMachineBuilder`, `StateMachineBuilderFactory`, `TransitionBuilder`, `From`, `To`, `On`, `When`, `Perform` |
| `com.alibaba.cola.statemachine.impl` | Core implementations: `StateMachineImpl`, `StateImpl`, `TransitionImpl`, `StateContextImpl` |
| `com.alibaba.cola.statemachine.exception` | `StateMachineException` |

### 1.1 StateMachine Interface

The central interface defining the state machine contract.

```java
public interface StateMachine<S, E, C> {
    /**
     * Fire an event and return the target state.
     * @param sourceState the source state
     * @param event the event to fire
     * @param ctx the business context
     * @return the target state after transition
     * @throws StateMachineException if no transition matches or action fails
     */
    S fireEvent(S sourceState, E event, C ctx);

    /**
     * Verify if an event can be fired from the source state.
     */
    boolean verify(S sourceState, E event);

    /**
     * Generate a PlantUML state diagram.
     */
    String generatePlantUML();

    String getMachineId();
}
```

**Type Parameters:**
- `S` — State type (typically an enum)
- `E` — Event type (typically an enum)
- `C` — Business context type (carries domain data)

### 1.2 Action Interface

The functional interface for transition actions.

```java
@FunctionalInterface
public interface Action<S, E, C> {
    /**
     * Execute the action.
     * @param from the source state
     * @param to the target state
     * @param event the event that triggered the transition
     * @param context the business context
     */
    void execute(S from, S to, E event, C context);
}
```

**Key points:**
- Actions are executed synchronously before state transition
- If an action throws an exception, the state does NOT change
- Actions are stateless and can be shared across multiple transitions

### 1.3 Condition Interface (Guard)

The functional interface for transition guards/conditions.

```java
@FunctionalInterface
public interface Condition<C> {
    /**
     * Check if the condition is satisfied.
     * @param context the business context
     * @return true if the transition is allowed
     */
    boolean isSatisfied(C context);
}
```

### 1.4 State and Transition

**State:**
```java
public interface State<S, E, C> {
    S getId();
    Transition<S, E, C> addTransition(E event, State<S, E, C> target, TransitionType transitionType);
    List<Transition<S, E, C>> getEventTransitions(E event);
    Collection<Transition<S, E, C>> getAllTransitions();
}
```

**Transition:**
```java
public interface Transition<S, E, C> {
    State<S, E, C> getSource();
    void setSource(State<S, E, C> state);
    E getEvent();
    void setEvent(E event);
    void setType(TransitionType type);
    State<S, E, C> getTarget();
    void setTarget(State<S, E, C> state);
    Action<S, E, C> getAction();
    void setAction(Action<S, E, C> action);
    Condition<C> getCondition();
    void setCondition(Condition<C> condition);
}
```

## 2. Builder DSL

### 2.1 StateMachineBuilderFactory

Entry point for creating state machine builders.

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();
```

### 2.2 External Transition

Define an external state transition (state changes).

```java
builder.externalTransition()
        .from(ConversationState.NEW)
        .to(ConversationState.INITIATED)
        .on(ConversationFact.CONVERSATION_INITIATED)
        .when(ctx -> ctx.getMarketConfig() != null)  // optional guard
        .perform(new ConversationInitAction());
```

**Builder API order:** `from() → to() → on() → when() → perform()`

### 2.3 Internal Transition

Define an internal transition (state does NOT change, but action executes).

```java
builder.internalTransition()
        .within(ConversationState.IN_PROGRESS)
        .on(ConversationFact.SURVEY_START)
        .perform(new SurveyStartAction());
```

### 2.4 Build and Register

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

### 2.5 Retrieve State Machine

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        StateMachineFactory.get("conversation");
```

## 3. StateMachineFactory

Central registry for state machines.

```java
public class StateMachineFactory {
    /**
     * Register a state machine.
     * @throws StateMachineException if a state machine with the same id already exists
     */
    public static <S, E, C> void register(StateMachine<S, E, C> stateMachine);

    /**
     * Get a registered state machine by id.
     * @throws StateMachineException if no state machine with the given id exists
     */
    public static <S, E, C> StateMachine<S, E, C> get(String machineId);
}
```

**Important:** COLA StateMachine does NOT allow rebuilding a state machine with the same id. Use factory caching pattern to prevent duplicate builds.

## 4. Event Processing

### 4.1 Fire Event

```java
CbolStateContext ctx = buildContext();
ConversationState newState = sm.fireEvent(
        ConversationState.NEW,
        ConversationFact.CONVERSATION_INITIATED,
        ctx);
```

**Return value:** The target state after successful transition.

**Exceptions:**
- `StateMachineException` — if no transition matches for the given source state and event
- `StateMachineException` — if the transition action fails (action-first principle)

### 4.2 Verify Event

```java
boolean canFire = sm.verify(ConversationState.NEW, ConversationFact.CONVERSATION_INITIATED);
```

## 5. Performance Characteristics

| Operation | Complexity | Notes |
|-----------|------------|-------|
| `fireEvent()` | O(1) | HashMap lookup for transitions by event |
| `verify()` | O(1) | HashMap lookup |
| `build()` | O(n) | n = number of transitions |
| Action execution | Synchronous | Blocking, action-first principle |

**Key optimizations:**
- Table-driven transition lookup (ConcurrentHashMap)
- Stateless engine (current state injected by caller)
- Generic type-safe (no reflection)
- Zero external dependencies in core

## 6. PlantUML Diagram Generation

COLA StateMachine can generate PlantUML state diagrams automatically.

```java
String plantUml = sm.generatePlantUML();
System.out.println(plantUml);
```

Output example:
```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
@enduml
```

## 7. Factory Caching Pattern

Since COLA StateMachine does not allow rebuilding, use this caching pattern in factory classes:

```java
public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
    // Try to get existing state machine first
    try {
        StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                StateMachineFactory.get(MACHINE_ID);
        if (existing != null) {
            return existing;
        }
    } catch (Exception ignored) {
        // State machine not built yet
    }

    synchronized (ConversationStateMachineFactory.class) {
        // Double-check after acquiring lock
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (Exception ignored) {
            // State machine not built yet
        }

        // Build and register
        try {
            StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                    StateMachineBuilderFactory.create();
            // ... define transitions ...
            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                    builder.build(MACHINE_ID);
            StateMachineFactory.register(sm);
            return sm;
        } catch (Exception e) {
            // State machine already built, return existing instance
            return StateMachineFactory.get(MACHINE_ID);
        }
    }
}
```

## 8. References

- Alibaba COLA GitHub: https://github.com/alibaba/COLA
- COLA StateMachine module: `cola-components/cola-component-statemachine`
- COLA StateMachine tests: `cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`

---

*Last updated: 2026-09-04 (v3.0 — migrated to Alibaba COLA StateMachine)*
