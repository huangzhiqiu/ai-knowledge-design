# Tiledesk/Chat21 Deep Architecture Analysis

> Source: [Tiledesk](https://github.com/Tiledesk) / [Chat21](https://github.com/chat21) ⭐ ~0.5k (multi-repo) | MIT | Node.js + Angular
> Official docs: https://developer.tiledesk.com
> Positioning: Full-stack open-source real-time chat + AI customer service platform, with built-in chatbot

---

## 1. Project Overview

Tiledesk is an open-source omnichannel real-time chat platform with built-in AI chatbot, positioned as an open-source alternative to Intercom/Zendesk/Tawk. Its messaging engine core is **Chat21** — a lightweight real-time messaging system evolved from Firebase to RabbitMQ+MQTT.

### Multi-Repo Architecture

```
Tiledesk ecosystem
├── Tiledesk Server          # Core server (Node.js + Express)
├── Tiledesk Dashboard       # Admin backend (Angular)
├── Tiledesk Deployment      # Helm + K8s / Docker Compose
├── Tiledesk Android/iOS     # Mobile apps
└── Chat21 (messaging engine)
    ├── chat21-server        # RabbitMQ observer (message relay)
    ├── chat21-http-server   # RabbitMQ REST API
    ├── chat21-cloud-functions # Firebase cloud functions (legacy engine)
    ├── chat21-web-widget    # Website chat widget (Angular)
    ├── chat21-ionic         # Agent console (Ionic + Angular)
    └── SDKs (Node/Android/iOS)
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Node.js + Express |
| Frontend | Angular (Dashboard + Widget) |
| Database | MongoDB |
| Real-time messaging | RabbitMQ + MQTT (new engine) / Firebase (legacy) |
| Cache/sync | Redis |
| Vector storage | Qdrant (AI knowledge base) |
| LLM | vLLM / Ollama (open-source models self-hosted) |
| Deployment | Docker Compose / Kubernetes (Helm) |

### Dual Engine Support

| Engine | Transport | Characteristics |
|--------|-----------|----------------|
| **RabbitMQ + MQTT** (recommended) | MQTT over WebSocket/TCP | Self-hosted, inbox pattern, fine-grained JWT security |
| **Firebase** (legacy) | Firebase Realtime DB + WebSocket | Managed, cloud functions, depends on Google Cloud |

---

## 2. Chat21 Messaging Engine Core Design

### 2.1 Inbox Pattern (Core Innovation)

Chat21's core design is the **Inbox pattern**, similar to email's SMTP/POP3:

```
Sender client                    Chat21 Server                  Recipient client
    │                              (Observer)                      │
    │  MQTT publish                 │                              │
    │  to /outgoing path            │                              │
    ├──────────────────────────────►│                              │
    │                              │  AMQP publish                 │
    │                              │  to /clientadded path         │
    │                              ├─────────────────────────────►│
    │                              │                              │  MQTT subscribe
    │                              │                              │  to own inbox
```

**Key path design**:

| Direction | MQTT Topic Path |
|-----------|----------------|
| Sender writes | `/apps/{appId}/users/{senderId}/{recipientId}/messages/outgoing` |
| Recipient receives | `/apps/{appId}/users/{recipientId}/{senderId}/messages/clientadded` |

### 2.2 Why Inbox Pattern?

| Advantage | Description |
|-----------|-------------|
| **Privacy/security** | Messages relayed through observer, can enforce policies (blocking, filtering, auditing) |
| **Persistence** | Observer can persist messages before forwarding |
| **Fine-grained permissions** | RabbitMQ JWT Token restricts users to read/write only their own paths |
| **Decoupling** | Sender doesn't need to know recipient's connection status |
| **Offline messages** | Messages written to recipient inbox, auto-delivered when online |

> **Analogy**: Just like email — you send mail to your own SMTP server (outgoing), server forwards to recipient's mail server (inbox), recipient retrieves via POP3.

### 2.3 Chat21 Server (RabbitMQ Observer)

- A simple **RabbitMQ message observer**
- Subscribes to all users' `/outgoing` paths
- On receiving message, forwards via AMQP publish to corresponding recipient's `/clientadded` path
- Simultaneously triggers webhook to notify Tiledesk Server
- Lightweight, stateless, horizontally scalable

### 2.4 Security Mechanism (RabbitMQ JWT Token)

- Each user gets a JWT Token
- Token restricts user to read/write only on **own paths**
- Users cannot directly read/write other users' inboxes
- Observer is the only component that can cross-path forward

```
User A's Token permissions:
  ✓ Read: /apps/tilechat/users/A/# (all own paths)
  ✓ Write: /apps/tilechat/users/A/# (all own paths)
  ✗ Read: /apps/tilechat/users/B/# (other's paths)
  ✗ Write: /apps/tilechat/users/B/# (other's paths)
```

---

## 3. Overall Architecture

### 3.1 Component Interaction

```
┌──────────────┐     Webhook (HTTP POST)      ┌──────────────────┐
│              │◄─────────────────────────────│                  │
│ Tiledesk     │                              │ Chat21 Server    │
│ Server       │                              │ (RabbitMQ        │
│ (business    │─────────────────────────────►│ Observer)        │
│  logic)      │     REST API (chat21-http)    │                  │
└──────┬───────┘                              └────────┬─────────┘
       │                                               │
       │ MongoDB                                       │ AMQP
       ▼                                               ▼
┌──────────────┐                              ┌──────────────────┐
│  MongoDB     │                              │  RabbitMQ        │
│  (business   │                              │  (message queue  │
│   data)      │                              │   + MQTT)        │
└──────────────┘                              └────────┬─────────┘
                                                       │ MQTT
                                          ┌────────────┼────────────┐
                                          ▼            ▼            ▼
                                    ┌─────────┐  ┌─────────┐  ┌─────────┐
                                    │ Web     │  │ Mobile  │  │ Agent   │
                                    │ Widget  │  │ App     │  │ Console │
                                    └─────────┘  └─────────┘  └─────────┘
```

### 3.2 Chat21 & Tiledesk Communication

- **Chat21 → Tiledesk**: via webhook, Chat21 events (new message, new member joins group, etc.) sent via HTTP POST to Tiledesk's webhook endpoint
- **Tiledesk → Chat21**: via Chat21 HTTP Server's REST API to send messages, create groups, etc.

### 3.3 Tiledesk Server Responsibilities

| Module | Responsibility |
|--------|---------------|
| Project management | Multi-tenant/project isolation |
| User management | Visitors, agents, admins |
| Department/routing | Conversation assignment, routing rules |
| Chatbot | Built-in AI bot (Rasa/native/external LLM) |
| Omnichannel | Web Widget, WhatsApp, Facebook, Telegram, Email |
| Analytics | Conversation stats, satisfaction, response time |
| CRM | Contact management, tags |
| Webhook/API | Third-party integration |

---

## 4. Message Flow Details

### 4.1 Send Message (Direct)

```
1. Client A connects to RabbitMQ via MQTT
2. A publishes message to /apps/tilechat/users/A/B/messages/outgoing
   Payload: { text: "hello", sender: "A", recipient: "B", ... }
3. Chat21 Server (observer) receives outgoing message
4. Observer persists message (optional)
5. Observer publishes via AMQP to /apps/tilechat/users/B/A/messages/clientadded
6. Observer triggers webhook -> Tiledesk Server (record business data, trigger bot, etc.)
7. Client B subscribed to own inbox path, receives MQTT push
8. B decodes message (path suffix clientadded indicates new message arrived)
```

### 4.2 Group Messages

- Group messages similar, but observer needs to forward to all group members
- Each member has own group inbox path
- Supports info message notifications for group creation, member join/leave

### 4.3 Offline Messages

- Messages written to RabbitMQ queue (persistent)
- Recipient receives history via MQTT subscription after coming online
- Can also pull history via Chat21 HTTP Server's REST API

---

## 5. Firebase Legacy Engine (Comparison Reference)

### 5.1 Architecture

```
Client ←WebSocket→ Firebase Realtime DB ←trigger→ Firebase Cloud Functions
```

- Client directly connects to Firebase Realtime DB
- Cloud Functions handle business logic (send message, create conversation, push notifications)
- Depends on Google Cloud Platform

### 5.2 Why Migrate to RabbitMQ+MQTT?

| Dimension | Firebase | RabbitMQ+MQTT |
|-----------|----------|---------------|
| Hosting | Depends on Google Cloud | Fully self-hosted |
| Cost | Billed by read/write volume | Self-built server cost controllable |
| Privacy | Data on Google Cloud | Data fully self-controlled |
| Flexibility | Limited by Firebase features | Customizable observer logic |
| Performance | Managed scaling | Can optimize for scenario |

---

## 6. Omnichannel

Tiledesk's core feature is multi-channel unification:

| Channel | Integration Method |
|---------|-------------------|
| Web Widget | Native Chat21 |
| WhatsApp | WhatsApp Business API |
| Facebook Messenger | Facebook Graph API |
| Telegram | Telegram Bot API |
| Email | IMAP/SMTP |
| Custom | Webhook + REST API |

- All channel messages unified into Tiledesk interface
- Agents can reply to all channels in one interface
- Chatbot can auto-reply across all channels

---

## 7. AI Integration

### 7.1 Built-in AI Capabilities

| Capability | Technology |
|-----------|-----------|
| Chatbot | Native bot / Rasa / external LLM |
| Knowledge base Q&A | Qdrant vector storage + RAG |
| LLM inference | vLLM / Ollama (open-source models self-hosted) |
| Intent recognition | Built-in NLU |

### 7.2 AI Architecture

```
User message -> Tiledesk Server -> Bot engine
                              ├── Rule matching (keyword/regex)
                              ├── Rasa NLU (intent recognition)
                              ├── LLM (vLLM/Ollama)
                              └── Knowledge base RAG (Qdrant vector search)
```

---

## 8. Performance Benchmark

Chat21 Server official benchmark:

| Metric | Direct Message | Group Message |
|--------|---------------|---------------|
| Average latency | **13.85ms** | **45.61ms** |
| Target latency | < 160ms | < 160ms |
| Throughput | 60 msg/s | 60 msg/s |
| Concurrent users | 1 VU | 1 VU |
| Test duration | 10s | 10s |

> Benchmark config is low (single concurrency), actual production can improve throughput by horizontally scaling observers.

---

## 9. Design Principles & Trade-offs

| Design Decision | Choice | Trade-off |
|----------------|--------|-----------|
| **Inbox pattern** | Observer relay rather than P2P | Adds one hop latency, but gains security/policy/persistence capabilities |
| **MQTT protocol** | Lightweight IoT protocol | Small bandwidth, suitable for mobile, but not as universal as WebSocket |
| **RabbitMQ-centric** | Message queue as message bus | Reliable persistence, but RabbitMQ becomes critical dependency |
| **Multi-repo** | Each component independent repo | Flexible decoupling, but complex deployment and version management |
| **Dual engine** | Support both Firebase and RabbitMQ | Smooth migration, but maintain two codebases |
| **Node.js** | Non-blocking I/O | Suitable for I/O-intensive chat scenarios, but CPU-intensive tasks limited |

---

## 10. Reference Value for CBOL Project

### 10.1 Messaging Architecture Level

| Chat21 Design | CBOL Can Learn |
|--------------|---------------|
| **Inbox pattern** | Message relay layer can enforce policies (conversation handoff/forwarding/filtering/auditing) |
| **MQTT path design** | Topic-based message routing, naturally supports pub/sub |
| **RabbitMQ JWT fine-grained security** | User-level path permission control |
| **Observer stateless scaling** | Message relay layer horizontally scalable |

### 10.2 Business Level

| Tiledesk Design | CBOL Can Learn |
|----------------|---------------|
| Omnichannel unification | Multi-channel access for conversation handoff scenarios (Web/App/third-party) |
| Conversation routing/assignment | Routing rules for conversation forwarding, human transfer |
| Department management | Customer service team organizational structure |
| Webhook event notification | Message event-driven external systems |

### 10.3 Technology Selection Level

| Tiledesk Design | CBOL Can Learn |
|----------------|---------------|
| RabbitMQ + MQTT combination | Lightweight real-time messaging solution (alternative to heavy Netty self-development) |
| Redis cache sync | Fast access to session state, presence |
| MongoDB flexible storage | Document-based storage for chat messages |
| Docker Compose one-click deployment | Quick setup for dev/test environments |

> **Note**: Chat21's MQTT+RabbitMQ solution suits small-to-medium scale and rapid prototyping. If CBOL project targets high concurrency (100K+ connections), Turms' Netty self-developed solution is more appropriate. Consider choosing different tech stacks for different scenarios.

---

## 11. References

- Tiledesk GitHub: https://github.com/Tiledesk
- Chat21 GitHub: https://github.com/chat21
- Architecture components: https://developer.tiledesk.com/architecture/components
- Chat21 Server (npm): https://www.npmjs.com/package/@chat21/chat21-server
- Migration from Firebase to MQTT/RabbitMQ: https://tiledesk.com/2021/02/12/tiledesk-new-messaging-engine-moving-from-firebase-to-mqtt-rabbitmq
- Chat21 Cloud Functions: https://github.com/chat21/chat21-cloud-functions
- Tiledesk REST API: https://developer.tiledesk.com/apis/rest-api

---

*Analysis date: 2026-08-18*
