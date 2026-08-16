# State Machine Integration with AI Messaging Hub

> How the state machine integrates with the AI Messaging Hub session and dialog lifecycle.

## Session State Management

### Storage: Redis Hash
```
Key: session:{sessionId}
Fields:
  - state: INIT | AI_PROCESSING | TRANSFERRING | ...
  - userId: string
  - aiBotId: string
  - agentId: string (nullable)
  - createdAt: timestamp
  - updatedAt: timestamp
```

### Expiration Strategy
- TTL-based expiration (e.g., 24 hours)
- Lazy refresh on access (extend TTL on each state transition)
- Session cleanup job for expired sessions

### Consistency
- State persisted to Redis on **every successful transition**
- Write-through: state machine returns new state → business layer writes to Redis → acknowledge
- Optimistic locking for concurrent updates (if needed)

## Dialog Lifecycle

```
┌──────┐  USER_INPUT   ┌──────────────┐
│ INIT │ ─────────────> │ AI_PROCESSING│
└──────┘   (valid msg)  └──────┬───────┘
                               │
                 AI_RESPONSE   │  TRANSFER_AGENT
                 (continue)    │  (human needed)
                               v
                        ┌──────────────┐
                        │ TRANSFERRING │
                        └──────┬───────┘
                               │
                    AGENT_ACCEPT│  TRANSFER_FAILED
                               │
              ┌────────────────┼────────────────┐
              v                v                v
      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
      │AGENT_CONNECTED│  │TRANSFER_FAILED│  │    ERROR     │
      └──────┬───────┘  └──────┬───────┘  └──────────────┘
             │                 │
   AGENT_RELEASE│          retry│
             │                 │
             v                 v
      ┌──────────────┐  ┌──────────────┐
      │AGENT_HANDLING│  │ AI_PROCESSING│ (fallback)
      └──────┬───────┘  └──────────────┘
             │
   END_CHAT  │  (or AGENT_RELEASE -> CLOSED)
             v
      ┌──────────────┐
      │   CLOSED     │
      └──────────────┘
```

## State Transition Table

| From | Event | To | Guard | Action |
|------|-------|-----|-------|--------|
| INIT | USER_INPUT | AI_PROCESSING | validMessage | log, route to AI |
| AI_PROCESSING | AI_RESPONSE | AI_PROCESSING | - | send AI response |
| AI_PROCESSING | TRANSFER_AGENT | TRANSFERRING | humanAgentRequired | initiate transfer |
| AI_PROCESSING | USER_INPUT | AI_PROCESSING | - | process message |
| TRANSFERRING | AGENT_ACCEPT | AGENT_CONNECTED | agentAvailable | connect agent |
| TRANSFERRING | TRANSFER_FAILED | AI_PROCESSING | - | fallback to AI |
| TRANSFERRING | TIMEOUT | TRANSFER_FAILED | - | alert, fallback |
| AGENT_CONNECTED | AGENT_RELEASE | AI_PROCESSING | - | return to AI |
| AGENT_CONNECTED | USER_INPUT | AGENT_HANDLING | - | route to agent |
| AGENT_HANDLING | AGENT_RELEASE | CLOSED | conversationEnded | close session |
| AGENT_HANDLING | AGENT_RELEASE | AI_PROCESSING | !conversationEnded | return to AI |
| ANY | END_CHAT | CLOSED | - | close session |
| ANY | ERROR | ERROR | - | log, alert |
| ANY | TIMEOUT | TIMEOUT | - | cleanup, notify |

## Integration Points

### 1. AI Bot Provider Integration
```
State: AI_PROCESSING
- Event: USER_INPUT → action: send message to AI Bot API
- Event: AI_RESPONSE → action: forward response to user
- Circuit breaker: wraps AI Bot API calls
  - If circuit OPEN → transition to ERROR or DEGRADED state
```

### 2. Human Agent Platform Integration
```
State: TRANSFERRING
- Event: TRANSFER_AGENT → action: call agent platform API to request agent
- Event: AGENT_ACCEPT → action: establish agent connection
- Event: AGENT_RELEASE → action: cleanup agent session
- Circuit breaker: wraps agent platform API
```

### 3. Message Gateway Integration
```
- All states: incoming messages trigger USER_INPUT event
- State machine determines routing: AI vs Agent
- Outbound messages sent based on current state
```

### 4. Redis State Persistence
```java
@Service
public class SessionStateService {
    
    private final StateMachine<DialogState, DialogEvent, DialogContext> stateMachine;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public DialogState transition(String sessionId, DialogEvent event, DialogContext context) {
        // 1. Load current state
        DialogState currentState = (DialogState) redisTemplate.opsForHash()
            .get("session:" + sessionId, "state");
        
        // 2. Fire event (stateless engine)
        DialogState newState = stateMachine.fireEvent(currentState, event, context);
        
        // 3. Persist new state
        redisTemplate.opsForHash().put("session:" + sessionId, "state", newState);
        redisTemplate.expire("session:" + sessionId, 24, TimeUnit.HOURS);
        
        return newState;
    }
}
```

## Circuit Breaker Integration

### Architecture
```
┌──────────────────────────────────────────────┐
│         State Machine (stateless)             │
│  No circuit breaker needed - pure computation │
└───────────────────┬──────────────────────────┘
                    │ actions call downstream services
                    v
┌──────────────────────────────────────────────┐
│    Resilience4j Circuit Breaker               │
│  Wraps: AI Bot API, Agent Platform API        │
└───────────────────┬──────────────────────────┘
                    │
          ┌─────────┴─────────┐
          v                   v
   Circuit CLOSED       Circuit OPEN
   (normal flow)        (fallback)
          │                   │
          v                   v
   Normal transition   Transition to ERROR/DEGRADED
```

### Circuit Open Handling
```java
@CircuitBreaker(name = "aiBot", fallbackMethod = "aiBotFallback")
public void sendToAiBot(DialogContext context) {
    aiBotClient.sendMessage(context.getMessage());
}

public void aiBotFallback(DialogContext context, Exception ex) {
    // Circuit is open - transition to error state
    stateMachine.fireEvent(currentState, DialogEvent.ERROR, context);
    // Alert operations team
    alertService.notifyAiBotFailure(context.getSessionId());
}
```

## Timeout Handling

Timeouts are managed by the business layer, not the state machine:

```java
@Scheduled(fixedDelay = 60000)
public void checkSessionTimeouts() {
    // Find sessions idle > threshold
    List<String> expiredSessions = redisService.findExpiredSessions();
    
    for (String sessionId : expiredSessions) {
        DialogState currentState = redisService.getState(sessionId);
        DialogContext context = new DialogContext(sessionId);
        
        // Fire TIMEOUT event
        DialogState newState = stateMachine.fireEvent(
            currentState, DialogEvent.TIMEOUT, context);
        
        redisService.saveState(sessionId, newState);
    }
}
```

## Glossary

| Term | Definition |
|------|-----------|
| State Machine | Mathematical model of computation for sequential logic |
| Guard Condition | Boolean expression that must be true for transition |
| Fluent API | API design using method chaining for readability |
| Stateless Engine | Engine stores only rules, not current state |
| External Transition | Transition from one state to another (source → target) |
