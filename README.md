# CBOL Refactor — AI Messaging Hub Knowledge Base & Development Platform

> **Self-Development** — A comprehensive knowledge base and AI-driven development platform for an AI Messaging Hub (instant messaging: message reception, management, forwarding, and related IM features). Built for Jira-driven AI development with design guidelines, coding standards, OpenCode-compatible skills, and a complete ticket-to-deploy workflow.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Why This Repository Exists](#why-this-repository-exists)
- [Tech Stack](#tech-stack)
- [Directory Structure](#directory-structure)
- [Knowledge Base Deep Dive](#knowledge-base-deep-dive)
- [AI Development Pipeline](#ai-development-pipeline)
- [POC Workflow](#poc-workflow)
- [Skills Ecosystem](#skills-ecosystem)
- [Quick Start](#quick-start)
- [How to Use the Knowledge Base](#how-to-use-the-knowledge-base)
- [Reference Projects](#reference-projects)
- [Contributing](#contributing)
- [Documentation Statistics](#documentation-statistics)
- [License](#license)

---

## Project Overview

**CBOL Refactor (Self-Development)** is an AI Messaging Hub for instant messaging, featuring:

- **Custom lightweight state machine** — stateless, table-driven, zero-dependency engine for conversation lifecycle management
- **Jira-driven AI development pipeline** — 7-stage workflow with 6 approval gates, TDD enforcement, and evidence-based completion
- **Comprehensive knowledge base** — domain knowledge, design guidelines, coding standards, and open-source deep dives
- **OpenCode-compatible skills** — 12 custom skills + 85 external executable skills for every stage of development
- **POC workflow** — fully specified ticket-to-deploy pipeline with per-stage skills, verification checklists, and Mermaid diagrams

### Core Capabilities

| Capability | Description |
|-----------|-------------|
| **Message Reception** | WebSocket/TCP connection management, message parsing, protocol handling |
| **Message Management** | Message storage, retrieval, status tracking, read diffusion (fanout read) |
| **Message Forwarding** | Real-time delivery, offline messages, multi-device sync, push-pull mechanism |
| **AI Processing** | AI-powered conversation handling, intent recognition, automated responses |
| **Agent Transfer** | Human agent escalation, transfer workflow, session handoff |
| **State Management** | Custom state machine: INIT → AI_PROCESSING → TRANSFERRING → AGENT_CONNECTED → CLOSED |

---

## Why This Repository Exists

This repository serves three critical purposes:

### 1. Knowledge Repository for AI Code Generation
Every AI agent (OpenCode, Claude Code, Cursor, Copilot) working on this project reads relevant knowledge base documents **before writing any code**. This ensures:
- Consistent architecture and design decisions
- Adherence to coding standards and security guidelines
- Domain-specific correctness (IM patterns, state machine, WebSocket)
- Reference to proven open-source implementations

### 2. AI Development Workflow Platform
The repository defines and implements a complete **Jira ticket → deployment** pipeline:
- Ticket intake and normalization
- Requirements generation with knowledge injection
- SDD (Software Design Document) with human review gates
- TDD-based implementation (RED → GREEN → REFACTOR)
- Automated PR creation and review
- Deployment with verification

### 3. Skills Library for OpenCode
A curated collection of **executable skills** (SKILL.md with YAML frontmatter) that can be directly loaded into OpenCode:
- 12 project-specific custom skills
- 85 external skills from 7 GitHub repositories
- Organized by development stage and category

---

## Tech Stack

| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| **Language** | Java | 17+ (LTS) | Records, sealed classes, pattern matching |
| **Framework** | Spring Boot | 3.x | Reactive + MVC hybrid |
| **Network** | Netty | 4.1.x | WebSocket/TCP, zero-copy, event-driven |
| **Relational DB** | MySQL | 8.0 | Core business data, transactions |
| **NoSQL DB** | MongoDB | 6.x | Message storage, read diffusion, sharding |
| **Cache** | Redis | 6.x (Cluster) | Session, presence, distributed locks, rate limiting |
| **Message Queue** | Kafka / RocketMQ | — | Event-driven architecture, async processing, DLQ |
| **State Machine** | Custom lightweight | — | Stateless, table-driven, zero-dependency, generic type-safe |
| **Build** | Maven | 3.9+ | Multi-module project |
| **Code Quality** | SonarQube + SpotBugs + Semgrep | — | SAST, quality gates, security scanning |
| **Testing** | JUnit 5 + Mockito + AssertJ + Testcontainers | — | Unit, integration, E2E, TDD |
| **Observability** | SLF4J + Micrometer + Prometheus + OpenTelemetry | — | Logging, metrics, distributed tracing |
| **Container** | Docker + Kubernetes | — | Containerization, orchestration |
| **AI Development** | OpenCode + custom skills + Jira workflow | — | AI-driven development pipeline |

---

## Directory Structure

```
ai-knowledge-design/
├── README.md                                          # This file — comprehensive project guide
├── AGENTS.md                                          # AI agent instructions (OpenCode/Claude/Cursor/Copilot)
├── QUICKSTART.md                                      # Quick start guide
├── .gitignore
│
├── .ai-workflow/                                      # AI workflow configuration
│   ├── config.example.yaml                            # Example config (copy to config.yaml, gitignored)
│   ├── project_mapping.yaml                           # Jira project mapping
│   ├── templates/                                     # PR body template, SDD template, etc.
│   └── state/                                         # Pipeline state files (gitignored)
│
├── .opencode/                                         # OpenCode configuration
│   ├── opencode.json                                  # Main config
│   ├── README.md                                      # OpenCode usage guide
│   ├── commands/                                      # Custom slash commands (/workflow, /analyze, /sdd)
│   ├── rules/                                         # Additional rules
│   └── skills/                                        # Skill junctions → 06-Skills/ (gitignored symlinks)
│
├── 00-Project-Overview/                               # Project background, goals, timeline, stakeholders
│
├── 01-CBOL-Domain-Knowledge/                          # CBOL-specific domain knowledge (to be filled by team)
│   ├── README.md
│   ├── domain-model/                                  # Domain entities, value objects, aggregates, relationships
│   ├── module-structure/                              # Maven module structure, dependency graph
│   ├── database-schema/                               # Database tables, indexes, sharding strategy, migrations
│   ├── api-definitions/                               # OpenAPI/YAML API definitions, WebSocket protocol
│   ├── uml-diagrams/                                  # Class, sequence, component, deployment diagrams
│   ├── configuration/                                 # application.yml, environment-specific config
│   ├── deployment-architecture/                       # Deployment topology, capacity planning, CI/CD
│   ├── related-systems/                               # Upstream/downstream systems, integration contracts
│   └── state-machine/                                 # Custom lightweight state machine design
│       ├── README.md                                  # Design principles & philosophy
│       ├── architecture.md                            # Core architecture (stateless, table-driven)
│       ├── api-design.md                              # API & Builder pattern, generic type-safe design
│       └── integration.md                             # Integration with AI Messaging Hub
│
├── 02-Chat-Domain-Knowledge/                          # Generic IM knowledge + Java implementation references
│   ├── README.md
│   ├── domain-model/                                  # User, conversation, message, group, device models
│   ├── message-design/                                # Message ID (Snowflake), types, status, state machine
│   ├── sync-mechanism/                                # Multi-device sync, cursor design, push-pull, offline
│   ├── storage-design/                                # Message storage, read diffusion (fanout), timeline, indexing
│   ├── architecture-patterns/                         # Layered, microservices, event-driven, federation
│   ├── design-patterns/                               # Gateway, routing, fanout, presence, connection pool
│   ├── reliability/                                   # Delivery guarantees (at-least-once), idempotency, retry, DLQ
│   ├── java-implementation/                           # Java tech stack, project structure, package conventions
│   ├── concurrency/                                   # Thread pools, SessionRegistry, distributed locks, CAS
│   ├── networking/                                    # Netty WebSocket, protocol design, connection management, heartbeat
│   ├── serialization/                                 # Protobuf/JSON serialization, schema evolution
│   ├── data-structures/                               # POJO, Enum, Redis Key design, DDL templates
│   ├── code-templates/                                # Reusable code templates (controller, service, repository)
│   └── open-source-deep-dive/                         # Deep analysis of 6+ open source IM projects
│       ├── turms-deep-analysis.md                     # Turms — Java/Netty/read diffusion/lock-free/minimal arch
│       ├── mattermost-deep-analysis.md                # Mattermost — Go/layered/plugins RPC/enterprise
│       ├── rocketchat-deep-analysis.md                # Rocket.Chat — DDP/OpLog/NATS microservices/MongoDB
│       ├── matrix-synapse-deep-analysis.md            # Matrix/Synapse — federation/Event DAG/Olm encryption
│       ├── tiledesk-chat21-deep-analysis.md           # Tiledesk/Chat21 — Inbox/MQTT/RabbitMQ routing
│       └── openchat-deep-analysis.md                  # OpenChat — ICP blockchain/Canister/SNS DAO
│
├── 03-Design-Guidelines/                              # Design guidelines (6 categories, 26+ docs)
│   ├── README.md
│   ├── 01-architecture/                               # Architecture design principles & patterns
│   ├── 02-api-design/                                  # API design (REST, WebSocket, GraphQL)
│   ├── 03-data-design/                                 # Data modeling, schema design, indexing
│   ├── 04-security-design/                            # Security architecture, threat modeling, zero trust
│   ├── 05-reliability/                                # Reliability engineering, SLA/SLO, fault tolerance
│   └── 06-design-process/                             # Design process, ADR, review checklist
│
├── 04-Coding-Guidelines/                              # Coding guidelines (6 categories, 22+ docs)
│   ├── README.md
│   ├── 01-java-core/                                  # Java core standards
│   │   ├── java-coding-standards.md                   # Naming, formatting, OOP principles, clean code
│   │   ├── java-concurrency.md                        # Thread pools, locks, CAS, CompletableFuture, virtual threads
│   │   ├── java-exception-logging.md                  # Exception hierarchy, SLF4J, MDC, structured logging
│   │   ├── java-collections-io.md                     # Collections, Stream API, NIO.2, serialization
│   │   └── state-machine-guidelines.md                # Stateless engine, table-driven, testing, anti-patterns
│   ├── 02-spring-framework/                           # Spring framework best practices
│   │   ├── spring-boot-best-practices.md              # Layered architecture, DI, thin controllers, DTO, validation
│   │   └── spring-configuration-transaction-aop.md    # Profiles, config properties, transactions, AOP
│   ├── 03-networking-api/                             # Networking & API standards
│   │   ├── websocket-guidelines.md                    # WebSocket protocol, connection management, heartbeat, reconnection
│   │   ├── http-rest-api-guidelines.md                # REST design, HTTP client, caching, rate limiting, versioning
│   │   └── netty-guidelines.md                        # Thread model, ChannelPipeline, memory management, zero-copy
│   ├── 04-data-layer/                                 # Data layer standards
│   │   ├── relational-database-mysql.md               # Schema, indexing, SQL best practices, connection pool, sharding
│   │   ├── nosql-database-mongodb.md                  # Document design, read diffusion, indexing, sharding, aggregation
│   │   ├── redis-cache-guidelines.md                  # Key design, data structures, caching patterns, distributed locks
│   │   └── message-queue-guidelines.md                # Kafka/RocketMQ, producer/consumer, idempotency, DLQ, exactly-once
│   ├── 05-security/                                   # Security standards
│   │   ├── security-guidelines.md                     # OWASP Top 10, input validation, secure storage, crypto
│   │   ├── authentication-authorization.md            # JWT, refresh tokens, RBAC/ABAC, WebSocket security, OAuth2
│   │   └── sonarqube-devsecops-guidelines.md         # SonarQube quality gates, DevSecOps pipeline, SAST/DAST, Cyber Flow
│   └── 06-quality-ops/                                # Quality & operations
│       ├── code-quality.md                             # Code review checklist, complexity metrics, refactoring
│       ├── sonar-rules.md                              # SonarQube rules, quality profiles, Maven/Gradle config
│       ├── unit-testing-guidelines.md                  # FIRST principles, AAA pattern, Mockito, AssertJ, parameterized
│       ├── testing-guidelines.md                       # Test pyramid, integration tests, Testcontainers, performance
│       └── observability-guidelines.md                 # Logging, metrics, distributed tracing, alerting, SLO
│
├── 05-References/                                     # External references & inspiration
│   ├── README.md
│   ├── open-source-projects.md                        # Open source IM project references with star counts
│   └── ai-driven-development.md                       # AI-driven development references (Forge, Jira-Flow, etc.)
│
├── 06-Skills/                                         # OpenCode-compatible skills (12 custom + 85 external)
│   ├── README.md
│   ├── 01-ai-development-pipeline/                    # AI development pipeline skills (7 skills)
│   │   ├── workflow-ticket-to-deploy/                  # Master pipeline orchestrator
│   │   ├── jira-ticket-fetcher/                        # Fetch & normalize Jira tickets
│   │   ├── sdd-generator/                              # Generate SDD from requirements + knowledge base
│   │   ├── tdd-implementer/                            # TDD implementation (RED→GREEN→REFACTOR)
│   │   ├── test-verifier/                              # Verify test coverage & quality
│   │   ├── pr-creator/                                 # Create PR with auto-generated body
│   │   └── deploy-doc-updater/                         # Update deployment documentation
│   ├── 02-code-analysis/                               # Code analysis skills (3 skills)
│   │   ├── java-maven-project-analyzer/                # Analyze Maven Java project structure
│   │   ├── codebase-architecture-analyst/              # Analyze architecture & design patterns
│   │   └── architecture-analyzer-skill/                # Deep architecture analysis
│   ├── 03-knowledge-collection/                        # Knowledge collection skills (2 skills)
│   │   ├── cbol-knowledge-collector/                   # Collect CBOL-specific domain knowledge
│   │   └── chat-pattern-collector/                     # Collect IM/chat patterns from codebases
│   ├── 04-reference-skills/                            # Reference skill analysis (9 docs)
│   │   ├── README.md
│   │   ├── 01-instant-messaging-chat.md
│   │   ├── 02-websocket-realtime.md
│   │   ├── 03-rag-knowledge-retrieval.md
│   │   ├── 04-context-memory-management.md
│   │   ├── 05-multi-agent-collaboration.md
│   │   ├── 06-state-machine-workflow.md
│   │   ├── 07-message-queue-event-driven.md
│   │   └── 08-session-conversation-history.md
│   └── 05-external-skills/                             # 85 executable skills from 7 GitHub repos
│       ├── README.md                                    # Index, usage, CBOL workflow mapping
│       ├── 01-jira/                                    # (1 skill) Jira ticket management
│       ├── 02-requirements-sdd/                        # (22 skills) Atomic SDD: specify, clarify, design, etc.
│       ├── 03-coding/                                  # (22 skills) Java/Spring/coding standards & patterns
│       ├── 04-testing-tdd/                             # (5 skills) TDD, Spring Boot TDD, integration testing
│       ├── 05-devops-cicd/                             # (7 skills) build, release, dependency audit, security scan
│       ├── 06-code-review/                             # (5 skills) 5-axis review, security review, architecture review
│       └── 07-productivity/                            # (23 skills) API design, ADR, frontend, DB patterns, triage
│
└── 07-Workflows/                                       # AI development workflows & POC
    ├── README.md
    ├── ticket-to-deploy-workflow.md                    # Complete pipeline spec: 7 stages, 6 gates, state management
    ├── reference-workflows.md                           # 10+ industry workflow comparisons with star counts
    ├── best-practices.md                                # Consolidated best practices, anti-patterns, maturity model
    ├── reference-analysis/                              # Detailed analysis of reference workflow projects
    └── poc-workflow/                                    # Proof-of-concept workflow (fully executable)
        ├── README.md                                    # POC overview & usage
        ├── workflow-spec.md                             # Complete workflow specification
        ├── jira-ticket-spec.md                         # Jira ticket normalization spec
        ├── knowledge-integration.md                     # Knowledge base integration strategy
        ├── verify-checklist.md                          # Per-stage verification checklist
        ├── stages/                                      # 7 stage specifications
        │   ├── 01-ticket-intake.md
        │   ├── 02-requirements.md
        │   ├── 03-sdd.md
        │   ├── 04-test-cases.md
        │   ├── 05-code-generation.md
        │   ├── 06-pr-review.md
        │   └── 07-deployment.md
        └── opencode/                                    # OpenCode-compatible implementation
            ├── commands/
            │   └── poc-workflow.md                      # /poc-workflow slash command
            └── skills/                                  # 8 custom POC skills
                ├── README.md
                ├── poc-pipeline/                        # Master pipeline orchestrator
                ├── ticket-intake/                       # Stage 1: Jira ticket intake
                ├── requirements/                        # Stage 2: Requirements generation
                ├── sdd/                                 # Stage 3: SDD generation
                ├── test-cases/                          # Stage 4: Test case generation (TDD RED)
                ├── code-generation/                     # Stage 5: Code generation (TDD GREEN)
                ├── pr-review/                           # Stage 6: PR creation & auto-review
                └── deployment/                          # Stage 7: Deployment & verification
```

---

## Knowledge Base Deep Dive

### 01-CBOL-Domain-Knowledge — Project-Specific Domain
This directory contains **CBOL-specific** domain knowledge that the team fills in. It serves as the single source of truth for:
- Domain entities and their relationships
- Database schema and migration strategy
- API definitions (REST + WebSocket protocol)
- Module structure and dependency graph
- Deployment architecture and capacity planning
- Custom state machine design and integration

**Status**: Templates ready, content to be filled by team.

### 02-Chat-Domain-Knowledge — Generic IM Knowledge
Pre-filled with comprehensive instant messaging knowledge:
- **Domain models**: User, Conversation, Message, Group, Device
- **Message design**: Snowflake ID, message types, status state machine
- **Sync mechanism**: Multi-device sync, cursor-based pagination, push-pull
- **Storage design**: Read diffusion (fanout read), timeline, indexing, sharding
- **Architecture patterns**: Layered, microservices, event-driven, federation
- **Reliability**: Delivery guarantees, idempotency, retry, dead letter queues
- **Java implementation**: Tech stack, project structure, concurrency, Netty networking
- **Open source deep dives**: 6+ projects analyzed in detail (Turms, Mattermost, Rocket.Chat, Matrix, Tiledesk, OpenChat)

### 03-Design-Guidelines — 6 Categories
| Category | Focus |
|----------|-------|
| Architecture | Layered design, microservices, event-driven, CQRS, DDD |
| API Design | REST, WebSocket, GraphQL, versioning, error handling |
| Data Design | Normalization, indexing, sharding, CAP theorem, eventual consistency |
| Security Design | Zero trust, threat modeling, encryption, secure by design |
| Reliability | SLA/SLO, fault tolerance, circuit breaker, bulkhead, graceful degradation |
| Design Process | ADR (Architecture Decision Records), design review, documentation |

### 04-Coding-Guidelines — 6 Categories, 22+ Docs
| Category | Key Documents |
|----------|--------------|
| Java Core | Coding standards, concurrency, exception/logging, collections/IO, state machine |
| Spring Framework | Boot best practices, configuration, transactions, AOP |
| Networking & API | WebSocket, HTTP/REST, Netty |
| Data Layer | MySQL, MongoDB, Redis, Message Queue |
| Security | OWASP, auth/authz, SonarQube/DevSecOps, Cyber Flow |
| Quality & Ops | Code quality, Sonar rules, unit testing, testing, observability |

---

## AI Development Pipeline

Jira-driven AI development pipeline with **7 stages + 6 approval gates**:

```
CBOL-XXX (Jira Ticket)
      │
      ▼
┌─────────────────────┐     ┌──────────────────┐
│  [0] Ticket Intake  │────▶│  [Gate 0] Clarity?│
│  - Fetch & normalize │     │  - Complete?       │
│  - Extract metadata  │     │  - Well-defined?   │
└─────────────────────┘     └────────┬───────────┘
                                       │ Approved
                                       ▼
┌─────────────────────┐     ┌──────────────────┐
│  [1] Requirements   │────▶│  [Gate 1] Approve?│
│  - Read KB docs      │     │  - Human review    │
│  - Generate spec     │     │  - Stakeholder sign│
└─────────────────────┘     └────────┬───────────┘
                                       │ Approved
                                       ▼
┌─────────────────────┐     ┌──────────────────┐
│  [2] SDD             │────▶│  [Gate 2] Approve?│
│  - Architecture       │     │  - Tech lead review│
│  - Data model         │     │  - Design review   │
│  - API design         │     │                    │
│  - Test plan          │     │                    │
└─────────────────────┘     └────────┬───────────┘
                                       │ Approved
                                       ▼
┌─────────────────────┐     ┌──────────────────┐
│  [3] TDD Implement   │────▶│  [Gate 3] Auto   │
│  - RED: write tests   │     │  - Code review    │
│  - GREEN: make pass   │     │  - Quality gate   │
│  - REFACTOR: clean    │     │  - Security scan  │
└─────────────────────┘     └────────┬───────────┘
                                       │ Passed
                                       ▼
┌─────────────────────┐     ┌──────────────────┐
│  [4] Test & Verify   │────▶│  [Gate 4] Approve?│
│  - Run full suite     │     │  - Coverage >= 80% │
│  - Coverage check     │     │  - All tests pass   │
│  - Integration tests  │     │  - No Sonar critical│
└─────────────────────┘     └────────┬───────────┘
                                       │ Approved
                                       ▼
┌─────────────────────┐     ┌──────────────────┐
│  [5] PR Creation     │────▶│  [Gate 5] Peer   │
│  - Auto PR body       │     │  - Human review   │
│  - Auto review        │     │  - Required approvals│
│  - CI checks          │     │                    │
└─────────────────────┘     └────────┬───────────┘
                                       │ Merged
                                       ▼
┌─────────────────────┐
│  [6] Deploy + Docs   │
│  - Auto deploy        │
│  - Smoke tests        │
│  - Update docs        │
│  - Close ticket       │
└─────────────────────┘
```

### Core Principles

1. **Design before code** — SDD must be approved before any implementation
2. **TDD enforcement** — RED → GREEN → REFACTOR → Commit (no production code before failing test)
3. **Evidence over claims** — Every completion claim needs command + output + exit code
4. **3-strike escalation** — Auto-retry 3 times, then escalate to human
5. **State persistence** — Resume from breakpoint, every stage writes operation logs
6. **Knowledge injection** — Every stage reads relevant KB documents before acting
7. **Human-in-the-loop** — 6 approval gates ensure human oversight at critical points

### Pipeline Documentation

| Document | Location |
|----------|----------|
| Full pipeline specification | `07-Workflows/ticket-to-deploy-workflow.md` |
| Reference workflows (10+ comparisons) | `07-Workflows/reference-workflows.md` |
| Best practices & maturity model | `07-Workflows/best-practices.md` |
| POC implementation | `07-Workflows/poc-workflow/` |

---

## POC Workflow

The **Proof-of-Concept workflow** at `07-Workflows/poc-workflow/` is a fully specified, executable implementation of the ticket-to-deploy pipeline.

### Key Features

- **7 stage specifications** — Each stage has inputs, outputs, skills, verification steps, and failure handling
- **8 custom OpenCode skills** — One skill per stage + master pipeline orchestrator
- **Knowledge base integration** — Every stage reads relevant KB documents and can update the KB
- **Verification checklists** — Per-stage verify gates with concrete criteria
- **Jira ticket spec** — Normalized ticket format for consistent intake
- **Mermaid diagrams** — Visual workflow representations
- **OpenCode command** — `/poc-workflow` slash command to execute the pipeline

### POC Skills

| Skill | Stage | Purpose |
|-------|-------|---------|
| `poc-pipeline` | Master | Orchestrates the entire pipeline, manages state, gates |
| `ticket-intake` | 1 | Fetch Jira ticket, normalize, extract metadata |
| `requirements` | 2 | Generate requirements doc with KB injection |
| `sdd` | 3 | Generate SDD (architecture, data model, API, test plan) |
| `test-cases` | 4 | Generate test cases (TDD RED phase) |
| `code-generation` | 5 | Generate code (TDD GREEN phase), verify against tests |
| `pr-review` | 6 | Create PR, auto-review, quality gate |
| `deployment` | 7 | Deploy, smoke test, update docs, close ticket |

---

## Skills Ecosystem

The repository contains **97 executable skills** organized into two tiers:

### Tier 1: Custom Project Skills (12 skills)
Located in `06-Skills/01-ai-development-pipeline/`, `02-code-analysis/`, `03-knowledge-collection/`

These are purpose-built for the CBOL project and integrate with its knowledge base.

### Tier 2: External Skills (85 skills)
Located in `06-Skills/05-external-skills/`, downloaded from 7 GitHub repositories:

| Repository | Skills | Highlights |
|-----------|--------|-----------|
| [illarion/claude-jira-skill](https://github.com/illarion/claude-jira-skill) | 1 | Full Jira: MCP+REST, ADF, transitions, assignments, digests |
| [or-ituran/claude-tdd-skill](https://github.com/or-ituran/claude-tdd-skill) | 1+10 agents | Interactive TDD, sub-agents, progress persistence, checkpoints |
| [gthimmes/code-reviewer](https://github.com/gthimmes/code-reviewer) | 1 | 5-axis review, find-then-verify, confidence scoring |
| [Kevinweisl/claude-skills-cicd](https://github.com/Kevinweisl/claude-skills-cicd) | 4 | build-and-release, dependency-audit, lint-and-test, security-scan |
| [genkovich/sdd](https://github.com/genkovich/sdd) | 22 | Atomic SDD: specify, clarify, design, data-model, implement, etc. |
| [excalibase/claude-toolkiit](https://github.com/excalibase/claude-toolkiit) | 44 | Java/Spring patterns, TDD, coding standards, security, deployment |
| [adamcaviness/agentic-toolkit](https://github.com/adamcaviness/agentic-toolkit) | 13 | create-ticket, next-ticket, code-review, pr, ship, triage-* |

### External Skills by Category

| Category | Count | Key Skills |
|----------|-------|-----------|
| Jira | 1 | claude-jira-skill |
| Requirements/SDD | 22 | specify, clarify, design, data-model, sequences, implement, plan-tests, review |
| Coding | 22 | java-coding-standards, springboot-patterns, jpa-patterns, refactor-clean, self-check |
| Testing/TDD | 5 | claude-tdd-skill, tdd, springboot-tdd, integration-testing, ui-testing |
| DevOps/CI-CD | 7 | build-and-release, dependency-audit, lint-and-test, security-scan, deployment-patterns |
| Code Review | 5 | gthimmes-code-reviewer, code-review, security-review, architecture-review, quality-gate |
| Productivity | 23 | api-design, ADR, frontend-patterns, mongodb/mysql/postgres-patterns, triage-* |

### How to Install Skills

```bash
# Option 1: Symlink to OpenCode global skills directory
ln -s /path/to/06-Skills/05-external-skills/01-jira/claude-jira-skill \
      ~/.config/opencode/skills/claude-jira-skill

# Option 2: Copy to project-level .opencode/skills/
cp -r 06-Skills/05-external-skills/04-testing-tdd/claude-tdd-skill .opencode/skills/

# Option 3: Reference in AGENTS.md (AI agent reads the SKILL.md directly)
```

See `06-Skills/05-external-skills/README.md` for complete installation and usage instructions.

---

## Quick Start

### 1. Prerequisites

- [OpenCode](https://github.com/sst/opencode) installed (`npm install -g opencode-ai`)
- Git
- Java 17+ (for actual code development)
- Maven 3.9+

### 2. Clone & Configure

```bash
# Clone the repository
git clone https://github.com/huangzhiqiu/ai-knowledge-design.git
cd ai-knowledge-design

# Copy example config
cp .ai-workflow/config.example.yaml .ai-workflow/config.yaml

# Edit config with your credentials (or use environment variables)
export JIRA_API_TOKEN="your-jira-token"
export GITHUB_TOKEN="your-github-token"
```

### 3. Start OpenCode

```bash
# OpenCode automatically loads AGENTS.md, .opencode config, and skills
opencode
```

### 4. Run the Pipeline

```bash
# In OpenCode, run the full pipeline for a Jira ticket:
/poc-workflow jira_key=CBOL-123

# Or use individual skills:
/skill ticket-intake
/skill requirements
/skill sdd
/skill test-cases
/skill code-generation
/skill pr-review
/skill deployment
```

### 5. Browse the Knowledge Base

| What You Need | Where to Look |
|---------------|---------------|
| Generic IM architecture | `02-Chat-Domain-Knowledge/` |
| Open source IM deep dives | `02-Chat-Domain-Knowledge/open-source-deep-dive/` |
| Design principles | `03-Design-Guidelines/` |
| Java/Spring coding standards | `04-Coding-Guidelines/` |
| WebSocket/Netty guidelines | `04-Coding-Guidelines/03-networking-api/` |
| Security guidelines | `04-Coding-Guidelines/05-security/` |
| Unit testing standards | `04-Coding-Guidelines/06-quality-ops/unit-testing-guidelines.md` |
| State machine design | `01-CBOL-Domain-Knowledge/state-machine/` |
| Workflow documentation | `07-Workflows/` |
| POC workflow | `07-Workflows/poc-workflow/` |
| All skills | `06-Skills/` |

---

## How to Use the Knowledge Base

### For AI Agents
Every AI agent working on this project should:

1. **Read `AGENTS.md` first** — Contains project overview, working conventions, and key rules
2. **Read relevant KB docs before coding** — Use the mapping table in `AGENTS.md` to find relevant documents
3. **Follow the AI development pipeline** — Use `/poc-workflow` or individual skills
4. **Cite knowledge sources** — Reference which KB documents informed design decisions
5. **Update the KB** — When new patterns or decisions emerge, update the relevant documents

### For Human Developers
- **New to the project?** Start with `00-Project-Overview/`, then `02-Chat-Domain-Knowledge/README.md`
- **Designing a feature?** Read `03-Design-Guidelines/` and relevant `02-Chat-Domain-Knowledge/` docs
- **Writing code?** Follow `04-Coding-Guidelines/` standards
- **Working on IM features?** Study `02-Chat-Domain-Knowledge/open-source-deep-dive/turms-deep-analysis.md`
- **Using the state machine?** Read `01-CBOL-Domain-Knowledge/state-machine/`

---

## Reference Projects

### Open Source IM Projects (Deep Analyzed)

| Project | Language | Stars | Reference Value |
|---------|----------|-------|-----------------|
| [Turms](https://github.com/turms-im/turms) | Java | 2k+ | High-concurrency IM, read diffusion, lock-free, Netty, minimal architecture |
| [Mattermost](https://github.com/mattermost/mattermost) | Go + React | 28k+ | Layered architecture, plugin system, enterprise collaboration, RPC |
| [Rocket.Chat](https://github.com/RocketChat/Rocket.Chat) | Node.js + MongoDB | 38k+ | Real-time communication, NATS microservices, DDP protocol, OpLog |
| [Matrix/Synapse](https://github.com/matrix-org/synapse) | Python | 12k+ | Federation architecture, Event DAG, Olm/Megolm encryption |
| [Tiledesk/Chat21](https://github.com/chat21) | Node.js | — | MQTT + RabbitMQ message routing, Inbox pattern, customer support |
| [OpenChat](https://github.com/open-chat-labs/open-chat) | Motoko | 2k+ | ICP blockchain, Canister architecture, SNS DAO governance |

### AI-Driven Development References

| Project | Description |
|---------|-------------|
| [Forge](https://github.com/anthropics/forge) | Anthropic's AI coding agent framework |
| [Jira-Flow](https://github.com/wenttt/ai-coding-workflow) | Jira-driven AI coding workflow reference |
| [OpenCode](https://github.com/sst/opencode) | Open-source AI coding agent with skills support |
| [genkovich/sdd](https://github.com/genkovich/sdd) | Atomic software design & development skills |
| [excalibase/claude-toolkiit](https://github.com/excalibase/claude-toolkiit) | Comprehensive Java/Spring skill collection |

---

## Contributing

### Document Standards
- All documents in **Markdown** format
- File names in **kebab-case** (lowercase + hyphens, e.g., `websocket-guidelines.md`)
- Folder names in **English**, kebab-case (e.g., `message-storage`, `websocket-protocol`)
- Every directory has a `README.md` as index
- Technical documents cite reference sources with links
- Code examples use ✅ Good / ❌ Bad comparison format
- Mermaid diagrams for architecture, flow, and sequence visualizations

### Commit Standards
```
<type>(<scope>): <subject> (CBOL-XXX)

Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore

Examples:
  feat(02-chat): add websocket protocol design document
  docs(04-coding): add unit testing guidelines
  refactor(03-design): reorganize by categories
  feat(06-skills): add external skills collection
```

### CBOL-Specific Content Workflow
1. Extract information from existing codebase or design discussions
2. Organize by corresponding directory template
3. Submit PR with clear description
4. Team review and merge
5. Update `Documentation Statistics` table in this README

### Skill Contribution
- Custom skills go in `06-Skills/01-ai-development-pipeline/`, `02-code-analysis/`, or `03-knowledge-collection/`
- External skills go in `06-Skills/05-external-skills/` with proper attribution
- Every skill must have a `SKILL.md` with YAML frontmatter (name, description, author, license)
- Update the relevant README with skill index

---

## Documentation Statistics

| Directory | Documents/Skills | Status |
|-----------|-------------------|--------|
| 00-Project-Overview | 1 | 🟡 Template ready |
| 01-CBOL-Domain-Knowledge | 13+ | 🟡 Templates ready, to be filled by team |
| 02-Chat-Domain-Knowledge | 49+ | 🟢 Pre-filled (6+ open source deep analyses) |
| 03-Design-Guidelines | 26+ | 🟢 Pre-filled (6 categories) |
| 04-Coding-Guidelines | 22+ | 🟢 Pre-filled (6 categories, includes state machine, security, testing) |
| 05-References | 3 | 🟢 Pre-filled |
| 06-Skills — Custom | 12 skills | 🟢 Pre-filled (OpenCode-compatible) |
| 06-Skills — Reference Analysis | 9 docs | 🟢 Pre-filled |
| 06-Skills — External | 85 skills | 🟢 Downloaded from 7 GitHub repos |
| 07-Workflows — Specs | 4 docs | 🟢 Pre-filled |
| 07-Workflows — POC | 20+ files | 🟢 Fully specified (7 stages, 8 skills, commands) |
| **Total** | **220+** | |

---

## Notes

- **Sensitive information**: Never commit passwords, tokens, internal IPs, or credentials
- **Self-Development compliance**: Internal standards must be verified before commit
- **Code references**: Open source code references must comply with respective licenses
- **Token security**: Git remote URL tokens are for push only, remove before public sharing
- **External skills**: Each external skill retains its original license — see individual `SKILL.md` files
- **AGENTS.md**: AI agents must read `AGENTS.md` before making any changes to this repository

---

## License

This knowledge base is for internal use by the CBOL Refactor (Self-Development) project. External skills retain their original licenses as specified in their respective `SKILL.md` files and source repositories.

---

*Last updated: 2026-08-24*
*Repository: https://github.com/huangzhiqiu/ai-knowledge-design*
