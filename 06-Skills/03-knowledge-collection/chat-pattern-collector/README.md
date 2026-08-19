# Chat Pattern Collector

> Skill to collect design patterns and code templates from excellent open-source IM projects.

## Purpose

Research and extract reusable patterns from open-source instant messaging projects:
- Architecture patterns
- Design patterns
- Code templates
- Best practices

## Reference Projects

| Project | Language | Focus Area |
|---------|----------|------------|
| Mattermost | Go | Layered architecture, plugin system |
| Rocket.Chat | Node.js | Real-time, microservices (NATS) |
| Matrix/Synapse | Python | Federation, event DAG |
| Turms | Java | High-concurrency, fanout |
| OpenChat | Go | WebSocket scaling |
| Tiledesk/Chat21 | Node.js | MQTT + RabbitMQ |

## Output Target

`02-Chat-Domain-Knowledge/`

## Collection Tasks

| Task | Output Directory |
|------|-----------------|
| Architecture patterns | `architecture-patterns/` |
| Design patterns | `design-patterns/` |
| Reliability patterns | `reliability/` |
| Java implementation reference | `java-implementation/` |
| Concurrency patterns | `concurrency/` |
| Networking patterns | `networking/` |
| Data structures | `data-structures/` |
| Code templates | `code-templates/` |

## Prompt Template

```
You are a chat system architecture researcher. Analyze the open-source project 
{project_name} and extract:

1. Overall architecture (layers, services, data flow)
2. Key design patterns (gateway, routing, fanout, etc.)
3. Message delivery and reliability mechanisms
4. Concurrency and connection management
5. Storage and sync strategies
6. Reusable code templates (Java preferred)

Document each pattern with:
- Problem statement
- Solution approach
- Implementation details (with code snippets)
- Trade-offs
- Reference to source project

Store in 02-Chat-Domain-Knowledge/ following the existing structure.
```

## Usage

1. Select a reference project
2. Run the collector with project URL/source
3. Extract patterns and code templates
4. Cross-reference with existing knowledge
5. Add to `02-Chat-Domain-Knowledge/`
