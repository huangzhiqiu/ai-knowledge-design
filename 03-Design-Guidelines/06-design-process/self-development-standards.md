# Self-Development Internal Standards

> Internal design and development standards specific to the CBOL Refactor (Self-Development) project. These standards complement the generic guidelines in 03-Design-Guidelines and 04-Coding-Guidelines with project-specific requirements.

## Overview

This document captures project-specific standards that apply to all CBOL Messaging Hub development. These are derived from Self-Development internal requirements, team conventions, and lessons learned. All team members must follow these standards; deviations require explicit approval and documentation.

## Naming Conventions

### Project & Module Names

| Item | Convention | Example |
|------|-----------|---------|
| Java package | `com.selfdevelopment.ai.messaging.{module}` | `com.selfdevelopment.ai.messaging.message` |
| Maven artifactId | `cbol-{module}` | `cbol-message-service` |
| Maven groupId | `com.selfdevelopment.ai` | `com.selfdevelopment.ai` |
| Docker image | `cbol/{module}:{version}` | `cbol/message-service:1.0.0` |
| Kubernetes namespace | `cbol-{env}` | `cbol-prod`, `cbol-staging` |
| Git branch | `{type}/CBOL-{XXX}-{short-desc}` | `feat/CBOL-123-message-forwarding` |

### Code Naming

| Item | Convention | Example |
|------|-----------|---------|
| Class | PascalCase | `MessageService`, `ConversationStateMachine` |
| Interface | PascalCase (no `I` prefix) | `MessageRepository`, `StateMachine` |
| Method | camelCase | `sendMessage()`, `getConversationById()` |
| Variable | camelCase | `messageId`, `conversationState` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT_MS` |
| Enum | PascalCase (type), UPPER_SNAKE_CASE (values) | `ConversationState.INIT` |
| DTO | `{Action}{Entity}Request/Response` | `SendMessageRequest`, `MessageResponse` |
| Exception | `{Type}Exception` | `MessageNotFoundException`, `InvalidStateException` |

### Database Naming

| Item | Convention | Example |
|------|-----------|---------|
| Table | snake_case, plural | `messages`, `conversations`, `users` |
| Column | snake_case | `message_id`, `sender_id`, `created_at` |
| Primary key | `id` | `id BIGINT PRIMARY KEY` |
| Foreign key | `{entity}_id` | `conversation_id`, `sender_id` |
| Index | `idx_{table}_{columns}` | `idx_messages_conversation_created` |
| Unique constraint | `uk_{table}_{columns}` | `uk_users_email` |
| MongoDB collection | snake_case, plural | `messages`, `conversations` |
| MongoDB field | camelCase | `messageId`, `senderId` |

### API Naming

| Item | Convention | Example |
|------|-----------|---------|
| REST endpoint | kebab-case, plural nouns | `/api/v1/messages`, `/api/v1/conversations` |
| Query parameter | camelCase | `?pageSize=20&cursor=abc` |
| JSON field | camelCase | `messageId`, `createdAt` |
| HTTP header | `X-CBOL-{Name}` | `X-CBOL-Request-Id`, `X-CBOL-Device-Type` |
| WebSocket message type | `{domain}.{action}` | `message.send`, `conversation.create` |
| Error code | `{DOMAIN}_{ERROR}` | `MESSAGE_NOT_FOUND`, `INVALID_STATE_TRANSITION` |

### Configuration Naming

| Item | Convention | Example |
|------|-----------|---------|
| Environment variable | UPPER_SNAKE_CASE | `DB_HOST`, `REDIS_PORT`, `JWT_SECRET` |
| YAML property | kebab-case | `server.port`, `spring.datasource.url` |
| Redis key | `{domain}:{entity}:{id}` | `user:123`, `conversation:456:members` |
| Kafka topic | `{domain}.{event}` | `message.sent`, `conversation.created` |
| Kafka consumer group | `{service}-{purpose}` | `message-service-persister` |

## Architecture Principles

### Project-Specific Architecture Rules

1. **Stateless services**: All application services must be stateless. State goes in Redis, MongoDB, or MySQL. This enables horizontal scaling.

2. **Read diffusion for messages**: Follow Turms design — messages stored by recipient, queried on read. No fanout write.

3. **Lock-free concurrency**: Thread count = CPU cores, use CAS instead of locks where possible. Reference Turms implementation.

4. **Custom lightweight state machine**: Use our stateless, table-driven state machine (see 01-CBOL-Domain-Knowledge/state-machine/). No external state machine library.

5. **Session ID = user ID + device type**: Multi-device support requires per-device sessions.

6. **Minimal architecture**: Avoid over-engineering. If a pattern isn't needed, don't use it. Reference Turms philosophy.

7. **Polyglot persistence**: MySQL for relational data (users, config), MongoDB for messages, Redis for cache/sessions.

8. **Event-driven for async**: Use message queue for non-critical path operations (persistence, notifications, analytics).

### Module Boundaries

```
cbol-parent/
├── cbol-common/          # Shared utilities, constants, exceptions
├── cbol-domain/          # Domain entities, value objects, domain services
├── cbol-infrastructure/  # Database, cache, messaging, external integrations
├── cbol-application/     # Use cases, DTOs, orchestration
├── cbol-api/             # REST controllers, WebSocket handlers
├── cbol-gateway/         # API gateway, routing, load balancing
└── cbol-auth/            # Authentication, authorization, token management
```

**Dependency rule**: `api → application → domain ← infrastructure`
- Domain has no external dependencies
- Infrastructure implements domain interfaces
- Application orchestrates domain and infrastructure
- API handles HTTP/WebSocket protocol

## Security Requirements

### Mandatory Security Controls

1. **Authentication**: All endpoints except `/auth/**` and `/health/**` require authentication. Use JWT with short-lived access tokens (15 min) + refresh token rotation.

2. **Authorization**: RBAC + ABAC hybrid. Roles: USER, AGENT, ADMIN, SYSTEM. Resource-level checks for ownership and membership.

3. **Input validation**: All external input must be validated. Use Bean Validation (`@NotNull`, `@Size`, `@Pattern`) + custom validators.

4. **Output encoding**: All user-generated content must be escaped before rendering. No raw HTML in messages.

5. **Encryption**: TLS 1.3 for all traffic. AES-256 for sensitive data at rest. No hardcoded secrets.

6. **Audit logging**: All security-sensitive operations (login, permission change, data export) must be audited with user ID, timestamp, action, and result.

7. **Rate limiting**: All APIs rate-limited per user. Auth endpoints have stricter limits to prevent brute force.

8. **WebSocket security**: First message must be authentication. Unauthenticated connections closed after 5 seconds.

### Prohibited Practices

- ❌ Hardcoding secrets, tokens, or passwords in code or config
- ❌ Using `com.hsbc.*` package names (use `com.selfdevelopment.ai.messaging.*`)
- ❌ Storing passwords in plaintext or reversible encryption
- ❌ Disabling security for "convenience" in any environment
- ❌ Logging sensitive data (passwords, tokens, message content, PII)
- ❌ Using MD5 or SHA-1 for hashing (use bcrypt or Argon2)
- ❌ Trusting client-side validation only (always validate server-side)

## Performance Guidelines

### Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| API P50 latency | < 50ms | Server-side, excluding network |
| API P99 latency | < 500ms | Server-side, excluding network |
| API P99.9 latency | < 2s | Server-side, excluding network |
| WebSocket message delivery | < 100ms | From send to recipient receive (online) |
| Message persistence | < 50ms | Async, not on critical path |
| Error rate | < 0.1% | 5xx / total requests |
| Availability | 99.99% | Monthly uptime |

### Performance Rules

1. **No N+1 queries**: Always use JOIN or batch fetch. Use `@EntityGraph` or fetch joins in JPA.

2. **Pagination required**: All list endpoints must support pagination. Use keyset pagination (cursor) for large datasets, not OFFSET.

3. **Cache everything possible**: Multi-level cache (Caffeine L1 + Redis L2). Cache-aside pattern with TTL + invalidation on write.

4. **Async non-critical path**: Message persistence, notifications, analytics — all async via message queue. Critical path only validates and acks.

5. **Connection pooling**: HikariCP for MySQL (15-20 connections), Lettuce for Redis (shared connection).

6. **Batch operations**: Use batch inserts/updates for bulk operations. JDBC batch size = 100.

7. **Avoid blocking in Netty event loop**: All blocking operations (DB, cache, HTTP) must be offloaded to business thread pool.

8. **Zero-copy where possible**: Use Netty's zero-copy for message transfer between connections.

## Documentation Standards

### Required Documentation

| Document | When Required | Owner |
|----------|--------------|-------|
| SDD (Software Design Document) | New feature, architectural change | Feature author |
| ADR (Architecture Decision Record) | Technology choice, architecture pattern | Tech lead |
| API documentation (OpenAPI) | New or changed API | Feature author |
| Runbook | New service or operational procedure | SRE / feature author |
| README | Every module and service | Module owner |
| Code comments | Complex logic, public APIs | Developer |

### Documentation Rules

1. **English only**: All documentation, code comments, commit messages, and folder/file names must be in English.

2. **Markdown format**: All documents use Markdown. Folder names in kebab-case.

3. **Diagrams as code**: Use Mermaid or PlantUML for diagrams. No binary image files in docs.

4. **Keep docs current**: Code changes that affect behavior must update relevant docs. PRs without doc updates for behavior changes will be rejected.

5. **Reference sources**: Technical documents must cite reference sources (links to GitHub, articles, books).

## Restrictions & Do-Nots

### Absolute Restrictions

- ❌ **Do not commit secrets**: API keys, tokens, passwords, private keys — never in git. Use environment variables or secret management.
- ❌ **Do not use `com.hsbc.*` packages**: All code must use `com.selfdevelopment.ai.messaging.*`.
- ❌ **Do not create Chinese folder/file names**: All names must be English.
- ❌ **Do not auto-merge PRs**: All PRs require human peer review before merge.
- ❌ **Do not skip tests**: TDD required. No production code before failing test exists.
- ❌ **Do not bypass 3-strike escalation**: If 3 retries fail, stop and ask human. Don't keep retrying.
- ❌ **Do not claim completion without evidence**: Every completion claim needs command + output + exit code.
- ❌ **Do not skip knowledge base reading**: Always inject relevant KB knowledge before writing code.
- ❌ **Do not use production data in test/dev**: Anonymize or generate synthetic data.
- ❌ **Do not disable security controls**: Even in dev/staging. Use test credentials, not disabled security.

### Coding Restrictions

- ❌ **Do not use `System.out.println()`**: Use SLF4J logger.
- ❌ **Do not swallow exceptions**: Empty catch blocks are prohibited. At minimum log the exception.
- ❌ **Do not use `@SuppressWarnings` without justification**: Add comment explaining why.
- ❌ **Do not use raw types**: Always use generics (`List<String>`, not `List`).
- ❌ **Do not return `null`** for collections: Return empty collections (`Collections.emptyList()`).
- ❌ **Do not use `Date`/`Calendar`**: Use `java.time` API (`Instant`, `LocalDateTime`, `ZonedDateTime`).
- ❌ **Do not use `String` concatenation in loops**: Use `StringBuilder`.
- ❌ **Do not hardcode magic numbers**: Define constants with meaningful names.

### Architecture Restrictions

- ❌ **Do not add circular dependencies** between modules.
- ❌ **Do not let domain layer depend on infrastructure** (inversion of control).
- ❌ **Do not put business logic in controllers** (thin controllers, fat domain).
- ❌ **Do not use distributed transactions** (use Saga pattern / eventual consistency).
- ❌ **Do not sync call in Netty event loop** (offload to business thread pool).
- ❌ **Do not store session state in application memory** (use Redis for distributed sessions).

## Compliance & Governance

### Code Review Requirements

- Every PR requires at least 1 approver (2 for critical changes)
- Critical changes: security, authentication, database schema, state machine
- All SonarQube critical/blocker issues must be fixed before merge
- Code coverage must not decrease (line >= 80%, branch >= 70%)

### Change Management

- All changes must have a Jira ticket
- Branch naming: `{type}/CBOL-{XXX}-{short-desc}`
- Commit format: Conventional Commits `{type}({scope}): {subject} (CBOL-XXX)`
- PR title must include Jira ticket number
- Deployment to production requires change approval (CAB) for major changes

### Data Governance

- Data classification: Public, Internal, Confidential, Restricted
- PII must be identified and protected (encryption, access control, audit)
- Data retention policy must be defined per data type
- Data deletion must be supported (GDPR right to be forgotten)
- No production data in non-production environments

## References

- Project AGENTS.md: `../AGENTS.md`
- Project QUICKSTART.md: `../QUICKSTART.md`
- Design Guidelines: `../03-Design-Guidelines/`
- Coding Guidelines: `../04-Coding-Guidelines/`
- CBOL Domain Knowledge: `../01-CBOL-Domain-Knowledge/`
- State Machine Design: `../01-CBOL-Domain-Knowledge/state-machine/`

---

*Last updated: 2026-08-19*
*Owner: CBOL Refactor Tech Lead*
*Review cycle: Quarterly*
