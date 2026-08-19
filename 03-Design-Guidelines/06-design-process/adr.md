# Architecture Decision Record (ADR) Guidelines

> Best practices for creating and managing Architecture Decision Records (ADRs) in CBOL Messaging Hub. Covers ADR format, process, lifecycle, and examples.

## What is an ADR?

```
Architecture Decision Record (ADR) = short document capturing an important architectural decision

An ADR captures:
  - Context: What is the issue and background?
  - Decision: What did we decide?
  - Consequences: What are the trade-offs and impacts?

Why ADRs?
  - Document why decisions were made (not just what)
  - Prevent re-litigating past decisions
  - Onboard new team members quickly
  - Provide historical context for future changes
  - Make architecture explicit and reviewable

ADR vs SDD:
  - ADR: Single decision, 1-2 pages, focuses on "why"
  - SDD: Complete design for a feature, 10+ pages, focuses on "what" and "how"
  - Relationship: An SDD may reference multiple ADRs
```

## When to Create an ADR

```
✅ Create an ADR for:
  - Technology choice (framework, library, database, message queue)
  - Architecture pattern (microservices vs monolith, event-driven vs request-response)
  - Cross-cutting concern (authentication, logging, monitoring, error handling)
  - Significant trade-off (consistency vs availability, performance vs simplicity)
  - Reversal of a previous decision
  - Standard that affects multiple services

🟡 Consider an ADR for:
  - API design pattern (REST vs GraphQL, versioning strategy)
  - Data modeling approach (normalized vs denormalized)
  - Deployment strategy (containerization, orchestration)

❌ Don't create an ADR for:
  - Implementation details (specific class design, algorithm choice)
  - Trivial decisions (library version bump, config change)
  - Already covered by existing ADR (unless reversing it)
```

## ADR Format

### Standard ADR Template (MADR)

```markdown
# ADR-{NNN}: {Short Title of Decision}

- **Status**: Proposed / Accepted / Deprecated / Superseded by ADR-{NNN}
- **Date**: {YYYY-MM-DD}
- **Deciders**: {Names}
- **Consulted**: {Names}
- **Informed**: {Names}

## Context and Problem Statement
{Describe the context and the problem we're trying to solve. What forces are at play?}

## Decision Drivers
{List the key factors driving this decision:}
- {Driver 1: e.g., Performance requirements}
- {Driver 2: e.g., Team expertise}
- {Driver 3: e.g., Cost constraints}

## Considered Options
{List 2-5 options considered, with brief description:}

### Option 1: {Name}
{Description}

### Option 2: {Name}
{Description}

### Option 3: {Name}
{Description}

## Decision Outcome
{State the chosen option and why.}

**Chosen option**: {Option Name}

{Explain why this option was chosen, referencing decision drivers.}

## Consequences

### Positive Consequences
- {Benefit 1}
- {Benefit 2}

### Negative Consequences
- {Drawback 1}
- {Drawback 2}

### Neutral Consequences
- {Neutral impact 1}

## Validation
{How will we validate this decision? Metrics, tests, reviews?}

## Pros and Cons of the Options

### Option 1: {Name}
- ✅ {Pro 1}
- ✅ {Pro 2}
- ❌ {Con 1}
- ❌ {Con 2}

### Option 2: {Name}
- ✅ {Pro 1}
- ✅ {Pro 2}
- ❌ {Con 1}
- ❌ {Con 2}

## Links
- Related ADRs: ADR-{NNN}, ADR-{NNN}
- Related SDDs: SDD-{NNN}
- References: {URLs, books, articles}
```

### Minimal ADR Template (For Simple Decisions)

```markdown
# ADR-{NNN}: {Title}

- **Status**: Accepted
- **Date**: {YYYY-MM-DD}

## Context
{1-2 sentences on the problem}

## Decision
{What we decided}

## Consequences
- {Positive}
- {Negative}
```

## ADR Lifecycle

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Proposed │────►│ Accepted │────►│ Superseded│
└──────────┘     └──────────┘     └────┬─────┘
     │              │                     │
     │              ▼                     ▼
     │         ┌──────────┐         ┌──────────┐
     └────────►│ Rejected │         │ Deprecated│
               └──────────┘         └──────────┘

Status Definitions:
  - Proposed: Under discussion, not yet decided
  - Accepted: Decision made and approved
  - Rejected: Decision was proposed but not accepted
  - Superseded: Replaced by a newer ADR (link to new ADR)
  - Deprecated: Decision is no longer recommended, but not yet replaced
```

## ADR Examples

### Example 1: Database Choice

```markdown
# ADR-001: Use MongoDB for Message Storage

- **Status**: Accepted
- **Date**: 2024-01-15
- **Deciders**: Tech Lead, Architect

## Context and Problem Statement
CBOL Messaging Hub needs to store chat messages. Messages have variable structure
(text, image, file, system messages), high write throughput, and are queried by
recipient and time range. Traditional relational databases may not be optimal for
this workload.

## Decision Drivers
- High write throughput (10K+ msg/s)
- Flexible schema (different message types)
- Query pattern: by recipient + time range (read diffusion)
- Horizontal scalability
- Team familiarity

## Considered Options

### Option 1: MySQL (Relational)
- Mature, ACID, team familiar
- Fixed schema, harder to handle variable message types
- Scaling writes requires sharding

### Option 2: MongoDB (Document)
- Flexible document schema, ideal for variable message types
- Native sharding, horizontal scalability
- Index on (recipientId, timestamp) for read diffusion
- Less mature than MySQL, eventual consistency

### Option 3: Cassandra (Wide-Column)
- Excellent write throughput, linear scalability
- Query-by-design model, less flexible
- Steeper learning curve, less team familiarity

## Decision Outcome
**Chosen option**: MongoDB

MongoDB's flexible document model fits variable message types, native sharding
supports horizontal scalability, and compound index on (recipientId, timestamp)
enables efficient read diffusion queries. Team has some MongoDB experience.

## Consequences

### Positive Consequences
- Flexible schema for different message types
- Horizontal scalability via sharding
- Efficient read diffusion with compound indexes
- Good write performance

### Negative Consequences
- Eventual consistency (not ACID)
- Less mature tooling than MySQL
- Need to learn MongoDB best practices
- Transactions are limited (multi-document only)

### Neutral Consequences
- Need MongoDB-specific monitoring and backup strategy

## Validation
- Load test: verify 10K+ msg/s write throughput
- Monitor query latency for read diffusion pattern
- Review after 3 months in production

## Links
- Related SDD: SDD-001 (Message Storage Design)
- Reference: Turms IM (uses MongoDB for message storage)
```

### Example 2: Technology Choice (State Machine)

```markdown
# ADR-002: Build Custom Lightweight State Machine Instead of Using Existing Library

- **Status**: Accepted
- **Date**: 2024-02-01
- **Deciders**: Tech Lead

## Context and Problem Statement
CBOL conversation flow requires a state machine (INIT → AI_PROCESSING →
TRANSFERRING → AGENT_CONNECTED → CLOSED, etc.). We need to decide whether to
use an existing library (Spring StateMachine, COLA StateMachine, squirrel-foundation)
or build a custom lightweight implementation.

## Decision Drivers
- Minimal dependencies (avoid heavy frameworks)
- Performance (state transitions on hot path)
- Simplicity (easy to understand and debug)
- Type safety
- No external dependencies required

## Considered Options

### Option 1: Spring StateMachine
- Full-featured, integrated with Spring
- Heavy, complex API, many abstractions
- Overkill for our simple state machine needs

### Option 2: COLA StateMachine
- Lightweight, fluent API, table-driven
- External dependency, but small
- Good design philosophy

### Option 3: Custom Lightweight State Machine
- Full control, zero dependencies
- Can optimize for our specific use case
- Need to build and maintain ourselves
- Risk of reinventing the wheel

## Decision Outcome
**Chosen option**: Custom Lightweight State Machine

We will build a custom stateless, table-driven state machine inspired by COLA
StateMachine's design philosophy (but no code import). The state machine will
be generic, type-safe, zero-dependency, and store only transition rules (current
state injected by business layer). This gives us full control with minimal complexity.

## Consequences

### Positive Consequences
- Zero external dependencies
- Full control over API and behavior
- Can optimize for performance (ConcurrentHashMap O(1) lookup)
- Simple, easy to understand and debug
- Type-safe with generics

### Negative Consequences
- Need to build and maintain ourselves
- Risk of bugs (less battle-tested than libraries)
- No community support or documentation
- May need to add features over time

### Neutral Consequences
- Need to write comprehensive unit tests
- Need to document the API and usage patterns

## Validation
- Unit tests: 100% coverage of state machine engine
- Performance test: verify O(1) transition lookup
- Code review by 2+ team members
- Compare with COLA StateMachine API for consistency

## Links
- Supersedes: None (first decision on this topic)
- Related: 01-CBOL-Domain-Knowledge/state-machine/
- Reference: COLA StateMachine (https://github.com/alibaba/COLA)
```

### Example 3: Reversing a Previous Decision

```markdown
# ADR-003: Use Redis Cluster Instead of Redis Sentinel

- **Status**: Accepted
- **Date**: 2024-03-01
- **Deciders**: Tech Lead, SRE
- **Supersedes**: ADR-001 (Use Redis Sentinel)

## Context and Problem Statement
ADR-001 decided to use Redis Sentinel for high availability. After 6 months in
production, we've hit scalability limits. Sentinel provides HA but not horizontal
scaling. Our cache and session storage needs have grown beyond single-master capacity.

## Decision Drivers
- Need horizontal scalability (more memory, more throughput)
- Current single-master Redis is at 80% memory
- Need to support 10x growth in next year
- Minimal application code changes

## Considered Options

### Option 1: Continue with Sentinel + Vertical Scaling
- Upgrade to larger instance (more RAM)
- No code changes
- But: hardware limit, expensive, can't scale infinitely

### Option 2: Migrate to Redis Cluster
- Horizontal scaling across multiple nodes
- Supports up to 1000 nodes
- But: requires client-side cluster support, key hash tags for multi-key ops
- Migration effort needed

### Option 3: Use Twemproxy (Nutcracker)
- Proxy-based sharding
- But: project is less active, single point of failure (proxy)

## Decision Outcome
**Chosen option**: Redis Cluster

Redis Cluster provides native horizontal scalability, high availability, and is
supported by our client library (Lettuce). We'll use hash tags for multi-key
operations. Migration will be done with dual-write and gradual cutover.

**This ADR supersedes ADR-001.**

## Consequences

### Positive Consequences
- Horizontal scalability (add nodes as needed)
- Higher availability (no single point of failure)
- Better performance (distributed load)
- Future-proof for growth

### Negative Consequences
- Migration effort (dual-write, data migration, cutover)
- Need to use hash tags for multi-key operations
- Some Redis commands not supported in cluster mode
- More complex operations and monitoring

### Neutral Consequences
- Need to update application configuration
- Need cluster-specific monitoring dashboards

## Validation
- Load test: verify cluster handles 10x current load
- Migration test: verify no data loss during cutover
- Monitor: cluster health, slot distribution, latency
- Rollback plan: revert to Sentinel if issues

## Links
- Supersedes: ADR-001 (Use Redis Sentinel)
- Related: 04-Coding-Guidelines/04-data-layer/redis-cache-guidelines.md
- Reference: Redis Cluster Specification (https://redis.io/docs/reference/cluster-spec/)
```

## ADR Management

### ADR Storage

```
Location: docs/adr/ (or 03-Design-Guidelines/06-design-process/adr/)

Naming: ADR-{NNN}-{short-title}.md
  - NNN: zero-padded sequential number (001, 002, ...)
  - short-title: kebab-case description

Example:
  docs/adr/ADR-001-use-mongodb-for-message-storage.md
  docs/adr/ADR-002-custom-lightweight-state-machine.md
  docs/adr/ADR-003-redis-cluster-instead-of-sentinel.md

Index: docs/adr/README.md with table of all ADRs
```

### ADR Index Template

```markdown
# Architecture Decision Records

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [ADR-001](./ADR-001-use-mongodb-for-message-storage.md) | Use MongoDB for Message Storage | ✅ Accepted | 2024-01-15 |
| [ADR-002](./ADR-002-custom-lightweight-state-machine.md) | Custom Lightweight State Machine | ✅ Accepted | 2024-02-01 |
| [ADR-003](./ADR-003-redis-cluster-instead-of-sentinel.md) | Redis Cluster Instead of Sentinel | ✅ Accepted (Supersedes ADR-001) | 2024-03-01 |

## Status Legend
- ✅ Accepted: Decision is in effect
- 🟡 Proposed: Under discussion
- ❌ Rejected: Not accepted
- ⚠️ Deprecated: No longer recommended
- 🔄 Superseded: Replaced by newer ADR
```

### ADR Process

```
1. Identify need for ADR
   - When making significant architectural decision
   - When reversing a previous decision

2. Draft ADR
   - Use standard template
   - Include context, options, decision, consequences
   - Be concise (1-2 pages for most decisions)

3. Review ADR
   - Share with tech lead / architect
   - Discuss in design review meeting (if significant)
   - Get feedback and revise

4. Accept ADR
   - Tech lead / architect approves
   - Update status to "Accepted"
   - Commit to repository

5. Follow ADR
   - Implementation follows the decision
   - If decision needs to change, create new ADR (don't edit accepted ADR)

6. Maintain ADR
   - Keep index up to date
   - Mark superseded/deprecated ADRs
   - Review ADRs periodically (are they still valid?)
```

## ADR Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| **ADR after the fact** | Decision already made, ADR is just documentation | Create ADR before implementation, as part of design |
| **Too many ADRs** | ADR for trivial decisions, noise | Only create ADRs for significant architectural decisions |
| **Too few ADRs** | Important decisions not documented | Create ADRs for technology choices, architecture patterns, cross-cutting concerns |
| **Editing accepted ADRs** | History lost, can't see why decision changed | Create new ADR to supersede, don't edit accepted ones |
| **No consequences section** | Only says what, not why or trade-offs | Always include positive, negative, and neutral consequences |
| **No alternatives** | Only one option considered, no comparison | Always list 2-3 alternatives with pros and cons |
| **ADR without owner** | No one responsible for following up | Assign deciders and consulted parties |
| **Ignoring ADRs** | Decisions documented but not followed | Reference ADRs in SDDs, code reviews, and onboarding |
| **ADR too long** | 10+ pages, no one reads it | Keep concise (1-2 pages), focus on key decision |
| **ADR too vague** | "Use best practices" without specifics | Be specific: what technology, what pattern, what version |
| **No status tracking** | Can't tell if ADR is current | Use status field, mark superseded/deprecated |
| **No index** | Can't find existing ADRs, duplicate decisions | Maintain index table with all ADRs |

## References

- MADR (Markdown Any Decision Records): https://adr.github.io/madr/
- ADR GitHub Organization: https://adr.github.io/
- Documenting Architecture Decisions (Michael Nygard): https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
- AWS ADR Examples: https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/welcome.html
- Google ADR Guide: https://google.github.io/eng-practices/
- The Pragmatic Programmer (Architecture section): https://pragprog.com/titles/tpp20/the-pragmatic-programmer-20th-anniversary-edition/
- Architecture Decision Records in Practice: https://www.thoughtworks.com/radar/techniques/lightweight-architecture-decision-records
