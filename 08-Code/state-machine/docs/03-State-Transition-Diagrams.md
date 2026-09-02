# State Transition Diagrams & Tables

> Version: 1.0 | Last Updated: 2026-09-01

## 1. Conversation State Machine

### 1.1 Full State Diagram

```mermaid
stateDiagram-v2
    [*] --> INITIATED : Create conversation

    INITIATED --> ACTIVE : CUSTOMER_CONNECT
    INITIATED --> ENDING : SYS_CUSTOMER_IDLE

    ACTIVE --> TRANSFERRED : TRANSFER_REQUEST
    ACTIVE --> SURVEY_IN_PROGRESS : SURVEY_START (surveyEnabled)
    ACTIVE --> ENDING : CUSTOMER_CLOSE (no survey)
    ACTIVE --> ENDING : SYS_CUSTOMER_IDLE

    TRANSFERRED --> ACTIVE : TRANSFER_CONNECTED (reserved)
    TRANSFERRED --> INITIATED : TRANSFER_FAILED
    TRANSFERRED --> INITIATED : TRANSFER_TIMEOUT
    TRANSFERRED --> INITIATED : SYS_TRANSFER_TIMEOUT
    TRANSFERRED --> SURVEY_IN_PROGRESS : SURVEY_START (surveyEnabled)
    TRANSFERRED --> ENDING : SYS_CUSTOMER_IDLE

    SURVEY_IN_PROGRESS --> ENDING : SURVEY_COMPLETE
    SURVEY_IN_PROGRESS --> ENDING : SYS_SURVEY_TIMEOUT
    SURVEY_IN_PROGRESS --> ENDING : SYS_CUSTOMER_IDLE
    SURVEY_IN_PROGRESS --> ENDING : CUSTOMER_CLOSE

    ENDING --> CLOSED : SYS_ENDING_GRACE_TIMEOUT

    %% Failover: action error → SYS_ACTION_FAILED → ERROR → retry/abort
    INITIATED --> ERROR : SYS_ACTION_FAILED
    ACTIVE --> ERROR : SYS_ACTION_FAILED
    TRANSFERRED --> ERROR : SYS_ACTION_FAILED
    SURVEY_IN_PROGRESS --> ERROR : SYS_ACTION_FAILED
    ERROR --> ACTIVE : SYS_RETRY
    ERROR --> CLOSED : SYS_ABORT

    CLOSED --> [*]
```

> **Survey as in-progress state**: When `surveyEnabled=true`, `closeConversation()` fires `SURVEY_START` to enter `SURVEY_IN_PROGRESS` instead of directly going to `ENDING`. The survey flow is fully controlled by the state machine.

> **Failover (action error → fail event)**: When an action throws an unhandled exception, `FailoverStateMachine` automatically fires `SYS_ACTION_FAILED` to enter `ERROR` state. From `ERROR`, the system can retry (`SYS_RETRY` → ACTIVE) or abort (`SYS_ABORT` → CLOSED). Fail events themselves do NOT trigger another failover (loop prevention).

### 1.2 Transition Table

| ID | Source | Event | Target | Guard | Action | Monitor | v6 Note |
|----|--------|-------|--------|-------|--------|---------|---------|
| T01 | INITIATED | CUSTOMER_CONNECT | ACTIVE | - | - | - | Customer establishes connection |
| T02 | ACTIVE | TRANSFER_REQUEST | TRANSFERRED | transferEnabled | - | - | Request human agent |
| T03 | TRANSFERRED | TRANSFER_CONNECTED | ACTIVE | - | - | - | Reserved for future |
| T04 | TRANSFERRED | TRANSFER_FAILED | INITIATED | - | - | - | **v6: no rollback to ACTIVE** |
| T05 | TRANSFERRED | TRANSFER_TIMEOUT | INITIATED | - | - | - | **v6: no rollback to ACTIVE** |
| T06 | ACTIVE | CUSTOMER_CLOSE | ENDING | !surveyEnabled | - | - | Customer closes, no survey |
| T07 | INITIATED | SYS_CUSTOMER_IDLE | ENDING | - | - | CustomerIdleMonitor | Idle before connect |
| T08 | ACTIVE | SYS_CUSTOMER_IDLE | ENDING | - | - | CustomerIdleMonitor | Customer idle timeout |
| T09 | TRANSFERRED | SYS_CUSTOMER_IDLE | ENDING | - | - | CustomerIdleMonitor | Idle during transfer |
| T10 | TRANSFERRED | SYS_TRANSFER_TIMEOUT | INITIATED | - | - | TransferMonitor | **v6: no rollback** |
| T11 | ENDING | SYS_ENDING_GRACE_TIMEOUT | CLOSED | - | - | EndingGraceMonitor | Terminal transition |
| T12 | ACTIVE | SURVEY_START | SURVEY_IN_PROGRESS | surveyEnabled | Show survey UI | - | **Survey as in-progress state** |
| T13 | TRANSFERRED | SURVEY_START | SURVEY_IN_PROGRESS | surveyEnabled | Show survey UI | - | **Survey as in-progress state** |
| T14 | SURVEY_IN_PROGRESS | SURVEY_COMPLETE | ENDING | - | Save survey results | - | Survey completed normally |
| T15 | SURVEY_IN_PROGRESS | SYS_SURVEY_TIMEOUT | ENDING | - | Save partial results | SurveyTimeoutMonitor | Survey timed out |
| T16 | SURVEY_IN_PROGRESS | SYS_CUSTOMER_IDLE | ENDING | - | - | CustomerIdleMonitor | Customer left during survey |
| T17 | SURVEY_IN_PROGRESS | CUSTOMER_CLOSE | ENDING | - | - | - | Customer explicitly closes survey |
| T18 | INITIATED | SYS_ACTION_FAILED | ERROR | - | Log error, alert | FailoverStateMachine | **Failover: action error** |
| T19 | ACTIVE | SYS_ACTION_FAILED | ERROR | - | Log error, alert | FailoverStateMachine | **Failover: action error** |
| T20 | TRANSFERRED | SYS_ACTION_FAILED | ERROR | - | Log error, alert | FailoverStateMachine | **Failover: action error** |
| T21 | SURVEY_IN_PROGRESS | SYS_ACTION_FAILED | ERROR | - | Log error, alert | FailoverStateMachine | **Failover: action error** |
| T22 | ERROR | SYS_RETRY | ACTIVE | - | Re-initialize resources | - | Manual or system retry |
| T23 | ERROR | SYS_ABORT | CLOSED | - | Clean up, notify | - | Unrecoverable error |

### 1.3 State Entry/Exit Actions

| State | Entry Action | Exit Action |
|-------|-------------|-------------|
| INITIATED | (none) | (none) |
| ACTIVE | Send welcome message (reserved) | Notify channel (reserved) |
| TRANSFERRED | Initiate transfer request | Cancel pending transfer (reserved) |
| SURVEY_IN_PROGRESS | Show survey UI, start survey timer | Save survey results, stop timer |
| ENDING | Start grace period timer | (none) |
| ERROR | Log error, alert on-call, capture diagnostics | Clear error state |
| CLOSED | Clean up resources, archive conversation | (none, terminal) |

## 2. Interaction State Machine

### 2.1 States

```java
public enum InteractionState {
    CONNECTING,     // Channel establishing connection
    CONNECTED,      // Channel active, communication flowing
    RECONNECTING,   // Channel dropped, attempting reconnection
    HELD,           // Customer on hold (agent-initiated)
    TRANSFERRING,   // Channel transfer in progress (e.g., WebSocket handoff)
    DISCONNECTED    // Channel terminated (terminal)
}
```

### 2.2 Events (InteractionFact)

```java
public enum InteractionFact {
    // Connection lifecycle
    CONNECTION_ESTABLISHED, CONNECTION_FAILED, CONNECTION_DROPPED,
    RECONNECT_SUCCESS, RECONNECT_FAILED, RECONNECT_EXHAUSTED, CLOSE_REQUEST,
    // Hold
    HOLD_REQUEST, HOLD_RESUME,
    // Transfer (channel-level)
    TRANSFER_START, TRANSFER_COMPLETE, TRANSFER_FAILED
}
```

### 2.3 State Diagram

```mermaid
stateDiagram-v2
    [*] --> CONNECTING

    CONNECTING --> CONNECTED : CONNECTION_ESTABLISHED
    CONNECTING --> DISCONNECTED : CONNECTION_FAILED

    CONNECTED --> RECONNECTING : CONNECTION_DROPPED
    CONNECTED --> HELD : HOLD_REQUEST
    CONNECTED --> TRANSFERRING : TRANSFER_START
    CONNECTED --> DISCONNECTED : CLOSE_REQUEST

    RECONNECTING --> CONNECTED : RECONNECT_SUCCESS
    RECONNECTING --> DISCONNECTED : RECONNECT_FAILED
    RECONNECTING --> DISCONNECTED : RECONNECT_EXHAUSTED

    HELD --> CONNECTED : HOLD_RESUME
    HELD --> DISCONNECTED : CLOSE_REQUEST

    TRANSFERRING --> CONNECTED : TRANSFER_COMPLETE
    TRANSFERRING --> CONNECTED : TRANSFER_FAILED
    TRANSFERRING --> DISCONNECTED : CLOSE_REQUEST

    DISCONNECTED --> [*]
```

### 2.4 Transition Table

| ID | Source | Event | Target | Notes |
|----|--------|-------|--------|-------|
| I01 | CONNECTING | CONNECTION_ESTABLISHED | CONNECTED | Channel connected successfully |
| I02 | CONNECTING | CONNECTION_FAILED | DISCONNECTED | Connection failed (network/auth) |
| I03 | CONNECTED | CONNECTION_DROPPED | RECONNECTING | Active connection dropped unexpectedly |
| I04 | CONNECTED | CLOSE_REQUEST | DISCONNECTED | Explicit close (customer/agent) |
| I05 | RECONNECTING | RECONNECT_SUCCESS | CONNECTED | Reconnection attempt succeeded |
| I06 | RECONNECTING | RECONNECT_FAILED | DISCONNECTED | Reconnection attempt failed |
| I07 | RECONNECTING | RECONNECT_EXHAUSTED | DISCONNECTED | Max reconnection retries reached |
| I08 | CONNECTED | HOLD_REQUEST | HELD | Agent puts customer on hold |
| I09 | HELD | HOLD_RESUME | CONNECTED | Customer retrieved from hold |
| I10 | HELD | CLOSE_REQUEST | DISCONNECTED | Close while on hold |
| I11 | CONNECTED | TRANSFER_START | TRANSFERRING | Channel transfer initiated |
| I12 | TRANSFERRING | TRANSFER_COMPLETE | CONNECTED | Transfer completed, on new channel |
| I13 | TRANSFERRING | TRANSFER_FAILED | CONNECTED | Transfer failed, stay on original channel |
| I14 | TRANSFERRING | CLOSE_REQUEST | DISCONNECTED | Close during transfer |

## 3. Monitor Trigger Diagrams

### 3.1 CustomerIdleMonitor

```mermaid
flowchart LR
    A[Conversation in<br/>INITIATED/ACTIVE/TRANSFERRED] --> B{lastActivityTs<br/>+ customerIdleSeconds<br/>< now?}
    B -->|No| C[No action]
    B -->|Yes| D[fire SYS_CUSTOMER_IDLE]
    D --> E[State -> ENDING]
```

### 3.2 TransferMonitor

```mermaid
flowchart LR
    A[Conversation in<br/>TRANSFERRED] --> B{transferStartTs<br/>+ transferTimeoutSeconds<br/>< now?}
    B -->|No| C[No action]
    B -->|Yes| D[fire SYS_TRANSFER_TIMEOUT]
    D --> E[State -> INITIATED<br/>v6: no rollback]
```

### 3.3 EndingGraceMonitor

```mermaid
flowchart LR
    A[Conversation in<br/>ENDING] --> B{endingStartTs<br/>+ endingGraceSeconds<br/>< now?}
    B -->|No| C[No action]
    B -->|Yes| D[fire SYS_ENDING_GRACE_TIMEOUT]
    D --> E[State -> CLOSED<br/>terminal]
```

## 4. Event Classification

### 4.1 By Source

| Category | Events | Source |
|----------|--------|--------|
| Customer-driven | CUSTOMER_CONNECT, CUSTOMER_CLOSE | Customer action via channel |
| Agent-driven | AGENT_ATTACHED, AGENT_CLOSE | Human agent action (reserved) |
| System-driven | TRANSFER_REQUEST, TRANSFER_FAILED, TRANSFER_TIMEOUT | Business logic / integration |
| Monitor-driven | SYS_CUSTOMER_IDLE, SYS_TRANSFER_TIMEOUT, SYS_ENDING_GRACE_TIMEOUT | Scheduled monitors |
| Survey-driven | SURVEY_COMPLETE | Post-conversation survey (reserved) |

### 4.2 By Effect

| Effect | Events |
|--------|--------|
| State advances (normal) | CUSTOMER_CONNECT, TRANSFER_REQUEST, CUSTOMER_CLOSE |
| State resets (v6) | TRANSFER_FAILED, TRANSFER_TIMEOUT, SYS_TRANSFER_TIMEOUT |
| State enters ending | SYS_CUSTOMER_IDLE (from any non-terminal) |
| State terminates | SYS_ENDING_GRACE_TIMEOUT |
| No state change (internal) | (none currently defined) |

## 5. Default Market Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| customerIdleSeconds | 300 (5 min) | Customer inactivity before auto-close |
| transferTimeoutSeconds | 180 (3 min) | Max wait for agent connection |
| endingGraceSeconds | 120 (2 min) | Grace period before final closure |
| surveyEnabled | false | Whether to send post-conversation survey |
| transferEnabled | true | Whether human agent transfer is allowed |
| genesysEnabled | false | Whether Genesys integration is active |
| fallbackRoutingStrategy | "DROP" | Strategy when transfer fails |

## 6. State Machine Lifecycle

### 6.1 Startup Phase

```mermaid
flowchart TD
    A[Application Startup] --> B[ConversationStateMachineFactory.build]
    B --> C[Define 23 Transition rules<br/>T01-T23]
    C --> D[StateMachineValidator<br/>8 build-time rules]
    D --> E{Validation passed?}
    E -->|No| F[Throw IllegalStateException<br/>with all validation errors]
    E -->|Yes| G[Register in StateMachineRegistry]
    G --> H[State machine ready<br/>id = conversation-sm]
```

### 6.2 Event Processing Phase

```mermaid
flowchart TD
    A[ChatEngineStateMachineService.fire] --> B[Load conversation from DB]
    B --> C[Resolve market config]
    C --> D[fireEvent sourceState, event, context]

    D --> E{Transition exists<br/>for source+event?}
    E -->|No| F[Throw StateMachineException<br/>No transition found]
    E -->|Yes| G{Guard satisfied?}

    G -->|No| H[Throw StateMachineException<br/>Guard rejected transition]
    G -->|Yes| I[Execute exit action<br/>best-effort]

    I --> J[Execute transition action]
    J --> K{Action threw exception?}

    K -->|Yes| L[FailoverStateMachine intercepts]
    L --> M[Generate SYS_ACTION_FAILED event]
    M --> N[Re-fire fail event<br/>→ enter ERROR state]
    N --> O{Fail event also failed?}
    O -->|Yes| P[Throw StateMachineException<br/>loop prevention]
    O -->|No| Q[Continue with ERROR state result]

    K -->|No| R[Execute entry action<br/>best-effort]
    Q --> R
    R --> S[Notify listeners<br/>8 callback points]
    S --> T[Return StateContext]

    F --> U[Exception propagates to caller]
    H --> U
    P --> U

    T --> V[Caller updates conversation state<br/>optimistic lock + version]
    V --> W{CAS succeeded?}
    W -->|No| X[Retry with backoff<br/>max 3 attempts]
    X --> V
    W -->|Yes| Y[Persist StateTransitionRecord<br/>audit log]
    Y --> Z[Done]
    U --> Z
```

### 6.3 Lifecycle Callbacks

| Phase | Listener Callback | When |
|-------|-------------------|------|
| State machine start | `stateMachineStarted()` | After `start()` called |
| Event received | `eventReceived(event)` | Before transition lookup |
| Transition accepted | `transitionAccepted(transition)` | After guard passes, before actions |
| Transition rejected | `transitionRejected(event, source)` | No transition or guard failed |
| State entered | `stateEntered(state)` | After entry action executes |
| State exited | `stateExited(state)` | After exit action executes |
| Action executed | `actionExecuted(action, result)` | After transition action completes |
| State machine stop | `stateMachineStopped()` | After `stop()` called |
