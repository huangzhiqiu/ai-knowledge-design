# Matrix/Synapse Deep Architecture Analysis

> Source: [element-hq/synapse](https://github.com/element-hq/synapse) ⭐ ~12k | Apache-2.0 | Python
> Protocol spec: https://spec.matrix.org
> Positioning: Reference implementation of decentralized federated real-time communication protocol

---

## 1. Project Overview

Matrix is an **open, decentralized, federated** real-time communication protocol, and Synapse is its official reference implementation (Python). Unlike traditional centralized chat systems, Matrix has no single control center — each homeserver stores its own users' data, and servers synchronize shared room history via federation protocol.

### Core Design Philosophy

| Principle | Description |
|-----------|-------------|
| **Decentralization** | No single point of control, federated model similar to email |
| **Eventual consistency** | Choose AP (availability + partition tolerance) in CAP theorem, sacrifice strong consistency |
| **Open standard** | Public protocol specification, multiple implementations interoperate |
| **End-to-end encryption** | Olm (1:1) + Megolm (group) double ratchet encryption |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Server language | Python (Twisted async framework) |
| Database | PostgreSQL (production) / SQLite (demo only) |
| Cache/messaging | Redis (worker pub/sub + shared cache) |
| Federation transport | HTTPS + JSON + digital signatures |
| Client transport | REST API + long polling /sync |
| Encryption | libolm (Olm + Megolm) |
| Deployment | Docker / bare metal / Kubernetes |

### Matrix Four Major APIs

| API | Purpose |
|-----|---------|
| **Client-Server API** | Client-homeserver communication (REST + /sync) |
| **Server-Server API (Federation)** | Homeserver-homeserver communication (PDU/EDU/Query) |
| **Application Service API** | Bridges and bots (IRC/Slack/WhatsApp/Teams) |
| **Identity Service API** | Third-party identifiers (email/phone) mapping to Matrix ID (optional) |

---

## 2. Federation Architecture Design

### 2.1 Overall Architecture

```
┌─────────────────────┐         ┌─────────────────────┐
│  Homeserver A        │         │  Homeserver B        │
│  (matrix.org)        │         │  (example.com)       │
│                      │         │                      │
│  ┌───────────────┐   │         │   ┌───────────────┐  │
│  │  Client API   │◄──┼─────────┼──►│  Client API   │  │
│  │  (/sync REST) │   │  HTTPS  │   │  (/sync REST) │  │
│  └───────┬───────┘   │  +JSON  │   └───────┬───────┘  │
│          │           │  +signature│          │          │
│  ┌───────▼───────┐   │         │   ┌───────▼───────┐  │
│  │  Event Graph  │   │  PDU/   │   │  Event Graph  │  │
│  │  (DAG replica)│◄──┼──EDU───►│   │  (DAG replica)│  │
│  └───────┬───────┘   │  Query  │   └───────┬───────┘  │
│          │           │         │           │          │
│  ┌───────▼───────┐   │         │   ┌───────▼───────┐  │
│  │  PostgreSQL   │   │         │   │  PostgreSQL   │  │
│  └───────────────┘   │         │   └───────────────┘  │
└─────────────────────┘         └─────────────────────┘
         │                                  │
    ┌────▼────┐                      ┌─────▼────┐
    │ Clients │                      │ Clients  │
    │(Element)│                      │(Element) │
    └─────────┘                      └──────────┘
```

### 2.2 Federation Communication Mechanism

Homeservers communicate via **Server-Server API**, based on HTTPS + JSON, using public key signature authentication.

#### Three Communication Types

| Type | Full Name | Characteristics | Purpose |
|------|-----------|----------------|---------|
| **PDU** | Persistent Data Units | Persisted, signed, broadcast to all servers in room | Messages, state events (room name/permissions/members) |
| **EDU** | Ephemeral Data Units | Not persisted, server-to-server push, no reply needed | Typing indicators, presence, read receipts |
| **Query** | Query | Request/response, not persisted | Get user profile, presence snapshot |

#### Transaction Wrapping

PDUs and EDUs are wrapped in **Transaction**, sent from source server to target server via HTTPS PUT:

```
Source server -> PUT /_matrix/federation/v1/send/{txnId}
           Body: { pdus: [...], edus: [...] }
Target server -> 200 OK { pdus: { eventId: result } }
```

- Transaction ID guarantees idempotency
- PDUs signed with source server private key, can be forwarded via third-party servers
- Exponential backoff retry on transmission failure
- Batch processing reduces network overhead

### 2.3 Message Send Flow (Federated Room)

```
1. Alice (@alice:matrix.org) sends message in room
2. matrix.org homeserver writes event to local Event Graph
3. matrix.org pushes PDU to other homeservers in room via Federation API
4. Each target homeserver verifies signature, writes to local Event Graph replica
5. Target homeserver pushes event to local clients via /sync
```

---

## 3. Event Model & DAG

### 3.1 Event

Event is Matrix's basic data unit, all messages and state changes are Events:

```json
{
  "event_id": "$abc123:matrix.org",
  "type": "m.room.message",
  "sender": "@alice:matrix.org",
  "origin_server_ts": 1234567890,
  "room_id": "!room:example.com",
  "content": { "body": "Hello", "msgtype": "m.text" },
  "prev_events": ["$prev1:matrix.org", "$prev2:example.com"],
  "signatures": { "matrix.org": { "ed25519:auto": "..." } }
}
```

### 3.2 Two Event Types

| Type | Description | Example |
|------|-------------|---------|
| **Message Event** | Transient message, doesn't affect room persistent state | m.room.message (chat message) |
| **State Event** | Updates room persistent state, has state_key | m.room.name (room name), m.room.member (member), m.room.power_levels (permissions) |

### 3.3 Event Graph (DAG)

Each room's history is modeled as a **directed acyclic graph (DAG)**:

- Each Event references `prev_events` (preceding events)
- Multiple servers sending events simultaneously creates branches
- **Forward Extremities**: DAG end events (no child events)
- New events must reference all current forward extremities

```
        Event A
       /        \
  Event B      Event C    (two servers send simultaneously, creates branch)
       \        /
        Event D           (references B and C, merges branch)
```

### 3.4 State Resolution

When DAG has multiple forward extremities, need to determine room's "true" current state:

| Version | Characteristics |
|---------|----------------|
| **v1** | Simple algorithm, vulnerable to attacks |
| **v2** | Handles Byzantine conditions, prevents malicious servers from rolling back history or excluding valid events |

**v2 state resolution core idea**:
- Conflicting state events verified for legitimacy via "authorization event chain"
- No single server can unilaterally determine room state
- After network partition recovery, all servers eventually converge to same state

---

## 4. Synapse Server Architecture

### 4.1 Evolution from Monolith to Worker

Synapse evolved from single-process Python app to **main process + multi-Worker** architecture:

| Phase | Architecture | Scale |
|-------|-------------|-------|
| Monolith | Single process handles all functionality | Small teams/personal |
| Worker | Main process + multiple dedicated workers | Medium-to-large scale |

### 4.2 Worker Types

| Worker | Responsibility | Description |
|--------|---------------|-------------|
| **Main process** | Database write management, coordination | Only process that can write to database (some scenarios) |
| **generic_worker** | Handle client REST API requests | Horizontally scalable, share read requests |
| **federation_sender** | Send federation traffic to other servers | Doesn't handle REST endpoints, dedicated to outbound federation |
| **media_repository** | Media file upload/download/thumbnail | Independently handle large files |
| **user_dir** | User directory search | Independent indexing and search |
| **frontend_proxy** | Frontend proxy | Load balancing entry |

### 4.3 Replication Protocol

Workers sync state via Synapse's dedicated **replication protocol** (similar to database replication):

```
Main process writes DB -> generates replication stream -> Redis pub/sub -> all workers receive -> update local cache
```

**Replication Streams** (data stream types):

| Stream | Content |
|--------|---------|
| EventsStream | New events |
| PresenceStream | Presence changes |
| TypingStream | Typing indicators |
| DeviceListsStream | Device list changes |
| ReceiptsStream | Read receipts |
| AccountDataStream | Account data |
| CachesStream | Cache invalidation notifications |

**Key components**:
- `ReplicationCommandHandler`: receives Redis commands, routes to each stream's BackgroundQueue
- `ReplicationDataHandler`: processes incoming stream rows, updates store, notifier, federation sender

### 4.4 Redis Role

- **Pub/Sub**: Broadcast replication stream to all workers
- **Shared cache**: Share cache data between workers
- All workers and main process connect to Redis

---

## 5. Client Sync Mechanism

### 5.1 /sync Long Polling

Clients get incremental updates via `GET /_matrix/client/v3/sync`:

| Parameter | Description |
|-----------|-------------|
| `since` | Last sync token (incremental starting point) |
| `timeout` | Long polling timeout (holds when no new events) |
| `filter` | Filter conditions (room/event type) |

**Flow**:
```
1. Client first /sync (no since) -> get full state + next_batch token
2. Client /sync?since=token -> server holds until new event or timeout
3. Server returns incremental update + new next_batch token
4. Repeat steps 2-3
```

### 5.2 Sync Response Structure

```json
{
  "next_batch": "s_abc_123",
  "rooms": {
    "join": {
      "!room:example.com": {
        "timeline": { "events": [...], "limited": false },
        "state": { "events": [...] },
        "ephemeral": { "events": [...] }
      }
    }
  },
  "presence": { "events": [...] }
}
```

---

## 6. End-to-End Encryption

### 6.1 Double Ratchet Encryption System

| Protocol | Purpose | Characteristics |
|----------|---------|----------------|
| **Olm** | 1:1 device encryption | Double Ratchet, forward secrecy + post-compromise security |
| **Megolm** | Group encryption | Single ratchet, each sender maintains own ratchet, keys distributed to group members |

### 6.2 Key Management

- Each device has identity keys (Curve25519 + Ed25519)
- Device verification: verify other device via emoji/SAS or QR code
- Key backup: can encrypt backup to homeserver, decrypt with recovery key or passphrase
- Cross-device signing: user signs own devices, establishes trust chain

---

## 7. Data Storage

### 7.1 PostgreSQL Design

- Production must use PostgreSQL (SQLite only for demo)
- Events stored in `events` table, content in JSONB format
- State events stored separately, support query by room+type+state_key
- Room DAG relationships stored in `event_edges` table

### 7.2 Core Tables

| Table | Description |
|-------|-------------|
| events | All events (messages + state) |
| event_edges | DAG edges (prev_events relationships) |
| room_memberships | Room membership state |
| users | User accounts |
| devices | User devices (for encryption) |
| e2e_room_keys | Encryption key backups |
| presence | Online status |
| receipts | Read receipts |

---

## 8. Design Principles & Trade-offs

| Design Decision | Choice | Trade-off |
|----------------|--------|-----------|
| **Federated decentralization** | No central server, data distributed storage | No single point of failure, but state resolution complex, higher latency |
| **AP priority** | Choose availability+partition tolerance in CAP | Eventual consistency, state resolution needed after partition recovery |
| **Event DAG** | Directed acyclic graph models history | Supports branching and merging, but high computation cost (large rooms CPU-intensive) |
| **Full replication** | Each homeserver stores complete room replica | Fast reads, but high storage and bandwidth overhead |
| **PDU signature** | Event signature, forwardable via third party | Secure and trustworthy, but signature verification has computation overhead |
| **Python/Twisted** | Async single-threaded | Fast development, but CPU-intensive tasks need worker scaling |
| **Long polling /sync** | Rather than WebSocket | Good compatibility, but high connection overhead (reconnect each timeout) |

---

## 9. Reference Value for CBOL Project

### 9.1 Architecture Level

| Matrix Design | CBOL Can Learn |
|--------------|---------------|
| Federation architecture (server peer-to-peer) | Cross-system/cross-organization message interoperability design |
| Event DAG models history | Message ordering and conflict resolution in distributed scenarios |
| Worker + Replication protocol | Server horizontal scaling solution (main process writes + workers read) |
| Redis pub/sub sync | Multi-process state synchronization mechanism |

### 9.2 Protocol Level

| Matrix Design | CBOL Can Learn |
|--------------|---------------|
| PDU/EDU/Query three-way classification | Protocol classification of persistent events vs ephemeral events vs queries |
| Transaction idempotent transport | Batch + idempotent reliable transport design |
| Event signature | Non-repudiation and tamper-proofing |
| /sync incremental sync | Token mechanism for client state sync |

### 9.3 Security Level

| Matrix Design | CBOL Can Learn |
|--------------|---------------|
| Olm/Megolm double ratchet | End-to-end encryption solution (if needed) |
| Device verification (emoji/SAS) | Trusted device establishment mechanism |
| Key backup | Cross-device key recovery |

### 9.4 State Resolution Level

| Matrix Design | CBOL Can Learn |
|--------------|---------------|
| v2 state resolution algorithm | Distributed conflict resolution (Byzantine fault tolerance) |
| Authorization event chain | Legitimacy verification of state changes |

> **Note**: Matrix's federation and DAG design is very complex. If CBOL project is centralized deployment, no need to fully replicate. What can be borrowed are ideas like **event modeling, incremental sync, worker scaling**, rather than full federation protocol.

---

## 10. References

- GitHub: https://github.com/element-hq/synapse
- Protocol spec: https://spec.matrix.org
- Server-Server API: https://spec.matrix.org/v1.11/server-server-api/
- Client-Server API: https://spec.matrix.org/v1.11/client-server-api/
- Synapse Workers: https://element-hq.github.io/synapse/latest/workers.html
- Room DAG concepts: https://github.com/matrix-org/synapse/blob/develop/docs/development/room-dag-concepts.md
- State resolution: https://deepwiki.com/matrix-org/synapse/5.3-state-resolution
- Replication system: https://deepwiki.com/element-hq/synapse/3.4-replication-system
- Matrix protocol explained: https://havenmessenger.com/blog/posts/matrix-protocol-explained/

---

*Analysis date: 2026-08-18*
