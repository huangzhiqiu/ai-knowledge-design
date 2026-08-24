---
name: architecture-review
description: Review system architecture for scalability, maintainability, separation of concerns, and design patterns. Use when evaluating architectural decisions, planning refactors, or reviewing system design.
argument-hint: [module or system to review]
---

# Architecture Review

Deep review of system architecture. Uses `architect` agent for analysis.

## Step 1: Map the Architecture

Read the codebase and produce an architecture map:

```
[Component A] → [Component B] → [Component C]
       ↓                              ↓
  [Database]                      [External API]
```

Identify:
- Entry points (routes, controllers, handlers, main)
- Business logic layer (services, use cases, domain)
- Data access layer (repositories, DAOs, models)
- External integrations (APIs, queues, caches)
- Shared utilities and cross-cutting concerns

## Step 2: Evaluate Architecture Principles

### Separation of Concerns
- [ ] Controllers only handle HTTP — no business logic
- [ ] Services contain all business logic
- [ ] Repositories encapsulate data access
- [ ] No circular dependencies between layers

### Single Responsibility
- [ ] Each module has one reason to change
- [ ] Files are focused (< 800 lines)
- [ ] No "god classes" or "god modules"

### Dependency Direction
- [ ] Dependencies point inward (controllers → services → repositories)
- [ ] Domain/business logic has zero framework dependencies
- [ ] External integrations are behind interfaces/abstractions

### DRY vs Coupling
- [ ] Shared logic is extracted — not copy-pasted
- [ ] BUT: modules don't share code just to avoid duplication if it creates coupling
- [ ] Common utilities are genuinely common, not forced abstractions

### Scalability
- [ ] Stateless services (no in-memory state between requests)
- [ ] Database queries are efficient (indexes, no N+1)
- [ ] External calls have timeouts and retries
- [ ] Long-running tasks are async (queues, background jobs)

### Error Handling
- [ ] Errors propagate correctly through layers
- [ ] Central error handling (middleware / exception handler)
- [ ] No swallowed exceptions
- [ ] External failures are handled gracefully (circuit breaker, fallback)

### Testability
- [ ] Dependencies are injected (constructor injection, not field injection)
- [ ] External services are behind interfaces (mockable)
- [ ] Test suite exists: unit + integration + E2E
- [ ] TDD workflow followed (tests before implementation)

## Step 3: Identify Patterns and Anti-patterns

**Good patterns to confirm:**
- Repository pattern for data access
- DTO/record projections for API responses (not raw entities)
- Constructor injection for DI
- Immutable data objects
- Event-driven for async work

**Anti-patterns to flag:**
- Anemic domain model (logic in services, entities are just data bags)
- Service locator instead of DI
- Shared mutable state
- Framework lock-in in business logic
- Over-engineering (abstractions with single implementation)

## Step 4: ADR for Recommendations

For each significant finding, write an Architecture Decision Record:

```markdown
## ADR-NNN: [Title]

**Status:** Proposed
**Context:** [What is the current state and why is it a problem?]
**Decision:** [What change do we recommend?]
**Consequences:**
- Positive: [benefits]
- Negative: [trade-offs]
- Neutral: [side effects]
```

## Step 5: Report

```markdown
# Architecture Review: [System/Module]

## Architecture Map
[ASCII diagram from Step 1]

## Principle Compliance
| Principle | Status | Notes |
|-----------|--------|-------|
| Separation of Concerns | PASS/WARN/FAIL | ... |
| Single Responsibility | PASS/WARN/FAIL | ... |
| Dependency Direction | PASS/WARN/FAIL | ... |
| Scalability | PASS/WARN/FAIL | ... |
| Testability | PASS/WARN/FAIL | ... |

## Patterns Found
- [Good pattern]: where and how it's used
- [Anti-pattern]: where and recommended fix

## ADRs
[List of proposed ADRs]

## Verdict
- **APPROVE** — architecture is sound, minor improvements only
- **IMPROVE** — significant issues but not blocking, create improvement tasks
- **REDESIGN** — fundamental problems require architectural changes before proceeding
```
