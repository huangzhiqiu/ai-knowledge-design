# Matrix/Synapse 深度架构分析

> 来源：[element-hq/synapse](https://github.com/element-hq/synapse) ⭐ ~12k | Apache-2.0 | Python
> 协议规范：https://spec.matrix.org
> 定位：去中心化联邦实时通信协议的参考实现

---

## 1. 项目概览

Matrix 是一个**开放、去中心化、联邦式**的实时通信协议，Synapse 是其官方参考实现（Python）。与传统中心化聊天系统不同，Matrix 没有单一控制中心——每个 homeserver 存储自己用户的数据，服务器间通过联邦协议同步共享房间的历史。

### 核心设计哲学

| 原则 | 说明 |
|------|------|
| **去中心化** | 无单点控制，类似电子邮件的联邦模型 |
| **最终一致性** | CAP 定理中选择 AP（可用性+分区容忍），牺牲强一致性 |
| **开放标准** | 协议公开规范，多实现互操作 |
| **端到端加密** | Olm（1:1）+ Megolm（群组）双棘轮加密 |

### 技术栈

| 层级 | 技术 |
|------|------|
| 服务端语言 | Python (Twisted 异步框架) |
| 数据库 | PostgreSQL（生产）/ SQLite（仅演示） |
| 缓存/消息 | Redis（worker 间 pub/sub + 共享缓存） |
| 联邦传输 | HTTPS + JSON + 数字签名 |
| 客户端传输 | REST API + 长轮询 /sync |
| 加密 | libolm (Olm + Megolm) |
| 部署 | Docker / 裸机 / Kubernetes |

### Matrix 四大 API

| API | 用途 |
|-----|------|
| **Client-Server API** | 客户端与 homeserver 通信（REST + /sync） |
| **Server-Server API (Federation)** | homeserver 间通信（PDU/EDU/Query） |
| **Application Service API** | 桥接和机器人（IRC/Slack/WhatsApp/Teams） |
| **Identity Service API** | 第三方标识（邮箱/手机号）映射到 Matrix ID（可选） |

---

## 2. 联邦架构设计

### 2.1 整体架构

```
┌─────────────────────┐         ┌─────────────────────┐
│  Homeserver A        │         │  Homeserver B        │
│  (matrix.org)        │         │  (example.com)       │
│                      │         │                      │
│  ┌───────────────┐   │         │   ┌───────────────┐  │
│  │  Client API   │◄──┼─────────┼──►│  Client API   │  │
│  │  (/sync REST) │   │  HTTPS  │   │  (/sync REST) │  │
│  └───────┬───────┘   │  +JSON  │   └───────┬───────┘  │
│          │           │  +签名   │           │          │
│  ┌───────▼───────┐   │         │   ┌───────▼───────┐  │
│  │  Event Graph  │   │  PDU/   │   │  Event Graph  │  │
│  │  (DAG 副本)   │◄──┼──EDU───►│   │  (DAG 副本)   │  │
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

### 2.2 联邦通信机制

Homeserver 间通过 **Server-Server API** 通信，基于 HTTPS + JSON，使用公钥签名认证。

#### 三种通信类型

| 类型 | 全称 | 特点 | 用途 |
|------|------|------|------|
| **PDU** | Persistent Data Units | 持久化、签名、广播给房间内所有服务器 | 消息、状态事件（房间名/权限/成员） |
| **EDU** | Ephemeral Data Units | 不持久化、服务器间推送、无需回复 | 打字指示器、在线状态、已读回执 |
| **Query** | 查询 | 请求/响应、不持久化 | 获取用户资料、在线状态快照 |

#### Transaction 包装

PDU 和 EDU 被包装在 **Transaction** 中，通过 HTTPS PUT 从源服务器发送到目标服务器：

```
源服务器 → PUT /_matrix/federation/v1/send/{txnId}
           Body: { pdus: [...], edus: [...] }
目标服务器 → 200 OK { pdus: { eventId: result } }
```

- 事务 ID 保证幂等
- PDU 使用源服务器私钥签名，可通过第三方服务器转发
- 传输失败时指数退避重试
- 批量处理减少网络开销

### 2.3 消息发送流程（联邦房间）

```
1. Alice (@alice:matrix.org) 在房间发送消息
2. matrix.org homeserver 将事件写入本地 Event Graph
3. matrix.org 通过 Federation API 将 PDU 推送给房间内其他 homeserver
4. 每个目标 homeserver 验证签名，写入本地 Event Graph 副本
5. 目标 homeserver 通过 /sync 将事件推送给本地客户端
```

---

## 3. 事件模型与 DAG

### 3.1 Event（事件）

Event 是 Matrix 的基本数据单位，所有消息和状态变更都是 Event：

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

### 3.2 两种 Event 类型

| 类型 | 说明 | 示例 |
|------|------|------|
| **Message Event** | 瞬时消息，不影响房间持久状态 | m.room.message（聊天消息） |
| **State Event** | 更新房间持久状态，有 state_key | m.room.name（房间名）、m.room.member（成员）、m.room.power_levels（权限） |

### 3.3 Event Graph（DAG）

每个房间的历史建模为**有向无环图（DAG）**：

- 每个 Event 引用 `prev_events`（前序事件）
- 多个服务器同时发送事件会创建分支
- **Forward Extremities**：DAG 的末端事件（没有子事件）
- 新事件必须引用所有当前 forward extremities

```
        Event A
       /        \
  Event B      Event C    (两个服务器同时发送，产生分支)
       \        /
        Event D           (引用 B 和 C，合并分支)
```

### 3.4 状态解析（State Resolution）

当 DAG 有多个 forward extremities 时，需要确定房间的"真实"当前状态：

| 版本 | 特点 |
|------|------|
| **v1** | 简单算法，易受攻击 |
| **v2** | 处理拜占庭条件，防止恶意服务器回滚历史或排除有效事件 |

**v2 状态解析核心思想**：
- 冲突的状态事件通过"授权事件链"验证合法性
- 不允许任何单一服务器单方面决定房间状态
- 网络分区恢复后，所有服务器最终收敛到同一状态

---

## 4. Synapse 服务端架构

### 4.1 单体到 Worker 的演进

Synapse 从单进程 Python 应用演进为**主进程 + 多 Worker** 架构：

| 阶段 | 架构 | 适用规模 |
|------|------|---------|
| 单体 | 单进程处理所有功能 | 小团队/个人 |
| Worker | 主进程 + 多个专用 worker | 中大规模 |

### 4.2 Worker 类型

| Worker | 职责 | 说明 |
|--------|------|------|
| **主进程** | 数据库写入管理、协调 | 唯一可写数据库的进程（部分场景） |
| **generic_worker** | 处理客户端 REST API 请求 | 可水平扩展，分担读请求 |
| **federation_sender** | 发送联邦流量到其他服务器 | 不处理 REST 端点，专责出站联邦 |
| **media_repository** | 媒体文件上传/下载/缩略图 | 独立处理大文件 |
| **user_dir** | 用户目录搜索 | 独立索引和搜索 |
| **frontend_proxy** | 前端代理 | 负载均衡入口 |

### 4.3 Replication 协议

Worker 间通过 Synapse 专用的 **replication 协议**同步状态（类似数据库复制）：

```
主进程写入 DB → 生成 replication stream → Redis pub/sub → 所有 worker 接收 → 更新本地缓存
```

**Replication Streams**（数据流类型）：

| Stream | 内容 |
|--------|------|
| EventsStream | 新事件 |
| PresenceStream | 在线状态变更 |
| TypingStream | 打字指示器 |
| DeviceListsStream | 设备列表变更 |
| ReceiptsStream | 已读回执 |
| AccountDataStream | 账户数据 |
| CachesStream | 缓存失效通知 |

**关键组件**：
- `ReplicationCommandHandler`：接收 Redis 命令，路由到各 stream 的 BackgroundQueue
- `ReplicationDataHandler`：处理流入的 stream 行，更新 store、notifier、federation sender

### 4.4 Redis 的角色

- **Pub/Sub**：广播 replication stream 给所有 worker
- **共享缓存**：worker 间共享缓存数据
- 所有 worker 和主进程都连接 Redis

---

## 5. 客户端同步机制

### 5.1 /sync 长轮询

客户端通过 `GET /_matrix/client/v3/sync` 获取增量更新：

| 参数 | 说明 |
|------|------|
| `since` | 上一次同步的 token（增量起点） |
| `timeout` | 长轮询超时时间（无新事件时挂起） |
| `filter` | 过滤条件（房间/事件类型） |

**流程**：
```
1. 客户端首次 /sync（无 since）→ 获取全量状态 + next_batch token
2. 客户端 /sync?since=token → 服务器挂起直到有新事件或超时
3. 服务器返回增量更新 + 新的 next_batch token
4. 重复步骤 2-3
```

### 5.2 同步响应结构

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

## 6. 端到端加密

### 6.1 双棘轮加密体系

| 协议 | 用途 | 特点 |
|------|------|------|
| **Olm** | 1:1 设备间加密 | 双棘轮（Double Ratchet），前向保密 + 后向保密 |
| **Megolm** | 群组加密 | 单棘轮，每个发送方维护自己的 ratchet，密钥分发给群成员 |

### 6.2 密钥管理

- 每个设备有身份密钥（Curve25519 + Ed25519）
- 设备验证：通过 emoji/SAS 或二维码验证对方设备
- 密钥备份：可加密备份到 homeserver，用恢复密钥或密码解密
- 跨设备签名：用户签名自己的设备，建立信任链

---

## 7. 数据存储

### 7.1 PostgreSQL 设计

- 生产环境必须使用 PostgreSQL（SQLite 仅演示）
- 事件存储在 `events` 表，JSONB 格式存储 content
- 状态事件单独存储，支持按房间+类型+state_key 查询
- 房间 DAG 关系存储在 `event_edges` 表

### 7.2 核心表

| 表 | 说明 |
|----|------|
| events | 所有事件（消息+状态） |
| event_edges | DAG 边（prev_events 关系） |
| room_memberships | 房间成员状态 |
| users | 用户账户 |
| devices | 用户设备（加密用） |
| e2e_room_keys | 加密密钥备份 |
| presence | 在线状态 |
| receipts | 已读回执 |

---

## 8. 设计原则与权衡

| 设计决策 | 选择 | 权衡 |
|---------|------|------|
| **联邦去中心化** | 无中心服务器，数据分布式存储 | 无单点故障，但状态解析复杂，延迟较高 |
| **AP 优先** | CAP 中选择可用性+分区容忍 | 最终一致性，分区恢复后需状态解析 |
| **Event DAG** | 有向无环图建模历史 | 支持分支和合并，但计算成本高（大房间 CPU 密集） |
| **全量复制** | 每个 homeserver 存储房间完整副本 | 读取快，但存储和带宽开销大 |
| **PDU 签名** | 事件签名，可第三方转发 | 安全可信，但签名验证有计算开销 |
| **Python/Twisted** | 异步单线程 | 开发快，但 CPU 密集任务需 worker 扩展 |
| **长轮询 /sync** | 而非 WebSocket | 兼容性好，但连接开销大（每 timeout 重连） |

---

## 9. 对 CBOL 项目的参考价值

### 9.1 架构层面

| Matrix 设计 | CBOL 可借鉴 |
|------------|------------|
| 联邦架构（服务器对等） | 跨系统/跨组织消息互通设计 |
| Event DAG 建模历史 | 分布式场景下的消息顺序和冲突解决 |
| Worker + Replication 协议 | 服务端水平扩展方案（主进程写+worker 读） |
| Redis pub/sub 同步 | 多进程间状态同步机制 |

### 9.2 协议层面

| Matrix 设计 | CBOL 可借鉴 |
|------------|------------|
| PDU/EDU/Query 三分法 | 持久事件 vs 临时事件 vs 查询的协议分类 |
| Transaction 幂等传输 | 批量+幂等的可靠传输设计 |
| 事件签名 | 不可否认性和防篡改 |
| /sync 增量同步 | 客户端状态同步的 token 机制 |

### 9.3 安全层面

| Matrix 设计 | CBOL 可借鉴 |
|------------|------------|
| Olm/Megolm 双棘轮 | 端到端加密方案（如需要） |
| 设备验证（emoji/SAS） | 可信设备建立机制 |
| 密钥备份 | 跨设备密钥恢复 |

### 9.4 状态解析层面

| Matrix 设计 | CBOL 可借鉴 |
|------------|------------|
| v2 状态解析算法 | 分布式冲突解决（拜占庭容错） |
| 授权事件链 | 状态变更的合法性验证 |

> **注意**：Matrix 的联邦和 DAG 设计复杂度很高，CBOL 项目如果是中心化部署，不需要完整复制。可借鉴的是其**事件建模、增量同步、worker 扩展**等思想，而非完整联邦协议。

---

## 10. 参考资料

- GitHub: https://github.com/element-hq/synapse
- 协议规范: https://spec.matrix.org
- Server-Server API: https://spec.matrix.org/v1.11/server-server-api/
- Client-Server API: https://spec.matrix.org/v1.11/client-server-api/
- Synapse Workers: https://element-hq.github.io/synapse/latest/workers.html
- Room DAG 概念: https://github.com/matrix-org/synapse/blob/develop/docs/development/room-dag-concepts.md
- 状态解析: https://deepwiki.com/matrix-org/synapse/5.3-state-resolution
- Replication 系统: https://deepwiki.com/element-hq/synapse/3.4-replication-system
- Matrix 协议详解: https://havenmessenger.com/blog/posts/matrix-protocol-explained/

---

*分析日期：2026-08-18*
