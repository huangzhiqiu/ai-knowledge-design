# CBOL Refactor — Project Knowledge Base

> 面向 AI Messaging Hub（Self-Development）的即时通讯项目知识库，覆盖领域模型、架构设计、Java 实现参考、设计规范与编码标准，支持基于知识库生成 Java 代码。

---

## 📋 项目简介

本知识库为 **CBOL Refactor** 项目提供系统化的知识管理，目标是：

- **沉淀领域知识**：从现有代码库和关联系统中提取 CBOL 特定的领域模型、接口定义、数据库设计
- **参考业界最佳实践**：收集 Mattermost、Rocket.Chat、Matrix、Turms 等优秀开源 IM 项目的架构模式和设计模式
- **支撑代码生成**：提供 Java 技术栈、数据结构、Netty 网络通信、并发模型等可直接参考的实现细节
- **统一规范标准**：集成设计原则、API 规范、编码规范、安全规范、质量门禁

---

## 📁 目录结构

```
ai-knowledge-design/
├── README.md                                    # 本文件
│
├── 00-Project-Overview/                         # 项目概述
│   └── project-intro.md                         # 背景、目标、范围、里程碑
│
├── 01-CBOL-Domain-Knowledge/                    # 🔧 CBOL 特定领域知识（待项目组填写）
│   ├── README.md
│   ├── domain-model/                            # CBOL 领域实体与关系
│   ├── module-structure/                        # Maven 模块划分与依赖
│   ├── database-schema/                         # 数据库表、索引、分片策略
│   ├── api-definitions/                         # OpenAPI/YAML 接口定义
│   ├── uml-diagrams/                            # 类图、时序图、组件图
│   ├── configuration/                           # application.yml 配置说明
│   ├── deployment-architecture/                 # 部署拓扑、容量规划、CI/CD
│   ├── related-systems/                         # 上下游系统与集成点
│   └── state-machine/                           # 自研轻量级状态机设计
│       ├── README.md                            # 设计原则与对比
│       ├── architecture.md                      # 核心架构与数据流
│       ├── api-design.md                        # API 与 Builder 模式
│       └── integration.md                       # 与 AI Messaging Hub 集成
│
├── 02-Chat-Domain-Knowledge/                    # 📚 通用 IM 知识 + Java 实现参考（已预填）
│   ├── README.md
│   │
│   ├── # === 领域与设计 ===
│   ├── domain-model/                            # 5 大核心实体模型
│   │   ├── user-model.md                        # 用户模型
│   │   ├── conversation-model.md                # 会话模型
│   │   ├── message-model.md                     # 消息模型（msg_id + seq_id）
│   │   ├── group-model.md                       # 群组模型
│   │   └── device-session-model.md              # 设备与会话模型
│   ├── message-design/                          # 消息设计
│   │   ├── message-id-design.md                 # 消息 ID 设计（Snowflake/UUID）
│   │   ├── message-types.md                     # 消息类型分类
│   │   └── message-states.md                    # 消息状态机
│   ├── sync-mechanism/                          # 同步机制
│   │   ├── multi-device-sync.md                 # 多端同步策略
│   │   ├── cursor-design.md                     # 游标设计
│   │   └── push-pull-models.md                  # 推拉模型对比
│   ├── storage-design/                          # 存储设计
│   │   ├── message-storage.md                   # 消息存储（MySQL/Cassandra）
│   │   ├── offline-messages.md                  # 离线消息队列
│   │   └── timeline-model.md                    # 时间线模型（写扩散/读扩散）
│   │
│   ├── # === 架构与模式 ===
│   ├── architecture-patterns/                   # 架构模式
│   │   ├── layered-architecture.md              # 分层架构
│   │   ├── microservices.md                     # 微服务架构
│   │   ├── event-driven.md                      # 事件驱动（Matrix DAG）
│   │   └── federation.md                        # 联邦架构
│   ├── design-patterns/                         # 设计模式
│   │   ├── gateway-pattern.md                   # 网关模式
│   │   ├── message-routing.md                   # 消息路由
│   │   ├── fanout-pattern.md                    # 扇出模式
│   │   └── presence-management.md               # 在线状态管理
│   ├── reliability/                             # 可靠性设计
│   │   ├── message-delivery.md                  # 投递保证
│   │   ├── idempotency.md                       # 幂等性
│   │   └── retry-backoff.md                     # 重试与退避
│   │
│   └── # === Java 实现参考 ===
│   ├── java-implementation/                     # Java 技术栈与项目结构
│   ├── concurrency/                             # 并发模型（线程池/SessionRegistry/分布式锁）
│   ├── networking/                              # Netty WebSocket 实现
│   │   ├── websocket-protocol.md                # WebSocket 协议设计
│   │   └── connection-management.md             # 连接管理与重连
│   ├── serialization/                           # Protobuf/JSON 序列化
│   ├── data-structures/                         # POJO/Enum/Redis Key/DDL
│   └── code-templates/                          # 代码模板
│   └── open-source-deep-dive/                   # 开源项目深度架构分析
│       ├── README.md
│       ├── turms-deep-analysis.md               # Turms (Java/Netty/读扩散/无锁)
│       ├── mattermost-deep-analysis.md          # Mattermost (Go/分层架构/插件RPC)
│       ├── rocketchat-deep-analysis.md          # Rocket.Chat (DDP/OpLog/NATS微服务)
│       ├── matrix-synapse-deep-analysis.md      # Matrix/Synapse (联邦/Event DAG/Olm加密)
│       ├── tiledesk-chat21-deep-analysis.md     # Tiledesk/Chat21 (Inbox模式/MQTT/RabbitMQ)
│       └── openchat-deep-analysis.md            # OpenChat (ICP区块链/Canister/SNS DAO)
│
├── 03-Design-Guidelines/                        # 🎨 设计指南
│   ├── README.md
│   ├── design-principles.md                     # SOLID/DRY/KISS/YAGNI 等设计原则
│   ├── api-design-guidelines.md                 # RESTful API 设计规范
│   ├── architecture-principles.md               # 分布式/微服务/12-Factor 架构原则
│   └── self-development-standards.md            # Self-Development 内部编码要求
│
├── 04-Coding-Guidelines/                        # 💻 编码规范
│   ├── README.md
│   ├── java-coding-standards.md                 # Google + 阿里 Java 编码规范
│   ├── security-guidelines.md                   # OWASP Top 10 安全编码规范
│   ├── code-quality.md                          # 代码质量与 SonarQube 质量门禁
│   ├── concurrency-guidelines.md                # Java 并发编程规范
│   ├── exception-and-logging.md                 # 异常处理与结构化日志
│   └── sonar-rules.md                           # Sonar 规则配置
│
├── 05-References/                               # 🔗 外部参考资料
│   ├── README.md
│   ├── open-source-projects.md                  # 开源 IM 项目详细参考
│   └── ai-driven-development.md                 # AI 驱动开发参考项目 (Forge/Jira-Flow/ai-coding-workflow/Devin)
│
└── 06-Skills/                                   # 🤖 自动化技能
    ├── README.md
    ├── # === OpenCode 兼容技能 (SKILL.md + frontmatter) ===
    ├── workflow-ticket-to-deploy/               # 流水线总编排: Ticket→SDD→Code→Test→PR→Deploy
    ├── jira-ticket-fetcher/                     # Jira Ticket 拉取与结构化
    ├── sdd-generator/                           # SDD 生成 (12章节 + 知识库注入)
    ├── java-maven-project-analyzer/             # Java Maven 多模块项目分析
    ├── architecture-analyzer-skill/             # 通用代码库深度分析 (16章节)
    ├── codebase-architecture-analyst/           # 文件级逆向工程 + OWASP安全审计
    └── # === 知识库概念技能 ===
    ├── cbol-knowledge-collector/                # CBOL 知识收集技能
    ├── chat-pattern-collector/                  # 开源项目模式收集技能
    ├── code-analyzer/                           # 代码分析技能
    └── doc-generator/                           # 文档生成技能
```

---

## 📊 文档统计

| 目录 | 文档数 | 状态 |
|------|--------|------|
| 00-Project-Overview | 1 | 🟡 待填写 |
| 01-CBOL-Domain-Knowledge | 13 | 🟡 模板就绪，待填写 |
| 02-Chat-Domain-Knowledge | 40+ | 🟢 已预填（含6个开源项目深度分析） |
| 03-Design-Guidelines | 4 | 🟢 已预填 |
| 04-Coding-Guidelines | 7 | 🟢 已预填 |
| 05-References | 2 | 🟢 已预填 |
| 06-Skills | 13 | 🟢 已预填（10个OpenCode技能 + 2个概念技能） |
| **合计** | **95+** | |

---

## 🚀 快速开始

### 1. 浏览通用 IM 知识
从 `02-Chat-Domain-Knowledge/` 开始，了解即时通讯系统的通用领域模型、架构模式和 Java 实现参考。

### 2. 填写 CBOL 特定知识
参考 `01-CBOL-Domain-Knowledge/` 中的模板，从现有代码库提取：
- 领域实体 → `domain-model/`
- 模块结构 → `module-structure/`
- 数据库设计 → `database-schema/`
- API 定义 → `api-definitions/`
- 配置说明 → `configuration/`

### 3. 应用设计与编码规范
开发时遵循 `03-Design-Guidelines/` 和 `04-Coding-Guidelines/` 中的标准。

### 4. 使用自动化技能
利用 `06-Skills/` 中的技能模板，自动化收集和整理知识。

### 5. Jira 驱动的 AI 开发流水线
使用 `06-Skills/workflow-ticket-to-deploy/` 技能，从 Jira ticket 驱动完整开发流程：

```
/workflow-ticket-to-deploy jira_key=CBOL-123
```

流水线包含 7 个阶段 + 6 个人工审批门控：
- Stage 0: Ticket Intake (`jira-ticket-fetcher`)
- Stage 1: Requirements Analysis
- Stage 2: SDD Generation (`sdd-generator`)
- Stage 3: Implementation (TDD)
- Stage 4: Test & Verification
- Stage 5: PR Creation
- Stage 6: Deploy & Doc Update

参考 `05-References/ai-driven-development.md` 了解业界最佳实践（Forge、Jira-Flow、ai-coding-workflow）。

---

## 🏗️ 技术栈参考

| 层级 | 技术选型 |
|------|---------|
| 语言 | Java 17+ (LTS) |
| 框架 | Spring Boot 3.x |
| 网络通信 | Netty 4.1.x (WebSocket/TCP) |
| 数据库 | MySQL 8.0 / MongoDB |
| 缓存 | Redis 6.x (集群) |
| 消息队列 | Kafka / RocketMQ |
| 对象存储 | MinIO / S3 |
| 序列化 | Protobuf / Jackson JSON |
| 构建工具 | Maven / Gradle |
| 容器化 | Docker + Kubernetes |
| 监控 | Prometheus + Grafana + ELK |
| 链路追踪 | SkyWalking / Jaeger |

---

## 📖 参考项目

| 项目 | 语言 | 参考价值 |
|------|------|---------|
| [Mattermost](https://github.com/mattermost/mattermost) | Go + React | 分层架构、插件系统、企业级协作 |
| [Rocket.Chat](https://github.com/RocketChat/Rocket.Chat) | Node.js + MongoDB | 实时通信、NATS 微服务、DDP 协议 |
| [Matrix/Synapse](https://github.com/matrix-org/synapse) | Python | 联邦架构、事件 DAG、状态解析 |
| [Turms](https://github.com/turms-im/turms) | Java | 高并发 IM、扇出读、推拉模型 |
| [Tiledesk/Chat21](https://github.com/chat21) | Node.js | MQTT + RabbitMQ 消息路由 |
| [OpenChat](https://github.com/open-chat-labs/open-chat) | Go | WebSocket + Redis pub/sub 水平扩展 |

---

## 🤝 贡献指南

### 文档规范
- 所有文档使用 **Markdown** 格式
- 文件名使用 **kebab-case**（小写+连字符）
- 每个目录包含 `README.md` 作为索引
- 技术文档需标注参考来源

### 提交规范
```
<type>(<scope>): <subject>

类型:
  feat:     新增文档/内容
  update:   更新现有文档
  refactor: 重构文档结构
  docs:     文档元信息更新
  chore:    杂项

示例:
  feat(02-chat): add websocket protocol design
  update(01-cbol): fill database schema
```

### CBOL 特定内容填写流程
1. 从现有代码库提取信息
2. 按对应目录的模板格式整理
3. 提交 PR / 直接提交
4. 团队评审后合并

---

## ⚠️ 注意事项

- **敏感信息**：不要在文档中提交密码、Token、内部 IP 等敏感信息
- **Self-Development 合规**：涉及内部标准的内容需确认合规性后再提交
- **代码引用**：引用开源项目代码时需遵守对应许可证
- **Token 安全**：Git 远程 URL 中的 Token 仅用于推送，公开仓库前请移除

---

## 📄 许可证

本项目知识库仅供 CBOL Refactor 项目内部使用。

---

*最后更新：2026-08-19*
