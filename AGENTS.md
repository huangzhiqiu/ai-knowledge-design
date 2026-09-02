# AGENTS.md — AI Development Guidelines

> This file guides AI agents (OpenCode, Claude Code, Cursor, Copilot) working on this project. Read it first before making any changes.

## Project Overview

**CBOL Refactor (Self-Development)** — An AI Messaging Hub for instant messaging: message reception, message management, message forwarding, and related IM features.

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Network**: Netty 4.1.x (WebSocket/TCP)
- **Database**: MySQL 8.0 / MongoDB
- **Cache**: Redis 6.x (cluster)
- **Build**: Maven (multi-module)
- **Knowledge base**: This repository (Markdown, English folder names)

## How to Work With This Project

### 1. Always Read the Knowledge Base First

Before writing any code, read relevant knowledge base documents:

| Task | Must Read |
|------|-----------|
| Domain understanding | `01-CBOL-Domain-Knowledge/README.md` |
| IM architecture/patterns | `02-Chat-Domain-Knowledge/` (search by keyword) |
| Design decisions | `03-Design-Guidelines/` (all) |
| Code standards | `04-Coding-Guidelines/` (all) |
| Open source references | `05-References/` |
| Workflow skills | `06-Skills/` |
| Workflow definitions | `07-Workflows/` |
| State machine code | `08-Code/state-machine/` |

### 2. Follow the AI Development Pipeline

For any Jira ticket, use the pipeline:

```
/workflow-ticket-to-deploy jira_key=CBOL-XXX
```

The pipeline enforces:
- **Design before code**: SDD must be approved before implementation
- **TDD**: RED → GREEN → REFACTOR → Commit (no production code before failing test)
- **Evidence**: Every completion claim needs command + output + exit code
- **3-strike**: Auto-retry 3 times, then escalate to human
- **Operation logs**: Every stage writes to `docs/operations/{KEY}/`

### 3. Configuration

- Actual config: `.ai-workflow/config.yaml` (gitignored, contains secrets)
- Example config: `.ai-workflow/config.example.yaml` (committed)
- Environment variables: `JIRA_API_TOKEN`, `GITHUB_TOKEN`

### 4. Naming Conventions

- **Folders**: English, kebab-case (e.g., `message-storage`, `websocket-protocol`)
- **Files**: English, kebab-case, `.md` extension
- **Branches**: `feat/CBOL-XXX-{short-desc}` / `fix/CBOL-XXX-{short-desc}`
- **Commits**: Conventional Commits: `{type}({scope}): {subject} (CBOL-XXX)`
- **Java packages**:
  - State machine core: `com.selfdevelopment.statemachine.*`
  - Chat engine: `com.selfdevelopment.chatengine.*`
  - Agent connector: `com.selfdevelopment.agentconnector.*`
  - Other modules: `com.selfdevelopment.ai.messaging.{module}`

### 5. Code Quality Gates

Before submitting any code:
- [ ] All tests pass (`mvn test`)
- [ ] Line coverage >= 80%, branch coverage >= 70%
- [ ] No Sonar critical/blocker issues
- [ ] Follows `04-Coding-Guidelines/` (all documents)
- [ ] Security guidelines followed (`04-Coding-Guidelines/security-guidelines.md`)
- [ ] Concurrency guidelines followed (`04-Coding-Guidelines/concurrency-guidelines.md`)
- [ ] Multi-module dependency rules: no circular dependencies

### 6. State Machine

This project uses a custom lightweight state machine (see `08-Code/state-machine/`):
- Multi-module Maven project: `statemachine-core`, `chat-engine`, `agent-connector`
- Stateless engine (only stores transition rules, current state injected by business layer)
- Table-driven (ConcurrentHashMap O(1) lookup)
- Zero external core dependencies
- Generic type-safe
- Spring-style `StateMachineConfigurerAdapter` configuration
- Reference: COLA StateMachine design philosophy (but no code import)

**Conversation states** (chat-engine): INITIATED, ACTIVE, TRANSFERRED, SURVEY_IN_PROGRESS, ENDING, ERROR, CLOSED

**Interaction states** (agent-connector): CONNECTING, CONNECTED, RECONNECTING, HELD, TRANSFERRING, DISCONNECTED

### 7. Key Architecture Decisions

- **Read diffusion (fanout read)**: Reference Turms design — messages stored by recipient, query on read
- **Lock-free concurrency**: Reference Turms — thread count = CPU cores, CAS instead of locks
- **MongoDB sharding**: Message index = sending time + recipient ID
- **Session ID**: user ID + device type
- **Minimal architecture**: Avoid over-engineering (Turms philosophy)
- **Multi-market support**: Configuration-driven per-market behavior (HK, SG, UK, etc.)
- **Failover mechanism**: Unhandled exceptions trigger FAIL event, routed to fail branch
- **Survey as in-progress**: SURVEY_IN_PROGRESS is a sub-state of active flow, controlled by flow

### 8. What NOT to Do

- ❌ Don't write production code before a failing test exists (TDD)
- ❌ Don't skip knowledge base reading — always inject relevant knowledge
- ❌ Don't commit secrets/tokens to git
- ❌ Don't use `com.hsbc.*` package — use `com.selfdevelopment.*`
- ❌ Don't create Chinese folder/file names — use English
- ❌ Don't auto-merge PRs — human peer review required
- ❌ Don't claim completion without evidence (command + output + exit code)
- ❌ Don't bypass the 3-strike escalation — if 3 retries fail, stop and ask human
- ❌ Don't create circular dependencies between Maven modules
- ❌ Don't put business-specific code in `statemachine-core` — it must stay generic

### 9. Project Structure

```
ai-knowledge-design/
├── 00-Project-Overview/          # Project background, goals, timeline
├── 01-CBOL-Domain-Knowledge/     # CBOL-specific domain (to be filled by team)
├── 02-Chat-Domain-Knowledge/     # Generic IM knowledge + Java implementation refs
├── 03-Design-Guidelines/         # Design principles, API guidelines, architecture
├── 04-Coding-Guidelines/         # Java/Spring/WebSocket/DB/Cache/Queue/API/HTTP/Security/Quality/StateMachine/Testing standards
├── 05-References/                 # Open source projects, AI dev references
├── 06-Skills/                     # OpenCode-compatible skills (categorized)
│   ├── 01-ai-development-pipeline/
│   ├── 02-code-analysis/
│   ├── 03-knowledge-collection/
│   ├── 04-domain-skills/
│   └── 05-external-skills/
├── 07-Workflows/                  # Workflow definitions + POC workflow
│   ├── reference-workflows/       # Analyzed reference workflows
│   └── poc-workflow/              # POC implementation with skills
├── 08-Code/                       # Project code
│   └── state-machine/             # Multi-module state machine project
│       ├── statemachine-core/     # Shared core engine
│       ├── chat-engine/           # Conversation state machine
│       └── agent-connector/       # Interaction state machine
├── .ai-workflow/                  # Pipeline config (gitignored actual config)
├── AGENTS.md                      # This file
├── QUICKSTART.md                  # Quick start guide
└── README.md                      # Project overview
```

---

*Last updated: 2026-09-02*
