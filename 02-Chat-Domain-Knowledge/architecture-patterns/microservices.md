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

---

## Open Source Project Architecture Patterns

### Turms: Minimalist Architecture Philosophy (Anti-Over-Engineering)

The most distinctive feature of Turms' architecture design is its **explicit opposition to over-engineering**, with core principles:

> "Don't over-engineer for your resume. Don't blindly split services, don't introduce message queues."

**Turms' actual architecture has only two layers:**
```
┌──────────────┐     RPC      ┌──────────────┐
│   Gateway    │◄────────────►│   Service    │
│ (connection  │              │ (business    │
│  management) │              │  logic)      │
└──────────────┘              └──────────────┘
       │                            │
       ▼                            ▼
   MongoDB/Redis              MongoDB/Redis
```

**What Turms explicitly does NOT do:**
1. **No message queue** (Kafka/RabbitMQ): Gateway and Service use custom binary RPC, lower latency
2. **No blind microservice splitting**: Core business in one Service process, divided by packages
3. **No service mesh**: Direct TCP connections, simple and efficient
4. **No distributed transactions**: Messages are immutable, eventual consistency is sufficient

**When to split services?**
- Turms only considers splitting when clear performance bottlenecks appear
- Admin service is independent (management backend, isolated from business traffic)
- Plugin system is separate process (stability isolation)

**Implications for CBOL:**
- CBOL project should start with a **modular monolith**, divided by domain (conversation handoff, conversation management, conversation forwarding)
- Don't split microservices from the start; wait for clear performance or team boundary requirements
- Gateway and Business can be different layers in one process, or two processes, depending on scale

### Rocket.Chat: Gradual Microservices Evolution

Rocket.Chat demonstrates a **gradual evolution path** from monolith to microservices:

**Phase 1: Meteor Monolith**
- All functionality in one Meteor process
- DDP + MongoDB OpLog for real-time
- Suitable for small teams, rapid iteration

**Phase 2: Monolith + External Services**
- Core still in Meteor process
- Gradually split out stateless services:
  - Authorization
  - Account
  - Presence
  - DDPStreamer (DDP connection management)
- Services communicate via NATS

**Phase 3: Full Microservices (Target)**
- All services independently deployed
- StreamHub unifies real-time data distribution
- NATS as service bus

**Key design: Internal vs External Services**

| Type | Runtime | Examples | Scalability |
|------|---------|----------|-------------|
| Internal service | Inside Meteor process | Messaging, Room, Push, Upload, Settings | Scales with monolith |
| External service | Independent process | Authorization, Account, Presence, DDPStreamer | Independently horizontally scalable |

**StreamHub's role:**
- Captures MongoDB OpLog changes
- Broadcasts real-time data to other services
- Currently single-instance (architecture bottleneck), future plans for horizontal scaling

**Implications for CBOL:**
- Microservices refactoring doesn't need to be done all at once; split services one by one
- Prioritize splitting stateless, CPU-intensive, or independently scalable services
- Keep a "core monolith" for state-intensive business, reducing complexity
- NATS is a good choice for lightweight service bus (simpler than Kafka, more reliable than Redis Pub/Sub)

### Mattermost: Transport-Agnostic Layered Architecture

Mattermost's architecture highlight is **complete decoupling of business logic from transport mechanism**:

```
┌─────────────────────────────────────────┐
│  api4 (REST)     wsapi (WebSocket)       │  Transport layer
├─────────────────────────────────────────┤
│              app (business logic)        │  Transport-agnostic
├─────────────────────────────────────────┤
│           store (data access)            │
└─────────────────────────────────────────┘
```

- `app` layer does not depend on HTTP or WebSocket, can be called by any transport layer
- Adding a new transport protocol (e.g., gRPC) only requires adding an adapter, no business logic changes
- Plugins call app layer via RPC, independent of transport layer

### Matrix/Synapse: Worker + Replication Architecture

Synapse's scaling model is **main process + Workers**, similar to database master-slave replication:

```
          ┌─────────────┐
          │  Main Process│  Database write management
          └──────┬──────┘
                 │ Replication Stream (Redis pub/sub)
    ┌────────────┼────────────┐
    ▼            ▼            ▼
┌────────┐  ┌────────┐  ┌────────┐
│Worker 1│  │Worker 2│  │Worker 3│
│(API)   │  │(federation)│  │(media)│
└────────┘  └────────┘  └────────┘
```

- Main process handles database writes, Workers handle read requests and specific functions
- Workers sync replication stream via Redis pub/sub
- Worker types: generic_worker (API), federation_sender (federation), media_repository (media)
- Similar pattern can be used for CBOL: main process writes, multiple workers handle different request types

### Architecture Selection Decision Tree

```
Early stage project?
├── Yes → Modular monolith (Mattermost-style layering)
└── No → Clear performance bottleneck?
    ├── Yes → Gradual splitting (Rocket.Chat-style)
    │       ├── Prioritize stateless services
    │       └── Keep core monolith
    └── No → Maintain status quo, don't split just for the sake of it

Need extreme performance (100K+ connections)?
├── Yes → Turms style: minimalist two-layer + fully async lock-free
└── No → Conventional layered architecture is fine

Need cross-organization/cross-server communication?
├── Yes → Federation architecture (Matrix style)
└── No → Centralized architecture
```
