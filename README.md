# CBOL Refactor - Project Knowledge Base

> Project knowledge base for CBOL Refactor, organized by knowledge domains. Designed to support Java code generation.

## Directory Navigation

| Directory | Description | Owner |
|-----------|-------------|-------|
| [00-Project-Overview](./00-Project-Overview/) | Project background, goals, scope | Project team |
| [01-CBOL-Domain-Knowledge](./01-CBOL-Domain-Knowledge/) | CBOL-specific: domain model, modules, DB schema, APIs, config, deployment | **To be filled by project team** |
| [02-Chat-Domain-Knowledge](./02-Chat-Domain-Knowledge/) | Generic IM knowledge + Java implementation reference | Pre-filled |
| [03-Design-Guidelines](./03-Design-Guidelines/) | Design guidelines & restrictions (HSBC standards) | To be filled |
| [04-Coding-Guidelines](./04-Coding-Guidelines/) | Coding guidelines & restrictions (Sonar rules) | To be filled |
| [05-References](./05-References/) | External references & resources | To be filled |
| [06-Skills](./06-Skills/) | Automation skills for knowledge collection & generation | Pre-filled |

## Knowledge Domains

### 1. CBOL Domain Knowledge (Project-Specific)
> Extracted from CBOL's existing codebase. Templates provided, fill with actual implementation.
- **Domain Model**: CBOL-specific entities and relationships
- **Module Structure**: Maven/Gradle module decomposition
- **Database Schema**: Tables, indexes, sharding strategy
- **API Definitions**: YAML/OpenAPI interface specs
- **UML Diagrams**: Class, sequence, component diagrams
- **Configuration**: application.yml, environment configs
- **Deployment Architecture**: Topology, capacity planning, CI/CD
- **Related Systems**: Upstream/downstream integrations

### 2. Chat Domain Knowledge (Generic + Java Reference)
> Collected from open-source IM projects (Mattermost, Rocket.Chat, Matrix, Turms, etc.)

**Domain & Design:**
- Domain Model: User, Conversation, Message, Group, Device/Session
- Message Design: msg_id + seq_id dual-ID, message types, state machine
- Sync Mechanism: Multi-device sync, cursor design, push-pull models
- Storage Design: Message storage, offline messages, timeline (write/read fanout)

**Architecture & Patterns:**
- Architecture Patterns: Layered, microservices, event-driven, federation
- Design Patterns: Gateway, message routing, fanout, presence management
- Reliability: Delivery guarantees, idempotency, retry & backoff

**Java Implementation (for code generation):**
- Java Implementation: Tech stack, project structure, key class design
- Concurrency: Thread pools, session registry, distributed locking, async patterns
- Networking: Netty WebSocket server, channel pipeline, handlers
- Serialization: Protobuf/JSON formats, schema evolution
- Data Structures: Java POJOs, enums, Redis keys, DDL
- Code Templates: Reusable boilerplate

### 3. Design Guidelines & Restrictions
- HSBC internal coding requirements

### 4. Coding Guidelines & Restrictions
- Sonar quality rules & standards

### 5. Skills (Automation)
> Reusable skills for collecting, organizing, and generating knowledge.
- **CBOL Knowledge Collector**: Extract domain knowledge from existing codebase
- **Chat Pattern Collector**: Collect patterns from open-source IM projects
- **Code Analyzer**: Analyze code structure, dependencies, quality
- **Doc Generator**: Generate API docs, UML diagrams, reports

## How to Use for Code Generation

1. **Reference**: Use `02-Chat-Domain-Knowledge` for generic IM patterns and Java implementation templates
2. **Adapt**: Fill `01-CBOL-Domain-Knowledge` with CBOL's actual implementation details
3. **Generate**: Combine domain model + data structures + networking patterns to generate Java code
4. **Guidelines**: Apply `03` and `04` standards to generated code

## Maintenance Guidelines
- All documents use Markdown format
- CBOL-specific docs should reference actual code modules and APIs
- Generic chat docs include references to source open-source projects
- Java implementation docs include working code snippets
