# 05 — Multi-Agent Collaboration

> Reference projects for agent swarms, team coordination, inter-agent messaging, mailbox communication, and multi-agent orchestration.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [Claude Swarm Orchestration](#1-claude-swarm-orchestration) | MaTriXy | Swarm with inbox messaging |
| 2 | [Orchestrating Swarms Skill](#2-orchestrating-swarms-skill) | kieranklaassen | TeammateTool + Task system guide |
| 3 | [Agent Swarms & Teams Docs](#3-agent-swarms--teams-docs) | AppGambitStudio | SendMessage + mailbox docs |
| 4 | [Recursive Agent Architecture](#4-recursive-agent-architecture) | junwatu | Self-spawning agents + messaging |
| 5 | [Three Multi-Agent Modes](#5-three-multi-agent-modes) | DeepSeek | Coordinator/Swarm/Fork analysis |

---

## 1. Claude Swarm Orchestration

**URL**: https://github.com/MaTriXy/claude-swarm-orchestration
**Author**: MaTriXy
**Last updated**: 2026-08-08

### Description
Swarm orchestration for Claude Code with inbox-based messaging. Agents communicate via inbox messages, can claim shared tasks, and use structured message types for coordination.

### Key Features
- **Inbox messaging**: Agents send/receive messages via inbox
- **Shared task claiming**: Agents can claim tasks from shared queue
- **Structured message types**: Text, shutdown, plan approval, etc.
- Team coordination protocol
- Graceful termination handshake

### Message Types
| Type | Description |
|------|-------------|
| `text` | Regular messages |
| `shutdown_request` / `shutdown_approved` | Graceful termination |
| `plan_approval_request` | Plan review workflow |
| `task_claim` | Claim shared task |
| `result_report` | Report task completion |

### CBOL Relevance
- **Inbox messaging**: Reference for building message forwarding between AI agents in CBOL
- **Shared task queue**: Pattern for distributing message processing tasks
- **Graceful termination**: Reference for agent disconnection and session cleanup
- **Plan approval**: Pattern for human-in-the-loop approval in agent workflow
- **Message type structure**: Reference for defining CBOL message types

---

## 2. Orchestrating Swarms Skill

**URL**: https://gist.github.com/kieranklaassen/4f2aba89594a4aea4ad64d753984b2ea
**Author**: kieranklaassen
**Published**: 2026-08-20

### Description
Complete guide to multi-agent orchestration using Claude Code's TeammateTool and Task system. Covers parallel code reviews, pipeline workflows, self-organizing task queues.

### Key Features
- **TeammateTool**: Agent that joined a team, has name, color, inbox
- **Task system**: Spawned via Task with team_name + name
- **Parallel code reviews**: Multiple agents review different parts
- **Pipeline workflows**: Dependencies between agent tasks
- **Self-organizing task queues**: Agents claim tasks autonomously
- **Divide-and-conquer patterns**

### Primitives
| Primitive | What It Is |
|-----------|-----------|
| Teammate | Agent that joined a team. Has name, color, inbox. Spawned via Task with team_name + name |
| Task | Unit of work assigned to agent |
| Inbox | Message queue for inter-agent communication |
| Team | Named group of agents with shared context |

### CBOL Relevance
- **Teammate pattern**: Reference for building AI agent teams in CBOL messaging
- **Parallel processing**: Pattern for parallel message processing by multiple agents
- **Pipeline dependencies**: Reference for message processing pipeline with dependencies
- **Task queue**: Self-organizing task distribution for message handling
- **Divide-and-conquer**: Breaking complex message processing into subtasks

---

## 3. Agent Swarms & Teams Docs

**URL**: https://github.com/AppGambitStudio/Claude-Code-Docs/blob/main/agent_swarms_and_teams.md
**Author**: AppGambitStudio
**Last updated**: 2026-08-06

### Description
Documentation for Claude Code agent swarms and teams. Covers persistent teams, SendMessage for bidirectional messaging, shared task lists, mailbox-based communication.

### Key Concepts
| Concept | Description |
|---------|-------------|
| Sub-agent | Child agent spawned via Agent tool, runs autonomously, returns result |
| Fork | Sub-agent that inherits parent's full conversation context (experiment-gated) |
| Team | Persistent group of named agents |
| SendMessage | Bidirectional messaging between agents |
| Mailbox | Message queue for team communication |
| Shared tasks | Collaborative task list |

### CBOL Relevance
- **SendMessage**: Reference for building inter-agent message forwarding in CBOL
- **Mailbox communication**: Pattern for message queue between AI agents
- **Persistent teams**: Reference for maintaining agent connections across sessions
- **Shared task lists**: Collaborative message processing
- **Fork pattern**: Full context inheritance for complex message processing

---

## 4. Recursive Agent Architecture

**URL**: https://github.com/junwatu/claude-code/blob/main/RECURSIVE_AGENT_ARCHITECTURE.md
**Author**: junwatu
**Last updated**: 2026-08-18

### Description
Recursive self-spawning agent architecture. Agents can spawn sub-agents recursively, with inter-agent messaging, terminal backend (tmux/iTerm2 panes), handoff safety classifier, auto-compaction, and trace visualization.

### Key Features
- **Multi-agent teams**: With inter-agent messaging
- **Terminal backend**: tmux/iTerm2 panes per agent
- **Handoff safety classifier**: Review agent output before returning
- **Auto-compaction**: Handle context window overflow
- **Trace visualization**: Agent hierarchy + timing
- **Model-agnostic**: Works with any LLM supporting tool use

### Checklist
- [x] Multi-agent teams with inter-agent messaging
- [x] Terminal backend (tmux/iTerm2 panes per agent)
- [x] Handoff safety classifier (review agent output before returning)
- [x] Auto-compaction (handle context window overflow)
- [x] Trace visualization (agent hierarchy + timing)

### CBOL Relevance
- **Recursive spawning**: Reference for agents spawning sub-agents for complex message processing
- **Handoff safety**: Pattern for reviewing agent output before forwarding in CBOL
- **Auto-compaction**: Context management for long-running message processing
- **Trace visualization**: Reference for debugging and monitoring agent message flow
- **Terminal backend**: If building local agent orchestration for CBOL development

---

## 5. Three Multi-Agent Modes

**URL**: https://deepseek.csdn.net/6a0abe4610ee7a33f2736770.html
**Author**: DeepSeek (monsion)
**Published**: 2026-03-31

### Description
Analysis of Claude Code's three multi-agent modes: Coordinator (centralized scheduling), Swarm (team with team lead), Fork (lightweight clone sharing parent context).

### Three Modes
| Mode | Description | Use Case |
|------|-------------|----------|
| **Coordinator** | Centralized scheduling, parent agent controls sub-agents | Sequential pipeline, clear dependencies |
| **Swarm** | Team with team lead, agents collaborate via mailbox | Parallel work, peer collaboration |
| **Fork** | Lightweight clone sharing parent's full context | Quick parallel exploration |

### CBOL Relevance
- **Mode selection**: Reference for choosing the right multi-agent mode for CBOL message processing
- **Coordinator**: Pipeline-style message processing (intake → AI → forward → archive)
- **Swarm**: Parallel message processing by multiple AI agents
- **Fork**: Quick parallel exploration of different response strategies
- **Mode comparison**: Understanding tradeoffs for CBOL workflow design

---

## Summary: Multi-Agent Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| Inbox messaging | MaTriXy swarm | Message forwarding between AI agents |
| Shared task queue | MaTriXy, kieranklaassen | Distribute message processing tasks |
| SendMessage bidirectional | AppGambitStudio | Inter-agent message communication |
| Mailbox communication | AppGambitStudio | Message queue between agents |
| Graceful termination | MaTriXy | Agent disconnection and session cleanup |
| Plan approval workflow | MaTriXy | Human-in-the-loop for AI responses |
| Parallel processing | kieranklaassen | Parallel message processing |
| Pipeline dependencies | kieranklaassen | Message processing pipeline |
| Handoff safety classifier | junwatu | Review AI output before forwarding |
| Recursive spawning | junwatu | Complex message decomposition |
| Three modes (Coordinator/Swarm/Fork) | DeepSeek | Choose right mode for CBOL workflow |
| Persistent teams | AppGambitStudio | Maintain agent connections |
| Trace visualization | junwatu | Debug message flow between agents |

---

*Multi-Agent Collaboration Reference — 2026-08-24*
