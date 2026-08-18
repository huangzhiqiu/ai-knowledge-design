# Rocket.Chat 深度架构分析

> 来源：[RocketChat/Rocket.Chat](https://github.com/RocketChat/Rocket.Chat) ⭐ ~45.9k | MIT | TypeScript (Node.js/Meteor) + MongoDB
> 官方文档：https://developer.rocket.chat
> 定位：Secure CommsOS™，面向关键任务运营的安全通信平台

---

## 1. 项目概览

Rocket.Chat 是一个开源核心的企业级实时通信平台，从 Meteor 单体应用演进而来，正在向微服务架构转型。其特点是**安全优先、Omnichannel 全渠道、Matrix 联邦、可扩展 Apps Engine**。

### Monorepo 结构

```
Rocket.Chat/
├── apps/
│   └── meteor/              # 核心服务端（Meteor 应用，97个功能模块）
│       ├── app/             # 按功能划分的模块（每个含 client/ + server/ + lib/）
│       ├── server/          # 通用服务端逻辑
│       └── client/          # 通用客户端逻辑
├── packages/                # 55个共享包
│   ├── apps-engine/         # Apps Engine（应用扩展框架）
│   ├── model-typings/       # 模型类型定义
│   ├── rest-typings/        # REST API 类型
│   └── ...
├── ee/                      # 企业版功能
└── e2e-tests/               # 端到端测试
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Node.js + Meteor (TypeScript) |
| 前端 | React |
| 数据库 | MongoDB（生产需副本集，8.x 目标 MongoDB 8.0） |
| 实时通信 | DDP over WebSocket + MongoDB OpLog |
| 微服务总线 | NATS |
| 文件存储 | 本地 / S3 / WebDAV |
| 部署 | Docker / Kubernetes / Podman |
| 联邦 | Matrix 协议 |

### 部署模式

| 模式 | 适用场景 | 特点 |
|------|---------|------|
| **单体** | 小团队 | 单进程，所有功能集成 |
| **多节点单体** | 中等规模 | 多个单体节点协同 |
| **微服务**（企业版） | 大规模/高可用 | 服务独立部署，NATS 通信 |

---

## 2. 架构设计

### 2.1 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                      Clients (Web/Desktop/Mobile)              │
│                   (REST API + WebSocket/DDP)                   │
└──────────────────────────────┬───────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Reverse Proxy      │  (NGINX/HAProxy, 负载均衡, TLS)
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
   │  DDPStreamer │      │  DDPStreamer │      │  DDPStreamer │
   │  (可水平扩展) │      │  (可水平扩展) │      │  (可水平扩展) │
   └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │ NATS (消息总线)
          ┌────────────────────┼────────────────────┐
          │          │         │         │          │
   ┌──────▼──┐ ┌────▼───┐ ┌───▼────┐ ┌──▼─────┐ ┌──▼───────┐
   │Account  │ │Auth    │ │Presence│ │StreamHub│ │AppsEngine│
   │(无状态)  │ │(无状态) │ │(无状态)│ │(单实例) │ │(开发中)   │
   └──────┬──┘ └────┬───┘ └───┬────┘ └──┬─────┘ └──┬───────┘
          │         │         │         │          │
          └─────────┴─────────┼─────────┴──────────┘
                              │
                     ┌────────▼────────┐
                     │   MongoDB        │  (副本集, OpLog 尾部跟踪)
                     │   (核心数据)      │
                     └─────────────────┘
```

### 2.2 从单体到微服务的演进

Rocket.Chat 正在从 Meteor 单体向微服务架构转型：

| 阶段 | 架构 | 特点 |
|------|------|------|
| **传统** | Meteor 单体 | 所有功能在一个进程，DDP + OpLog 实时更新 |
| **过渡** | 单体 + 外部服务 | 核心仍在 Meteor，部分服务拆出（Presence、Authorization 等） |
| **目标** | 全微服务 | 所有服务独立部署，NATS 通信，StreamHub 统一实时数据分发 |

### 2.3 核心设计理念

- **MongoDB 中心化**：所有数据存在 MongoDB，通过 OpLog 实现响应式更新
- **DDP 协议**：Meteor 的分布式数据协议，客户端订阅数据集合，服务端推送变更
- **渐进式微服务**：不一次性重构，逐步将服务从 Meteor 进程拆出
- **安全优先**：面向国防、情报、关键基础设施场景

---

## 3. 微服务架构详解

### 3.1 外部服务（独立进程，可水平扩展）

| 服务 | 职责 | 状态 | 扩展性 |
|------|------|------|--------|
| **Authorization** | 用户授权和权限管理 | 稳定 | 无状态，可水平扩展 |
| **Account** | 用户账户管理（创建/更新/删除/登录/登出） | 稳定 | 无状态，可水平扩展 |
| **Presence** | 用户在线状态跟踪和更新 | 稳定 | 无状态，可水平扩展 |
| **StreamHub** | 捕获数据库变更，广播实时数据给其他服务 | 稳定 | **单实例，不支持水平扩展** |
| **DDPStreamer** | 管理 DDP 连接，客户端-服务端交互、订阅、数据传输 | 稳定 | 可水平扩展 |
| **AppsEngine** | Rocket.Chat Apps 管理（安装/更新/执行/移除） | 开发中 | 设计支持水平扩展 |

### 3.2 内部服务（Meteor 进程内）

| 服务 | 职责 |
|------|------|
| Messaging | 消息管理 |
| Room | 聊天室管理（创建/更新/删除） |
| Team | 团队管理 |
| OmniChannel | 全渠道客服 |
| Omnichannel Voip | VoIP 语音通话 |
| Push | 移动推送通知 |
| Upload | 文件上传管理 |
| Settings | 系统设置管理 |
| Banner | 横幅管理 |
| LDAP | LDAP 集成 |
| NPS | 用户满意度调查 |
| UiKitCoreApp | UI Kit 交互处理 |

### 3.3 NATS 消息总线

- 微服务间通过 **NATS** 通信
- 服务指向 NATS 分发器而非直接指向每个组件
- NATS 决定将请求转发到哪个服务实例
- 支持服务发现和负载均衡

---

## 4. 实时通信设计

### 4.1 DDP 协议

Rocket.Chat 使用 **DDP（Distributed Data Protocol）** 作为客户端-服务端实时通信协议：

| 特性 | 说明 |
|------|------|
| 传输层 | WebSocket |
| 模式 | 客户端订阅集合 → 服务端推送变更 |
| 操作 | subscribe / unsubscribe / method call |
| 数据同步 | 服务端维护客户端订阅的数据视图，增量推送 |

**DDP 消息流**：
```
客户端 → 服务端: subscribe("stream-room-messages", roomId)
服务端 → 客户端: added / changed / removed (增量数据)
客户端 → 服务端: method("sendMessage", message)
服务端 → 客户端: result (方法调用结果)
```

### 4.2 MongoDB OpLog 响应式层

Rocket.Chat 的实时更新核心机制是 **MongoDB OpLog 尾部跟踪**：

```
数据写入 MongoDB → OpLog 记录变更 → StreamHub 捕获 → 广播给订阅者 → DDPStreamer 推送给客户端
```

- 所有服务实例监听 MongoDB OpLog
- 数据变更自动推送到订阅的客户端
- 无需轮询，延迟低

### 4.3 StreamHub 的角色

StreamHub 是实时数据分发的核心：
- 捕获数据库变更
- 广播给其他服务和客户端
- **当前为单实例**，是微服务架构的瓶颈
- 未来可能支持水平扩展

### 4.4 REST API + WebSocket 双轨

- **REST API**：非实时操作（创建用户、管理频道等）
- **WebSocket/DDP**：实时消息、状态更新、打字指示器
- 所有功能通过 REST API 和 WebSocket 暴露，便于第三方集成

---

## 5. Apps Engine（应用扩展框架）

### 5.1 架构

Apps Engine 是 Rocket.Chat 的插件系统，采用**三层架构**：

```
┌─────────────────────────────────────────┐
│  App Code (沙箱中运行)                   │
│  ┌───────────────────────────────────┐  │
│  │  Definition Layer (接口定义)       │  │
│  │  IRead / IModify / IHttp / ...    │  │
│  └───────────────┬───────────────────┘  │
│                  │                       │
│  ┌───────────────▼───────────────────┐  │
│  │  Server Layer (具体实现)           │  │
│  │  Reader / Modifier / ...          │  │
│  └───────────────┬───────────────────┘  │
│                  │                       │
│  ┌───────────────▼───────────────────┐  │
│  │  Bridge Layer (连接核心)           │  │
│  │  与 Rocket.Chat 核心交互           │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### 5.2 沙箱环境

- 基于 **Node.js VM 模块**创建隔离上下文
- App 代码在沙箱中运行，不能直接访问核心系统
- 通过定义好的 API 接口（IRead、IModify、IHttp 等）与核心交互
- 安全隔离，防止恶意 App 影响系统稳定

### 5.3 App 能力

| 能力 | 说明 |
|------|------|
| 斜杠命令 | 注册自定义 /command |
| 消息处理 | 拦截和修改消息 |
| API 端点 | 注册自定义 HTTP 端点 |
| 定时任务 | 注册 cron 任务 |
| UI Kit | 构建交互式消息组件 |
| 外部 HTTP | 调用外部 API |
| 事件监听 | 监听消息发送、用户加入等事件 |

### 5.4 官方 App 示例

- WhatsApp App（全渠道集成）
- Google Calendar App
- Matrix Bridge（联邦桥接）
- Jira / GitLab 等第三方集成

---

## 6. 数据模型与存储

### 6.1 MongoDB 设计

- **生产环境必须使用 MongoDB 副本集**（OpLog 跟踪需要）
- Rocket.Chat 8.x 目标 MongoDB 8.0
- 数据模型以文档为中心，适合聊天场景的灵活结构

### 6.2 核心集合

| 集合 | 说明 |
|------|------|
| users | 用户账户和凭证 |
| rooms | 聊天室（频道/私聊/讨论组） |
| messages | 消息（核心数据） |
| subscriptions | 用户-房间订阅关系 |
| roles | 角色和权限 |
| settings | 系统设置 |
| integration_history | 集成历史 |
| apps | 已安装的 Apps |
| omnichannel 相关 | 全渠道客服数据 |

### 6.3 模型抽象

- `@rocket.chat/model-typings` 包定义 TypeScript 接口（IUsersModel、IRoomsModel 等）
- 模型提供 CRUD 操作抽象
- 支持不同存储后端的实现

### 6.4 文件存储

| 存储方式 | 说明 |
|---------|------|
| 本地文件系统 | 默认，简单部署 |
| Amazon S3 | 云存储，适合大规模 |
| WebDAV | 网络存储协议 |

---

## 7. Omnichannel 全渠道

Rocket.Chat 的差异化特色是 **Omnichannel 全渠道客服**：

| 渠道 | 说明 |
|------|------|
| Web Widget | 网站嵌入式聊天组件 |
| WhatsApp | WhatsApp Business API 集成 |
| Facebook Messenger | Facebook 消息集成 |
| Instagram | Instagram 私信 |
| Telegram | Telegram Bot |
| Email | 邮件转工单 |
| SMS | 短信集成 |
| VoIP | 语音通话 |

- 所有渠道的消息统一到 Rocket.Chat 界面
- 人工客服 + 聊天机器人混合
- 会话路由、分配、统计

---

## 8. 安全与联邦

### 8.1 安全特性

| 特性 | 说明 |
|------|------|
| E2EE | 端到端加密（部分场景） |
| SSO | SAML、OAuth、LDAP/AD |
| RBAC | 细粒度角色权限 |
| 审计 | 操作审计日志 |
| 合规 | 数据保留策略、合规导出 |
| 气隙部署 | 支持隔离网络环境 |

### 8.2 Matrix 联邦

- 支持通过 **Matrix 协议**与其他平台联邦通信
- Rocket.Chat 可作为 Matrix homeserver 或桥接
- 跨平台消息互通

---

## 9. 设计原则与权衡

| 设计决策 | 选择 | 权衡 |
|---------|------|------|
| **Meteor 起步** | 全栈框架，快速开发 | 与 Meteor 耦合深，微服务拆分困难 |
| **MongoDB OpLog 实时** | 数据库级变更跟踪 | 依赖 MongoDB 副本集，StreamHub 单实例瓶颈 |
| **DDP 协议** | Meteor 标准实时协议 | 非通用标准，学习成本高 |
| **渐进式微服务** | 逐步拆分，不一次性重构 | 过渡阶段架构复杂，单体+微服务混合 |
| **Apps Engine VM 沙箱** | Node.js VM 隔离 | 性能不如原生，但安全隔离好 |
| **MongoDB 中心化** | 所有数据存 MongoDB | 非关系型，复杂查询受限 |

---

## 10. 对 CBOL 项目的参考价值

### 10.1 架构层面

| Rocket.Chat 设计 | CBOL 可借鉴 |
|-----------------|------------|
| 渐进式微服务演进 | 从单体逐步拆分，不追求一步到位 |
| NATS 微服务总线 | 服务间通信和服务发现 |
| StreamHub 实时分发 | 统一的实时事件分发层 |
| 无状态服务设计 | Authorization/Account/Presence 均可水平扩展 |

### 10.2 实时通信层面

| Rocket.Chat 设计 | CBOL 可借鉴 |
|-----------------|------------|
| DDP 订阅-推送模式 | 客户端订阅数据集合，服务端增量推送 |
| MongoDB OpLog 尾部跟踪 | 数据库变更驱动实时更新（如用 MySQL binlog） |
| REST + WebSocket 双轨 | 非实时用 REST，实时用 WebSocket |
| 打字指示器/在线状态 | Presence 服务独立部署 |

### 10.3 可扩展性层面

| Rocket.Chat 设计 | CBOL 可借鉴 |
|-----------------|------------|
| Apps Engine 三层架构 | Definition→Server→Bridge 的插件设计模式 |
| VM 沙箱隔离 | 第三方代码安全执行 |
| 事件钩子机制 | 消息发送/用户加入等事件可扩展 |
| UI Kit 交互组件 | 富交互消息格式 |

### 10.4 业务层面

| Rocket.Chat 设计 | CBOL 可借鉴 |
|-----------------|------------|
| Omnichannel 全渠道 | 接回话/回话转发场景的多渠道统一 |
| 人工+机器人混合 | AI 处理 + 人工转接的流程设计 |
| Matrix 联邦 | 跨系统消息互通 |

---

## 11. 参考资料

- GitHub: https://github.com/RocketChat/Rocket.Chat
- 开发者文档: https://developer.rocket.chat
- 架构与组件: https://developer.rocket.chat/docs/architecture-and-components
- 服务端架构: https://developer.rocket.chat/docs/server-architecture
- 微服务部署: https://docs.rocket.chat/deploy/scaling/microservices
- Apps Engine: https://developer.rocket.chat/apps-engine
- DDP 协议: https://github.com/meteor/meteor/blob/devel/packages/ddp/DDP.md

---

*分析日期：2026-08-18*
