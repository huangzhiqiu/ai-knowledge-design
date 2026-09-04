# State Machine Core Framework Design

> Version: 3.1 | Last Updated: 2026-09-05
> Based on Alibaba COLA StateMachine: https://github.com/alibaba/COLA

## 0. Design Principles

### 0.1 Action-First Transition (Core Principle)

The state machine follows the **action-first transition** principle:

> **Action executes BEFORE state change. If action fails, state does NOT change.**

This ensures that business logic (action) is the gatekeeper for state transitions. A transition from state A to state B only completes if the associated action executes successfully.

**Execution order for EXTERNAL transitions (COLA StateMachine):**
1. Guard/Condition check (`when()`) — if false, transition is rejected, state stays at source
2. **Transition action (`perform()`) — failure → exception propagates, state does NOT change**
3. State transition completes, returns target state

**Key points:**
- Transition action is the only action that can block a state transition
- When transition action fails, the source state is preserved and the exception propagates
- COLA StateMachine does NOT throw `StateMachineException` when no transition matches — it returns the source state and invokes `failCallback`

### 0.2 Stateless Design

COLA StateMachine is intentionally designed to be **stateless**:

> Once built, the state machine instance can be safely shared across multiple threads.

**Implications:**
- The state machine does NOT store current state — the caller must pass `sourceState` to `fireEvent()`
- No mutable state during event processing — transitions are immutable once built
- Thread-safe by design — can be used as a singleton in Spring containers

### 0.3 Table-Driven Transition Lookup

Transitions are stored in a `Map<S, State<S,E,C>>` where each `State` contains a `Map<E, List<Transition<S,E,C>>` for O(1) event lookup.

### 0.4 Generic Type-Safe

All core interfaces use Java generics:
- `S` — State type (typically an enum)
- `E` — Event type (typically an enum)
- `C` — Business context type (carries domain data)

No reflection is used at runtime.

---

## 1. Core Abstractions (Alibaba COLA StateMachine)

### 1.1 Package Structure

The core framework is based on Alibaba COLA StateMachine:

| Package | Responsibility | Key Classes |
|---------|----------------|-------------|
| `com.alibaba.cola.statemachine` | Core interfaces | `StateMachine`, `Action`, `Condition`, `State`, `Transition`, `StateMachineFactory`, `StateContext`, `Visitable`, `Visitor` |
| `com.alibaba.cola.statemachine.builder` | Builder DSL | `StateMachineBuilder`, `StateMachineBuilderFactory`, `ExternalTransitionBuilder`, `ExternalTransitionsBuilder`, `InternalTransitionBuilder`, `ExternalParallelTransitionBuilder`, `From`, `To`, `On`, `When`, `Perform`, `FailCallback` |
| `com.alibaba.cola.statemachine.impl` | Core implementations | `StateMachineImpl`, `StateImpl`, `TransitionImpl`, `StateHelper`, `EventTransitions`, `TransitionType`, `Debugger`, `SysOutVisitor`, `PlantUMLVisitor`, `StateMachineException` |
| `com.alibaba.cola.statemachine.exception` | Exceptions | `TransitionFailException` |

### 1.2 StateMachine Interface

The central interface defining the state machine contract.

```java
public interface StateMachine<S, E, C> extends Visitable {

    /**
     * Verify if an event can be fired from the source state.
     * @param sourceStateId the source state
     * @param event the event to verify
     * @return true if at least one transition exists for (sourceState, event)
     */
    boolean verify(S sourceStateId, E event);

    /**
     * Send an event to the state machine.
     *
     * @param sourceState the source state
     * @param event the event to send
     * @param ctx the user defined business context
     * @return the target state after transition (or source state if no transition matches)
     */
    S fireEvent(S sourceState, E event, C ctx);

    /**
     * Send a parallel event to the state machine.
     * Multiple transitions may match, all are executed.
     *
     * @param sourceState the source state
     * @param event the event to send
     * @param ctx the user defined business context
     * @return list of target states after all parallel transitions
     */
    List<S> fireParallelEvent(S sourceState, E event, C ctx);

    /**
     * MachineId is the identifier for a State Machine.
     * @return the machine ID
     */
    String getMachineId();

    /**
     * Use visitor pattern to display the structure of the state machine to stdout.
     */
    void showStateMachine();

    /**
     * Generate a PlantUML state diagram string.
     * @return PlantUML string
     */
    String generatePlantUML();
}
```

**Type Parameters:**
- `S` — State type (typically an enum)
- `E` — Event type (typically an enum)
- `C` — Business context type (carries domain data)

**Key behaviors:**
- `fireEvent()` returns the target state, NOT a wrapper object
- If no transition matches, `fireEvent()` returns the source state and invokes `failCallback`
- `fireParallelEvent()` executes ALL matching transitions and returns all target states
- `verify()` only checks if transitions exist, does NOT check conditions

### 1.3 Action Interface

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
- Actions are executed synchronously before state transition (action-first principle)
- If an action throws an exception, the state does NOT change and the exception propagates
- Actions are stateless and can be shared across multiple transitions
- Can be implemented as lambda expressions or concrete classes

**Usage example:**
```java
// Lambda style
Action<ConversationState, ConversationFact, CbolStateContext> logAction =
    (from, to, event, ctx) -> log.info("Transition: {} -> {} via {}", from, to, event);

// Concrete class style
public class CustomerConnectAction implements Action<ConversationState, ConversationFact, CbolStateContext> {
    @Override
    public void execute(ConversationState from, ConversationState to,
                        ConversationFact event, CbolStateContext ctx) {
        // business logic
    }
}
```

### 1.4 Condition Interface (Guard)

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

**Key points:**
- Conditions are evaluated BEFORE actions
- If condition returns false, the transition is skipped (state stays at source)
- Multiple transitions with the same (source, event) can have different conditions
- The first transition whose condition is satisfied is selected (for `fireEvent`)

**Condition matching logic in `routeTransition()`:**
```java
for (Transition<S, E, C> transition : transitions) {
    if (transition.getCondition() == null) {
        transit = transition;  // unconditional transition, but continue checking
    } else if (transition.getCondition().isSatisfied(ctx)) {
        transit = transition;
        break;  // condition satisfied, select this transition immediately
    }
}
```

**Important:** If an unconditional transition (null condition) appears BEFORE a conditional one, and the conditional one's condition is satisfied, the conditional one is selected (because of the `break`). If the conditional one's condition is NOT satisfied, the unconditional one is selected.

### 1.5 State Interface

Represents a state in the state machine.

```java
public interface State<S, E, C> extends Visitable {

    /**
     * Gets the state identifier.
     * @return the state identifier
     */
    S getId();

    /**
     * Add a single transition to the state.
     * @param event the event of the Transition
     * @param target the target of the transition
     * @param transitionType the type of transition (EXTERNAL, INTERNAL, LOCAL)
     * @return the created transition
     */
    Transition<S, E, C> addTransition(E event, State<S, E, C> target, TransitionType transitionType);

    /**
     * Add multiple transitions (for parallel transitions).
     * @param event the event of the Transitions
     * @param targets the list of target states
     * @param transitionType the type of transition
     * @return list of created transitions
     */
    List<Transition<S, E, C>> addTransitions(E event, List<State<S, E, C>> targets, TransitionType transitionType);

    /**
     * Get all transitions for a specific event.
     * @param event the event
     * @return list of transitions (may be null if no transitions for this event)
     */
    List<Transition<S, E, C>> getEventTransitions(E event);

    /**
     * Get all transitions from this state.
     * @return collection of all transitions
     */
    Collection<Transition<S, E, C>> getAllTransitions();
}
```

**Internal implementation (`StateImpl`):**
- Stores transitions in `EventTransitions` which wraps a `Map<E, List<Transition<S,E,C>>>`
- `getEventTransitions()` returns the list directly (O(1) lookup)
- States are created and managed by the builder during `build()`

### 1.6 Transition Interface

Represents a transition between states.

```java
public interface Transition<S, E, C> {

    State<S, E, C> getSource();
    void setSource(State<S, E, C> state);

    E getEvent();
    void setEvent(E event);

    void setType(TransitionType type);

    State<S, E, C> getTarget();
    void setTarget(State<S, E, C> state);

    Condition<C> getCondition();
    void setCondition(Condition<C> condition);

    Action<S, E, C> getAction();
    void setAction(Action<S, E, C> action);

    /**
     * Do transition from source state to target state.
     * @param ctx the business context
     * @param checkCondition whether to check condition
     * @return the target state (or source state if condition not satisfied)
     */
    State<S, E, C> transit(C ctx, boolean checkCondition);

    /**
     * Verify transition correctness.
     * For INTERNAL transitions, source and target must be the same.
     */
    void verify();
}
```

**`transit()` implementation (action-first principle):**
```java
@Override
public State<S, E, C> transit(C ctx, boolean checkCondition) {
    this.verify();
    if (!checkCondition || condition == null || condition.isSatisfied(ctx)) {
        if (action != null) {
            action.execute(source.getId(), target.getId(), event, ctx);
        }
        return target;
    }
    // Condition not satisfied, stay at source state
    return source;
}
```

**Key points:**
- `verify()` checks that INTERNAL transitions have source == target
- `transit()` executes action BEFORE returning target state
- If condition is not satisfied, returns source state (no action executed)
- Transitions are designed to be immutable after build (thread-safe)

### 1.7 TransitionType Enum

Defines the type of transition.

```java
public enum TransitionType {
    /**
     * Internal transition: does not cause a state change.
     * Source and target must be the same state.
     * Action is executed, but state does not change.
     */
    INTERNAL,

    /**
     * Local transition: does not exit the composite (source) state,
     * but exits and re-enters any state within the composite state.
     */
    LOCAL,

    /**
     * External transition: exits the composite (source) state
     * and enters the target state.
     */
    EXTERNAL
}
```

**Usage in this project:**
- `EXTERNAL` — standard state changes (NEW → INITIATED, INITIATED → IN_PROGRESS, etc.)
- `INTERNAL` — survey as sub-phase (IN_PROGRESS → IN_PROGRESS on SURVEY_START)
- `LOCAL` — not currently used (reserved for future composite states)

### 1.8 StateContext Interface (Internal)

Internal interface used during transition processing. This is NOT the business context — the business context is the generic type `C` passed to `fireEvent()`.

```java
public interface StateContext<S, E, C> {
    /**
     * Gets the transition being processed.
     */
    Transition<S, E, C> getTransition();

    /**
     * Gets the state machine.
     */
    StateMachine<S, E, C> getStateMachine();
}
```

**Note:** In the current COLA implementation, `StateContext` is defined but not actively used in the main `fireEvent()` flow. The business context `C` is passed directly to actions and conditions.

### 1.9 FailCallback Interface

Callback invoked when no transition matches for a (sourceState, event) pair.

```java
@FunctionalInterface
public interface FailCallback<S, E, C> {
    /**
     * Callback function to execute if failed to trigger an Event.
     * @param sourceState the source state
     * @param event the event that failed
     * @param context the business context
     */
    void onFail(S sourceState, E event, C context);
}
```

**Built-in implementations:**
- `NumbFailCallback` — default, does nothing
- `AlertFailCallback` — logs a warning (uses `Debugger.debug()`)

**Usage:**
```java
builder.setFailCallback((source, event, ctx) ->
    log.warn("No transition for state={}, event={}", source, event));
```

**Important:** `failCallback.onFail()` is invoked when `routeTransition()` returns null (no transition matches or all conditions fail). The `fireEvent()` method then returns the source state.

### 1.10 StateMachineFactory

Central registry for state machines.

```java
public class StateMachineFactory {
    static Map<String, StateMachine> stateMachineMap = new ConcurrentHashMap<>();

    /**
     * Register a state machine.
     * @throws StateMachineException if a state machine with the same id already exists
     */
    public static <S, E, C> void register(StateMachine<S, E, C> stateMachine) {
        String machineId = stateMachine.getMachineId();
        if (stateMachineMap.get(machineId) != null) {
            throw new StateMachineException(
                "The state machine with id [" + machineId + "] is already built, no need to build again");
        }
        stateMachineMap.put(stateMachine.getMachineId(), stateMachine);
    }

    /**
     * Get a registered state machine by id.
     * @throws StateMachineException if no state machine with the given id exists
     */
    public static <S, E, C> StateMachine<S, E, C> get(String machineId) {
        StateMachine stateMachine = stateMachineMap.get(machineId);
        if (stateMachine == null) {
            throw new StateMachineException(
                "There is no stateMachine instance for " + machineId + ", please build it first");
        }
        return stateMachine;
    }
}
```

**Key behaviors:**
- Uses `ConcurrentHashMap` for thread-safe registration and lookup
- `register()` throws if machine ID already exists (prevents duplicate builds)
- `get()` throws if machine ID not found
- State machines are stored as raw types (erasure), but cast back on retrieval

---

## 2. Builder DSL

### 2.1 StateMachineBuilderFactory

Entry point for creating state machine builders.

```java
StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
        StateMachineBuilderFactory.create();
```

### 2.2 StateMachineBuilder Interface

```java
public interface StateMachineBuilder<S, E, C> {

    /**
     * Builder for a single external transition.
     */
    ExternalTransitionBuilder<S, E, C> externalTransition();

    /**
     * Builder for multiple external transitions (from multiple source states).
     */
    ExternalTransitionsBuilder<S, E, C> externalTransitions();

    /**
     * Builder for parallel external transitions (to multiple target states).
     */
    ExternalParallelTransitionBuilder<S, E, C> externalParallelTransition();

    /**
     * Builder for an internal transition (state does not change).
     */
    InternalTransitionBuilder<S, E, C> internalTransition();

    /**
     * Set up fail callback, default is NumbFailCallback (does nothing).
     */
    void setFailCallback(FailCallback<S, E, C> callback);

    /**
     * Build the state machine with the given machine ID.
     * @param machineId the unique identifier for this state machine
     * @return the built state machine
     */
    StateMachine<S, E, C> build(String machineId);
}
```

### 2.3 External Transition (Single)

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

**Builder stage interfaces:**
- `From<S,E,C>` — `.from(S state)` returns `To`
- `To<S,E,C>` — `.to(S state)` returns `On`
- `On<S,E,C>` — `.on(E event)` returns `When`
- `When<S,E,C>` — `.when(Condition<C> condition)` returns `Perform`
- `Perform<S,E,C>` — `.perform(Action<S,E,C> action)` completes the transition

### 2.4 External Transitions (Multiple Sources)

Define multiple external transitions from multiple source states to the same target with the same event.

```java
// Multiple source states → same target, same event, same action
builder.externalTransitions()
        .fromAmong(ConversationState.NEW, ConversationState.INITIATED, ConversationState.IN_PROGRESS)
        .to(ConversationState.ERROR)
        .on(ConversationFact.SYS_ACTION_FAILED)
        .perform(new ErrorAction());
```

This is equivalent to defining three separate external transitions.

### 2.5 External Parallel Transition (Multiple Targets)

Define a parallel transition from one source state to multiple target states.

```java
builder.externalParallelTransition()
        .from(ConversationState.IN_PROGRESS)
        .toAmong(ConversationState.ENDING, ConversationState.SURVEY_COMPLETED)
        .on(ConversationFact.CONVERSATION_COMPLETE)
        .perform(new CompleteAction());
```

**When fired with `fireParallelEvent()`:**
- All matching transitions are executed
- All actions are executed (in order)
- Returns a list of all target states

**Note:** Parallel transitions should be used carefully — if two target states are mutually exclusive, this can lead to inconsistent state. The business layer is responsible for handling the list of returned states.

### 2.6 Internal Transition

Define an internal transition (state does NOT change, but action executes).

```java
builder.internalTransition()
        .within(ConversationState.IN_PROGRESS)
        .on(ConversationFact.SURVEY_START)
        .perform(new SurveyStartAction());
```

**Key points:**
- Source and target must be the same state (verified by `Transition.verify()`)
- Action is executed, but state does not change
- Useful for sub-phases within a state (e.g., survey within IN_PROGRESS)

### 2.7 Build and Register

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        builder.build("conversation");
StateMachineFactory.register(sm);
```

**`build()` process (in `StateMachineBuilderImpl`):**
1. Creates all `State` objects for all referenced states
2. Adds all transitions to their source states
3. Creates `StateMachineImpl` with the state map
4. Sets machine ID and marks as ready
5. Returns the state machine

### 2.8 Retrieve State Machine

```java
StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
        StateMachineFactory.get("conversation");
```

---

## 3. Event Processing (Internal Implementation)

### 3.1 fireEvent() Flow

```java
@Override
public S fireEvent(S sourceStateId, E event, C ctx) {
    isReady();  // throws if state machine not built
    Transition<S, E, C> transition = routeTransition(sourceStateId, event, ctx);

    if (transition == null) {
        Debugger.debug("There is no Transition for " + event);
        failCallback.onFail(sourceStateId, event, ctx);
        return sourceStateId;  // return source state, no change
    }

    return transition.transit(ctx, false).getId();  // execute transition
}
```

**Step-by-step:**
1. **isReady()** — check if state machine is built, throw `StateMachineException` if not
2. **routeTransition()** — find the matching transition for (sourceState, event)
3. **If no transition** — invoke `failCallback.onFail()`, return source state
4. **If transition found** — call `transition.transit(ctx, false)` which:
   - Verifies transition correctness
   - Checks condition (if `checkCondition=true`)
   - Executes action (action-first!)
   - Returns target state
5. **Return target state ID**

### 3.2 routeTransition() Logic

```java
private Transition<S, E, C> routeTransition(S sourceStateId, E event, C ctx) {
    State sourceState = getState(sourceStateId);
    List<Transition<S, E, C>> transitions = sourceState.getEventTransitions(event);

    if (transitions == null || transitions.size() == 0) {
        return null;
    }

    Transition<S, E, C> transit = null;
    for (Transition<S, E, C> transition : transitions) {
        if (transition.getCondition() == null) {
            transit = transition;  // unconditional, keep as fallback
        } else if (transition.getCondition().isSatisfied(ctx)) {
            transit = transition;
            break;  // conditional satisfied, select immediately
        }
    }

    return transit;
}
```

**Condition matching algorithm:**
1. Iterate through all transitions for the (source, event) pair
2. If a transition has NO condition (null), mark it as candidate (fallback)
3. If a transition HAS a condition and it's satisfied, select it immediately (break)
4. If no conditional transition is satisfied, return the unconditional one (if exists)
5. If no transition matches at all, return null

**Important implication:** The ORDER of transition definitions matters. Conditional transitions should be defined BEFORE unconditional fallbacks to ensure they are checked first.

### 3.3 transition.transit() Implementation

```java
@Override
public State<S, E, C> transit(C ctx, boolean checkCondition) {
    Debugger.debug("Do transition: " + this);
    this.verify();  // verify INTERNAL transition has source==target

    if (!checkCondition || condition == null || condition.isSatisfied(ctx)) {
        if (action != null) {
            action.execute(source.getId(), target.getId(), event, ctx);  // ACTION FIRST!
        }
        return target;  // state changes
    }

    Debugger.debug("Condition is not satisfied, stay at the " + source + " state");
    return source;  // state does NOT change
}
```

**Action-first principle in code:**
- Action is executed BEFORE returning target state
- If action throws exception, `return target` is never reached
- Exception propagates up to `fireEvent()` caller
- State machine state does NOT change (stateless design, caller manages state)

### 3.4 fireParallelEvent() Flow

```java
@Override
public List<S> fireParallelEvent(S sourceState, E event, C context) {
    isReady();
    List<Transition<S, E, C>> transitions = routeTransitions(sourceState, event, context);
    List<S> result = new ArrayList<>();

    if (transitions == null || transitions.isEmpty()) {
        Debugger.debug("There is no Transition for " + event);
        failCallback.onFail(sourceState, event, context);
        result.add(sourceState);
        return result;
    }

    for (Transition<S, E, C> transition : transitions) {
        S id = transition.transit(context, false).getId();
        result.add(id);
    }
    return result;
}
```

**Difference from `fireEvent()`:**
- `routeTransitions()` returns ALL matching transitions (not just the first)
- All transitions are executed in order
- Returns a list of all target states

### 3.5 verify() Method

```java
@Override
public boolean verify(S sourceStateId, E event) {
    isReady();
    State sourceState = getState(sourceStateId);
    List<Transition<S, E, C>> transitions = sourceState.getEventTransitions(event);
    return transitions != null && transitions.size() != 0;
}
```

**Note:** `verify()` only checks if transitions EXIST for (source, event). It does NOT check if conditions are satisfied. Use `fireEvent()` and check if returned state equals source state to determine if a transition actually occurred.

---

## 4. Visitor Pattern (PlantUML & Debug)

COLA StateMachine uses the Visitor pattern for displaying state machine structure.

### 4.1 Visitable Interface

```java
public interface Visitable {
    String accept(Visitor visitor);
}
```

Both `StateMachine` and `State` extend `Visitable`.

### 4.2 Visitor Interface

```java
public interface Visitor {
    String visitOnEntry(StateMachine<?, ?, ?> stateMachine);
    String visitOnExit(StateMachine<?, ?, ?> stateMachine);
    String visitOnEntry(State<?, ?, ?> state);
    String visitOnExit(State<?, ?, ?> state);
}
```

### 4.3 PlantUMLVisitor

Generates PlantUML state diagram string.

```java
@Override
public String generatePlantUML() {
    PlantUMLVisitor plantUMLVisitor = new PlantUMLVisitor();
    return accept(plantUMLVisitor);
}
```

**Output example:**
```
@startuml
[*] --> NEW
NEW --> INITIATED : CONVERSATION_INITIATED
INITIATED --> IN_PROGRESS : CUSTOMER_CONNECT
IN_PROGRESS --> TRANSFERRED : TRANSFER_REQUEST
IN_PROGRESS --> ENDING : CUSTOMER_CLOSE
@enduml
```

### 4.4 SysOutVisitor

Prints state machine structure to stdout.

```java
@Override
public void showStateMachine() {
    SysOutVisitor sysOutVisitor = new SysOutVisitor();
    accept(sysOutVisitor);
}
```

Useful for debugging state machine structure at runtime.

---

## 5. Performance Characteristics

### 5.1 Complexity Analysis

| Operation | Complexity | Notes |
|-----------|------------|-------|
| `fireEvent()` | O(1) average | HashMap lookup for state, then HashMap lookup for event, then iterate transitions (usually 1-2) |
| `fireParallelEvent()` | O(n) | n = number of matching transitions |
| `verify()` | O(1) | HashMap lookups only |
| `build()` | O(n) | n = number of transitions, creates all State objects |
| Action execution | Synchronous | Blocking, action-first principle |
| `generatePlantUML()` | O(n) | n = number of states + transitions |

### 5.2 Key Optimizations

1. **Table-driven transition lookup** — `Map<S, State>` + `Map<E, List<Transition>>` for O(1) lookup
2. **Stateless engine** — no mutable state during event processing, thread-safe
3. **Immutable transitions** — once built, transitions cannot be modified (thread-safe)
4. **Generic type-safe** — no reflection at runtime
5. **Zero external dependencies in core** — only Java standard library
6. **ConcurrentHashMap in StateMachineFactory** — thread-safe registration and lookup

### 5.3 Thread Safety

**StateMachineImpl is thread-safe because:**
- `stateMap` is immutable after `build()`
- `ready` flag is set once during `build()`
- `machineId` is set once during `build()`
- `failCallback` is set once during `build()`
- No mutable state during `fireEvent()` processing

**Can be safely used as a singleton in Spring:**
```java
@Bean
public StateMachine<ConversationState, ConversationFact, CbolStateContext> conversationStateMachine() {
    return ConversationStateMachineFactory.build();  // cached, returns same instance
}
```

---

## 6. Error Handling

### 6.1 StateMachineException

Runtime exception thrown by the state machine.

```java
public class StateMachineException extends RuntimeException {
    public StateMachineException(String message) {
        super(message);
    }
}
```

**When thrown:**
- State machine not built yet (`isReady()` check)
- Source state not found in state map
- Duplicate machine ID registration in `StateMachineFactory`
- Machine ID not found in `StateMachineFactory.get()`
- INTERNAL transition with source != target (`verify()` check)

**When NOT thrown:**
- No transition matches for (source, event) — returns source state, invokes failCallback
- Condition not satisfied — returns source state
- Action throws exception — propagates the original exception (not wrapped in StateMachineException)

### 6.2 TransitionFailException

```java
public class TransitionFailException extends RuntimeException {
    // thrown in specific transition failure scenarios
}
```

### 6.3 Action Exception Propagation

When an action throws an exception:
1. `transition.transit()` does NOT catch it — propagates directly
2. `fireEvent()` does NOT catch it — propagates directly to caller
3. State does NOT change (action-first principle)
4. Caller is responsible for catching and handling

```java
try {
    ConversationState newState = sm.fireEvent(source, event, ctx);
} catch (RuntimeException e) {
    // Action failed, state remains at source
    log.error("Transition failed", e);
    // Business layer can: retry, fire failover event, or escalate
}
```

---

## 7. Factory Caching Pattern

Since COLA StateMachine does not allow rebuilding a state machine with the same ID, use this caching pattern in factory classes:

```java
public class ConversationStateMachineFactory {
    public static final String MACHINE_ID = "conversation";

    public static StateMachine<ConversationState, ConversationFact, CbolStateContext> build() {
        // Fast path: try to get existing state machine first
        try {
            StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                    StateMachineFactory.get(MACHINE_ID);
            if (existing != null) {
                return existing;
            }
        } catch (StateMachineException ignored) {
            // State machine not built yet
        }

        // Slow path: build with double-checked locking
        synchronized (ConversationStateMachineFactory.class) {
            // Double-check after acquiring lock
            try {
                StateMachine<ConversationState, ConversationFact, CbolStateContext> existing =
                        StateMachineFactory.get(MACHINE_ID);
                if (existing != null) {
                    return existing;
                }
            } catch (StateMachineException ignored) {
                // State machine not built yet
            }

            // Build and register
            StateMachineBuilder<ConversationState, ConversationFact, CbolStateContext> builder =
                    StateMachineBuilderFactory.create();

            // ... define transitions ...

            StateMachine<ConversationState, ConversationFact, CbolStateContext> sm =
                    builder.build(MACHINE_ID);
            StateMachineFactory.register(sm);
            return sm;
        }
    }
}
```

**Why this pattern:**
- `StateMachineFactory.register()` throws if machine ID already exists
- `StateMachineFactory.get()` throws if machine ID not found
- Double-checked locking ensures only one thread builds the state machine
- Fast path avoids synchronization on subsequent calls

---

## 8. Best Practices

### 8.1 State Management

**DO:**
- Manage current state in the business layer (database, entity, etc.)
- Pass source state explicitly to `fireEvent()`
- Update entity state after successful `fireEvent()`

```java
ConversationState currentState = conversation.getState();
ConversationState newState = sm.fireEvent(currentState, event, ctx);
conversation.setState(newState);
```

**DON'T:**
- Expect the state machine to store current state (it's stateless!)
- Call `fireEvent()` without knowing the current source state
- Ignore the return value of `fireEvent()`

### 8.2 Action Design

**DO:**
- Make actions stateless (no instance fields that mutate)
- Make actions idempotent (safe to retry)
- Keep actions focused on single responsibility
- Use constructor injection for dependencies

```java
public class CustomerConnectAction implements Action<ConversationState, ConversationFact, CbolStateContext> {
    private final NotificationService notificationService;

    public CustomerConnectAction(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void execute(ConversationState from, ConversationState to,
                        ConversationFact event, CbolStateContext ctx) {
        notificationService.sendWelcome(ctx.conversation().getCustomerId());
    }
}
```

**DON'T:**
- Put stateful fields in actions (they're shared across transitions)
- Throw checked exceptions from actions (wrap in RuntimeException if needed)
- Perform heavy IO in actions without considering timeouts

### 8.3 Condition Design

**DO:**
- Keep conditions simple and fast (no IO)
- Use conditions for business rule checks
- Consider using a default unconditional transition as fallback

**DON'T:**
- Put side effects in conditions (they may be evaluated multiple times)
- Use conditions for actions (use `perform()` instead)

### 8.4 Transition Order

**DO:**
- Define conditional transitions BEFORE unconditional fallbacks
- Use meaningful event names that describe what happened

**DON'T:**
- Define multiple unconditional transitions for the same (source, event) — only the last one will be used as fallback

### 8.5 Testing

**DO:**
- Test each transition independently
- Test condition true/false branches
- Test action failure scenarios
- Test invalid (source, event) pairs
- Use unique machine IDs in tests to avoid conflicts

```java
class ConversationStateMachineTest {
    private static final String TEST_MACHINE_ID = "conversation-test-" + UUID.randomUUID();

    @Test
    void shouldTransitionFromNewToInitiated() {
        StateMachine<ConversationState, ConversationFact, CbolStateContext> sm = buildTestMachine(TEST_MACHINE_ID);
        CbolStateContext ctx = buildTestContext();

        ConversationState result = sm.fireEvent(ConversationState.NEW, ConversationFact.CONVERSATION_INITIATED, ctx);

        assertEquals(ConversationState.INITIATED, result);
    }
}
```

---

## 9. Comparison with Other Frameworks

| Feature | COLA StateMachine | Spring StateMachine | Sqllin StateMachine |
|---------|-------------------|---------------------|---------------------|
| Stateless | Yes | No (has extended state) | Yes |
| Action-first | Yes | No (action after transition) | Yes |
| Builder DSL | Fluent | ConfigurerAdapter | Fluent |
| PlantUML generation | Built-in | Via support | No |
| Parallel transitions | Yes | Yes (regions) | No |
| External dependencies | None | Spring Context | None |
| Learning curve | Low | Medium | Low |
| Performance | High (O(1) lookup) | Medium | High |

**Why COLA StateMachine for this project:**
1. **Stateless** — fits our architecture where state is managed in the business layer
2. **Action-first** — ensures business logic is the gatekeeper for state changes
3. **Zero dependencies** — lightweight, can be used in any Java project
4. **Simple API** — easy to learn and use
5. **PlantUML generation** — automatic documentation
6. **Alibaba proven** — used in production at Alibaba

---

## 10. References

- Alibaba COLA GitHub: https://github.com/alibaba/COLA
- COLA StateMachine module: `cola-components/cola-component-statemachine`
- COLA StateMachine source: `cola-components/cola-component-statemachine/src/main/java/com/alibaba/cola/statemachine/`
- COLA StateMachine tests: `cola-components/cola-component-statemachine/src/test/java/com/alibaba/cola/test/`
- COLA StateMachine author: Frank Zhang (https://github.com/FrankZhang007)

---

*Last updated: 2026-09-05 (v3.1 — detailed core framework documentation based on actual COLA source code)*
