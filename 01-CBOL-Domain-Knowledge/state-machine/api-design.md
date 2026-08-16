# State Machine API Design

> API design for the lightweight state machine engine.

## State & Event Enums

### Dialog State Example
```java
public enum DialogState {
    INIT,
    AI_PROCESSING,
    TRANSFERRING,
    AGENT_CONNECTED,
    AGENT_HANDLING,
    TRANSFER_FAILED,
    CLOSED,
    ERROR,
    TIMEOUT
}
```

### Dialog Event Example
```java
public enum DialogEvent {
    USER_INPUT,
    AI_RESPONSE,
    TRANSFER_AGENT,
    AGENT_ACCEPT,
    AGENT_RELEASE,
    END_CHAT,
    TIMEOUT,
    ERROR
}
```

### Context Example
```java
public class DialogContext {
    private String sessionId;
    private String userId;
    private String message;
    private boolean validMessage;
    private boolean humanAgentRequired;
    
    // getters and setters
}
```

## Functional Interfaces

### Condition (Guard)
```java
@FunctionalInterface
public interface Condition<C> {
    boolean test(C context);
}
```

### Action (Perform)
```java
@FunctionalInterface
public interface Action<C> {
    void execute(C context);
}
```

## Configuration API (Builder Pattern)

### Basic Transition
```java
StateMachineBuilder<DialogState, DialogEvent, DialogContext> builder = 
    new StateMachineBuilder<>();

builder.externalTransition()
    .from(DialogState.INIT)
    .to(DialogState.AI_PROCESSING)
    .on(DialogEvent.USER_INPUT)
    .when(ctx -> ctx.isValidMessage())
    .perform(ctx -> log.info("Processing initial user input"));
```

### Transition with Service Call
```java
builder.externalTransition()
    .from(DialogState.AI_PROCESSING)
    .to(DialogState.TRANSFERRING)
    .on(DialogEvent.TRANSFER_AGENT)
    .when(ctx -> ctx.isHumanAgentRequired())
    .perform(ctx -> agentService.initiateTransfer(ctx.getSessionId()));
```

### Self-Transition (Same State)
```java
builder.externalTransition()
    .from(DialogState.AI_PROCESSING)
    .to(DialogState.AI_PROCESSING)
    .on(DialogEvent.AI_RESPONSE)
    .perform(ctx -> aiService.sendResponse(ctx));
```

### Multi-State Source (Any State)
```java
// Either party ends chat from any state
builder.externalTransition()
    .from(DialogState.INIT, DialogState.AI_PROCESSING, 
          DialogState.AGENT_CONNECTED, DialogState.AGENT_HANDLING)
    .to(DialogState.CLOSED)
    .on(DialogEvent.END_CHAT)
    .perform(ctx -> sessionService.closeSession(ctx.getSessionId()));
```

## Core Engine API

```java
public interface StateMachine<S, E, C> {
    
    /**
     * Main entry point to trigger a transition.
     * @param currentState Current state (injected by caller)
     * @param event Triggering event
     * @param context Context data for guards and actions
     * @return New target state
     * @throws IllegalStateTransitionException if no transition rule matches
     * @throws GuardConditionFailedException if guard condition fails (optional)
     */
    S fireEvent(S currentState, E event, C context);
    
    /**
     * Programmatic registration of transition rules.
     */
    void addTransition(S source, E event, S target, 
                       Condition<C> condition, Action<C> action);
    
    /**
     * Introspection: get all supported states.
     */
    Set<S> getSupportedStates();
    
    /**
     * Pre-check validation without firing the event.
     * @return true if transition is valid (rule exists + guard passes)
     */
    boolean isValidTransition(S source, E event, C context);
}
```

## Builder API

```java
public class StateMachineBuilder<S, E, C> {
    
    public TransitionBuilder<S, E, C> externalTransition();
    
    public StateMachine<S, E, C> build();
}

public interface TransitionBuilder<S, E, C> {
    TransitionBuilder<S, E, C> from(S... sourceStates);
    TransitionBuilder<S, E, C> to(S targetState);
    TransitionBuilder<S, E, C> on(E event);
    TransitionBuilder<S, E, C> when(Condition<C> condition);
    TransitionBuilder<S, E, C> perform(Action<C> action);
}
```

## StateMachineRegistry (Optional)

For managing multiple state machine instances:

```java
public class StateMachineRegistry {
    private final Map<String, StateMachine<?, ?, ?>> machines = new ConcurrentHashMap<>();
    
    public void register(String name, StateMachine<?, ?, ?> machine);
    public <S, E, C> StateMachine<S, E, C> get(String name);
    public Set<String> getRegisteredNames();
}
```

## Usage Example (Complete Flow)

```java
// 1. Build state machine (once, at startup)
StateMachine<DialogState, DialogEvent, DialogContext> dialogSM = 
    new StateMachineBuilder<DialogState, DialogEvent, DialogContext>()
        .externalTransition()
            .from(DialogState.INIT)
            .to(DialogState.AI_PROCESSING)
            .on(DialogEvent.USER_INPUT)
            .when(ctx -> ctx.isValidMessage())
        .externalTransition()
            .from(DialogState.AI_PROCESSING)
            .to(DialogState.TRANSFERRING)
            .on(DialogEvent.TRANSFER_AGENT)
            .when(ctx -> ctx.isHumanAgentRequired())
        .build();

// 2. Use in business logic (per request)
public void handleUserInput(String sessionId, String message) {
    // Load current state from Redis
    DialogState currentState = redisService.getState(sessionId);
    
    DialogContext context = new DialogContext(sessionId, message);
    
    // Fire event (stateless - current state injected)
    DialogState newState = dialogSM.fireEvent(currentState, 
        DialogEvent.USER_INPUT, context);
    
    // Persist new state to Redis
    redisService.saveState(sessionId, newState);
}
```

## Error Handling

### Custom Exceptions
```java
public class IllegalStateTransitionException extends RuntimeException {
    private final Object sourceState;
    private final Object event;
    // constructor, getters
}

public class GuardConditionFailedException extends RuntimeException {
    private final Object sourceState;
    private final Object event;
    // constructor, getters
}
```

### Configuration Option: Guard Failure Behavior
```java
// Option 1: Throw exception on guard failure
builder.setGuardFailurePolicy(GuardFailurePolicy.THROW);

// Option 2: Silently ignore (return current state)
builder.setGuardFailurePolicy(GuardFailurePolicy.IGNORE);
```

## Development Constraints (Outsourcing Team)

1. **Mandatory Usage**: State machine usage is mandatory; if-else state management is strictly prohibited
2. **No Direct DB Access**: All persistence via Redis; state machine must not access databases directly
3. **Test Coverage**: All transitions must have corresponding unit tests
4. **Deterministic Guards**: Guard conditions must be deterministic and strictly side-effect free
5. **Governance**: Any new transitions require Architecture Board approval
