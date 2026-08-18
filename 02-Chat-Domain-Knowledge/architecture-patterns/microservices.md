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

### Turms: 极简架构哲学 (Anti-Over-Engineering)

Turms 的架构设计最鲜明的特点是**明确反对过度设计**，其核心原则：

> "不要为了简历好看而过度设计。不盲目拆服务，不引入消息队列。"

**Turms 的实际架构只有两层：**
```
┌──────────────┐     RPC      ┌──────────────┐
│   Gateway    │◄────────────►│   Service    │
│ (连接管理)    │              │ (业务逻辑)    │
└──────────────┘              └──────────────┘
       │                            │
       ▼                            ▼
   MongoDB/Redis              MongoDB/Redis
```

**Turms 明确不做的事：**
1. **不引入消息队列**（Kafka/RabbitMQ）：Gateway 和 Service 之间用自定义二进制 RPC，延迟更低
2. **不盲目拆微服务**：核心业务在一个 Service 进程中，按包划分模块
3. **不用服务网格**：直接 TCP 连接，简单高效
4. **不做分布式事务**：消息不可变，最终一致性即可

**什么时候才拆服务？**
- Turms 只有在明确的性能瓶颈出现时才考虑拆分
- Admin 服务是独立的（管理后台，与业务流量隔离）
- 插件系统是独立进程（稳定性隔离）

**对 CBOL 的启示：**
- CBOL 项目初期应采用**模块化单体**，按领域分包（接回话、回话管理、回话转发）
- 不要一开始就拆微服务，等有明确的性能或团队边界需求时再拆
- Gateway 和 Business 可以是一个进程的不同层，也可以是两个进程，取决于规模

### Rocket.Chat: 渐进式微服务演进

Rocket.Chat 展示了从单体到微服务的**渐进式演进路径**：

**阶段一：Meteor 单体**
- 所有功能在一个 Meteor 进程中
- DDP + MongoDB OpLog 实现实时
- 适合小团队快速迭代

**阶段二：单体 + 外部服务**
- 核心仍在 Meteor 进程
- 逐步将无状态服务拆出：
  - Authorization（授权）
  - Account（账户）
  - Presence（在线状态）
  - DDPStreamer（DDP 连接管理）
- 服务间通过 NATS 通信

**阶段三：全微服务（目标）**
- 所有服务独立部署
- StreamHub 统一实时数据分发
- NATS 作为服务总线

**关键设计：内部服务 vs 外部服务**

| 类型 | 运行位置 | 例子 | 扩展性 |
|------|---------|------|--------|
| 内部服务 | Meteor 进程内 | Messaging, Room, Push, Upload, Settings | 随单体扩展 |
| 外部服务 | 独立进程 | Authorization, Account, Presence, DDPStreamer | 可独立水平扩展 |

**StreamHub 的角色：**
- 捕获 MongoDB OpLog 变更
- 广播实时数据给其他服务
- 当前是单实例（架构瓶颈），未来计划支持水平扩展

**对 CBOL 的启示：**
- 微服务改造不需要一次性完成，可以逐个服务拆分
- 优先拆无状态、CPU 密集或独立扩展需求的服务
- 保留一个"核心单体"处理状态密集的业务，降低复杂度
- NATS 是轻量级服务总线的好选择（比 Kafka 简单，比 Redis Pub/Sub 可靠）

### Mattermost: 传输无关的分层架构

Mattermost 的架构亮点是**业务逻辑与传输机制完全解耦**：

```
┌─────────────────────────────────────────┐
│  api4 (REST)     wsapi (WebSocket)       │  传输层
├─────────────────────────────────────────┤
│              app (业务逻辑)               │  与传输无关
├─────────────────────────────────────────┤
│           store (数据访问)                │
└─────────────────────────────────────────┘
```

- `app` 层不依赖 HTTP 或 WebSocket，可以被任意传输层调用
- 新增传输协议（如 gRPC）只需加一层适配器，不改业务逻辑
- 插件通过 RPC 调用 app 层，与传输层无关

### Matrix/Synapse: Worker + Replication 架构

Synapse 的扩展模式是**主进程 + Worker**，类似数据库的主从复制：

```
          ┌─────────────┐
          │  主进程       │  数据库写入管理
          └──────┬──────┘
                 │ Replication Stream (Redis pub/sub)
    ┌────────────┼────────────┐
    ▼            ▼            ▼
┌────────┐  ┌────────┐  ┌────────┐
│Worker 1│  │Worker 2│  │Worker 3│
│(API)   │  │(联邦)   │  │(媒体)   │
└────────┘  └────────┘  └────────┘
```

- 主进程负责数据库写入，Worker 负责读请求和特定功能
- Worker 间通过 Redis pub/sub 同步 replication stream
- Worker 类型：generic_worker（API）、federation_sender（联邦）、media_repository（媒体）
- 类似的模式可用于 CBOL：主进程写，多个 worker 处理不同类型的请求

### 架构选择决策树

```
项目初期？
├── 是 → 模块化单体（Mattermost 风格分层）
└── 否 → 有明确性能瓶颈？
    ├── 是 → 渐进式拆分（Rocket.Chat 风格）
    │       ├── 优先拆无状态服务
    │       └── 保留核心单体
    └── 否 → 维持现状，不要为了拆而拆

需要极致性能(10万+连接)？
├── 是 → Turms 风格：极简两层 + 全异步无锁
└── 否 → 常规分层架构即可

需要跨组织/跨服务器通信？
├── 是 → 联邦架构（Matrix 风格）
└── 否 → 中心化架构
```
