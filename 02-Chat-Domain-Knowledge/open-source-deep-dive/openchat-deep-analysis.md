# OpenChat 深度架构分析

> 来源：[open-chat-labs/open-chat](https://github.com/open-chat-labs/open-chat) ⭐ ~0.2k | AGPL-3.0 | Rust + Svelte/TypeScript
> 官网：https://oc.app
> 定位：完全运行在 Internet Computer 区块链上的去中心化即时通讯

---

## 1. 项目概览

OpenChat 是一个**完全上链**的即时通讯应用，运行在 Internet Computer (ICP) 区块链上。与传统中心化聊天平台不同，OpenChat 没有中心化服务器——所有数据、逻辑、甚至前端都由区块链上的 canister 智能合约提供。

### 核心特性

| 特性 | 说明 |
|------|------|
| **完全上链** | 所有消息、群组、用户资料存储在链上，无法被管理员删除或关闭 |
| **无运营商** | 由 SNS DAO 社区治理，无 CEO/董事会 |
| **无密码** | Internet Identity（生物识别/安全密钥），跨设备一致 |
| **内置加密支付** | 原生支持 BTC/ETH/ICP 托管和聊天内转账 |
| **端到端加密** | 私密对话加密 |
| **可验证构建** | deterministic builds，可验证链上运行的代码 |
| **Bots 平台** | 提供 SDK 构建机器人 |

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Rust（编译为 WebAssembly，运行在 canister 中） |
| 前端 | Svelte + TypeScript（也由 canister 提供） |
| 平台 | Internet Computer (ICP) |
| 构建工具 | DFX 0.31+、Cargo、Vite |
| 治理 | SNS DAO |
| 移动端 | Kotlin (Android) |

### 仓库结构

```
open-chat/
├── architecture/       # 架构文档
├── backend/            # Rust canister 代码（核心）
├── frontend/           # Svelte 前端
├── scripts/            # 部署/构建/升级脚本
├── sns/                # SNS DAO 治理配置
├── dfx.json            # DFX 项目配置
├── canister_ids.json   # 各 canister ID
├── Cargo.toml          # Rust 工作区
├── rust-toolchain.toml # Rust 版本（1.95.0）
└── upgrade_order.md    # Canister 升级顺序
```

---

## 2. Canister 架构（核心设计）

### 2.1 什么是 Canister？

Canister 是 Internet Computer 上的计算单元，封装了**代码（WebAssembly）+ 状态（稳定内存）**，类似智能合约但能处理 HTTP 请求、存储大量数据、并行执行。

### 2.2 OpenChat 的 Canister 拓扑

```
┌─────────────────────────────────────────────────────────────┐
│                    Internet Computer (ICP)                   │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────┐               │
│  │  user_index       │    │  group_index      │               │
│  │  (用户注册表)      │    │  (群组注册表)      │               │
│  │  - 创建用户canister│    │  - 创建群组canister│               │
│  │  - 升级用户canister│    │  - 升级群组canister│               │
│  └────────┬─────────┘    └────────┬─────────┘               │
│           │                       │                          │
│     ┌─────┴─────┐           ┌─────┴─────┐                   │
│     ▼           ▼           ▼           ▼                   │
│  ┌──────┐   ┌──────┐    ┌──────┐   ┌──────┐                │
│  │User A│   │User B│    │Group1│   │Group2│   ...           │
│  │canister│ │canister│  │canister│ │canister│               │
│  └──────┘   └──────┘    └──────┘   └──────┘                │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────┐               │
│  │  storage_index    │    │  notifications    │               │
│  │  (媒体存储索引)    │    │  (推送通知)        │               │
│  └──────────────────┘    └──────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 各类 Canister 职责

| Canister 类型 | 职责 | 数量 |
|--------------|------|------|
| **user_index** | 用户注册表，创建/升级用户 canister | 1 |
| **user** | 单个用户的个人数据、会话列表、消息 | 每个用户 1 个 |
| **group_index** | 群组注册表，创建/升级群组 canister | 1 |
| **group** | 单个群组的消息、成员、设置 | 每个群组 1 个 |
| **storage_index** | 媒体文件存储索引 | 1 |
| **storage** | 实际文件存储（含 evidence vault 合规功能） | 多个 |
| **notifications** | 推送通知服务 | 1 |
| **OpenChatInstaller** | 安装引导 | 1 |

### 2.4 为什么每个用户/群组一个 Canister？

| 优势 | 说明 |
|------|------|
| **无限扩展** | 用户/群组增长只需创建新 canister，ICP 自动分配节点 |
| **数据隔离** | 用户数据完全在自己的 canister 中，隐私性强 |
| **并行执行** | 不同 canister 并行运行，无锁竞争 |
| **独立升级** | 可逐个升级用户 canister，不影响全局 |
| **防审查** | 没有中心化数据库可被整体删除 |

---

## 3. Canister 升级机制

### 3.1 升级流程

OpenChat 有数千个用户 canister 和群组 canister，升级需要精心设计：

```
1. 社区通过 SNS DAO 投票批准新版本
2. 新 wasm 推送到 user_index canister
3. user_index 将 wasm 存入 Rust struct（升级时序列化到 stable memory）
4. user_index 通过 heartbeat 机制逐个升级用户 canister
5. group_index 同理升级群组 canister
6. 每个 canister 升级时：
   - 数据从 heap 序列化到 stable memory
   - 替换 wasm 代码
   - 从 stable memory 反序列化数据回 heap
```

### 3.2 Stable Memory 的作用

- ICP canister 升级时，heap 内存会被清空
- **stable memory** 在升级间持久化
- OpenChat 将关键数据序列化到 stable memory，保证升级不丢数据
- `upgrade_order.md` 规定了 canister 的升级顺序，避免依赖问题

### 3.3 可验证构建

- 使用 Docker 进行 deterministic builds
- 本地构建的 wasm hash 与链上暴露的 hash 对比
- 社区可验证运行的代码就是审核通过的代码

---

## 4. 消息传递模型

### 4.1 直连消息（1:1）

```
用户 A 发送消息给用户 B：
1. A 的客户端调用 A 的 user canister 的 send_message 方法
2. A 的 user canister 将消息存入自己的历史
3. A 的 user canister 通过 inter-canister call 通知 B 的 user canister
4. B 的 user canister 接收消息，存入自己的历史
5. B 的客户端通过轮询/更新接口获取新消息
```

### 4.2 群组消息

```
用户 A 在群组发送消息：
1. A 的客户端调用 group canister 的 send_message 方法
2. group canister 验证权限，存入消息历史
3. group canister 通知所有成员的 user canister（有新消息）
4. 成员客户端从 group canister 拉取新消息
```

### 4.3 与传统架构的对比

| 维度 | 传统 IM（如 Turms） | OpenChat（ICP） |
|------|-------------------|----------------|
| 消息存储 | 中心化数据库（MongoDB/MySQL） | 每个用户/群组的 canister 状态 |
| 消息推送 | WebSocket 长连接 | 客户端轮询 + canister 更新 |
| 扩展性 | 分库分表、连接池 | 自动创建新 canister |
| 一致性 | 强一致（中心化） | 最终一致（跨 canister 调用） |
| 延迟 | 极低（内网） | 较高（区块链共识） |

---

## 5. 治理模型（SNS DAO）

### 5.1 SNS 是什么？

SNS（Service Nervous System）是 ICP 上的 DAO 框架，OpenChat 由 SNS 完全控制：

| 角色 | 传统公司 | OpenChat SNS |
|------|---------|-------------|
| 决策 | CEO/董事会 | 代币持有者投票 |
| 升级 | 工程团队发布 | 社区投票批准后自动升级 |
| 资金 | 公司预算 | SNS 金库（社区投票使用） |
| 所有权 | 股东 | CHAT 代币持有者 |

### 5.2 治理流程

```
1. 任何人提交提案（升级/参数变更/资金使用）
2. CHAT 代币持有者质押并投票
3. 投票通过后，SNS 自动执行
4. canister 升级按预定顺序执行
```

---

## 6. 安全与隐私

### 6.1 链上安全特性

| 特性 | 说明 |
|------|------|
| **防篡改** | 数据存在区块链上，不可单方修改 |
| **抗 DDoS** | ICP 网络分布式，无单点攻击目标 |
| **无服务器入侵** | 没有传统服务器可被入侵 |
| **端到端加密** | 私密对话内容加密 |
| **Internet Identity** | 无密码，生物识别/安全密钥，防跨站追踪 |

### 6.2 Evidence Vault（合规功能）

OpenChat 在 storage canister 中实现了 evidence vault：
- 捕获元数据（报告索引、聊天/消息/发送者、检测时间、分类器类别）
- 保留时钟（定时任务到期删除，除非设置 legal hold）
- Legal holds（法律保留，阻止删除）
- 销毁记录（请求引用日志）
- 哈希去重（同一文件哈希的所有别名一起处理）

---

## 7. 设计原则与权衡

| 设计决策 | 选择 | 权衡 |
|---------|------|------|
| **完全上链** | 所有数据和逻辑在 canister 中 | 无中心化风险，但延迟较高、成本（cycles）持续 |
| **每用户/群组一个 canister** | 细粒度数据隔离 | 无限扩展，但 canister 管理和升级复杂 |
| **SNS DAO 治理** | 社区自治 | 无单点控制，但决策慢、升级需投票 |
| **Rust + Wasm** | 内存安全 + 可验证 | 开发门槛高，工具链复杂 |
| **客户端轮询** | 而非 WebSocket 长连接 | ICP 不支持长连接，延迟较高 |
| **AGPL-3.0** | 强 Copyleft | 衍生作品必须开源，保护社区 |

---

## 8. 对 CBOL 项目的参考价值

### 8.1 架构思想层面

| OpenChat 设计 | CBOL 可借鉴 |
|--------------|------------|
| **每用户独立数据空间** | 用户数据隔离和隐私保护的思路 |
| **Index + 实体 canister 模式** | 注册表 + 实体实例的扩展模式（类似微服务中的 registry pattern） |
| **逐个滚动升级** | 大量实例的滚动升级策略（heartbeat 驱动） |
| **stable memory 持久化** | 升级时数据不丢失的设计模式 |

### 8.2 治理层面

| OpenChat 设计 | CBOL 可借鉴 |
|--------------|------------|
| SNS DAO 提案-投票-执行 | 如果需要社区治理，可参考其流程 |
| 可验证构建 | 供应链安全，确保运行的代码就是审核的代码 |

### 8.3 安全层面

| OpenChat 设计 | CBOL 可借鉴 |
|--------------|------------|
| Internet Identity 无密码 | 生物识别/安全密钥的认证方式 |
| Evidence Vault | 合规场景的消息保留和法律持有 |
| 哈希去重存储 | 文件存储优化 |

> **注意**：OpenChat 的完全上链架构不适合大多数企业 IM 场景（延迟、成本、监管合规）。但其**数据隔离、滚动升级、可验证构建**等思想有借鉴价值。CBOL 项目如果是中心化部署，不需要复制其区块链架构。

---

## 9. 参考资料

- GitHub: https://github.com/open-chat-labs/open-chat
- 官网: https://oc.app
- ICP 生态介绍: https://internetcomputer.org/ecosystem-spotlight/open-chat/
- Event Store (子项目): https://github.com/open-chat-labs/event-store
- Bots SDK: https://github.com/open-chat-labs/open-chat-bots
- Internet Computer: https://internetcomputer.org
- SNS 文档: https://internetcomputer.org/docs/current/developer-docs/integrations/sns/

---

*分析日期：2026-08-18*
