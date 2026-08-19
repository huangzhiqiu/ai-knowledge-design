# CBOL Refactor — Project Knowledge Base

> AI Messaging Hub (Self-Development) knowledge base for instant messaging: message reception, message management, message forwarding, and related IM features. Supports AI-driven development with Jira workflow, design guidelines, coding standards, and OpenCode-compatible skills.

---

## Project Overview

**CBOL Refactor (Self-Development)** — An AI Messaging Hub for instant messaging with a custom lightweight state machine, Jira-driven AI development pipeline, and comprehensive knowledge base for code generation.

- **Language**: Java 17+
- **Framework**: Spring Boot 3.x
- **Network**: Netty 4.1.x (WebSocket/TCP)
- **Database**: MySQL 8.0 / MongoDB 6.x
- **Cache**: Redis 6.x (Cluster)
- **Message Queue**: Kafka / RocketMQ
- **Build**: Maven
- **AI Development**: OpenCode + custom skills + Jira workflow pipeline

---

## Directory Structure

```
ai-knowledge-design/
├── README.md                                    # This file
├── AGENTS.md                                    # AI agent instructions (OpenCode/Claude/Cursor)
├── QUICKSTART.md                                # Quick start guide
├── .gitignore
│
├── .ai-workflow/                                # AI workflow configuration
│   ├── config.example.yaml                      # Example config (copy to config.yaml)
│   ├── project_mapping.yaml                     # Project mapping
│   ├── templates/                               # PR body template, etc.
│   └── state/                                   # Pipeline state files (gitignored)
│
├── .opencode/                                   # OpenCode configuration
│   ├── opencode.json                            # Main config
│   ├── README.md                                # OpenCode usage guide
│   ├── commands/                                # Custom slash commands (/workflow, /analyze, /sdd)
│   ├── rules/                                   # Additional rules
│   └── skills/                                  # Skill junctions → 06-Skills/ (gitignored)
│
├── 00-Project-Overview/                         # Project background, goals, timeline
│
├── 01-CBOL-Domain-Knowledge/                    # CBOL-specific domain knowledge
│   ├── README.md
│   ├── domain-model/                            # Domain entities and relationships
│   ├── module-structure/                        # Maven module structure
│   ├── database-schema/                         # Database tables, indexes, sharding
│   ├── api-definitions/                         # OpenAPI/YAML API definitions
│   ├── uml-diagrams/                            # Class, sequence, component diagrams
│   ├── configuration/                           # application.yml config
│   ├── deployment-architecture/                 # Deployment topology, capacity, CI/CD
│   ├── related-systems/                         # Upstream/downstream systems
│   └── state-machine/                           # Custom lightweight state machine
│       ├── README.md                            # Design principles
│       ├── architecture.md                      # Core architecture
│       ├── api-design.md                        # API & Builder pattern
│       └── integration.md                       # Integration with AI Messaging Hub
│
├── 02-Chat-Domain-Knowledge/                    # Generic IM knowledge + Java implementation
│   ├── README.md
│   ├── domain-model/                            # User, conversation, message, group, device models
│   ├── message-design/                          # Message ID, types, state machine
│   ├── sync-mechanism/                          # Multi-device sync, cursor design, push-pull
│   ├── storage-design/                          # Message storage, offline messages, timeline
│   ├── architecture-patterns/                   # Layered, microservices, event-driven, federation
│   ├── design-patterns/                         # Gateway, routing, fanout, presence
│   ├── reliability/                             # Delivery guarantees, idempotency, retry
│   ├── java-implementation/                     # Java tech stack, project structure
│   ├── concurrency/                             # Thread pools, SessionRegistry, distributed locks
│   ├── networking/                              # Netty WebSocket, protocol, connection management
│   ├── serialization/                           # Protobuf/JSON serialization
│   ├── data-structures/                         # POJO, Enum, Redis Key, DDL
│   ├── code-templates/                          # Code templates
│   └── open-source-deep-dive/                   # Deep analysis of 6+ open source IM projects
│       ├── turms-deep-analysis.md               # Turms (Java/Netty/read diffusion/lock-free)
│       ├── mattermost-deep-analysis.md          # Mattermost (Go/layered/plugins RPC)
│       ├── rocketchat-deep-analysis.md          # Rocket.Chat (DDP/OpLog/NATS microservices)
│       ├── matrix-synapse-deep-analysis.md      # Matrix/Synapse (federation/Event DAG/Olm)
│       ├── tiledesk-chat21-deep-analysis.md     # Tiledesk/Chat21 (Inbox/MQTT/RabbitMQ)
│       └── openchat-deep-analysis.md            # OpenChat (ICP blockchain/Canister/SNS DAO)
│
├── 03-Design-Guidelines/                        # Design guidelines (6 categories)
│   ├── README.md
│   ├── 01-architecture/                         # Architecture design
│   │   ├── architecture-principles.md           # Distributed systems, microservices, 12-Factor
│   │   ├── design-principles.md                 # SOLID, DRY, KISS, YAGNI
│   │   ├── layered-architecture.md              # Layered, hexagonal, clean architecture
│   │   ├── microservices-patterns.md            # Service decomposition, API gateway, discovery
│   │   ├── event-driven-architecture.md         # Domain events, event sourcing, CQRS, Outbox
│   │   └── ddd-guidelines.md                    # Bounded contexts, aggregates, value objects
│   ├── 02-api-design/                            # API design
│   │   ├── rest-api-design.md                   # REST resources, HTTP methods, status codes
│   │   ├── api-contract.md                      # OpenAPI 3.1, request/response, idempotency
│   │   ├── websocket-api-design.md              # WebSocket lifecycle, framing, heartbeat, reconnect
│   │   ├── api-versioning.md                    # Versioning strategies, deprecation, migration
│   │   └── api-design-guidelines.md             # General API best practices
│   ├── 03-data-design/                           # Data design
│   │   ├── data-modeling.md                     # ER modeling, normalization, indexing, schema evolution
│   │   ├── database-design.md                   # Polyglot persistence, replication, sharding, transactions
│   │   ├── cache-design.md                      # Multi-level caching, cache-aside, invalidation, consistency
│   │   └── message-queue-design.md              # Topic design, partitioning, ordering, DLQ, backpressure
│   ├── 04-security-design/                      # Security design
│   │   └── security-architecture.md             # Defense in depth, zero trust, least privilege
│   └── 06-design-process/                       # Design process
│       └── self-development-standards.md        # Self-Development internal standards
│
├── 04-Coding-Guidelines/                        # Coding guidelines (6 categories, 21 docs)
│   ├── README.md
│   ├── 01-java-core/                            # Java core
│   │   ├── java-coding-standards.md             # Naming, formatting, OOP principles
│   │   ├── java-concurrency.md                  # Thread pools, locks, CAS, CompletableFuture
│   │   ├── java-exception-logging.md            # Exception hierarchy, SLF4J, MDC
│   │   ├── java-collections-io.md               # Collections, Stream API, NIO.2, serialization
│   │   └── state-machine-guidelines.md          # Stateless engine, table-driven, testing
│   ├── 02-spring-framework/                     # Spring framework
│   │   ├── spring-boot-best-practices.md        # Layered architecture, DI, thin controllers, DTO
│   │   └── spring-configuration-transaction-aop.md # Profiles, config, transactions, AOP
│   ├── 03-networking-api/                       # Networking & API
│   │   ├── websocket-guidelines.md              # WebSocket protocol, connection management
│   │   ├── http-rest-api-guidelines.md          # REST design, HTTP client, caching, rate limiting
│   │   └── netty-guidelines.md                  # Thread model, ChannelPipeline, memory, zero-copy
│   ├── 04-data-layer/                           # Data layer
│   │   ├── relational-database-mysql.md         # Schema, indexing, SQL best practices, connection pool
│   │   ├── nosql-database-mongodb.md            # Document design, read diffusion, indexing, sharding
│   │   ├── redis-cache-guidelines.md            # Key design, data structures, caching patterns, locks
│   │   └── message-queue-guidelines.md          # Kafka/RocketMQ, producer/consumer, idempotency, DLQ
│   ├── 05-security/                             # Security
│   │   ├── security-guidelines.md               # OWASP Top 10, input validation, secure storage
│   │   ├── authentication-authorization.md      # JWT, refresh tokens, RBAC/ABAC, WebSocket security
│   │   └── sonarqube-devsecops-guidelines.md   # SonarQube quality gates, DevSecOps pipeline, SAST/DAST
│   └── 06-quality-ops/                          # Quality & operations
│       ├── code-quality.md                       # Code review checklist, complexity, refactoring
│       ├── sonar-rules.md                        # SonarQube rules, quality profiles, Maven config
│       ├── unit-testing-guidelines.md            # FIRST principles, AAA, Mockito, AssertJ, parameterized
│       ├── testing-guidelines.md                 # Test pyramid, integration tests, Testcontainers, performance
│       └── observability-guidelines.md           # Logging, metrics, distributed tracing, alerting
│
├── 05-References/                               # External references
│   ├── README.md
│   ├── open-source-projects.md                  # Open source IM project references
│   └── ai-driven-development.md                 # AI-driven development references (Forge/Jira-Flow/etc.)
│
└── 06-Skills/                                   # OpenCode-compatible skills
    ├── README.md
    ├── 01-ai-development-pipeline/              # AI development pipeline skills
    │   ├── workflow-ticket-to-deploy/            # Pipeline orchestration: Ticket→SDD→Code→Test→PR→Deploy
    │   ├── jira-ticket-fetcher/                  # Jira ticket fetching and structuring
    │   ├── sdd-generator/                        # SDD generation (12 sections + knowledge injection)
    │   ├── tdd-implementer/                      # TDD implementation: RED→GREEN→REFACTOR
    │   ├── test-verifier/                        # Test verification and coverage
    │   ├── pr-creator/                           # PR creation with template
    │   └── deploy-doc-updater/                   # Deployment documentation update
    ├── 02-code-analysis/                         # Code analysis skills
    │   ├── java-maven-project-analyzer/          # Java Maven multi-module project analysis
    │   ├── architecture-analyzer-skill/          # General codebase deep analysis (16 sections)
    │   └── codebase-architecture-analyst/        # File-level reverse engineering + OWASP audit
    └── 03-knowledge-collection/                  # Knowledge collection skills
        ├── cbol-knowledge-collector/             # CBOL knowledge collection
        └── chat-pattern-collector/               # Open source project pattern collection
```

---

## Documentation Statistics

| Directory | Documents | Status |
|-----------|-----------|--------|
| 00-Project-Overview | 1 | 🟡 Template ready |
| 01-CBOL-Domain-Knowledge | 13+ | 🟡 Templates ready, to be filled by team |
| 02-Chat-Domain-Knowledge | 40+ | 🟢 Pre-filled (6+ open source deep analyses) |
| 03-Design-Guidelines | 18 | 🟢 Pre-filled (4 categories) |
| 04-Coding-Guidelines | 21 | 🟢 Pre-filled (6 categories) |
| 05-References | 2 | 🟢 Pre-filled |
| 06-Skills | 10 skills | 🟢 Pre-filled (OpenCode-compatible) |
| **Total** | **105+** | |

---

## Quick Start

### 1. Set Up OpenCode

```bash
# Install OpenCode
npm install -g opencode-ai

# Navigate to project
cd ai-knowledge-design

# Start OpenCode (automatically loads AGENTS.md, .opencode config, skills)
opencode
```

### 2. Configure Workflow

```bash
# Copy example config
cp .ai-workflow/config.example.yaml .ai-workflow/config.yaml

# Edit config with your Jira/GitHub credentials
# Or set environment variables:
export JIRA_API_TOKEN="your-jira-token"
export GITHUB_TOKEN="your-github-token"
```

### 3. Run AI Development Pipeline

```bash
# In OpenCode:
/workflow jira_key=CBOL-123

# Or use individual skills:
/skill jira-ticket-fetcher
/skill sdd-generator
/skill tdd-implementer
```

### 4. Browse Knowledge Base

- **Generic IM knowledge**: Start with `02-Chat-Domain-Knowledge/`
- **Design guidelines**: `03-Design-Guidelines/`
- **Coding standards**: `04-Coding-Guidelines/`
- **Open source references**: `02-Chat-Domain-Knowledge/open-source-deep-dive/`

---

## AI Development Pipeline

Jira-driven AI development pipeline with 7 stages + 6 approval gates:

```
CBOL-XXX
  │
  ▼
[0] Ticket Intake ──── [Gate 0] Clarity?
  │
  ▼
[1] Requirements ───── [Gate 1] Approve?
  │
  ▼
[2] SDD ───────────── [Gate 2] Approve?
  │
  ▼
[3] TDD Implement ─── [Gate 3] Auto-review
  │
  ▼
[4] Test ──────────── [Gate 4] Approve?
  │
  ▼
[5] PR ────────────── [Gate 5] Peer review
  │
  ▼
[6] Deploy + Docs ──── Complete
```

**Core principles**:
- Design before code (SDD must be approved)
- TDD (RED → GREEN → REFACTOR → Commit)
- Evidence over claims (command + output + exit code)
- 3-strike escalation (auto-retry 3×, then ask human)
- State persistence (resume from breakpoint)
- Knowledge injection (every stage reads relevant KB docs)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17+ (LTS) |
| Framework | Spring Boot 3.x |
| Network | Netty 4.1.x (WebSocket/TCP) |
| Database | MySQL 8.0 + MongoDB 6.x |
| Cache | Redis 6.x (Cluster) |
| Message Queue | Kafka / RocketMQ |
| State Machine | Custom lightweight (stateless, table-driven, zero-dependency) |
| Build | Maven |
| Code Quality | SonarQube + SpotBugs + Semgrep |
| Testing | JUnit 5 + Mockito + AssertJ + Testcontainers |
| Observability | SLF4J + Micrometer + Prometheus + OpenTelemetry |
| Container | Docker + Kubernetes |
| AI Development | OpenCode + custom skills + Jira workflow |

---

## Reference Projects

| Project | Language | Reference Value |
|---------|----------|-----------------|
| [Turms](https://github.com/turms-im/turms) | Java | High-concurrency IM, read diffusion, lock-free, Netty |
| [Mattermost](https://github.com/mattermost/mattermost) | Go + React | Layered architecture, plugin system, enterprise collaboration |
| [Rocket.Chat](https://github.com/RocketChat/Rocket.Chat) | Node.js + MongoDB | Real-time communication, NATS microservices, DDP protocol |
| [Matrix/Synapse](https://github.com/matrix-org/synapse) | Python | Federation architecture, Event DAG, Olm encryption |
| [Tiledesk/Chat21](https://github.com/chat21) | Node.js | MQTT + RabbitMQ message routing, Inbox pattern |
| [OpenChat](https://github.com/open-chat-labs/open-chat) | Go | WebSocket + Redis pub/sub horizontal scaling |

---

## Contributing

### Document Standards
- All documents in **Markdown** format
- File names in **kebab-case** (lowercase + hyphens)
- Every directory has a `README.md` as index
- Technical documents cite reference sources
- Code examples use ✅ Good / ❌ Bad comparison format

### Commit Standards
```
<type>(<scope>): <subject> (CBOL-XXX)

Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore

Examples:
  feat(02-chat): add websocket protocol design
  update(01-cbol): fill database schema
  docs(04-coding): add unit testing guidelines
  refactor(03-design): reorganize by categories
```

### CBOL-Specific Content Workflow
1. Extract information from existing codebase
2. Organize by corresponding directory template
3. Submit PR / direct commit
4. Team review and merge

---

## Notes

- **Sensitive information**: Never commit passwords, tokens, internal IPs
- **Self-Development compliance**: Internal standards must be verified before commit
- **Code references**: Open source code references must comply with respective licenses
- **Token security**: Git remote URL tokens are for push only, remove before public sharing

---

## License

This knowledge base is for internal use by the CBOL Refactor (Self-Development) project.

---

*Last updated: 2026-08-19*
