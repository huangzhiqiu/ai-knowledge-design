# Turms Deep Architecture Analysis

> Source: [turms-im/turms](https://github.com/turms-im/turms) ⭐ ~1.9k | Apache-2.0 | Java
> Official docs: https://turms-im.github.io/docs/
> Positioning: Open-source instant messaging engine for 100K~10M concurrent users

---

## 1. Project Overview

Turms is currently the most professional Java implementation in the open-source community for medium-to-large IM scenarios. Its architecture design is derived from commercial instant messaging systems, with **extreme performance** as the top priority, supporting complete (not feature-rich) IM functionality.

### Core Sub-projects

| Sub-project | Responsibility | Status |
|-------------|---------------|--------|
| **turms-gateway** | Client access gateway: protocol parsing, connection management, user auth, session management, message push, turms-service load balancing | Required |
| **turms-service** | IM business logic: message routing, user/group/relationship management, RBAC, cluster management | Required |
| **turms-admin** | Admin backend: business data management, cluster monitoring, operational reports | Optional |
| **turms-client-*** | Multi-platform SDKs: Java/JS/Kotlin/Swift/Dart | Optional |
| **turms-plugin** | Plugin framework: custom logic triggered by events like user online/offline, message send/receive | Optional |
| **turms-plugin-antispam** | Sensitive word filtering plugin (Aho-Corasick + double-array trie, O(n) detection) | Optional |
| **turms-plugin-minio** | MinIO object storage plugin | Optional |
| **turms-plugin-rasa** | Rasa chatbot plugin | Optional |

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java (Reactive, fully async) |
| Network | Netty (TCP + WebSocket) |
| Database | MongoDB sharded cluster |
| Cache | Redis (distributed memory) + local in-memory cache |
| Serialization | Protobuf (client) + custom binary encoding (inter-service RPC) |
| Monitoring | Prometheus + Grafana |
| Deployment | Docker / Docker Compose / Terraform |

---

## 2. Architecture Design

### 2.1 Overall Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Clients (TCP/WebSocket)                      │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   DNS / SLB / Nginx  │  (TCP load balancing, Sticky Session)
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
   │ turms-gateway│      │ turms-gateway│      │ turms-gateway│
   │  (stateless) │      │  (stateless) │      │  (stateless) │
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          │                    │                    │
          │    RPC (custom binary encoding, fully async) │
          └────────────────────┼────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
   │turms-service │      │turms-service │      │turms-service │
   │  (stateless) │      │  (stateless) │      │  (stateless) │
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          │                    │                    │
   ┌──────▼────────────────────▼────────────────────▼──────┐
   │              MongoDB sharded cluster (mongos)          │
   │         (read-write separation, hot/cold separation, time-based sharding) │
   └──────────────────────────┬─────────────────────────────┘
                              │
   ┌──────────────────────────▼─────────────────────────────┐
   │              Redis cluster (distributed memory)         │
   │         (user sessions, online status, gateway node info) │
   └────────────────────────────────────────────────────────┘
```

### 2.2 Design Philosophy: Minimalist Architecture

Turms' core architecture principle is **"don't split if you can avoid it, don't introduce external services if you can avoid it"**:

| Common IM Architecture Practice | Turms' Choice | Reason |
|--------------------------------|---------------|--------|
| Split session management, message cache, message push into 3 independent services | **Merge into turms-gateway** | Reduce failure points, avoid RPC overhead, business logic is not complex |
| Introduce Kafka/RocketMQ as message queue for async consumption | **No message queue** | Cloud auto-scaling is more suitable for traffic shaping; use business log analysis for statistics |
| Split network connection management and session logic into two services | **Don't split** | Gateway has almost no session business logic, splitting yields little benefit and adds failure points |
| Use Hazelcast/Ignite distributed Map instead of Redis | **Choose Redis** | Cluster high availability and deployment process design require external distributed memory |

> **Key insight**: Turms explicitly criticizes "over-engineering for your resume", pointing out that many IM projects introduce message queues and microservice splits at only tens of thousands of online users, which is unnecessary technical complexity.

### 2.3 Stateless & Multi-Active

- **Both turms-gateway and turms-service are stateless**, horizontally scalable
- User session info stored in **Redis + local cache**
- Supports **cross-data-center multi-active** deployment
- Supports **user-transparent updates** (rolling release)

---

## 3. Client Access Flow

### 3.1 Connection Establishment

```
1. Client DNS query → SLB/ELB (LVS/Nginx) → turms-gateway
   - TCP load balancing based on client IP
   - Strongly recommend enabling Sticky Session (mitigates DDoS)
   - SSL certificate offloaded at upstream SLB/Nginx

2. turms-gateway checks:
   - Is IP banned? → actively disconnect
   - Is service overloaded? → actively disconnect
   - Pass → establish TCP connection

3. Protocol selection:
   - TCP: directly send Protobuf data stream
   - WebSocket: HTTP Upgrade → binary frames carrying Protobuf
```

### 3.2 Login & Session Establishment

```
Client → turms-gateway (TurmsRequest: login)
  │
  ├─ Gateway parses user ID + device type → composes session ID
  ├─ Query Redis/local cache → check if session ID conflicts
  │   ├─ Conflict → reject login (CREATE_EXISTING_SESSION)
  │   └─ No conflict → register session in Redis → return success
  │
  └─ User enters online state
```

**Session ID design:**
- `session ID = user ID + device type`
- Different devices of the same user can connect to **different** gateways
- **But recommend all devices of same user connect to same gateway**, because:
  1. Push messages only need to be sent to one gateway, reducing resource overhead
  2. Multiple devices of same user share heartbeat clock, reducing Redis TTL refresh count
  3. Avoid new message delay caused by user status cache not being updated

### 3.3 Request Forwarding

```
Client request → turms-gateway
  │
  ├─ Can gateway handle it itself? (login/logout/heartbeat)
  │   └─ Yes → handle directly and return
  │
  └─ No → check if user is logged in
      ├─ Not logged in → reject
      └─ Logged in → select a turms-service by load balancing strategy
          → RPC forward (custom binary encoding)
          → turms-service processes → returns response
          → Notifications generated during processing → query Redis for target user's gateway node
          → RPC push to corresponding gateway → gateway forwards to client
```

---

## 4. Network Communication Design

### 4.1 Protocol Stack

| Communication Direction | Transport Protocol | Encoding |
|------------------------|-------------------|----------|
| Client ↔ turms-gateway | TCP or WebSocket | **Protobuf** |
| turms-gateway ↔ turms-service | TCP (Netty) | **Custom binary encoding** (no redundant data) |
| turms-service ↔ MongoDB | MongoDB driver | BSON |
| turms-service ↔ Redis | Redis protocol | RESP |

### 4.2 TCP Protocol Format

```
┌──────────────────┬──────────────────────┐
│  body-length     │  body (Protobuf)     │
│  (ZigZag encoded)│  (TurmsRequest or    │
│                  │   TurmsNotification) │
└──────────────────┴──────────────────────┘
```

- `body-length`: ZigZag-encoded variable-length integer, reduces byte usage for small numbers
- `body`: Protobuf-encoded `TurmsRequest` (client→server) or `TurmsNotification` (server→client)

### 4.3 WebSocket Protocol Format

- WebSocket binary frame payload directly carries Protobuf-encoded `TurmsRequest`/`TurmsNotification`
- Frame length handled by WebSocket protocol itself, no extra body-length header needed

### 4.4 Heartbeat Mechanism

| Protocol | Heartbeat Implementation |
|----------|-------------------------|
| TCP | Client sends single byte `[0]` as heartbeat |
| WebSocket | Standard WebSocket Ping/Pong frames |

- Default heartbeat interval **30 seconds**
- Three-layer protection: application-layer heartbeat + TCP Keepalive + gateway read idle detection
- After heartbeat timeout, gateway closes connection; after Redis session expires, user goes offline

### 4.5 Fully Async Reactive Model

> **All network IO operations (database calls, Redis calls, service discovery, RPC) are implemented as non-blocking IO based on Netty.**

- No synchronous blocking calls
- Fully utilize system resources
- Constant thread count (see Section 6)

---

## 5. Message Model & Storage Design

### 5.1 Read Fanout

Turms' core architecture decision is **read fanout** (rather than write fanout):

| Model | Write Operation | Read Operation | Use Case |
|-------|----------------|---------------|----------|
| **Write Fanout** | Write to each recipient's inbox when sending | Directly read own inbox | Small groups, few active users |
| **Read Fanout** ⭐ | Message stored once, indexed by recipient | Query messages from all relevant conversations on read | Large groups, medium-to-large scale |
| **Push-Pull Hybrid** | Push to online users + pull for offline | Pull offline messages after coming online | General IM |

Turms uses read fanout, messages stored and indexed by **recipient dimension**, avoiding write amplification caused by write fanout in large groups.

### 5.2 Message Collection Design

**Default index scheme (Scheme 1):**
- Compound index: `message sending time + recipient ID`
- Shard key: `message sending time`
- Purpose: support **hot/cold data separation**, data from different time periods sharded to different Shards

**Optional scheme (Scheme 2, use-conversation-id=true):**
- Compound index: `message sending time + session ID`
- Shard key: `message sending time`
- Private chat session ID: 16 bytes (sender ID + receiver ID sorted combination)
- Group chat session ID: 8 bytes (group ID)
- Applicable: need to support "sender queries messages they sent"

**Not recommended (Scheme 3):**
- Add optional index for sender ID on top of Scheme 1
- Problem: querying messages within a conversation requires two queries (other's sent + own sent), low efficiency

### 5.3 Hot/Cold Separation

- Message is the **only collection supporting hot/cold separation**
- Hot data (recent messages) → high-performance servers (16-core 128G)
- Cold data (historical messages) → low-cost servers (4-core 8G)
- Sharded by message sending time, naturally supports hot/cold separation

### 5.4 Index Design Principles

| Principle | Description |
|-----------|-------------|
| **Design based on distributed sharding characteristics** | Index primarily considers mongos routing efficiency, not single-table query performance |
| **More reads fewer writes** | Index optimized for reads, minimize write operations |
| **Prioritize key general requirements** | Don't add extra indexes for "rich features" |
| **No Hashed indexes** | MongoDB Hashed indexes don't support uniqueness constraints, automatically add extra B-tree index, not worth it |
| **Optional indexes disabled by default** | Extended feature indexes enabled via configuration, avoid small-project thinking |

### 5.5 Other Collection Design Points

| Collection | Compound ID / Shard Key | Description |
|------------|------------------------|-------------|
| GroupMember | `group ID + user ID` | Shard by group ID, fast query of group members; query groups by user ID requires scatter-gather (not supported by default) |
| FriendRequest | `recipient ID + creation time` | Shard by recipient ID, fast query of received requests; sender queries own sent requests requires optional index |
| GroupRequest | `recipient ID + creation time` | Same as above |
| User | user ID | Shard by user ID |
| Group | group ID | Shard by group ID |

---

## 6. Concurrency & Performance Design

### 6.1 Thread Model

> **Turms server's peak thread count is constant, independent of online user count and request count.**

| Component | Thread Count | Description |
|-----------|-------------|-------------|
| Access layer (Netty EventLoop) | = CPU cores | Fully utilize CPU cache, reduce context switching and thread contention |
| Business logic processing | Reuse access layer threads | Fully async, no blocking, no extra business thread pool needed |
| Others (monitoring, logging, etc.) | Small fixed number of threads | Does not grow with load |

### 6.2 Lock-Free Design

- Business logic processing has **almost no locks**, only **CAS operations**
- Lock-free design is the key to high throughput
- State stored in Redis/database, no shared mutable state maintained in memory

### 6.3 Memory Optimization

- **Smart allocation of on-heap/off-heap memory**, chosen based on usage scenario
- **Refactor MongoDB/Redis client dependencies**, eliminate redundant memory allocation
- **Fully utilize local memory cache**, reduce remote calls
- Direct Memory used for Netty IO, avoid on-heap copying

### 6.4 Performance Target

- Design target: **100K ~ 10M concurrent users**
- Single gateway node can carry large number of connections (Netty non-blocking IO)
- All operations async non-blocking, no thread blocking points

---

## 7. Security Design

| Mechanism | Description |
|-----------|-------------|
| **API rate limiting** | Gateway-layer rate limiting, prevent CC attacks |
| **Global user/IP blacklist** | Gateway auto-detects abnormal behavior and bans, ban data synced to other gateways in 10~15 seconds |
| **Sticky Session** | SLB enables sticky sessions, mitigates DDoS (after banning IP, attacker can't quickly switch gateway) |
| **SSL/TLS** | Certificate offloaded at upstream SLB/Nginx |
| **Sensitive word filtering** | turms-plugin-antispam (Aho-Corasick + double-array trie, O(n) time complexity) |

---

## 8. Observability

### Three Types of Logs

| Log Type | Purpose |
|----------|---------|
| Monitoring logs | System metrics, performance monitoring |
| Business logs | Business event records, for data analysis |
| Statistics logs | Operational report data |

### Monitoring Stack

- Prometheus + Grafana
- Business logs can be integrated with CloudWatch Logs → Kinesis Firehose → S3 → Athena/QuickSight for data analysis

---

## 9. Plugin System

Turms provides an event-driven plugin framework, core events include:

| Event | Trigger Location | Purpose |
|-------|-----------------|---------|
| User online/offline | turms-gateway | Custom online status processing |
| Message received | turms-gateway | Sensitive word filtering, message auditing |
| Message forwarded | turms-gateway | Offline push, message encryption |
| Client request processing | turms-service | Custom business logic |

Plugin implementation examples:
- `turms-plugin-antispam`: sensitive word filtering
- `turms-plugin-minio`: object storage
- `turms-plugin-rasa`: AI chatbot

---

## 10. Reference Value for CBOL Project

### 10.1 Architecture Level

| Turms Design | CBOL Can Learn |
|-------------|---------------|
| gateway + service two-layer split | Access layer separated from business layer, gateway focuses on connection management |
| Stateless services + Redis sessions | Horizontal scaling solution |
| Don't blindly introduce message queues | Evaluate whether Kafka/RocketMQ is really needed, avoid over-engineering |
| Read fanout message model | Storage design for large group message scenarios |

### 10.2 Network Communication Level

| Turms Design | CBOL Can Learn |
|-------------|---------------|
| Fully async Netty Reactive model | Avoid synchronous blocking, use CompletableFuture/Reactor |
| Protobuf client communication | High-performance serialization solution |
| Custom binary RPC encoding | Extreme optimization for inter-service communication (if needed) |
| TCP + WebSocket dual protocol support | Multi-platform access |
| Constant thread count = CPU cores | Thread pool configuration reference |
| Almost no locks, only CAS | Concurrency design approach |

### 10.3 Data Storage Level

| Turms Design | CBOL Can Learn |
|-------------|---------------|
| MongoDB sharding + hot/cold separation | Message storage solution (if using MongoDB) |
| Index design based on sharding characteristics | Database index design methodology |
| Optional indexes disabled by default | Avoid over-indexing |
| session ID = user ID + device type | Multi-device session management |

### 10.4 Design Philosophy Level

- **Performance first, complete functionality not feature-rich**: core trade-off for medium-to-large IM systems
- **Minimalist architecture, oppose over-engineering**: don't blindly split services, don't blindly introduce middleware
- **Key requirements determine architecture, secondary requirements validate architecture**: requirement priority drives design
- **Architecture is the art of trade-offs**: clearly state the trade-off of each design decision

---

## 11. References

- GitHub: https://github.com/turms-im/turms
- Official docs: https://turms-im.github.io/docs/
- Architecture design: https://turms-im.github.io/docs/design/architecture
- Schema design: https://turms-im.github.io/docs/design/schema
- Communication protocol: https://turms-im.github.io/docs/client/communication-protocol.html
- Playground: http://playground.turms.im (guest/guest)

---

*Analysis date: 2026-08-18*
