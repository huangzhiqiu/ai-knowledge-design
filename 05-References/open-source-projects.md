# Open Source IM Projects Reference

> 详细的开源即时通讯项目参考，包含 GitHub 源码、star 数、技术栈、架构特点和关键源码目录。

---

## 1. Mattermost

| 属性 | 值 |
|------|-----|
| **GitHub** | https://github.com/mattermost/mattermost |
| **⭐ Stars** | ~28k |
| **语言** | Go (后端) + React (前端) |
| **许可证** | MIT (Team Edition) / AGPL (Enterprise) |
| **官网** | https://mattermost.com |
| **文档** | https://docs.mattermost.com |

### 架构特点
- 模块化单体 → 微服务演进
- 插件系统（Go plugin + React plugin）
- 支持 MySQL / PostgreSQL
- WebSocket 实时通信 + REST API
- 企业级 SSO（SAML, OAuth, LDAP）

### 关键源码目录
```
mattermost/
├── server/                      # Go 后端
│   ├── app/                     # 业务逻辑层
│   ├── api4/                    # REST API v4
│   ├── model/                   # 数据模型
│   ├── store/                   # 数据访问层 (SQL)
│   ├── web/                     # WebSocket 处理
│   ├── jobs/                    # 后台任务
│   └── plugin/                  # 插件系统
├── webapp/                      # React 前端
└── enterprise/                  # 企业版功能
```

### 参考价值
- 分层架构设计
- 插件系统实现
- WebSocket 连接管理
- 企业级权限模型

---

## 2. Rocket.Chat

| 属性 | 值 |
|------|-----|
| **GitHub** | https://github.com/RocketChat/Rocket.Chat |
| **⭐ Stars** | ~45.9k |
| **语言** | TypeScript (Node.js / Meteor) + MongoDB |
| **许可证** | MIT |
| **官网** | https://rocket.chat |
| **文档** | https://developer.rocket.chat |

### 架构特点
- Meteor 框架 + MongoDB oplog tailing
- NATS 微服务消息总线
- DDP (Distributed Data Protocol) 实时同步
- 支持矩阵桥接、Slack 兼容
- Omnichannel 客户服务

### 关键源码目录
```
Rocket.Chat/
├── apps/
│   ├── meteor/                  # 主应用 (Meteor)
│   │   ├── app/                 # 业务模块
│   │   ├── lib/                 # 共享库
│   │   └── server/              # 服务端逻辑
│   └── engine/                  # 微服务引擎
├── packages/                    # Meteor 包
└── ee/                          # 企业版
```

### 参考价值
- MongoDB oplog 实时推送
- NATS 微服务通信
- DDP 协议实现
- Omnichannel 多渠道整合

---

## 3. Matrix / Synapse

| 属性 | 值 |
|------|-----|
| **GitHub** | https://github.com/matrix-org/synapse (已归档) → https://github.com/element-hq/synapse |
| **⭐ Stars** | ~12k |
| **语言** | Python (Twisted async) |
| **许可证** | Apache-2.0 |
| **官网** | https://matrix.org |
| **规范** | https://spec.matrix.org |

### 架构特点
- 联邦架构（Federated homeservers）
- 事件 DAG（有向无环图）
- 状态解析算法（State Resolution）
- 端到端加密（Olm/Megolm）
- 房间版本化

### 关键源码目录
```
synapse/
├── synapse/
│   ├── handlers/                # 业务处理器
│   │   ├── message.py           # 消息处理
│   │   ├── room.py              # 房间管理
│   │   └── presence.py          # 在线状态
│   ├── replication/             # 联邦复制
│   ├── federation/              # 联邦通信
│   ├── storage/                 # 数据存储 (SQL)
│   ├── crypto/                  # 加密 (Olm/Megolm)
│   └── rest/                    # REST API
└── tests/
```

### 参考价值
- 联邦架构设计
- 事件 DAG 与状态解析
- 端到端加密实现
- 多服务器同步协议

---

## 4. Turms

| 属性 | 值 |
|------|-----|
| **GitHub** | https://github.com/turms-im/turms |
| **⭐ Stars** | ~1.9k |
| **语言** | Java (Spring + Netty) |
| **许可证** | Apache-2.0 |
| **官网** | https://turms-im.github.io/docs |
| **定位** | 面向 10万~1000万 并发用户的 IM 引擎 |

### 架构特点
- 网关 + 服务分离
- 读扇出（Fanout Read）模型
- 推拉结合的消息同步
- MongoDB + Redis
- 支持 TCP / WebSocket / UDP
- 自研轻量级状态机

### 关键源码目录
```
turms/
├── turms-gateway/               # 接入网关 (Netty)
│   └── src/main/java/im/turms/gateway/
│       ├── access/              # 接入管理
│       ├── codec/               # 编解码 (Protobuf)
│       └── handler/             # 消息处理器
├── turms-service/               # 业务服务
│   └── src/main/java/im/turms/service/
│       ├── service/             # 业务逻辑
│       ├── repository/          # 数据访问 (MongoDB)
│       └── cluster/             # 集群管理
├── turms-admin/                 # 管理后台
└── turms-client-*/              # 多端 SDK
```

### 参考价值
- **Java 技术栈最相关**：Spring + Netty + MongoDB + Redis
- 高并发网关设计
- 读扇出消息模型
- Protobuf 序列化
- 推拉结合同步策略

---

## 5. Tiledesk / Chat21

| 属性 | 值 |
|------|-----|
| **GitHub** | https://github.com/chat21 (Chat21) / https://github.com/Tiledesk (Tiledesk) |
| **⭐ Stars** | ~0.5k (多仓库) |
| **语言** | Node.js + Angular/Ionic |
| **许可证** | MIT |
| **官网** | https://tiledesk.com |
| **定位** | 开源在线客服 + 聊天机器人平台 |

### 架构特点
- MQTT 协议 + RabbitMQ 消息代理
- Firebase 实时数据库（早期版本）
- 多渠道整合（Web, Android, iOS）
- 内置聊天机器人引擎
- 人工客服 + AI 机器人混合

### 关键源码目录
```
chat21/
├── chat21-server/               # 服务端 (Node.js)
├── chat21-android-sdk/          # Android SDK (Java)
├── chat21-ios-demo/             # iOS Demo
├── chat21-web-widget/           # Web 聊天组件 (Angular)
└── chat21-cloud-functions/      # Firebase Cloud Functions

Tiledesk/
├── tiledesk-server/             # 主服务 (Node.js + Express)
├── tiledesk-dashboard/          # 管理后台 (Angular)
├── tiledesk-chatbot/            # 聊天机器人引擎
└── chat21-ionic/                # 客户端 (Ionic)
```

### 参考价值
- MQTT + RabbitMQ 消息路由
- 在线客服业务流程
- 机器人 + 人工转接
- 多渠道消息整合

---

## 6. OpenChat

| 属性 | 值 |
|------|-----|
| **GitHub** | https://github.com/open-chat-labs/open-chat |
| **⭐ Stars** | ~0.2k |
| **语言** | Rust (后端 canister) + TypeScript (前端) |
| **许可证** | AGPL-3.0 |
| **官网** | https://oc.app |
| **定位** | 运行在 Internet Computer 区块链上的去中心化聊天 |

### 架构特点
- 完全去中心化（无中心服务器）
- Internet Computer canister 架构
- 每个群组独立 canister
- DAO 治理（SNS）
- 端到端加密 + 代币转账

### 关键源码目录
```
open-chat/
├── backend/                     # Rust canisters
│   ├── user_index/              # 用户索引
│   ├── group_index/             # 群组索引
│   ├── group/                   # 单个群组 canister
│   ├── notifications/           # 通知服务
│   └── proposals/               # 治理提案
├── frontend/                    # TypeScript + Svelte
└── sdk/                         # SDK
```

### 参考价值
- 去中心化架构思路
- Canister 水平扩展
- 区块链上的消息存储
- DAO 治理模式

---

## 项目对比总结

| 维度 | Mattermost | Rocket.Chat | Matrix/Synapse | Turms | Tiledesk | OpenChat |
|------|-----------|-------------|----------------|-------|----------|----------|
| **定位** | 企业协作 | 企业协作+客服 | 联邦协议 | 高并发IM引擎 | 在线客服 | 去中心化聊天 |
| **并发规模** | 万级 | 万级 | 百万级(联邦) | 10万~1000万 | 千级 | 不限(区块链) |
| **Java相关** | ❌ Go | ❌ Node | ❌ Python | ✅ Java | ❌ Node | ❌ Rust |
| **开源协议** | MIT/AGPL | MIT | Apache-2.0 | Apache-2.0 | MIT | AGPL-3.0 |
| **最适合参考** | 分层架构/插件 | 实时推送/微服务 | 联邦/加密 | **Java实现/高并发** | 客服流程 | 去中心化 |

### 对 CBOL 项目的参考优先级
1. **Turms** ⭐⭐⭐⭐⭐ — Java 技术栈最匹配，高并发设计最相关
2. **Mattermost** ⭐⭐⭐⭐ — 分层架构和插件系统值得参考
3. **Matrix/Synapse** ⭐⭐⭐ — 联邦架构和状态机设计
4. **Rocket.Chat** ⭐⭐⭐ — 实时推送和微服务通信
5. **Tiledesk** ⭐⭐ — 客服业务流程（接回话/转人工场景）
6. **OpenChat** ⭐ — 去中心化思路（非核心需求）

---

## 其他值得关注的项目

| 项目 | GitHub | Stars | 说明 |
|------|--------|-------|------|
| **Ejabberd** | [processone/ejabberd](https://github.com/processone/ejabberd) | ~5.8k | Erlang XMPP 服务器，经典 IM 架构 |
| **Openfire** | [igniterealtime/Openfire](https://github.com/igniterealtime/Openfire) | ~2.8k | Java XMPP 服务器，老牌 Java IM |
| **Centrifugo** | [centrifugal/centrifugo](https://github.com/centrifugal/centrifugo) | ~7.8k | Go 实时消息服务器，WebSocket/SockJS |
| **Socket.IO** | [socketio/socket.io](https://github.com/socketio/socket.io) | ~61k | Node.js 实时通信库，最流行 |
| **Tinode** | [tinode/chat](https://github.com/tinode/chat) | ~1.8k | Go 即时通讯，支持 XMPP 兼容 |
| **LetsChat** | [sdelements/lets-chat](https://github.com/sdelements/lets-chat) | ~10k | Node.js 轻量团队聊天 |

---

*最后更新：2026-08-18*
