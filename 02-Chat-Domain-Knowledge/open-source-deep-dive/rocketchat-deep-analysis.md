# Rocket.Chat Deep Architecture Analysis

> Source: [RocketChat/Rocket.Chat](https://github.com/RocketChat/Rocket.Chat) ⭐ ~45.9k | MIT | TypeScript (Node.js/Meteor) + MongoDB
> Official docs: https://developer.rocket.chat
> Positioning: Secure CommsOS™, secure communication platform for mission-critical operations

---

## 1. Project Overview

Rocket.Chat is an open-core enterprise real-time communication platform, evolved from a Meteor monolith, currently transitioning to a microservices architecture. Its characteristics are **security-first, Omnichannel, Matrix federation, and extensible Apps Engine**.

### Monorepo Structure

```
Rocket.Chat/
├── apps/
│   └── meteor/              # Core server (Meteor app, 97 feature modules)
│       ├── app/             # Feature-based modules (each with client/ + server/ + lib/)
│       ├── server/          # General server logic
│       └── client/          # General client logic
├── packages/                # 55 shared packages
│   ├── apps-engine/         # Apps Engine (application extension framework)
│   ├── model-typings/       # Model type definitions
│   ├── rest-typings/        # REST API types
│   └── ...
├── ee/                      # Enterprise features
└── e2e-tests/               # End-to-end tests
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Node.js + Meteor (TypeScript) |
| Frontend | React |
| Database | MongoDB (production requires replica set, 8.x targets MongoDB 8.0) |
| Real-time communication | DDP over WebSocket + MongoDB OpLog |
| Microservice bus | NATS |
| File storage | Local / S3 / WebDAV |
| Deployment | Docker / Kubernetes / Podman |
| Federation | Matrix protocol |

### Deployment Modes

| Mode | Use Case | Characteristics |
|------|----------|----------------|
| **Monolith** | Small teams | Single process, all features integrated |
| **Multi-node monolith** | Medium scale | Multiple monolith nodes collaborating |
| **Microservices** (Enterprise) | Large scale/high availability | Services independently deployed, NATS communication |

---

## 2. Architecture Design

### 2.1 Overall Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      Clients (Web/Desktop/Mobile)              │
│                   (REST API + WebSocket/DDP)                   │
└──────────────────────────────┬───────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Reverse Proxy      │  (NGINX/HAProxy, load balancing, TLS)
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
   │  DDPStreamer │      │  DDPStreamer │      │  DDPStreamer │
   │  (horizontally scalable) │  (horizontally scalable) │  (horizontally scalable) │
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │ NATS (message bus)
          ┌────────────────────┼────────────────────┐
          │          │         │         │          │
   ┌──────▼──┐ ┌────▼───┐ ┌───▼────┐ ┌──▼─────┐ ┌──▼───────┐
   │Account  │ │Auth    │ │Presence│ │StreamHub│ │AppsEngine│
   │(stateless)│ │(stateless)│ │(stateless)│ │(single instance)│ │(in development)│
   └──────┬──┘ └────┬───┘ └───┬────┘ └──┬─────┘ └──┬───────┘
          │         │         │         │          │
          └─────────┴─────────┼─────────┴──────────┘
                              │
                     ┌────────▼────────┐
                     │   MongoDB        │  (replica set, OpLog tailing)
                     │   (core data)    │
                     └─────────────────┘
```

### 2.2 Evolution from Monolith to Microservices

Rocket.Chat is transitioning from Meteor monolith to microservices architecture:

| Phase | Architecture | Characteristics |
|-------|-------------|----------------|
| **Traditional** | Meteor monolith | All features in one process, DDP + OpLog real-time updates |
| **Transition** | Monolith + external services | Core still in Meteor, some services split out (Presence, Authorization, etc.) |
| **Target** | Full microservices | All services independently deployed, NATS communication, StreamHub unified real-time data distribution |

### 2.3 Core Design Philosophy

- **MongoDB-centric**: All data stored in MongoDB, reactive updates via OpLog
- **DDP protocol**: Meteor's distributed data protocol, client subscribes to data collections, server pushes changes
- **Gradual microservices**: No one-time refactoring, gradually split services out of Meteor process
- **Security-first**: For defense, intelligence, critical infrastructure scenarios

---

## 3. Microservices Architecture Details

### 3.1 External Services (Independent Process, Horizontally Scalable)

| Service | Responsibility | Status | Scalability |
|---------|---------------|--------|-------------|
| **Authorization** | User authorization and permission management | Stable | Stateless, horizontally scalable |
| **Account** | User account management (create/update/delete/login/logout) | Stable | Stateless, horizontally scalable |
| **Presence** | User presence tracking and updates | Stable | Stateless, horizontally scalable |
| **StreamHub** | Capture database changes, broadcast real-time data to other services | Stable | **Single instance, no horizontal scaling** |
| **DDPStreamer** | Manage DDP connections, client-server interaction, subscriptions, data transfer | Stable | Horizontally scalable |
| **AppsEngine** | Rocket.Chat Apps management (install/update/execute/remove) | In development | Designed to support horizontal scaling |

### 3.2 Internal Services (Inside Meteor Process)

| Service | Responsibility |
|---------|---------------|
| Messaging | Message management |
| Room | Chat room management (create/update/delete) |
| Team | Team management |
| OmniChannel | Omnichannel customer service |
| Omnichannel Voip | VoIP voice calls |
| Push | Mobile push notifications |
| Upload | File upload management |
| Settings | System settings management |
| Banner | Banner management |
| LDAP | LDAP integration |
| NPS | User satisfaction survey |
| UiKitCoreApp | UI Kit interaction handling |

### 3.3 NATS Message Bus

- Microservices communicate via **NATS**
- Services point to NATS dispatcher rather than directly to each component
- NATS decides which service instance to forward requests to
- Supports service discovery and load balancing

---

## 4. Real-time Communication Design

### 4.1 DDP Protocol

Rocket.Chat uses **DDP (Distributed Data Protocol)** as client-server real-time communication protocol:

| Feature | Description |
|---------|-------------|
| Transport layer | WebSocket |
| Mode | Client subscribes to collections -> Server pushes changes |
| Operations | subscribe / unsubscribe / method call |
| Data sync | Server maintains client's subscribed data view, incremental push |

**DDP message flow**:
```
Client -> Server: subscribe("stream-room-messages", roomId)
Server -> Client: added / changed / removed (incremental data)
Client -> Server: method("sendMessage", message)
Server -> Client: result (method call result)
```

### 4.2 MongoDB OpLog Reactive Layer

Rocket.Chat's real-time update core mechanism is **MongoDB OpLog tailing**:

```
Data write to MongoDB -> OpLog records change -> StreamHub captures -> broadcast to subscribers -> DDPStreamer pushes to clients
```

- All service instances listen to MongoDB OpLog
- Data changes automatically pushed to subscribed clients
- No polling needed, low latency

### 4.3 StreamHub's Role

StreamHub is the core of real-time data distribution:
- Captures database changes
- Broadcasts to other services and clients
- **Currently single instance**, is the bottleneck of microservices architecture
- May support horizontal scaling in future

### 4.4 REST API + WebSocket Dual Track

- **REST API**: Non-real-time operations (create users, manage channels, etc.)
- **WebSocket/DDP**: Real-time messages, state updates, typing indicators
- All functionality exposed via REST API and WebSocket, easy third-party integration

---

## 5. Apps Engine (Application Extension Framework)

### 5.1 Architecture

Apps Engine is Rocket.Chat's plugin system, adopting **three-layer architecture**:

```
┌─────────────────────────────────────────┐
│  App Code (running in sandbox)           │
│  ┌───────────────────────────────────┐  │
│  │  Definition Layer (interface def)  │  │
│  │  IRead / IModify / IHttp / ...    │  │
│  └───────────────┬───────────────────┘  │
│                  │                       │
│  ┌───────────────▼───────────────────┐  │
│  │  Server Layer (concrete impl)      │  │
│  │  Reader / Modifier / ...          │  │
│  └───────────────┬───────────────────┘  │
│                  │                       │
│  ┌───────────────▼───────────────────┐  │
│  │  Bridge Layer (connect to core)    │  │
│  │  Interact with Rocket.Chat core    │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### 5.2 Sandbox Environment

- Create isolated context based on **Node.js VM module**
- App code runs in sandbox, cannot directly access core system
- Interact with core via well-defined API interfaces (IRead, IModify, IHttp, etc.)
- Security isolation, prevent malicious Apps from affecting system stability

### 5.3 App Capabilities

| Capability | Description |
|-----------|-------------|
| Slash commands | Register custom /command |
| Message processing | Intercept and modify messages |
| API endpoints | Register custom HTTP endpoints |
| Scheduled tasks | Register cron tasks |
| UI Kit | Build interactive message components |
| External HTTP | Call external APIs |
| Event listening | Listen to message send, user join, etc. events |

### 5.4 Official App Examples

- WhatsApp App (omnichannel integration)
- Google Calendar App
- Matrix Bridge (federation bridge)
- Jira / GitLab and other third-party integrations

---

## 6. Data Model & Storage

### 6.1 MongoDB Design

- **Production must use MongoDB replica set** (required for OpLog tailing)
- Rocket.Chat 8.x targets MongoDB 8.0
- Data model is document-centric, suitable for flexible structure in chat scenarios

### 6.2 Core Collections

| Collection | Description |
|------------|-------------|
| users | User accounts and credentials |
| rooms | Chat rooms (channels/DMs/discussion groups) |
| messages | Messages (core data) |
| subscriptions | User-room subscription relationships |
| roles | Roles and permissions |
| settings | System settings |
| integration_history | Integration history |
| apps | Installed Apps |
| omnichannel related | Omnichannel customer service data |

### 6.3 Model Abstraction

- `@rocket.chat/model-typings` package defines TypeScript interfaces (IUsersModel, IRoomsModel, etc.)
- Models provide CRUD operation abstraction
- Supports different storage backend implementations

### 6.4 File Storage

| Storage Method | Description |
|---------------|-------------|
| Local filesystem | Default, simple deployment |
| Amazon S3 | Cloud storage, suitable for large scale |
| WebDAV | Network storage protocol |

---

## 7. Omnichannel

Rocket.Chat's differentiating feature is **Omnichannel customer service**:

| Channel | Description |
|---------|-------------|
| Web Widget | Website embedded chat component |
| WhatsApp | WhatsApp Business API integration |
| Facebook Messenger | Facebook message integration |
| Instagram | Instagram direct messages |
| Telegram | Telegram Bot |
| Email | Email to ticket |
| SMS | SMS integration |
| VoIP | Voice calls |

- All channel messages unified into Rocket.Chat interface
- Human agent + chatbot hybrid
- Conversation routing, assignment, statistics

---

## 8. Security & Federation

### 8.1 Security Features

| Feature | Description |
|---------|-------------|
| E2EE | End-to-end encryption (some scenarios) |
| SSO | SAML, OAuth, LDAP/AD |
| RBAC | Fine-grained role permissions |
| Audit | Operation audit logs |
| Compliance | Data retention policies, compliance exports |
| Air-gapped deployment | Supports isolated network environments |

### 8.2 Matrix Federation

- Supports federated communication with other platforms via **Matrix protocol**
- Rocket.Chat can act as Matrix homeserver or bridge
- Cross-platform message interoperability

---

## 9. Design Principles & Trade-offs

| Design Decision | Choice | Trade-off |
|----------------|--------|-----------|
| **Meteor start** | Full-stack framework, rapid development | Deep coupling with Meteor, difficult microservice split |
| **MongoDB OpLog real-time** | Database-level change tracking | Depends on MongoDB replica set, StreamHub single-instance bottleneck |
| **DDP protocol** | Meteor standard real-time protocol | Not universal standard, high learning curve |
| **Gradual microservices** | Split gradually, no one-time refactoring | Transition phase architecture complex, monolith+microservices hybrid |
| **Apps Engine VM sandbox** | Node.js VM isolation | Performance not as good as native, but good security isolation |
| **MongoDB-centric** | All data in MongoDB | Non-relational, complex queries limited |

---

## 10. Reference Value for CBOL Project

### 10.1 Architecture Level

| Rocket.Chat Design | CBOL Can Learn |
|-------------------|---------------|
| Gradual microservices evolution | Split from monolith gradually, don't pursue one-step completion |
| NATS microservice bus | Inter-service communication and service discovery |
| StreamHub real-time distribution | Unified real-time event distribution layer |
| Stateless service design | Authorization/Account/Presence all horizontally scalable |

### 10.2 Real-time Communication Level

| Rocket.Chat Design | CBOL Can Learn |
|-------------------|---------------|
| DDP subscription-push model | Client subscribes to data collections, server incremental push |
| MongoDB OpLog tailing | Database change-driven real-time updates (e.g., MySQL binlog) |
| REST + WebSocket dual track | Non-real-time uses REST, real-time uses WebSocket |
| Typing indicators/presence | Presence service independently deployed |

### 10.3 Extensibility Level

| Rocket.Chat Design | CBOL Can Learn |
|-------------------|---------------|
| Apps Engine three-layer architecture | Definition->Server->Bridge plugin design pattern |
| VM sandbox isolation | Third-party code secure execution |
| Event hook mechanism | Message send/user join etc. events extensible |
| UI Kit interactive components | Rich interactive message format |

### 10.4 Business Level

| Rocket.Chat Design | CBOL Can Learn |
|-------------------|---------------|
| Omnichannel | Multi-channel unification for conversation handoff/forwarding scenarios |
| Human + bot hybrid | AI processing + human transfer workflow design |
| Matrix federation | Cross-system message interoperability |

---

## 11. References

- GitHub: https://github.com/RocketChat/Rocket.Chat
- Developer docs: https://developer.rocket.chat
- Architecture and components: https://developer.rocket.chat/docs/architecture-and-components
- Server architecture: https://developer.rocket.chat/docs/server-architecture
- Microservices deployment: https://docs.rocket.chat/deploy/scaling/microservices
- Apps Engine: https://developer.rocket.chat/apps-engine
- DDP protocol: https://github.com/meteor/meteor/blob/devel/packages/ddp/DDP.md

---

*Analysis date: 2026-08-18*
