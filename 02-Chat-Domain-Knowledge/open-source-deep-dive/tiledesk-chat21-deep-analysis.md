# Tiledesk/Chat21 深度架构分析

> 来源：[Tiledesk](https://github.com/Tiledesk) / [Chat21](https://github.com/chat21) ⭐ ~0.5k (多仓库) | MIT | Node.js + Angular
> 官方文档：https://developer.tiledesk.com
> 定位：全栈开源实时聊天 + AI 客服平台，内置聊天机器人

---

## 1. 项目概览

Tiledesk 是一个开源的全渠道实时聊天平台，内置 AI 聊天机器人，定位为 Intercom/Zendesk/Tawk 的开源替代。其消息引擎核心是 **Chat21**——一个从 Firebase 演进到 RabbitMQ+MQTT 的轻量级实时消息系统。

### 多仓库架构

```
Tiledesk 生态
├── Tiledesk Server          # 核心服务端 (Node.js + Express)
├── Tiledesk Dashboard       # 管理后台 (Angular)
├── Tiledesk Deployment      # Helm + K8s / Docker Compose
├── Tiledesk Android/iOS     # 移动 App
└── Chat21 (消息引擎)
    ├── chat21-server        # RabbitMQ observer (消息中转)
    ├── chat21-http-server   # RabbitMQ REST API
    ├── chat21-cloud-functions # Firebase 云函数 (旧引擎)
    ├── chat21-web-widget    # 网站聊天组件 (Angular)
    ├── chat21-ionic         # 客服端 (Ionic + Angular)
    └── SDKs (Node/Android/iOS)
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Node.js + Express |
| 前端 | Angular (Dashboard + Widget) |
| 数据库 | MongoDB |
| 实时消息 | RabbitMQ + MQTT（新引擎）/ Firebase（旧引擎） |
| 缓存/同步 | Redis |
| 向量存储 | Qdrant（AI 知识库） |
| LLM | vLLM / Ollama（开源大模型） |
| 部署 | Docker Compose / Kubernetes (Helm) |

### 双引擎支持

| 引擎 | 传输 | 特点 |
|------|------|------|
| **RabbitMQ + MQTT**（推荐） | MQTT over WebSocket/TCP | 自托管、inbox 模式、细粒度 JWT 安全 |
| **Firebase**（旧） | Firebase Realtime DB + WebSocket | 托管、云函数、依赖 Google 云 |

---

## 2. Chat21 消息引擎核心设计

### 2.1 Inbox 模式（核心创新）

Chat21 的核心设计是 **Inbox 模式**，类似电子邮件的 SMTP/POP3：

```
发件人客户端                    Chat21 Server                  收件人客户端
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

**关键路径设计**：

| 方向 | MQTT Topic 路径 |
|------|----------------|
| 发件人写出 | `/apps/{appId}/users/{senderId}/{recipientId}/messages/outgoing` |
| 收件人接收 | `/apps/{appId}/users/{recipientId}/{senderId}/messages/clientadded` |

### 2.2 为什么用 Inbox 模式？

| 优势 | 说明 |
|------|------|
| **隐私/安全** | 消息通过 observer 中转，可实施策略（拉黑、过滤、审计） |
| **持久化** | observer 可在转发前持久化消息 |
| **细粒度权限** | RabbitMQ JWT Token 限制用户只能读写自己的路径 |
| **解耦** | 发件人不需要知道收件人的连接状态 |
| **离线消息** | 消息写入收件人 inbox，上线后自动接收 |

> **类比**：就像电子邮件——你把邮件发到自己的 SMTP 服务器（outgoing），服务器转发到收件人的邮件服务器（inbox），收件人通过 POP3 收取。

### 2.3 Chat21 Server（RabbitMQ Observer）

- 一个简单的 **RabbitMQ 消息观察者**
- 订阅所有用户的 `/outgoing` 路径
- 收到消息后，通过 AMQP publish 转发到对应收件人的 `/clientadded` 路径
- 同时触发 webhook 通知 Tiledesk Server
- 轻量级，无状态，可水平扩展

### 2.4 安全机制（RabbitMQ JWT Token）

- 每个用户获得 JWT Token
- Token 限定用户只能在**自己的路径**上读写
- 用户无法直接读写其他用户的 inbox
- observer 是唯一能跨路径转发的组件

```
用户 A 的 Token 权限：
  ✓ 读: /apps/tilechat/users/A/# (自己的所有路径)
  ✓ 写: /apps/tilechat/users/A/# (自己的所有路径)
  ✗ 读: /apps/tilechat/users/B/# (他人路径)
  ✗ 写: /apps/tilechat/users/B/# (他人路径)
```

---

## 3. 整体架构

### 3.1 组件交互

```
┌──────────────┐     Webhook (HTTP POST)      ┌──────────────────┐
│              │◄─────────────────────────────│                  │
│ Tiledesk     │                              │ Chat21 Server    │
│ Server       │                              │ (RabbitMQ        │
│ (业务逻辑)    │─────────────────────────────►│ Observer)        │
│              │     REST API (chat21-http)    │                  │
└──────┬───────┘                              └────────┬─────────┘
       │                                               │
       │ MongoDB                                       │ AMQP
       ▼                                               ▼
┌──────────────┐                              ┌──────────────────┐
│  MongoDB     │                              │  RabbitMQ        │
│  (业务数据)   │                              │  (消息队列+MQTT)  │
└──────────────┘                              └────────┬─────────┘
                                                       │ MQTT
                                          ┌────────────┼────────────┐
                                          ▼            ▼            ▼
                                    ┌─────────┐  ┌─────────┐  ┌─────────┐
                                    │ Web     │  │ Mobile  │  │ Agent   │
                                    │ Widget  │  │ App     │  │ Console │
                                    └─────────┘  └─────────┘  └─────────┘
```

### 3.2 Chat21 与 Tiledesk 的通信

- **Chat21 → Tiledesk**：通过 webhook，Chat21 事件（新消息、新成员加入群组等）通过 HTTP POST 发送到 Tiledesk 的 webhook endpoint
- **Tiledesk → Chat21**：通过 Chat21 HTTP Server 的 REST API 发送消息、创建群组等

### 3.3 Tiledesk Server 职责

| 模块 | 职责 |
|------|------|
| 项目管理 | 多租户/项目隔离 |
| 用户管理 | 访客、客服、管理员 |
| 部门/路由 | 会话分配、路由规则 |
| 聊天机器人 | 内置 AI bot（Rasa/原生/外部 LLM） |
| 全渠道 | Web Widget、WhatsApp、Facebook、Telegram、Email |
| 分析统计 | 会话统计、满意度、响应时间 |
| CRM | 联系人管理、标签 |
| Webhook/API | 第三方集成 |

---

## 4. 消息流程详解

### 4.1 发送消息（直连）

```
1. 客户端 A 通过 MQTT 连接 RabbitMQ
2. A 发布消息到 /apps/tilechat/users/A/B/messages/outgoing
   Payload: { text: "hello", sender: "A", recipient: "B", ... }
3. Chat21 Server (observer) 收到 outgoing 消息
4. Observer 持久化消息（可选）
5. Observer 通过 AMQP publish 到 /apps/tilechat/users/B/A/messages/clientadded
6. Observer 触发 webhook → Tiledesk Server（记录业务数据、触发 bot 等）
7. 客户端 B 订阅了自己的 inbox 路径，收到 MQTT 推送
8. B 解码消息（路径末尾 clientadded 表示新消息到达）
```

### 4.2 群组消息

- 群组消息类似，但 observer 需要转发给群内所有成员
- 每个成员有自己的群组 inbox 路径
- 支持群创建、成员加入/离开的 info message 通知

### 4.3 离线消息

- 消息写入 RabbitMQ 队列（持久化）
- 收件人上线后通过 MQTT 订阅接收历史消息
- 也可通过 Chat21 HTTP Server 的 REST API 拉取历史

---

## 5. Firebase 旧引擎（对比参考）

### 5.1 架构

```
客户端 ←WebSocket→ Firebase Realtime DB ←触发→ Firebase Cloud Functions
```

- 客户端直接连接 Firebase Realtime DB
- Cloud Functions 处理业务逻辑（发送消息、创建会话、推送通知）
- 依赖 Google 云平台

### 5.2 为什么迁移到 RabbitMQ+MQTT？

| 维度 | Firebase | RabbitMQ+MQTT |
|------|----------|---------------|
| 托管 | 依赖 Google 云 | 完全自托管 |
| 成本 | 按读写量计费 | 自建服务器成本可控 |
| 隐私 | 数据在 Google 云 | 数据完全自控 |
| 灵活性 | 受限于 Firebase 功能 | 可自定义 observer 逻辑 |
| 性能 | 托管扩展 | 可针对场景优化 |

---

## 6. 全渠道（Omnichannel）

Tiledesk 的核心特色是多渠道统一：

| 渠道 | 集成方式 |
|------|---------|
| Web Widget | 原生 Chat21 |
| WhatsApp | WhatsApp Business API |
| Facebook Messenger | Facebook Graph API |
| Telegram | Telegram Bot API |
| Email | IMAP/SMTP |
| 自定义 | Webhook + REST API |

- 所有渠道的消息统一到 Tiledesk 界面
- 客服可以在一个界面回复所有渠道
- 聊天机器人可在所有渠道自动回复

---

## 7. AI 集成

### 7.1 内置 AI 能力

| 能力 | 技术 |
|------|------|
| 聊天机器人 | 原生 bot / Rasa / 外部 LLM |
| 知识库问答 | Qdrant 向量存储 + RAG |
| LLM 推理 | vLLM / Ollama（开源模型自托管） |
| 意图识别 | 内置 NLU |

### 7.2 AI 架构

```
用户消息 → Tiledesk Server → Bot 引擎
                              ├── 规则匹配 (关键字/正则)
                              ├── Rasa NLU (意图识别)
                              ├── LLM (vLLM/Ollama)
                              └── 知识库 RAG (Qdrant 向量检索)
```

---

## 8. 性能基准

Chat21 Server 官方基准测试：

| 指标 | 直连消息 | 群组消息 |
|------|---------|---------|
| 平均延迟 | **13.85ms** | **45.61ms** |
| 目标延迟 | < 160ms | < 160ms |
| 吞吐量 | 60 msg/s | 60 msg/s |
| 并发用户 | 1 VU | 1 VU |
| 测试时长 | 10s | 10s |

> 基准测试配置较低（单并发），实际生产可通过水平扩展 observer 提升吞吐量。

---

## 9. 设计原则与权衡

| 设计决策 | 选择 | 权衡 |
|---------|------|------|
| **Inbox 模式** | observer 中转而非 P2P | 增加一跳延迟，但获得安全/策略/持久化能力 |
| **MQTT 协议** | 轻量级物联网协议 | 带宽小、适合移动，但不如 WebSocket 通用 |
| **RabbitMQ 为中心** | 消息队列即消息总线 | 可靠持久化，但 RabbitMQ 成为关键依赖 |
| **多仓库** | 每个组件独立仓库 | 灵活解耦，但部署和版本管理复杂 |
| **双引擎** | 同时支持 Firebase 和 RabbitMQ | 迁移平滑，但维护两套代码 |
| **Node.js** | 非阻塞 I/O | 适合 I/O 密集的聊天场景，但 CPU 密集任务受限 |

---

## 10. 对 CBOL 项目的参考价值

### 10.1 消息架构层面

| Chat21 设计 | CBOL 可借鉴 |
|------------|------------|
| **Inbox 模式** | 消息中转层可实施策略（接回话/回话转发/过滤/审计） |
| **MQTT 路径设计** | 基于 topic 的消息路由，天然支持发布/订阅 |
| **RabbitMQ JWT 细粒度安全** | 用户级别的路径权限控制 |
| **Observer 无状态扩展** | 消息中转层可水平扩展 |

### 10.2 业务层面

| Tiledesk 设计 | CBOL 可借鉴 |
|--------------|------------|
| 全渠道统一 | 接回话场景的多渠道接入（Web/App/第三方） |
| 会话路由/分配 | 回话转发、人工转接的路由规则 |
| 部门管理 | 客服团队组织架构 |
| Webhook 事件通知 | 消息事件驱动外部系统 |

### 10.3 技术选型层面

| Tiledesk 设计 | CBOL 可借鉴 |
|--------------|------------|
| RabbitMQ + MQTT 组合 | 轻量级实时消息方案（替代重的 Netty 自研） |
| Redis 缓存同步 | 会话状态、在线状态的快速访问 |
| MongoDB 灵活存储 | 聊天消息的文档型存储 |
| Docker Compose 一键部署 | 开发/测试环境快速搭建 |

> **注意**：Chat21 的 MQTT+RabbitMQ 方案适合中小规模和快速原型。如果 CBOL 项目目标是高并发（10万+连接），Turms 的 Netty 自研方案更合适。可考虑在不同场景选择不同技术栈。

---

## 11. 参考资料

- Tiledesk GitHub: https://github.com/Tiledesk
- Chat21 GitHub: https://github.com/chat21
- 架构组件: https://developer.tiledesk.com/architecture/components
- Chat21 Server (npm): https://www.npmjs.com/package/@chat21/chat21-server
- 从 Firebase 迁移到 MQTT/RabbitMQ: https://tiledesk.com/2021/02/12/tiledesk-new-messaging-engine-moving-from-firebase-to-mqtt-rabbitmq
- Chat21 Cloud Functions: https://github.com/chat21/chat21-cloud-functions
- Tiledesk REST API: https://developer.tiledesk.com/apis/rest-api

---

*分析日期：2026-08-18*
