# Architecture Design Principles

> Industry-standard architecture principles for building scalable, resilient systems.

## Core Architecture Principles

### 1. Single Responsibility (at Module Level)
- Each service/module has one clear responsibility
- Bounded context from DDD defines service boundaries
- A service should have one reason to change

### 2. Loose Coupling
- Services communicate via well-defined interfaces (APIs, events)
- No shared database between services
- Point-to-point integration minimized; prefer event/message bus
- Changes in one service shouldn't break others

### 3. High Cohesion
- Related functionality grouped together
- Data and behavior that belong together stay together
- Avoid "utility" services that do unrelated things

### 4. Stateless Services
- Service instances don't store session state locally
- State stored in shared cache (Redis) or database
- Enables horizontal scaling — any instance can handle any request

### 5. Fail Fast & Graceful Degradation
- Detect failures early (circuit breakers, timeouts)
- Degrade gracefully rather than cascade
- Return partial results when full result unavailable
- Bulkhead pattern: isolate failures to prevent spread

### 6. Defense in Depth
- Multiple layers of security
- Don't rely on single security control
- Validate at every boundary, not just edge

### 7. Observability by Design
- Logs, metrics, traces built in from day one
- Structured logging with correlation IDs
- Health checks and readiness probes
- Alerting on SLO violations

## Distributed System Principles

### CAP Theorem
- Consistency, Availability, Partition tolerance — choose 2
- In distributed systems, P is unavoidable
- Choose between CP (consistent) and AP (available) based on business need
- IM message delivery: AP preferred (availability > strict consistency)

### Fallacies of Distributed Computing
1. The network is reliable
2. Latency is zero
3. Bandwidth is infinite
4. The network is secure
5. Topology doesn't change
6. There is one administrator
7. Transport cost is zero
8. The network is homogeneous

**Design assuming all are false.**

### Idempotency
- Every operation should be safely retryable
- Use unique request IDs / idempotency keys
- At-least-once delivery + idempotent consumption = effectively exactly-once

### Backpressure
- Consumers signal when overwhelmed
- Producers slow down or buffer
- Prevent cascading failures
- Implement with bounded queues + rejection policies

## Microservices Principles

### When to Use Microservices
- Team size > 20 (Conway's Law)
- Independent scaling needs
- Different tech stacks for different modules
- Need for independent deployment

### When NOT to Use
- Small team (< 10)
- Simple domain
- No DevOps maturity
- Start with modular monolith, extract when needed

### Service Boundary Design
- One service per bounded context (DDD)
- Avoid distributed monolith (tightly coupled services)
- Each service owns its data
- API versioning for backward compatibility

### Data Management
- Database per service (no shared DB)
- Sagas for distributed transactions
- Event-driven data consistency (eventual)
- CQRS for complex read models

## Resilience Patterns

| Pattern | Purpose |
|---------|---------|
| Circuit Breaker | Stop calling failing service, fail fast |
| Retry with Backoff | Recover from transient failures |
| Bulkhead | Isolate resource pools, prevent spread |
| Timeout | Don't wait indefinitely |
| Fallback | Provide default/partial result |
| Rate Limiting | Protect from overload |
| Health Check | Detect unhealthy instances |

## Scalability Principles

### Horizontal vs Vertical Scaling
- **Horizontal**: add more instances (preferred for cloud)
- **Vertical**: bigger machine (simpler, but has limits)
- Design for horizontal from the start (stateless, shared-nothing)

### Scaling Bottlenecks
- Database (most common) → read replicas, sharding, caching
- Stateful services → make stateless or use consistent hashing
- Single points of failure → redundancy, clustering

### Caching Strategy
- Cache at multiple layers: CDN, reverse proxy, app cache, DB query cache
- Cache invalidation is hard — use TTL + event-based invalidation
- Cache stampede protection (lock, single-flight)

## 12-Factor App Principles

| Factor | Description |
|--------|-------------|
| 1. Codebase | One codebase tracked in VCS, many deploys |
| 2. Dependencies | Explicitly declare and isolate dependencies |
| 3. Config | Store config in environment |
| 4. Backing Services | Treat backing services as attached resources |
| 5. Build, Release, Run | Strictly separate build and run stages |
| 6. Processes | Execute app as one or more stateless processes |
| 7. Port Binding | Export services via port binding |
| 8. Concurrency | Scale out via process model |
| 9. Disposability | Maximize robustness with fast startup and graceful shutdown |
| 10. Dev/Prod Parity | Keep dev, staging, prod as similar as possible |
| 11. Logs | Treat logs as event streams |
| 12. Admin Processes | Run admin/management tasks as one-off processes |

## API Gateway Pattern

### Responsibilities
- Request routing
- Authentication / authorization
- Rate limiting
- Request/response transformation
- Aggregation (backend for frontend)
- Logging and metrics

### When to Use
- Multiple client types (web, mobile, third-party)
- Need for unified entry point
- Cross-cutting concerns at edge

## References
- Building Microservices by Sam Newman
- Release It! by Michael Nygard
- Domain-Driven Design by Eric Evans
- 12 Factor App: https://12factor.net/
- Google SRE Book
