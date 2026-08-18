# Turms 深度架构分析

> 来源：[turms-im/turms](https://github.com/turms-im/turms) ⭐ ~1.9k | Apache-2.0 | Java
> 官方文档：https://turms-im.github.io/docs/
> 定位：面向 10万~1000万 并发用户的开源即时通讯引擎

---

## 1. 项目概览

Turms 是目前开源社区中面向中大型 IM 场景最专业的 Java 实现。其架构设计脱胎于商业即时通讯系统，以**极致性能**为第一优先级，支持完整（而非丰富）的 IM 功能。

### 核心子项目

| 子项目 | 职责 | 状态 |
|--------|------|------|
| **turms-gateway** | 客户端接入网关：协议解析、连接管理、用户认证、会话管理、消息推送、turms-service 负载均衡 | 必选 |
| **turms-service** | IM 业务逻辑：消息路由、用户/群组/关系链管理、RBAC、集群管理 | 必选 |
| **turms-admin** | 管理后台：业务数据管理、集群监控、运营报表 | 可选 |
| **turms-client-*** | 多端 SDK：Java/JS/Kotlin/Swift/Dart | 可选 |
| **turms-plugin** | 插件框架：用户上下线、消息收发等事件触发自定义逻辑 | 可选 |
| **turms-plugin-antispam** | 敏感词过滤插件（Aho-Corasick + 双数组字典树，O(n) 检测） | 可选 |
| **turms-plugin-minio** | MinIO 对象存储插件 | 可选 |
| **turms-plugin-rasa** | Rasa 聊天机器人插件 | 可选 |

### 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java (Reactive, 全异步) |
| 网络 | Netty (TCP + WebSocket) |
| 数据库 | MongoDB 分片集群 |
| 缓存 | Redis (分布式内存) + 本地内存缓存 |
| 序列化 | Protobuf (客户端) + 自定义二进制编码 (服务间 RPC) |
| 监控 | Prometheus + Grafana |
| 部署 | Docker / Docker Compose / Terraform |

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端 (TCP/WebSocket)                     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   DNS / SLB / Nginx  │  (TCP 负载均衡, Sticky Session)
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
   │ turms-gateway│      │ turms-gateway│      │ turms-gateway│
   │  (无状态)    │      │  (无状态)    │      │  (无状态)    │
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          │                    │                    │
          │    RPC (自定义二进制编码, 全异步)        │
          └────────────────────┼────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
   │turms-service │      │turms-service │      │turms-service │
   │  (无状态)    │      │  (无状态)    │      │  (无状态)    │
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          │                    │                    │
   ┌──────▼────────────────────▼────────────────────▼──────┐
   │              MongoDB 分片集群 (mongos)                 │
   │         (读写分离, 冷热分离, 按时间分片)                 │
   └──────────────────────────┬─────────────────────────────┘
                              │
   ┌──────────────────────────▼─────────────────────────────┐
   │              Redis 集群 (分布式内存)                     │
   │         (用户会话, 在线状态, 网关节点信息)                │
   └────────────────────────────────────────────────────────┘
```

### 2.2 设计哲学：极简架构

Turms 的架构设计核心原则是**"能不拆就不拆，能不引入外部服务就不引入"**：

| 常见 IM 架构做法 | Turms 的选择 | 原因 |
|-----------------|-------------|------|
| 会话管理、消息缓存、消息推送拆为 3 个独立服务 | **合并在 turms-gateway** | 减少故障点、避免 RPC 开销、业务逻辑不复杂 |
| 引入 Kafka/RocketMQ 做消息队列异步消费 | **不引入消息队列** | 用云弹性伸缩(Auto Scaling)做流量整形更合适；统计用业务日志分析 |
| 网络连接管理和会话逻辑拆为两个服务 | **不拆分** | gateway 几乎无会话业务逻辑，拆分收益小、增加故障点 |
| 用 Hazelcast/Ignite 分布式 Map 替代 Redis | **选择 Redis** | 集群高可用和发布流程设计需要外部分布式内存 |

> **关键洞察**：Turms 明确批评了"为了简历好看而过度设计"的做法，指出很多 IM 项目在几万在线用户规模就引入消息队列、微服务拆分，属于不必要的技术复杂度。

### 2.3 无状态与多活

- **turms-gateway 和 turms-service 均为无状态**，可水平扩展
- 用户会话信息存储在 **Redis + 本地缓存**
- 支持**跨数据中心多活**部署
- 支持**用户无感知更新**（滚动发布）

---

## 3. 客户端接入流程

### 3.1 连接建立

```
1. 客户端 DNS 查询 → SLB/ELB (LVS/Nginx) → turms-gateway
   - 基于客户端 IP 做 TCP 负载均衡
   - 强烈建议开启 Sticky Session (缓解 DDoS)
   - SSL 证书放在上游 SLB/Nginx

2. turms-gateway 检测：
   - IP 是否被封禁 → 主动断开
   - 服务是否过载 → 主动断开
   - 通过则建立 TCP 连接

3. 协议选择：
   - TCP: 直接发送 Protobuf 数据流
   - WebSocket: HTTP Upgrade → 二进制帧承载 Protobuf
```

### 3.2 登录与会话建立

```
客户端 → turms-gateway (TurmsRequest: login)
  │
  ├─ gateway 解析出 user ID + device type → 组成 session ID
  ├─ 查询 Redis/本地缓存 → 检测 session ID 是否冲突
  │   ├─ 冲突 → 拒绝登录 (CREATE_EXISTING_SESSION)
  │   └─ 不冲突 → 注册会话到 Redis → 返回成功
  │
  └─ 用户进入 online 状态
```

**Session ID 设计：**
- `session ID = user ID + device type`
- 同一用户的不同设备可连接**不同的** gateway
- **但建议同用户所有设备连同一 gateway**，原因：
  1. 推送消息只需发给一个 gateway，减少资源开销
  2. 同用户多设备共享心跳时钟，减少 Redis TTL 刷新次数
  3. 避免用户状态缓存未更新导致新消息延迟

### 3.3 请求转发

```
客户端请求 → turms-gateway
  │
  ├─ gateway 能自己处理？(login/logout/心跳)
  │   └─ 是 → 直接处理并返回
  │
  └─ 否 → 检查用户是否已登录
      ├─ 未登录 → 拒绝
      └─ 已登录 → 按负载均衡策略选一个 turms-service
          → RPC 转发 (自定义二进制编码)
          → turms-service 处理 → 返回 response
          → 处理中产生的 notification → 查询 Redis 获取目标用户的 gateway 节点
          → RPC 推送到对应 gateway → gateway 转发给客户端
```

---

## 4. 网络通信设计

### 4.1 协议栈

| 通信方向 | 传输协议 | 编码方式 |
|---------|---------|---------|
| 客户端 ↔ turms-gateway | TCP 或 WebSocket | **Protobuf** |
| turms-gateway ↔ turms-service | TCP (Netty) | **自定义二进制编码**（无冗余数据） |
| turms-service ↔ MongoDB | MongoDB 驱动 | BSON |
| turms-service ↔ Redis | Redis 协议 | RESP |

### 4.2 TCP 协议格式

```
┌──────────────────┬──────────────────────┐
│  body-length     │  body (Protobuf)     │
│  (ZigZag 编码)   │  (TurmsRequest 或    │
│                  │   TurmsNotification) │
└──────────────────┴──────────────────────┘
```

- `body-length`：ZigZag 编码的变长整数，减少小数字的字节占用
- `body`：Protobuf 编码的 `TurmsRequest`（客户端→服务端）或 `TurmsNotification`（服务端→客户端）

### 4.3 WebSocket 协议格式

- WebSocket 二进制帧的 payload 直接承载 Protobuf 编码的 `TurmsRequest`/`TurmsNotification`
- 帧长度由 WebSocket 协议本身处理，无需额外的 body-length header

### 4.4 心跳机制

| 协议 | 心跳实现 |
|------|---------|
| TCP | 客户端发送单字节 `[0]` 作为心跳 |
| WebSocket | 标准 WebSocket Ping/Pong 帧 |

- 心跳间隔默认 **30秒**
- 三层保护机制：应用层心跳 + TCP Keepalive + 网关读空闲检测
- 心跳超时后 gateway 关闭连接，Redis 会话过期后用户变为离线

### 4.5 全异步 Reactive 模型

> **所有网络 IO 操作（数据库调用、Redis 调用、服务发现、RPC）均基于 Netty 实现非阻塞 IO。**

- 没有同步阻塞调用
- 充分利用系统资源
- 线程数恒定（见第 6 节）

---

## 5. 消息模型与存储设计

### 5.1 读扩散（Fanout Read）

Turms 的核心架构决策是**读扩散**（而非写扩散）：

| 模型 | 写时操作 | 读时操作 | 适用场景 |
|------|---------|---------|---------|
| **写扩散 (Fanout Write)** | 发消息时写入每个收件人的 inbox | 直接读自己的 inbox | 小群、活跃用户少 |
| **读扩散 (Fanout Read)** ⭐ | 消息只存一份，按收件人索引 | 读时查询所有相关会话的消息 | 大群、中大规模 |
| **推拉结合** | 在线用户推送 + 离线用户拉取 | 上线后拉取离线消息 | 通用 IM |

Turms 采用读扩散，消息按**收件人维度**存储和索引，避免大群写扩散导致的写放大。

### 5.2 Message 集合设计

**默认索引方案（方案 1）：**
- 复合索引：`message sending time + recipient ID`
- 分片键：`message sending time`
- 目的：支持**冷热数据分离**，不同时间段的数据分到不同 Shard

**可选方案（方案 2，use-conversation-id=true）：**
- 复合索引：`message sending time + session ID`
- 分片键：`message sending time`
- 私聊 session ID：16 字节（sender ID + receiver ID 排序组合）
- 群聊 session ID：8 字节（group ID）
- 适用：需要支持"发件人查询自己发过的消息"

**不推荐方案（方案 3）：**
- 在方案 1 基础上给 sender ID 加可选索引
- 问题：查询会话内消息需要查两次（对方发的 + 自己发的），效率低

### 5.3 冷热分离

- Message 是**唯一支持冷热分离**的集合
- 热数据（近期消息）→ 高性能服务器（16核 128G）
- 冷数据（历史消息）→ 低成本服务器（4核 8G）
- 按消息发送时间分片，天然支持冷热分离

### 5.4 索引设计原则

| 原则 | 说明 |
|------|------|
| **基于分布式分片特性设计** | 索引首要考虑 mongos 路由效率，而非单表查询性能 |
| **多读少写** | 索引为读优化，写操作尽量少 |
| **优先关键通用需求** | 不为"丰富功能"增加额外索引 |
| **不用 Hashed 索引** | MongoDB Hashed 索引不支持唯一性约束，会自动额外建 B-tree 索引，得不偿失 |
| **可选索引默认关闭** | 扩展功能的索引通过配置开启，避免小项目思维 |

### 5.5 其他集合设计要点

| 集合 | 复合 ID / 分片键 | 说明 |
|------|-----------------|------|
| GroupMember | `group ID + user ID` | 按 group ID 分片，快速查群成员；按 user ID 查群需 scatter-gather（默认不支持） |
| FriendRequest | `recipient ID + creation time` | 按 recipient ID 分片，快速查收到的请求；发件人查自己发的请求需可选索引 |
| GroupRequest | `recipient ID + creation time` | 同上 |
| User | user ID | 按用户 ID 分片 |
| Group | group ID | 按群组 ID 分片 |

---

## 6. 并发与性能设计

### 6.1 线程模型

> **Turms 服务端的峰值线程数是恒定的，与在线用户数和请求数无关。**

| 组件 | 线程数 | 说明 |
|------|--------|------|
| 接入层 (Netty EventLoop) | = CPU 核心数 | 充分利用 CPU 缓存，减少上下文切换和线程竞争 |
| 业务逻辑处理 | 复用接入层线程 | 全异步，无阻塞，无需额外业务线程池 |
| 其他（监控、日志等） | 少量固定线程 | 不随负载增长 |

### 6.2 无锁设计

- 业务逻辑处理中**几乎没有锁**，只有 **CAS 操作**
- 无锁设计是高吞吐量的关键
- 状态存储在 Redis/数据库，不在内存中维护共享可变状态

### 6.3 内存优化

- **智能分配堆内/堆外内存**，根据使用场景选择
- **重构 MongoDB/Redis 客户端依赖**，消除冗余内存分配
- **充分利用本地内存缓存**，减少远程调用
- 直接内存（Direct Memory）用于 Netty IO，避免堆内拷贝

### 6.4 性能目标

- 设计目标：**10万 ~ 1000万 并发用户**
- 单 gateway 节点可承载大量连接（Netty 非阻塞 IO）
- 所有操作异步非阻塞，无线程阻塞点

---

## 7. 安全设计

| 机制 | 说明 |
|------|------|
| **API 限流** | 网关层限流，防止 CC 攻击 |
| **全局用户/IP 黑名单** | gateway 自动检测异常行为并封禁，封禁数据 10~15 秒同步到其他 gateway |
| **Sticky Session** | SLB 开启粘性会话，缓解 DDoS（封禁 IP 后黑客无法快速切换 gateway） |
| **SSL/TLS** | 证书放在上游 SLB/Nginx 卸载 |
| **敏感词过滤** | turms-plugin-antispam（Aho-Corasick + 双数组字典树，O(n) 时间复杂度） |

---

## 8. 可观测性

### 三类日志

| 日志类型 | 用途 |
|---------|------|
| 监控日志 | 系统指标、性能监控 |
| 业务日志 | 业务事件记录，用于数据分析 |
| 统计日志 | 运营报表数据 |

### 监控栈

- Prometheus + Grafana
- 业务日志可对接 CloudWatch Logs → Kinesis Firehose → S3 → Athena/QuickSight 做数据分析

---

## 9. 插件系统

Turms 提供事件驱动的插件框架，核心事件包括：

| 事件 | 触发位置 | 用途 |
|------|---------|------|
| 用户上线/下线 | turms-gateway | 自定义在线状态处理 |
| 消息接收 | turms-gateway | 敏感词过滤、消息审计 |
| 消息转发 | turms-gateway | 离线推送、消息加密 |
| 客户端请求处理 | turms-service | 自定义业务逻辑 |

插件实现示例：
- `turms-plugin-antispam`：敏感词过滤
- `turms-plugin-minio`：对象存储
- `turms-plugin-rasa`：AI 聊天机器人

---

## 10. 对 CBOL 项目的参考价值

### 10.1 架构层面

| Turms 设计 | CBOL 可借鉴 |
|-----------|------------|
| gateway + service 两层拆分 | 接入层与业务层分离，gateway 专注连接管理 |
| 无状态服务 + Redis 会话 | 水平扩展方案 |
| 不盲目引入消息队列 | 评估是否真的需要 Kafka/RocketMQ，避免过度设计 |
| 读扩散消息模型 | 大群消息场景的存储设计 |

### 10.2 网络通信层面

| Turms 设计 | CBOL 可借鉴 |
|-----------|------------|
| 全异步 Netty Reactive 模型 | 避免同步阻塞，用 CompletableFuture/Reactor |
| Protobuf 客户端通信 | 高性能序列化方案 |
| 自定义二进制 RPC 编码 | 服务间通信极致优化（如需要） |
| TCP + WebSocket 双协议支持 | 多端接入 |
| 恒定线程数 = CPU 核心数 | 线程池配置参考 |
| 几乎无锁，只有 CAS | 并发设计思路 |

### 10.3 数据存储层面

| Turms 设计 | CBOL 可借鉴 |
|-----------|------------|
| MongoDB 分片 + 冷热分离 | 消息存储方案（如用 MongoDB） |
| 索引基于分片特性设计 | 数据库索引设计方法论 |
| 可选索引默认关闭 | 避免过度索引 |
| session ID = user ID + device type | 多设备会话管理 |

### 10.4 设计哲学层面

- **性能优先，功能完整而非丰富**：中大规模 IM 系统的核心取舍
- **极简架构，反对过度设计**：不盲目拆分服务、不盲目引入中间件
- **关键需求决定架构，次要需求验证架构**：需求优先级驱动设计
- **架构是权衡的艺术**：明确每个设计决策的 trade-off

---

## 11. 参考资料

- GitHub: https://github.com/turms-im/turms
- 官方文档: https://turms-im.github.io/docs/
- 架构设计: https://turms-im.github.io/docs/design/architecture
- Schema 设计: https://turms-im.github.io/docs/design/schema
- 通信协议: https://turms-im.github.io/docs/client/communication-protocol.html
- Playground: http://playground.turms.im (guest/guest)

---

*分析日期：2026-08-18*
