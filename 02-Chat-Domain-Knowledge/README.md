# Chat Domain Knowledge

> Generic instant messaging domain knowledge, design patterns, and Java implementation reference.

## Reference Projects

| Project | GitHub | ⭐ Stars | Language | License | Architecture | Key Patterns |
|---------|--------|---------|----------|---------|-------------|--------------|
| **Mattermost** | [mattermost/mattermost](https://github.com/mattermost/mattermost) | ~28k | Go + React | MIT/AGPL | Modular monolith -> microservices | Layered architecture, plugin system |
| **Rocket.Chat** | [RocketChat/Rocket.Chat](https://github.com/RocketChat/Rocket.Chat) | ~45.9k | TypeScript (Node.js) + MongoDB | MIT | Monolith + microservices (NATS) | Oplog tailing, DDP protocol |
| **Matrix/Synapse** | [matrix-org/synapse](https://github.com/matrix-org/synapse) → [element-hq/synapse](https://github.com/element-hq/synapse) | ~12k | Python | Apache-2.0 | Federated homeservers | Event DAG, state resolution |
| **Turms** | [turms-im/turms](https://github.com/turms-im/turms) | ~1.9k | Java | Apache-2.0 | Modern microservices | Fanout read, push-pull models, 100K~10M concurrent |
| **Tiledesk/Chat21** | [chat21](https://github.com/chat21) / [Tiledesk](https://github.com/Tiledesk) | ~0.5k (multi-repo) | Node.js + Firebase/MQTT | MIT | MQTT + RabbitMQ | Broker-based routing |
| **OpenChat** | [open-chat-labs/open-chat](https://github.com/open-chat-labs/open-chat) | ~0.2k | Rust + TypeScript | AGPL-3.0 | Internet Computer (blockchain) | Decentralized, canister-based |

> Star counts approximate as of 2026-08. Click project name for GitHub source.
>
> 📖 **详细参考**（源码目录结构、架构特点、参考优先级）见 [../05-References/open-source-projects.md](../05-References/open-source-projects.md)

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
