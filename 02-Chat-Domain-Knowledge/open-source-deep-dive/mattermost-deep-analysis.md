# Mattermost 深度架构分析

> 来源：[mattermost/mattermost](https://github.com/mattermost/mattermost) ⭐ ~28k | MIT/AGPL | Go + React
> 官方文档：https://docs.mattermost.com
> 定位：企业级安全协作平台，面向国防、情报、安全和关键基础设施

---

## 1. 项目概览

Mattermost 是一个开源核心（open core）的企业协作平台，以**安全、自托管、可扩展**为核心特点。其架构设计强调模块化、高可用和深度可定制性，被美国国防部和财富 500 强企业采用。

### Monorepo 结构

```
mattermost/
├── server/           # Go 后端服务
├── webapp/           # React 前端
├── api/              # OpenAPI 规范 (v4 + playbooks)
├── e2e-tests/        # 端到端测试
└── tools/            # 开发工具
```

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端语言 | Go |
| 前端 | React + Redux + TypeScript |
| 数据库 | PostgreSQL（主）/ MySQL（兼容） |
| 文件存储 | 本地 / NAS / S3 |
| 实时通信 | WebSocket (WSS) |
| 集群通信 | Gossip 协议 |
| 构建 | Go toolchain + Make / Webpack 5 |
| 部署 | Docker / Kubernetes / Terraform |
| 监控 | Prometheus (端口 8067) |

### 版本策略

| 版本 | 协议 | 说明 |
|------|------|------|
| Team Edition | MIT | 开源核心功能 |
| Enterprise Edition | 商业 | 集群、SSO、合规、高级安全等，通过 build tag 条件编译 |

---

## 2. 架构设计

### 2.1 三层架构

```
┌─────────────────────────────────────────────────────────┐
│                    Access Layer (接入层)                   │
│  Web / Desktop / Mobile / Email                          │
│  (HTTPS + WSS, 通过 NGINX/HAProxy 负载均衡)               │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│                 Application Layer (应用层)                 │
│  Mattermost Server (单 Go 二进制)                         │
│  ┌──────────┬──────────┬──────────┬──────────────────┐  │
│  │ REST API │ WebSocket│ 认证授权  │ 通知服务          │  │
│  │  (api4)  │ (wsapi)  │          │ (Push + Email)   │  │
│  └──────────┴──────────┴──────────┴──────────────────┘  │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Business Logic (app)                  │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │           Plugin System (RPC 独立进程)             │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│              Backend Infrastructure (后端基础设施)        │
│  PostgreSQL/MySQL  │  文件存储  │  推送代理  │  LDAP/SSO │
└─────────────────────────────────────────────────────────┘
```

### 2.2 服务端分层架构 (Go)

```
server/
├── cmd/
│   ├── mattermost/        # 主服务入口
│   └── mmctl/             # CLI 管理工具
├── channels/              # 核心频道功能
│   ├── api4/              # REST API v4 处理器
│   ├── app/               # 业务逻辑层（与传输机制无关）
│   ├── store/             # 数据访问层
│   │   ├── sqlstore/      # SQL 实现
│   │   └── searchlayer/   # 搜索功能
│   ├── web/               # Web 处理器
│   └── wsapi/             # WebSocket API
├── platform/              # 平台服务
│   ├── services/          # 共享服务
│   └── shared/            # 共享工具
├── public/                # 公共 API 和模型
│   ├── model/             # 数据模型 (User, Team, Channel, Post...)
│   ├── plugin/            # 插件系统
│   └── pluginapi/         # 插件 API 辅助
├── enterprise/            # 企业版功能（build tag 条件编译）
└── build/                 # 构建脚本和配置
```

### 2.3 各层职责

| 层 | 目录 | 职责 |
|----|------|------|
| **API 层** | `channels/api4/` | RESTful API v4，HTTP 请求处理、输入校验、调用 app 层、返回 JSON |
| **应用层** | `channels/app/` | 业务逻辑和编排，与传输机制（HTTP/WebSocket）无关 |
| **存储层** | `channels/store/` | 数据访问抽象，支持 PostgreSQL，含迁移管理 |
| **WebSocket 层** | `channels/wsapi/` | 实时双向通信，实时更新、打字指示器、在线状态 |
| **模型层** | `public/model/` | 共享数据结构、配置模型、API 请求/响应、验证和序列化 |

### 2.4 设计特点

- **单二进制部署**：Go 静态编译，一个二进制包含所有服务端功能
- **传输无关的业务逻辑**：app 层不依赖 HTTP 或 WebSocket，可被任意传输层调用
- **企业版条件编译**：通过 Go build tag（`enterprise`, `sourceavailable`）实现功能开关
- **集群感知**：企业版支持多节点集群，Gossip 协议同步状态

---

## 3. 网络通信设计

### 3.1 双协议通信

| 协议 | 用途 | 特点 |
|------|------|------|
| **HTTPS** | 页面渲染、API 请求、文件上传 | 间歇性连接，请求-响应模式 |
| **WSS** | 实时更新、通知、打字指示器 | 持久连接，服务端推送 |

> **关键**：如果 WSS 不可用而只用 HTTPS，系统看似正常但实时更新失效，只能刷新页面获取新消息。

### 3.2 WebSocket 设计

**端点**：`/api/v4/websocket`

**服务端事件类型**：

| 事件 | 用途 |
|------|------|
| `posted` | 新消息发布 |
| `typing` | 用户打字指示器 |
| `user_updated` | 用户资料变更 |
| `channel_updated` | 频道元数据变更 |
| `status_change` | 用户在线状态变更 |
| 自定义事件 | 插件通过 `PublishWebSocketEvent` 发布 |

**事件作用域优化**（v11 起永久启用）：
- `typing` 和 `reaction` 事件**只发送给打开了对应频道/线程的客户端**
- 减少不必要的网络流量和客户端处理开销

**客户端重连**：
- 自动重连，指数退避
- Redux middleware 处理 WebSocket 事件，自动更新 store
- 网络中断后优雅恢复

### 3.3 服务端口

| 服务 | 默认端口 | 协议 | 方向 |
|------|---------|------|------|
| HTTP/WebSocket | 8065 (或 80/443 TLS) | TCP | 入站 |
| 集群 Gossip | 8074 | TCP/UDP | 入站（内部） |
| Metrics | 8067 | TCP | 入站 |
| PostgreSQL | 5432 | TCP | 出站 |
| MySQL | 3306 | TCP | 出站 |
| LDAP | 389 | TCP/UDP | 出站 |
| S3 存储 | 443 (TLS) | TCP | 出站 |
| SMTP | 10025 | TCP/UDP | 出站 |
| 推送通知 | 443 (TLS) | TCP | 出站 |

### 3.4 集群通信

- 企业版多节点集群通过 **Gossip 协议**（端口 8074）同步节点状态
- WebSocket 连接需要 **Sticky Session**（粘性会话），确保客户端始终连到同一节点
- 负载均衡器（NGINX/HAProxy）分发 HTTP 和 WebSocket 连接

---

## 4. 插件系统架构

Mattermost 的插件系统是其可扩展性的核心，分为**服务端插件**和**Webapp 插件**两类。

### 4.1 服务端插件

```
server/public/plugin/
├── api.go           # 插件 API 接口
├── hooks.go         # 生命周期钩子
├── client_rpc.go    # 插件 RPC 客户端
└── environment.go   # 插件环境管理
```

**特点**：
- 插件以**独立进程**运行，通过 **RPC** 与主服务通信
- Go 语言编写
- 插件从 `plugins/` 目录加载二进制

**插件生命周期**：
```
1. 从 plugins/ 目录加载插件二进制
2. OnActivate 钩子 → 初始化
3. 注册 hooks 和 HTTP handlers
4. 通过 RPC 与服务端通信
5. OnDeactivate 钩子 → 清理
```

**插件能力**：
- 钩子拦截服务端事件（消息发布、用户加入等）
- 注册自定义 API 端点
- 通过插件 API 访问 Mattermost 功能
- 发布自定义 WebSocket 事件

### 4.2 Webapp 插件

- JavaScript bundle，与 React 前端集成
- 注册点：自定义消息类型、频道头部按钮、根组件、斜杠命令、菜单项、Reducer 扩展

```javascript
// 插件入口
export default class Plugin {
  initialize(registry, store) {
    registry.registerPostTypeComponent('custom_type', CustomComponent);
  }
}
```

### 4.3 插件通信

```
服务端插件 ←RPC→ Mattermost Server ←WebSocket→ Webapp 插件
```

服务端插件可通过 `PublishWebSocketEvent` 向客户端推送自定义事件，Webapp 插件监听并处理。

### 4.4 官方插件示例

| 插件 | 功能 |
|------|------|
| Calls | 自托管语音通话 + 屏幕共享 |
| Playbooks | 工作流自动化（SOP） |
| Boards | Kanban 项目管理 |
| Agents | AI 代理集成（多 Agent/多 LLM） |
| Jira/GitLab | 第三方系统集成 |

---

## 5. 数据模型与存储

### 5.1 核心数据表

| 表 | 说明 |
|----|------|
| Users | 用户账户和凭证 |
| Teams | 团队（工作区） |
| Channels | 频道（公开/私密/私聊） |
| Posts | 消息（核心数据表） |
| ChannelMembers | 频道成员关系 |
| TeamMembers | 团队成员关系 |
| Reactions | 消息表情回应 |
| Status | 用户在线状态 |
| SidebarCategories | 侧边栏分类 |
| SidebarChannels | 侧边栏频道排序 |
| Configurations | 系统配置（含 SchemaVersion） |
| Compliance | 合规导出 |
| SharedChannels | 共享频道（跨实例） |

### 5.2 存储设计要点

- **PostgreSQL 为主**，MySQL 兼容（MySQL 用 text 类型，PostgreSQL 用 varchar，迁移时需注意）
- **Schema 版本**存储在 Configurations 表的 JSON 配置中（`SchemaVersion` 字段）
- 支持**在线 Schema 迁移**（如 v7.1 给 Reactions 表加列和索引，1200万 Posts + 250万 Reactions 约 1分34秒）
- 文件存储支持：本地文件系统 / NAS / S3（含 MinIO 兼容）

### 5.3 消息（Posts）存储

- 消息是核心数据，支持频道消息、线程回复、私信
- 消息内容支持 Markdown、附件、自定义类型
- 消息删除支持逻辑删除（删除时间字段）
- 企业版支持合规导出和数据保留策略

---

## 6. 前端架构 (React)

### 6.1 目录结构

```
webapp/
├── channels/              # 主 Web 应用
│   └── src/
│       ├── actions/       # Redux actions
│       ├── components/    # React 组件
│       ├── reducers/      # Redux reducers
│       ├── selectors/     # Redux selectors
│       ├── plugins/       # 插件集成
│       ├── sass/          # 全局样式
│       ├── utils/         # 工具函数
│       └── i18n/          # 国际化
└── platform/              # 共享平台包
    ├── types/             # TypeScript 类型定义
    ├── client/            # API 客户端库 (Client4)
    ├── components/        # 可复用 UI 组件
    ├── mattermost-redux/  # Redux 状态管理
    └── shared/            # 共享工具
```

### 6.2 状态管理

- **Redux** 集中式状态管理
- **规范化存储**：entities（users, channels, teams, posts）按 ID 存在查找表中，引用单独存储
- `Client4` 类提供类型化的 REST API 接口
- WebSocket 事件由 Redux middleware 自动处理并更新 store

### 6.3 组件模式

- 函数组件 + Hooks（现代方式）
- 样式与组件同目录（component.tsx + component.scss）
- 测试与组件同目录（component.test.tsx）
- 性能优化：`React.memo`、`useCallback`、`useMemo`

---

## 7. 请求数据流示例

以"发送消息"为例：

```
1. 用户点击发送 → React 组件 dispatch(createPost(post))
2. Redux action creator → Client4.createPost(post) → POST /api/v4/posts
3. 服务端 api4/post.go createPost() → 校验 → c.App.CreatePost()
4. app 层 → 权限校验 → 插件钩子 → Store().Post().Save()
5. store 层 → SQL INSERT 到 Posts 表
6. app 层 → PublishWebSocketEvent("posted", ...) → 广播给频道内在线客户端
7. 客户端 Redux middleware 接收 WebSocket 事件 → 更新 store → React 重渲染
```

---

## 8. 安全与高可用

### 8.1 安全

| 机制 | 说明 |
|------|------|
| **反向代理** | NGINX/硬件代理，强制 HTTPS，负载均衡 |
| **SSL/TLS** | 传输加密 |
| **权限模型** | 细粒度用户权限和角色 |
| **SSO** | SAML、LDAP/AD、OAuth2、MFA |
| **VPN 推荐** | 建议部署在私有网络/VPN 后 |
| **审计日志** | 企业版合规审计 |
| **数据保留** | 企业版消息保留策略 |

### 8.2 高可用（企业版）

- 多节点集群部署，自动故障转移
- 数据库主从复制（PostgreSQL 同步/异步复制）
- 负载均衡器分发流量
- WebSocket Sticky Session
- 推送通知重试机制
- 多 SMTP 服务器冗余

---

## 9. 设计原则与权衡

| 设计决策 | 选择 | 权衡 |
|---------|------|------|
| **单体二进制** | Go 单二进制包含所有功能 | 部署简单，但微服务拆分灵活性低 |
| **传输无关业务层** | app 层不依赖 HTTP/WS | 清晰分层，可扩展新传输协议 |
| **插件独立进程** | RPC 通信而非进程内调用 | 稳定性好（插件崩溃不影响主进程），但有 RPC 开销 |
| **PostgreSQL 为主** | 优先支持 PostgreSQL | 功能完整，但 MySQL 兼容性需维护 |
| **开源核心 + 企业版** | MIT 核心 + 商业企业功能 | 社区活跃 + 商业可持续 |
| **WebSocket 事件作用域** | 只发给相关客户端 | 减少流量，但增加服务端状态管理复杂度 |

---

## 10. 对 CBOL 项目的参考价值

### 10.1 架构层面

| Mattermost 设计 | CBOL 可借鉴 |
|----------------|------------|
| 传输无关的业务逻辑层（app 与 api4/wsapi 分离） | 业务逻辑不绑定 HTTP/WebSocket，便于扩展 |
| 插件系统（独立进程 + RPC） | 可扩展架构，功能模块化 |
| 单二进制部署 | 简化部署和运维 |
| 集群 Gossip 协议 | 多节点状态同步方案 |

### 10.2 网络通信层面

| Mattermost 设计 | CBOL 可借鉴 |
|----------------|------------|
| HTTPS + WSS 双协议 | REST API + WebSocket 分离 |
| WebSocket 事件作用域优化 | 只向相关客户端推送，减少流量 |
| 客户端指数退避重连 | 连接恢复机制 |
| Sticky Session | WebSocket 负载均衡策略 |

### 10.3 数据模型层面

| Mattermost 设计 | CBOL 可借鉴 |
|----------------|------------|
| 规范化 Redux 状态（entities 按 ID 存储） | 前端状态管理设计 |
| Schema 版本管理 + 在线迁移 | 数据库演进策略 |
| 逻辑删除（删除时间字段） | 数据保留和恢复 |

### 10.4 可扩展性层面

| Mattermost 设计 | CBOL 可借鉴 |
|----------------|------------|
| 插件钩子机制（消息发布、用户加入等事件） | 业务扩展点设计 |
| 自定义 WebSocket 事件 | 插件与前端实时通信 |
| OpenAPI 规范（api/ 目录） | API 文档化和客户端 SDK 生成 |

---

## 11. 参考资料

- GitHub: https://github.com/mattermost/mattermost
- 官方文档: https://docs.mattermost.com
- 应用架构: https://docs.mattermost.com/deployment-guide/application-architecture.html
- 系统架构（社区）: https://mintlify.wiki/mattermost/mattermost/dev/architecture
- API 文档: https://api.mattermost.com
- 插件开发: https://developers.mattermost.com/integrate/plugins/

---

*分析日期：2026-08-18*
