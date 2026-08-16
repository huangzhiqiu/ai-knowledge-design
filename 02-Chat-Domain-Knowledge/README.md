# Chat Domain Knowledge

> Generic instant messaging domain knowledge, design patterns, and Java implementation reference.

## Reference Projects

| Project | Language | Architecture | Key Patterns |
|---------|----------|-------------|--------------|
| Mattermost | Go + React | Modular monolith -> microservices | Layered architecture, plugin system |
| Rocket.Chat | Node.js + MongoDB | Monolith + microservices (NATS) | Oplog tailing, DDP protocol |
| Matrix/Synapse | Python | Federated homeservers | Event DAG, state resolution |
| Turms | Java | Modern microservices | Fanout read, push-pull models |
| Tiledesk/Chat21 | Node.js | MQTT + RabbitMQ | Broker-based routing |
| OpenChat | Go | WebSocket + Redis pub/sub | Horizontal connection scaling |

## Sub-directories

### Domain & Design
| Directory | Description |
|-----------|-------------|
| [domain-model/](./domain-model/) | Core entities: User, Conversation, Message, Group, Device/Session |
| [message-design/](./message-design/) | Message ID design, types, state machine |
| [sync-mechanism/](./sync-mechanism/) | Multi-device sync, cursors, push-pull models |
| [storage-design/](./storage-design/) | Message storage, offline messages, timeline model |

### Architecture & Patterns
| Directory | Description |
|-----------|-------------|
| [architecture-patterns/](./architecture-patterns/) | Layered, microservices, event-driven, federation |
| [design-patterns/](./design-patterns/) | Gateway, message routing, fanout, presence management |
| [reliability/](./reliability/) | Delivery guarantees, idempotency, retry/backoff |

### Java Implementation
| Directory | Description |
|-----------|-------------|
| [java-implementation/](./java-implementation/) | Tech stack, project structure, key class design |
| [concurrency/](./concurrency/) | Thread pools, session registry, locking, async patterns |
| [networking/](./networking/) | Netty WebSocket server, handlers, pipeline |
| [serialization/](./serialization/) | Protobuf/JSON formats, schema evolution |
| [data-structures/](./data-structures/) | Java POJOs, enums, Redis keys, DDL |
| [code-templates/](./code-templates/) | Reusable code templates & boilerplate |

## Domain Model Index

| Entity | Document |
|--------|----------|
| User | [user-model.md](./domain-model/user-model.md) |
| Conversation | [conversation-model.md](./domain-model/conversation-model.md) |
| Message | [message-model.md](./domain-model/message-model.md) |
| Group | [group-model.md](./domain-model/group-model.md) |
| Device & Session | [device-session-model.md](./domain-model/device-session-model.md) |

## Java Implementation Quick Reference

| Topic | Document |
|-------|----------|
| Tech stack & project structure | [java-implementation/README.md](./java-implementation/README.md) |
| Netty server & handlers | [networking/README.md](./networking/README.md) |
| Thread pools & concurrency | [concurrency/README.md](./concurrency/README.md) |
| Protobuf & JSON serialization | [serialization/README.md](./serialization/README.md) |
| Entity classes & DDL | [data-structures/README.md](./data-structures/README.md) |
