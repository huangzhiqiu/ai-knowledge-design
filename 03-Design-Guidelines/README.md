# Design Guidelines

> Comprehensive design guidelines for CBOL Messaging Hub, organized by design domain. Covers architecture, API design, data design, security design, and design process.

## Categories

### 01-architecture — Architecture Design

| Document | Description |
|----------|-------------|
| [architecture-principles.md](./01-architecture/architecture-principles.md) | Distributed systems, microservices, resilience, 12-Factor architecture principles |
| [design-principles.md](./01-architecture/design-principles.md) | SOLID, DRY, KISS, YAGNI, and core software design principles |
| [layered-architecture.md](./01-architecture/layered-architecture.md) | Layered architecture, hexagonal architecture (ports & adapters), clean architecture |
| [microservices-patterns.md](./01-architecture/microservices-patterns.md) | Service decomposition, API gateway, service discovery, inter-service communication |
| [event-driven-architecture.md](./01-architecture/event-driven-architecture.md) | Domain events, event sourcing, CQRS, Outbox pattern, publish-subscribe |
| [ddd-guidelines.md](./01-architecture/ddd-guidelines.md) | Bounded contexts, ubiquitous language, aggregates, value objects, domain services, context mapping |

### 02-api-design — API Design

| Document | Description |
|----------|-------------|
| [rest-api-design.md](./02-api-design/rest-api-design.md) | REST resource modeling, HTTP methods, status codes, pagination, filtering, error handling |
| [api-contract.md](./02-api-design/api-contract.md) | OpenAPI 3.1 specification, request/response formats, idempotency, API documentation |
| [websocket-api-design.md](./02-api-design/websocket-api-design.md) | WebSocket connection lifecycle, message framing, heartbeat, reconnection, message reliability |
| [api-versioning.md](./02-api-design/api-versioning.md) | Versioning strategies (URI/header/query), deprecation policy, backward compatibility, migration guides |
| [api-design-guidelines.md](./02-api-design/api-design-guidelines.md) | General API design best practices (legacy, consolidated into above) |

### 03-data-design — Data Design

| Document | Description |
|----------|-------------|
| [data-modeling.md](./03-data-design/data-modeling.md) | Entity-relationship modeling, normalization, data types, indexing strategy, schema evolution |
| [database-design.md](./03-data-design/database-design.md) | Polyglot persistence (MySQL + MongoDB + Redis), replication, sharding, connection pooling, transactions |
| [cache-design.md](./03-data-design/cache-design.md) | Multi-level caching (Caffeine + Redis), cache-aside, write-through, invalidation, consistency, anti-patterns |
| [message-queue-design.md](./03-data-design/message-queue-design.md) | Topic design, partitioning, ordering guarantees, idempotent consumers, DLQ, backpressure |

### 04-security-design — Security Design

| Document | Description |
|----------|-------------|
| [security-architecture.md](./04-security-design/security-architecture.md) | Defense in depth, zero trust, least privilege, security boundaries, secure by design, data security |

### 06-design-process — Design Process

| Document | Description |
|----------|-------------|
| [self-development-standards.md](./06-design-process/self-development-standards.md) | Self-Development internal design requirements (to be filled by team) |

## Core Design Principles

### Architecture Principles
- **Stateless services** for horizontal scaling
- **Loose coupling, high cohesion**
- **Defense in depth** for security
- **12-Factor App** principles
- **CAP theorem awareness** (AP for IM systems)

### API Design Principles
- **Resource-oriented** (nouns, not verbs)
- **Proper HTTP methods** and status codes
- **Unified response/error format**
- **Pagination, filtering, sorting**
- **API versioning** (URI path recommended)
- **Idempotency** for critical operations

### Data Design Principles
- **Polyglot persistence** — right tool for the job
- **Cache-aside** as default caching strategy
- **Always set TTL** on cache keys
- **Partition by business key** for ordering
- **Idempotent consumers** for at-least-once delivery

### Security Design Principles
- **Never trust, always verify** (Zero Trust)
- **Least privilege** access
- **Defense in depth** — multiple security layers
- **Secure by design** — security in every phase
- **Encrypt** in transit and at rest

## Design Decision Workflow

```
1. Understand Requirements
   ├── Read Jira ticket / requirements
   ├── Identify stakeholders
   └── Define success criteria

2. Explore Options
   ├── Research existing patterns
   ├── Review knowledge base (02-Chat-Domain-Knowledge)
   ├── Consider 2-3 alternatives
   └── Evaluate trade-offs

3. Create Design Document (SDD)
   ├── Context and problem statement
   ├── Architecture overview + diagrams
   ├── API design
   ├── Data model
   ├── Security considerations
   ├── Testing strategy
   └── Implementation plan

4. Design Review
   ├── Peer review
   ├── Check against design guidelines
   ├── Identify risks
   └── Get approval

5. Implementation
   ├── Follow TDD (RED → GREEN → REFACTOR)
   ├── Follow coding guidelines (04-Coding-Guidelines)
   └── Update design docs as needed

6. Post-Implementation Review
   ├── Compare implementation vs design
   ├── Identify lessons learned
   └── Update knowledge base
```

## References

- Clean Architecture (Robert C. Martin): https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- Domain-Driven Design (Eric Evans): https://domainlanguage.com/ddd/
- Building Microservices (Sam Newman): https://www.oreilly.com/library/view/building-microservices-2nd/9781492034018/
- Release It! (Michael Nygard): https://pragprog.com/titles/mnee2/release-it-second-edition/
- 12-Factor App: https://12factor.net/
- Zalando RESTful API Guidelines: https://opensource.zalando.com/restful-api-guidelines/
- OWASP API Security Top 10: https://owasp.org/www-project-api-security/
- Microsoft REST API Guidelines: https://github.com/microsoft/api-guidelines
- Turms (Java/Netty IM reference): https://github.com/turms-im/turms

---

*Last updated: 2026-08-19*
