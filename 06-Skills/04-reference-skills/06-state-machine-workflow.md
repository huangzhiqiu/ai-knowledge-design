# 06 — State Machine & Workflow Engine

> Reference projects for FSM-based workflows, YAML-defined state machines, agent orchestration engines, and workflow management.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [AxGord/claude-workflow](#1-axgordclaude-workflow) | AxGord | YAML FSM state machine plugin |
| 2 | [slang-workflows](#2-slang-workflows) | shofer-dev | Agent SDK orchestration with mailbox |
| 3 | [Self-Developed Lightweight State Machine](#3-self-developed-lightweight-state-machine) | CBOL project | Custom lightweight FSM design |

---

## 1. AxGord/claude-workflow

**URL**: https://github.com/AxGord/claude-workflow
**Author**: AxGord
**Last updated**: 2026-08-05

### Description
A Claude Code plugin that drives agents through YAML-defined state machines. The engine tracks state, enforces guards, manages nested sub-workflow stacks, and visualizes everything in a web dashboard.

### Key Features
- **FSM-based state machines**: Define workflows in YAML with states, transitions, and prompts
- **Stack-based sub-workflows**: States can push nested workflows (max depth 10), auto-pop on completion
- **Three-tier loading**: Bundled templates < global (`~/.claude/workflows/`) < project
- **Web dashboard**: Visualize workflow state and progress
- **Guard enforcement**: Conditions must be met before transitions
- **State tracking**: Engine maintains current state and history

### YAML Workflow Example
```yaml
name: message-processing
states:
  - id: intake
    prompt: "Receive and validate incoming message"
    on_complete: validate
    guards:
      - message_not_empty
      - sender_authenticated

  - id: validate
    prompt: "Validate message content and format"
    on_complete:
      valid: ai_process
      invalid: reject
    guards:
      - format_valid

  - id: ai_process
    prompt: "Process message with AI"
    on_complete:
      need_agent: transfer
      complete: respond
    sub_workflow: ai-processing-pipeline

  - id: transfer
    prompt: "Transfer to human agent"
    on_complete: agent_connected

  - id: respond
    prompt: "Send response to user"
    on_complete: archive

  - id: archive
    prompt: "Archive message and metadata"
    on_complete: done

  - id: reject
    prompt: "Reject invalid message"
    on_complete: done
```

### Architecture
```
┌─────────────────────────────────────────┐
│           Workflow Engine                │
│  - State tracking                        │
│  - Guard enforcement                     │
│  - Sub-workflow stack (max depth 10)    │
│  - Transition routing                     │
└───────────────┬─────────────────────────┘
                │
        ┌───────┴───────┐
        ↓               ↓
   ┌─────────┐    ┌──────────────┐
   │ YAML    │    │ Web Dashboard│
   │ Workflow│    │ (visualize)  │
   └─────────┘    └──────────────┘
```

### CBOL Relevance
- **YAML-defined FSM**: Reference for defining CBOL conversation state machine in YAML
- **Guard enforcement**: Pattern for enforcing transition conditions (e.g., message validated before AI processing)
- **Sub-workflow stacks**: Nested workflows for complex message processing (e.g., AI processing pipeline)
- **Three-tier loading**: Project-specific vs global workflow definitions
- **Web dashboard**: Reference for visualizing CBOL conversation state
- **State tracking**: Engine maintains conversation lifecycle state
- **Direct application**: CBOL conversation states (INIT, AI_PROCESSING, TRANSFERRING, AGENT_CONNECTED, etc.) map perfectly to this pattern

---

## 2. slang-workflows

**URL**: https://github.com/shofer-dev/claude-code-slang-orchestrator/blob/main/README.md
**Author**: shofer-dev
**Last updated**: 2026-07-24

### Description
Workflow orchestration using Agent SDK sessions. Dispatches each stake to an Agent SDK session (one long-lived session per agent, resumed across rounds), checks every result against output contract (retrying on failure), routes through mailbox, repeats until converge condition or round budget met.

### Key Features
- **Agent SDK sessions**: One long-lived session per agent, resumed across rounds
- **Output contract checking**: Every result validated against contract, retry on failure
- **Mailbox routing**: Messages routed between agents via mailbox
- **Converge condition**: Workflow repeats until condition met or round budget
- **Mermaid topology**: Runs render as Mermaid topology and trace diagrams
- **esviz**: Execution visualization

### Architecture
```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Agent A    │←───→│   Mailbox    │←───→│  Agent B    │
│  (long-lived│     │   (routing)  │     │  (long-lived│
│   session)  │     └──────┬───────┘     │   session)  │
└──────┬──────┘            │               └──────┬──────┘
       │                   │                      │
       ↓                   ↓                      ↓
┌───────────────────────────────────────────────────────┐
│              Output Contract Checker                    │
│  - Validate every result against contract               │
│  - Retry on failure                                     │
│  - Converge condition / round budget                    │
└───────────────────────────────────────────────────────┘
```

### CBOL Relevance
- **Long-lived agent sessions**: Reference for maintaining AI agent sessions across multiple messages
- **Output contract checking**: Pattern for validating AI responses before sending to user
- **Mailbox routing**: Message routing between AI agents in CBOL
- **Converge condition**: Workflow repeats until message processing complete
- **Mermaid visualization**: Reference for visualizing CBOL workflow execution
- **Retry on failure**: Pattern for retrying failed AI processing

---

## 3. Self-Developed Lightweight State Machine

**URL**: Local project document (SelfDevelopedLightweightStateMachineDesignDocument.docx)
**Author**: CBOL project team
**Type**: Custom design document

### Description
Custom lightweight state machine design for CBOL project. Stateless engine that only stores transition rules, with current state injected by business layer. Table-driven using ConcurrentHashMap for O(1) lookup. Zero external dependencies. Generic type-safe.

### Key Features
- **Stateless engine**: Only stores transition rules, current state injected by business layer
- **Table-driven**: ConcurrentHashMap for O(1) transition lookup
- **Zero external dependencies**: No Spring StateMachine or other libraries
- **Generic type-safe**: Type-safe state and event definitions
- **Reference**: COLA StateMachine design philosophy (but no code import)

### CBOL Conversation States
```
INIT → AI_PROCESSING → TRANSFERRING → AGENT_CONNECTED → AGENT_HANDLING → CLOSED
                    ↘                ↗
                     TRANSFER_FAILED
                    ↗                ↘
ERROR ←─────────────────────────────────── TIMEOUT
```

### CBOL Relevance
- **Primary reference**: This is CBOL's own state machine design
- **Stateless pattern**: Engine doesn't store state, business layer injects — perfect for distributed messaging
- **Table-driven**: O(1) lookup for high-performance message processing
- **Zero dependencies**: Lightweight, no library bloat
- **Direct application**: Conversation lifecycle (INIT, AI_PROCESSING, TRANSFERRING, etc.)
- **Integration with AxGord pattern**: Can combine YAML workflow definition with lightweight engine execution

---

## Summary: State Machine Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| YAML-defined FSM | AxGord | Define conversation state machine in YAML |
| Guard enforcement | AxGord | Enforce transition conditions (message validated before AI) |
| Sub-workflow stacks | AxGord | Nested workflows for AI processing pipeline |
| Stateless engine | CBOL custom | Business layer injects state, engine only has rules |
| Table-driven O(1) lookup | CBOL custom | High-performance transition lookup |
| Zero dependencies | CBOL custom | Lightweight, no library bloat |
| Long-lived agent sessions | slang | Maintain AI agent sessions across messages |
| Output contract checking | slang | Validate AI responses before sending |
| Mailbox routing | slang | Message routing between AI agents |
| Converge condition | slang | Repeat until message processing complete |
| Mermaid visualization | AxGord, slang | Visualize conversation state and workflow |
| Web dashboard | AxGord | Monitor conversation state in real-time |
| Three-tier loading | AxGord | Project vs global workflow definitions |
| COLA StateMachine philosophy | CBOL custom | Design reference for custom engine |

---

*State Machine & Workflow Engine Reference — 2026-08-24*
