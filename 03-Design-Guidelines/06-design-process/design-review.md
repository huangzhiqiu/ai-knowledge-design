# Design Review Guidelines

> Best practices for conducting design reviews in CBOL Messaging Hub. Covers review process, checklist, roles, anti-patterns, and how to run effective design review meetings.

## What is a Design Review?

```
Design Review = structured evaluation of a software design by peers and stakeholders

Goals:
  - Catch design flaws early (before implementation)
  - Share knowledge and context
  - Ensure consistency with project standards
  - Identify risks and trade-offs
  - Get buy-in from stakeholders

Benefits:
  - Fewer rework cycles (design is cheaper to change than code)
  - Better quality (multiple perspectives catch more issues)
  - Knowledge sharing (team understands the design)
  - Onboarding (new team members learn through reviews)
  - Documentation (review comments become part of design history)
```

## When to Conduct a Design Review

```
✅ Required for:
  - New service or major feature
  - Architectural change (affects multiple services)
  - API design (new or breaking change)
  - Database schema change (new tables, major changes)
  - Security-sensitive feature (auth, encryption, data privacy)
  - Performance-critical component
  - Integration with external system
  - State machine design

🟡 Recommended for:
  - Medium-sized feature
  - Refactoring affecting public interfaces
  - New technology introduction
  - Configuration changes affecting multiple services

❌ Not required for:
  - Bug fixes (no design change)
  - Trivial UI changes
  - Internal refactoring (no external interface change)
  - Documentation updates
  - Configuration tweaks (single service)
```

## Design Review Process

### Phase 1: Pre-Review (Author)

```
1. Complete SDD draft (see sdd-template.md)
2. Self-review against design guidelines:
   - [ ] Follows 03-Design-Guidelines
   - [ ] Follows 04-Coding-Guidelines
   - [ ] All sections filled (or marked N/A)
   - [ ] Diagrams included (architecture, sequence, data model)
   - [ ] Alternatives considered
   - [ ] Risks identified
   - [ ] Open questions listed

3. Share SDD with reviewers (at least 24 hours before meeting)
   - PR with SDD document
   - Link to Jira ticket
   - Summary of key design decisions

4. Prepare presentation (5-10 min max):
   - Problem statement
   - Proposed solution (high-level)
   - Key decisions and trade-offs
   - Areas where you specifically want feedback
```

### Phase 2: Review Meeting (All Participants)

```
Meeting Structure (45-60 min):

1. Context Setting (5 min)
   - Author presents problem and goals
   - Reviewers confirm understanding

2. Design Walkthrough (10-15 min)
   - Author presents architecture, API, data model
   - Focus on key decisions, not every detail
   - Reviewers ask clarifying questions

3. Deep Dive Discussion (20-30 min)
   - Reviewers provide feedback
   - Discuss trade-offs and alternatives
   - Identify risks and gaps
   - Prioritize issues (must-fix vs should-fix vs nice-to-have)

4. Action Items & Next Steps (5 min)
   - Document all feedback and decisions
   - Assign action items with owners and deadlines
   - Determine if another review is needed

Meeting Rules:
  - Stay focused on design, not implementation details
  - Be respectful (critique ideas, not people)
  - Encourage dissenting opinions
  - Timebox discussions (parking lot for off-topic)
  - Document decisions and rationale
```

### Phase 3: Post-Review (Author)

```
1. Update SDD based on feedback:
   - Address all must-fix issues
   - Address should-fix issues (or document why not)
   - Add discussion outcomes to design rationale
   - Update open questions (resolve or keep open)

2. Respond to review comments:
   - For each comment: addressed / not addressed (with reason)
   - Push updated SDD as new commit

3. Get final approval:
   - Reviewers sign off (comment "LGTM" or approve PR)
   - Tech lead / architect gives final approval
   - SDD status changes to "Approved"

4. Implementation:
   - Follow approved SDD
   - If design changes during implementation, update SDD and notify reviewers
```

## Design Review Checklist

### Architecture

- [ ] Does the architecture solve the stated problem?
- [ ] Is it consistent with existing system architecture?
- [ ] Are component responsibilities clear and well-defined?
- [ ] Is there appropriate separation of concerns?
- [ ] Are dependencies between components clear?
- [ ] Is the architecture scalable to expected load?
- [ ] Are there single points of failure?
- [ ] Is the architecture too complex for the problem? (KISS)
- [ ] Are alternatives considered and documented?

### API Design

- [ ] Are REST endpoints resource-oriented (nouns, not verbs)?
- [ ] Are HTTP methods used correctly (GET, POST, PUT, PATCH, DELETE)?
- [ ] Are status codes appropriate (200, 201, 204, 400, 401, 403, 404, 409, 429, 500)?
- [ ] Is request/response format consistent?
- [ ] Is pagination designed for large datasets?
- [ ] Is error handling consistent (error code, message, details)?
- [ ] Is API versioning strategy defined?
- [ ] Are WebSocket message types and flows documented?
- [ ] Is idempotency handled for critical operations?

### Data Design

- [ ] Is the data model normalized appropriately?
- [ ] Are indexes designed for query patterns?
- [ ] Are primary keys appropriate (Snowflake ID, UUID, auto-increment)?
- [ ] Is sharding strategy defined (if needed)?
- [ ] Is data migration plan included?
- [ ] Is cache strategy defined (what to cache, TTL, invalidation)?
- [ ] Are message queue topics and partition keys defined?
- [ ] Is data retention policy defined?
- [ ] Are sensitive data fields identified and protected?

### Security

- [ ] Is authentication mechanism appropriate?
- [ ] Is authorization (RBAC/ABAC) defined?
- [ ] Is data encrypted at rest and in transit?
- [ ] Are input validation and output encoding handled?
- [ ] Is threat model included?
- [ ] Are security risks identified and mitigated?
- [ ] Is audit logging included for sensitive operations?
- [ ] Are rate limits and abuse prevention defined?
- [ ] Is secret management defined (no hardcoded secrets)?

### Reliability & Performance

- [ ] Are availability targets defined (SLO/SLA)?
- [ ] Is failover strategy defined?
- [ ] Are circuit breakers configured for external dependencies?
- [ ] Is retry strategy defined (backoff + jitter)?
- [ ] Is graceful degradation planned?
- [ ] Are performance targets defined (P50, P99 latency, throughput)?
- [ ] Is capacity planning included?
- [ ] Is monitoring and alerting plan included?
- [ ] Are bulkheads / resource isolation considered?

### Testing & Operations

- [ ] Is testing strategy defined (unit, integration, E2E, performance)?
- [ ] Are key test scenarios identified?
- [ ] Is test data strategy defined?
- [ ] Is deployment strategy defined (blue-green, canary, rolling)?
- [ ] Is rollback plan included?
- [ ] Are feature flags used for risky changes?
- [ ] Is runbook included for common issues?
- [ ] Are configuration changes documented?
- [ ] Is observability plan included (logs, metrics, traces)?

## Roles in Design Review

| Role | Responsibility |
|------|---------------|
| **Author** | Creates SDD, presents design, addresses feedback, updates SDD |
| **Reviewer (Peer)** | Reviews design, provides feedback, asks questions, identifies issues |
| **Tech Lead / Architect** | Final approval, ensures alignment with architecture vision, resolves disputes |
| **Product Manager** | Validates requirements, ensures design meets business needs |
| **Security Engineer** | Reviews security design, threat model, compliance (for security-sensitive features) |
| **SRE / DevOps** | Reviews deployment, monitoring, reliability, operational aspects |
| **QA Lead** | Reviews testability, test strategy, edge cases |

Minimum required: Author + 2 Reviewers + Tech Lead

## Common Design Review Issues

### Architecture Issues

| Issue | Description | How to Address |
|-------|-------------|----------------|
| **Over-engineering** | Design is more complex than needed | Apply KISS/YAGNI, simplify |
| **Under-engineering** | Design won't scale or handle edge cases | Consider future needs, add abstractions where needed |
| **Tight coupling** | Components too dependent on each other | Apply dependency inversion, event-driven communication |
| **God component** | One component does too much | Split into smaller, focused components |
| **Ignoring existing patterns** | Reinventing the wheel | Reuse existing patterns, libraries, services |
| **No fallback** | Single point of failure | Add redundancy, failover, graceful degradation |

### API Issues

| Issue | Description | How to Address |
|-------|-------------|----------------|
| **Verb-based URLs** | /getUser, /sendMessage | Use nouns: /users, /messages |
| **Inconsistent formats** | Different response formats per endpoint | Standardize response envelope |
| **Missing pagination** | Returns all data, will break at scale | Add cursor/offset pagination |
| **No error codes** | Only HTTP status, no specific error code | Add error code + message + details |
| **Breaking changes** | Changes existing API without versioning | Use API versioning, deprecation policy |
| **Chatty API** | Too many small requests, high latency | Combine endpoints, use batch operations |

### Data Issues

| Issue | Description | How to Address |
|-------|-------------|----------------|
| **Missing indexes** | Slow queries on large tables | Add indexes for query patterns |
| **Over-indexing** | Too many indexes, slow writes | Index only needed columns |
| **No sharding plan** | Single DB will hit capacity limit | Plan sharding strategy early |
| **No migration plan** | Schema change breaks existing data | Include migration and rollback plan |
| **Cache without invalidation** | Stale data served to users | Define cache invalidation strategy |
| **No data retention** | Data grows indefinitely, costs increase | Define retention and archiving policy |

## Design Review Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| **Rubber stamp** | Reviewers approve without reading | Require specific feedback, ask questions |
| **Bikeshedding** | Focus on trivial details, ignore big issues | Timebox discussions, prioritize by impact |
| **Design by committee** | Too many opinions, no decision | Clear decision maker (tech lead), timebox |
| **Late review** | Review after implementation started | Review before any code is written |
| **No follow-up** | Feedback ignored, SDD not updated | Track action items, require updated SDD before approval |
| **Only senior reviews** | Juniors don't participate, knowledge not shared | Encourage all team members to review |
| **Too long meeting** | 2+ hour review, fatigue, poor decisions | Keep to 45-60 min, split large designs |
| **No agenda** | Meandering discussion, no structure | Send agenda and SDD in advance |
| **Personal attacks** | Criticize person, not idea | Ground rules, facilitator enforces respect |
| **No documentation** | Decisions lost, repeated discussions | Document all decisions and rationale in SDD |
| **Scope creep** | Review becomes brainstorming for new features | Stay in scope, park new ideas for later |
| **Ignoring non-functional** | Only review features, ignore performance/security | Include NFR checklist, require SLO targets |

## Design Review Template

### Review Meeting Notes Template

```markdown
# Design Review: {SDD Title}

| Field | Value |
|-------|-------|
| **Date** | {YYYY-MM-DD} |
| **Author** | {Name} |
| **Attendees** | {Names} |
| **SDD Link** | {URL} |
| **Jira Ticket** | {CBOL-XXX} |

## Summary
{2-3 sentence summary of design}

## Key Decisions
1. {Decision} — Rationale: {reason}
2. {Decision} — Rationale: {reason}

## Feedback & Issues

### Must-Fix (Block Approval)
- [ ] {Issue} — Owner: {name} — Due: {date}
- [ ] {Issue} — Owner: {name} — Due: {date}

### Should-Fix (Address Before Implementation)
- [ ] {Issue} — Owner: {name} — Due: {date}
- [ ] {Issue} — Owner: {name} — Due: {date}

### Nice-to-Have (Consider During Implementation)
- [ ] {Suggestion}
- [ ] {Suggestion}

### Open Questions
- [ ] {Question} — Owner: {name}
- [ ] {Question} — Owner: {name}

## Approval
- [ ] {Reviewer 1} — Approved / Approved with changes / Request changes
- [ ] {Reviewer 2} — Approved / Approved with changes / Request changes
- [ ] {Tech Lead} — Final approval

## Next Steps
1. {Action} — Owner: {name} — Due: {date}
2. {Action} — Owner: {name} — Due: {date}
3. Follow-up review needed: Yes / No
```

## References

- Google Design Review Guide: https://www.google.com/url?q=https://docs.google.com/...
- Martin Fowler - Design Stamina Hypothesis: https://martinfowler.com/bliki/DesignStaminaHypothesis.html
- AWS Well-Architected Reviews: https://aws.amazon.com/architecture/well-architected/
- Code Review Best Practices (Google): https://google.github.io/eng-practices/review/
- The Pragmatic Programmer (Chapter on Design by Contract): https://pragprog.com/titles/tpp20/the-pragmatic-programmer-20th-anniversary-edition/
- Architecture Decision Records (ADR): https://adr.github.io/
- NIST SP 800-28 (Guidelines on Active Content and Mobile Code): https://csrc.nist.gov/publications/detail/sp/800-28/final
