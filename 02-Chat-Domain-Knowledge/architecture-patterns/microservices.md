# Microservices Architecture

## Service Decomposition

### Core IM Services

```
┌──────────────────────────────────────────────────────┐
│                    API Gateway                        │
├──────────┬──────────┬──────────┬──────────┬──────────┤
│  User    │Conversation│ Message  │  Group   │  Push   │
│ Service  │ Service   │ Service  │ Service  │ Service  │
├──────────┴──────────┴──────────┴──────────┴──────────┤
│              Message Bus (NATS / Kafka)               │
├──────────────────────────────────────────────────────┤
│   MySQL    │   Redis   │  MongoDB  │  Elasticsearch  │
└──────────────────────────────────────────────────────┘
```

### Service Responsibilities

| Service | Responsibility | Data |
|---------|---------------|------|
| User Service | Registration, profile, auth, relationships | User DB |
| Conversation Service | Conversation CRUD, membership | Conversation DB |
| Message Service | Send, receive, store, sync | Message DB |
| Group Service | Group management, roles, settings | Group DB |
| Push Service | Offline notifications (APNs/FCM) | Push tokens |
| Presence Service | Online/offline status | Redis |
| Gateway Service | WebSocket connections, routing | Session registry |

## Inter-Service Communication

### Synchronous (REST / gRPC)
- User profile lookup
- Conversation membership check
- Used when immediate response needed

### Asynchronous (Message Bus)
- Message delivery events
- Presence updates
- Group state changes
- Push notification triggers

**Message Bus Options:**
| Technology | Use Case |
|-----------|----------|
| NATS | Lightweight, high-throughput pub/sub (Rocket.Chat) |
| Kafka | Durable event streaming, log replay |
| RabbitMQ | Complex routing, dead-letter queues (Tiledesk) |
| Redis Pub/Sub | Simple, low-latency, in-memory |

## Rocket.Chat Microservices (Reference)

Rocket.Chat's microservices architecture:
- **NATS**: Message bus for inter-service communication
- **stream-hub**: Real-time event streaming
- **Authorization service**: Stateless, horizontally scalable
- **MongoDB oplog tailing**: "Modified Kafka'ish optimized MongoDB oplog tailing" for real-time updates across servers

## Service Discovery
- Kubernetes DNS (if K8s)
- Consul / etcd
- Client-side load balancing

## Data Consistency in Microservices

### Challenge
Each service owns its database. Cross-service transactions need coordination.

### Patterns
1. **Saga pattern**: Orchestrated or choreographed sequence of local transactions
2. **Eventual consistency**: Accept short windows of inconsistency
3. **CQRS**: Separate read and write models

## When to Use Microservices vs Monolith

| Factor | Monolith | Microservices |
|--------|----------|---------------|
| Team size | < 10 devs | > 20 devs |
| Scale | Small-medium | Large (100K+ concurrent) |
| Complexity | Low-medium | High |
| Deployment | Simple | Complex (K8s needed) |
| Independent scaling | No | Yes |

**Recommendation**: Start with modular monolith, extract services when bottlenecks appear.
