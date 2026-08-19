# Coding Guidelines

> Comprehensive coding guidelines for CBOL Messaging Hub, organized by technology stack. Covers Java, Spring, networking, data layer, security, and quality operations.

## Categories

### 01-java-core — Java Core Guidelines

| Document | Description |
|----------|-------------|
| [java-coding-standards.md](./01-java-core/java-coding-standards.md) | Java naming conventions, formatting, comments, OOP principles |
| [java-concurrency.md](./01-java-core/java-concurrency.md) | Thread pools, locks, CAS, CompletableFuture, concurrent collections |
| [java-exception-logging.md](./01-java-core/java-exception-logging.md) | Exception hierarchy, handling patterns, SLF4J logging, MDC |
| [java-collections-io.md](./01-java-core/java-collections-io.md) | Collections framework, Stream API, NIO.2, serialization, resource management |
| [state-machine-guidelines.md](./01-java-core/state-machine-guidelines.md) | Stateless engine, table-driven transitions, testing, visualization (CBOL custom state machine) |

### 02-spring-framework — Spring Framework Guidelines

| Document | Description |
|----------|-------------|
| [spring-boot-best-practices.md](./02-spring-framework/spring-boot-best-practices.md) | Layered architecture, DI, thin controllers, DTO mapping, global exception handling |
| [spring-configuration-transaction-aop.md](./02-spring-framework/spring-configuration-transaction-aop.md) | Profile strategy, type-safe config, transaction propagation/isolation, AOP patterns |

### 03-networking-api — Networking & API Guidelines

| Document | Description |
|----------|-------------|
| [websocket-guidelines.md](./03-networking-api/websocket-guidelines.md) | WebSocket protocol, connection management, heartbeat, message framing |
| [http-rest-api-guidelines.md](./03-networking-api/http-rest-api-guidelines.md) | REST design, HTTP methods, status codes, versioning, pagination, HTTP client best practices |
| [netty-guidelines.md](./03-networking-api/netty-guidelines.md) | Thread model, ChannelPipeline, concurrency, memory management, zero-copy (Turms reference) |

### 04-data-layer — Data Layer Guidelines

| Document | Description |
|----------|-------------|
| [relational-database-mysql.md](./04-data-layer/relational-database-mysql.md) | Schema design, indexing, SQL best practices, connection pool, transactions |
| [nosql-database-mongodb.md](./04-data-layer/nosql-database-mongodb.md) | Document design, read diffusion (Turms), indexing, sharding, query optimization |
| [redis-cache-guidelines.md](./04-data-layer/redis-cache-guidelines.md) | Key design, data structures, caching patterns, rate limiting, distributed locks |
| [message-queue-guidelines.md](./04-data-layer/message-queue-guidelines.md) | Kafka/RocketMQ topic design, producer/consumer patterns, idempotency, DLQ, outbox pattern |

### 05-security — Security Guidelines

| Document | Description |
|----------|-------------|
| [security-guidelines.md](./05-security/security-guidelines.md) | OWASP Top 10, input validation, output encoding, secure storage, transport security |
| [authentication-authorization.md](./05-security/authentication-authorization.md) | JWT, refresh tokens, RBAC/ABAC, WebSocket security, API security, CORS, security headers |
| [sonarqube-devsecops-guidelines.md](./05-security/sonarqube-devsecops-guidelines.md) | SonarQube quality gates, issue types (bugs/vulnerabilities/hotspots), DevSecOps pipeline, SAST/DAST/SCA, secret scanning, incident response |

### 06-quality-ops — Quality & Operations Guidelines

| Document | Description |
|----------|-------------|
| [code-quality.md](./06-quality-ops/code-quality.md) | Code review checklist, complexity metrics, duplication, refactoring patterns |
| [sonar-rules.md](./06-quality-ops/sonar-rules.md) | SonarQube rule configuration, quality profiles |
| [testing-guidelines.md](./06-quality-ops/testing-guidelines.md) | Test pyramid, JUnit 5, Mockito, AssertJ, integration tests, Testcontainers, coverage, performance testing |
| [observability-guidelines.md](./06-quality-ops/observability-guidelines.md) | Logging (SLF4J/MDC), metrics (Micrometer/Prometheus), distributed tracing (OpenTelemetry), alerting, Grafana dashboards |

## Quick Reference

### Mandatory Rules

1. **No `System.out.println`** — use SLF4J logger
2. **No hardcoded secrets** — use environment variables / secret manager
3. **No SQL string concatenation** — use PreparedStatement / ORM parameterization
4. **No production code before failing test** — TDD discipline
5. **No `com.hsbc.*` package** — use `com.selfdevelopment.ai.messaging.*`
6. **No Chinese folder/file names** — use English, kebab-case
7. **Always set TTL on Redis cache keys** — except specific use cases
8. **Always use parameterized logging** — `log.debug("msg: {}", var)` not `log.debug("msg: " + var)`
9. **Always include exception as last parameter** — `log.error("msg", e)` for stack trace
10. **Always set timeouts on HTTP clients** — connect + read timeouts

### Quality Gates (SonarQube)

| Metric | Threshold |
|--------|-----------|
| Coverage | >= 80% |
| New Code Coverage | >= 80% |
| Duplicated Lines | <= 3% |
| Critical/Blocker Issues | 0 |
| Security Hotspots Reviewed | 100% |
| Vulnerabilities | 0 |
| Maintainability Rating | A |
| Reliability Rating | A |
| Security Rating | A |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Network | Netty 4.1.x (WebSocket/TCP) |
| Database | MySQL 8.0 + MongoDB 6.x |
| Cache | Redis 6.x (Cluster) |
| Message Queue | Kafka / RocketMQ |
| Build | Maven |
| Code Quality | SonarQube + SpotBugs + Semgrep |
| Testing | JUnit 5 + Mockito + AssertJ + Testcontainers |
| Observability | SLF4J + Micrometer + Prometheus + OpenTelemetry |
| Container | Docker + Kubernetes |

## How to Use

1. **Before writing code**: Read relevant category guidelines
2. **During code review**: Use guidelines as checklist
3. **In CI/CD**: Enforce quality gates via SonarQube
4. **For new technologies**: Add new guideline documents to appropriate category

## References

- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- Alibaba Java Coding Guidelines: https://github.com/alibaba/p3c
- Spring Boot Best Practices: https://github.com/kunaljainflair/Springboot_dev_best_practices
- OWASP Top 10: https://owasp.org/www-project-top-ten/
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/
- SonarQube Rules: https://rules.sonarsource.com/java
- 12-Factor App: https://12factor.net/
- Turms (Java/Netty IM reference): https://github.com/turms-im/turms

---

*Last updated: 2026-08-19*
