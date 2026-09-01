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

## 2. Interaction State Machine (Reserved)

### 2.1 States

```java
public enum InteractionState {
    CONNECTING,     // Channel establishing connection
    CONNECTED,      // Channel active, messages flowing
    RECONNECTING,   // Channel dropped, attempting reconnect
    DISCONNECTED    // Channel terminated
}
```

### 2.2 State Diagram (Reserved)

```mermaid
stateDiagram-v2
    [*] --> CONNECTING
    CONNECTING --> CONNECTED : CONNECTION_ESTABLISHED
    CONNECTING --> DISCONNECTED : CONNECTION_FAILED
    CONNECTED --> RECONNECTING : CONNECTION_DROPPED
    RECONNECTING --> CONNECTED : RECONNECT_SUCCESS
    RECONNECTING --> DISCONNECTED : RECONNECT_FAILED / MAX_RETRIES
    CONNECTED --> DISCONNECTED : CLOSE_REQUEST
    DISCONNECTED --> [*]
```

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

```mermaid
flowchart TD
    A[Application Startup] --> B[ConversationStateMachineFactory.build()]
    B --> C[Create 10 Transition rules]
    C --> D[Register in CbolStateMachineRegistry]
    D --> E[State machine ready]

    E --> F[CbolStateMachineService.fire()]
    F --> G[fireEvent(sourceState, event, context)]
    G --> H{Transition exists?}
    H -->|No| I[throw StateMachineException]
    H -->|Yes| J{Guard satisfied?}
    J -->|No| K[Next candidate / throw]
    J -->|Yes| L[Execute exit action]
    L --> M[Execute transition action]
    M --> N[Execute entry action]
    N --> O[Notify listeners]
    O --> P[Return StateContext]

    P --> Q[Caller updates conversation state in DB]
```
