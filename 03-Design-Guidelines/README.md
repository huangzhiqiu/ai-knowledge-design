# Design Guidelines

> Comprehensive design guidelines for CBOL Messaging Hub, organized by design domain. Covers architecture, API design, data design, security design, reliability, and design process.

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
| [api-design-guidelines.md](./02-api-design/api-design-guidelines.md) | General API design best practices |

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
| [authentication-authorization-design.md](./04-security-design/authentication-authorization-design.md) | JWT/OAuth2 authentication flows, token management, RBAC+ABAC hybrid, session management, multi-device, API keys |
| [threat-modeling.md](./04-security-design/threat-modeling.md) | STRIDE methodology, data flow diagrams, risk assessment (likelihood×impact), mitigation strategies, CBOL threat model example |

### 05-reliability — Reliability Engineering

| Document | Description |
|----------|-------------|
| [high-availability.md](./05-reliability/high-availability.md) | SLA/SLO/SLI, redundancy patterns (active-active, active-passive, N+1, 2N), load balancing, health checks, failover strategies, multi-AZ/region |
| [performance-scalability.md](./05-reliability/performance-scalability.md) | Latency numbers, scalability cube (AKF), stateless vs stateful, database/cache/async optimization, capacity planning, load testing |
| [resilience-patterns.md](./05-reliability/resilience-patterns.md) | Circuit breaker, retry with exponential backoff+jitter, bulkhead, rate limiting (token bucket), graceful degradation, chaos engineering |
| [observability-design.md](./05-reliability/observability-design.md) | Three pillars (logs/metrics/traces), structured logging with MDC, Micrometer metrics, OpenTelemetry distributed tracing, SLO burn rate alerting, dashboard design |

### 06-design-process — Design Process

| Document | Description |
|----------|-------------|
| [sdd-template.md](./06-design-process/sdd-template.md) | Software Design Document template (14 sections), quality checklist, review criteria, minimal/full SDD variants |
| [design-review.md](./06-design-process/design-review.md) | Design review process (pre-review/meeting/post-review), comprehensive checklist, roles, common issues, anti-patterns, meeting notes template |
| [adr.md](./06-design-process/adr.md) | Architecture Decision Records (MADR format), lifecycle, 3 complete examples (DB choice, state machine, reversing decision), management, anti-patterns |
| [self-development-standards.md](./06-design-process/self-development-standards.md) | Project-specific standards: naming conventions, architecture principles, security requirements, performance targets, documentation standards, restrictions & do-nots |

## Core Design Principles

### Architecture Principles
- **Stateless services** for horizontal scaling
- **Loose coupling, high cohesion**
- **Defense in depth** for security
- **12-Factor App** principles
- **CAP theorem awareness** (AP for IM systems)
- **Read diffusion** for message storage (Turms pattern)
- **Lock-free concurrency** (CAS, thread count = CPU cores)
- **Custom lightweight state machine** (stateless, table-driven, zero-dependency)

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
- **Read diffusion** for messages (store by recipient, query on read)

### Security Design Principles
- **Never trust, always verify** (Zero Trust)
- **Least privilege** access
- **Defense in depth** — multiple security layers
- **Secure by design** — security in every phase
- **Encrypt** in transit and at rest
- **STRIDE threat modeling** for all significant features

### Reliability Principles
- **SLO-driven** — define and monitor service level objectives
- **Graceful degradation** — partial failure ≠ total failure
- **Bulkhead isolation** — failure in one component doesn't exhaust all resources
- **Circuit breaker** — fail fast when dependencies are unhealthy
- **Exponential backoff + jitter** — avoid thundering herd
- **Chaos engineering** — proactively test failure modes

### Design Process Principles
- **Design before code** — SDD must be approved before implementation
- **ADR for significant decisions** — document why, not just what
- **Peer review** — every design reviewed by at least 2 people
- **Living documents** — update SDDs when design changes
- **Evidence over claims** — every decision backed by data or reference

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
   ├── Security considerations (threat model)
   ├── Reliability & performance
   ├── Testing strategy
   └── Implementation plan

4. Design Review
   ├── Peer review (use design-review.md checklist)
   ├── Check against design guidelines
   ├── Identify risks
   └── Get approval

5. Create ADRs (if significant decisions)
   ├── Technology choices
   ├── Architecture patterns
   └── Cross-cutting concerns

6. Implementation
   ├── Follow TDD (RED → GREEN → REFACTOR)
   ├── Follow coding guidelines (04-Coding-Guidelines)
   └── Update design docs as needed

7. Post-Implementation Review
   ├── Compare implementation vs design
   ├── Identify lessons learned
   └── Update knowledge base
```

## References

### Books
- Clean Architecture (Robert C. Martin): https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- Domain-Driven Design (Eric Evans): https://domainlanguage.com/ddd/
- Building Microservices (Sam Newman): https://www.oreilly.com/library/view/building-microservices-2nd/9781492034018/
- Release It! (Michael Nygard): https://pragprog.com/titles/mnee2/release-it-second-edition/
- Designing Data-Intensive Applications (Martin Kleppmann): https://dataintensive.net/

### Frameworks & Standards
- 12-Factor App: https://12factor.net/
- Zalando RESTful API Guidelines: https://opensource.zalando.com/restful-api-guidelines/
- Microsoft REST API Guidelines: https://github.com/microsoft/api-guidelines
- OWASP API Security Top 10: https://owasp.org/www-project-api-security/
- OWASP Security Cheat Sheets: https://cheatsheetseries.owasp.org/
- MADR (Architecture Decision Records): https://adr.github.io/madr/

### Reliability & Operations
- Google SRE Book: https://sre.google/sre-book/table-of-contents/
- Google SRE Workbook: https://sre.google/workbook/table-of-contents/
- AWS Well-Architected Framework: https://docs.aws.amazon.com/wellarchitected/latest/
- Resilience4j: https://resilience4j.readme.io/
- OpenTelemetry: https://opentelemetry.io/
- Prometheus: https://prometheus.io/

### Reference Projects
- Turms (Java/Netty IM reference): https://github.com/turms-im/turms
- Mattermost: https://github.com/mattermost/mattermost
- Rocket.Chat: https://github.com/RocketChat/Rocket.Chat
- Matrix/Synapse: https://github.com/matrix-org/synapse

---

*Last updated: 2026-08-19*
*Total documents: 26*
