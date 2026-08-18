# Design Guidelines

> Industry-standard design guidelines and principles.

## Documents

| Document | Description |
|----------|-------------|
| [design-principles.md](./design-principles.md) | SOLID, DRY, KISS, YAGNI, and core design principles |
| [api-design-guidelines.md](./api-design-guidelines.md) | RESTful API design best practices |
| [architecture-principles.md](./architecture-principles.md) | Distributed systems, microservices, resilience, 12-Factor |
| [self-development-standards.md](./self-development-standards.md) | Self-Development internal coding requirements (to be filled) |

## Design Principles Summary

### Core Principles
- **SOLID**: Single Responsibility, Open-Closed, Liskov Substitution, Interface Segregation, Dependency Inversion
- **DRY**: Don't Repeat Yourself (single source of truth)
- **KISS**: Keep It Simple, Stupid (avoid unnecessary complexity)
- **YAGNI**: You Aren't Gonna Need It (no speculative features)
- **POLA**: Principle of Least Astonishment (follow conventions)

### Application Order
1. YAGNI/KISS first — cut the superfluous
2. DRY — eliminate true duplication
3. SOLID — apply when code shows tangled responsibilities

## API Design Summary
- Resource-oriented (nouns, not verbs)
- Proper HTTP methods and status codes
- Unified response/error format
- Pagination, filtering, sorting
- API versioning (URI path recommended)
- Idempotency for critical operations
- OpenAPI 3.0 documentation

## Architecture Summary
- Stateless services for horizontal scaling
- Loose coupling, high cohesion
- Resilience: circuit breaker, retry, bulkhead, timeout
- 12-Factor App principles
- CAP theorem awareness (AP for IM systems)
- Defense in depth for security

## References
- Clean Code by Robert C. Martin
- Building Microservices by Sam Newman
- Release It! by Michael Nygard
- 12 Factor App: https://12factor.net/
- Postman API Best Practices
- OWASP API Security Top 10
