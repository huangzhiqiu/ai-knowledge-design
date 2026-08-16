# Architecture Patterns Overview

> Architecture patterns collected from excellent open-source IM projects.

## Sub-topics

| Document | Description |
|----------|-------------|
| [layered-architecture](./layered-architecture.md) | Access / Application / Data layering |
| [microservices](./microservices.md) | Service decomposition and message bus |
| [event-driven](./event-driven.md) | Event sourcing and CQRS patterns |
| [federation](./federation.md) | Cross-server federation (Matrix model) |

## Reference Projects

| Project | Architecture | Key Insight |
|---------|-------------|-------------|
| Mattermost | Modular monolith -> microservices | Layered: Access / App / Data |
| Rocket.Chat | Monolith + microservices (NATS) | MongoDB oplog tailing for real-time |
| Matrix/Synapse | Federated homeservers | Event DAG, state resolution |
| Turms | Modern microservices | Fanout read, push-pull models |
| Tiledesk/Chat21 | MQTT + RabbitMQ | Broker-based message routing |
