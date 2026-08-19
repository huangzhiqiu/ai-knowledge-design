# Project Overview

## Project Name
**CBOL Refactor (Self-Development)** — AI Messaging Hub

## Background

CBOL (Conversation Bot Orchestration Layer) is an instant messaging system that handles:
- **Message reception** (接回话): Receive messages from multiple channels (WebSocket, REST, webhooks)
- **Message management** (回话管理): Store, route, and manage conversation state
- **Message forwarding** (回话转发): Forward messages between AI agents, human agents, and end users
- **Related IM features**: Presence, typing indicators, read receipts, multi-device sync

The project is being refactored from a legacy monolith to a modern, scalable architecture with AI-driven development practices.

## Goals

1. **Scalable IM backend**: Support 100K+ concurrent connections with sub-100ms message latency
2. **AI-native development**: Use AI agents (OpenCode/Claude Code) with knowledge-base-driven code generation
3. **Clean architecture**: Separate domain, application, infrastructure, and interface layers
4. **High reliability**: 99.9% uptime, at-least-once delivery, idempotent operations
5. **Observability**: Structured logging, metrics, distributed tracing

## Scope

### In Scope
- WebSocket/TCP gateway for real-time messaging
- Message storage (MySQL + Redis cache + MongoDB for hot data)
- Conversation state machine (custom lightweight state machine)
- Message routing and fanout (read diffusion pattern)
- Multi-device synchronization
- AI agent integration (message forwarding to AI/human agents)
- REST API for management operations
- Jira-driven AI development pipeline

### Out of Scope
- End-user client applications (web/mobile/desktop)
- Voice/video calling
- File storage (use external object storage service)
- Email/SMS notification (use external service)
- Admin UI (use existing admin dashboard)

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 17+ (LTS) |
| Framework | Spring Boot | 3.x |
| Network | Netty | 4.1.x |
| Database | MySQL | 8.0 |
| Cache | Redis | 6.x (Cluster) |
| Hot Storage | MongoDB | 6.x |
| Message Queue | Kafka / RocketMQ | Latest |
| Serialization | Protobuf / Jackson | Latest |
| Build | Maven | 3.9+ |
| Container | Docker + Kubernetes | Latest |
| Monitoring | Prometheus + Grafana + ELK | Latest |
| Tracing | SkyWalking / Jaeger | Latest |

## Architecture Principles

1. **Minimal architecture**: Avoid over-engineering (reference: Turms)
2. **Read diffusion**: Store messages by recipient, query on read (reference: Turms)
3. **Lock-free concurrency**: Thread count = CPU cores, CAS instead of locks (reference: Turms)
4. **Stateless services**: Horizontal scaling without sticky sessions
5. **Contract-first**: Define API contracts before implementation (OpenAPI/Protobuf)
6. **Design before code**: SDD must be approved before implementation

## Project Structure (Maven Modules)

```
cbol-refactor/
├── cbol-gateway/          # Netty WebSocket/TCP gateway
├── cbol-domain/           # Domain entities, state machine, business logic
├── cbol-application/      # Application services, use cases
├── cbol-infrastructure/   # Repository implementations, external clients
├── cbol-interfaces/       # REST controllers, WebSocket handlers
├── cbol-common/           # Shared utilities, constants, exceptions
└── cbol-statemachine/     # Custom lightweight state machine engine
```

## Timeline

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 0 | Knowledge base setup + AI pipeline | ✅ Complete |
| Phase 1 | Domain model + state machine | 🔄 In Progress |
| Phase 2 | Gateway + connection management | 📋 Planned |
| Phase 3 | Message storage + routing | 📋 Planned |
| Phase 4 | AI agent integration | 📋 Planned |
| Phase 5 | Multi-device sync + reliability | 📋 Planned |
| Phase 6 | Performance optimization + hardening | 📋 Planned |

## Key References

- **Turms** (Java/Netty IM): Read diffusion, lock-free concurrency, minimal architecture
- **Mattermost** (Go/React): Layered architecture, plugin system
- **Rocket.Chat** (TypeScript/MongoDB): DDP protocol, NATS microservices
- **Matrix/Synapse** (Python/PostgreSQL): Federation, Event DAG, E2EE

See `05-References/open-source-projects.md` for details.

## Development Workflow

This project uses a **Jira-driven AI development pipeline**:

```
Jira Ticket → Requirements → SDD (approved) → TDD Implementation → Test → PR (reviewed) → Deploy
```

See `QUICKSTART.md` for setup and `06-Skills/01-ai-development-pipeline/` for skill details.

---

*Last updated: 2026-08-19*
