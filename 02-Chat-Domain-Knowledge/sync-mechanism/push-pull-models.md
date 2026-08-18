# Push-Pull Models

## Model Comparison

| Model | Latency | Server Load | Complexity | Offline Support |
|-------|---------|-------------|------------|-----------------|
| Push-only | Low | High | Medium | Poor |
| Pull-only | High | Low | Low | Good (on poll) |
| Push-Pull (hybrid) | Low | Medium | High | Excellent |

## Push Model

### How it works
- Server maintains active connections (WebSocket / TCP)
- On new message: server immediately pushes to connected clients
- Session Registry maps user_id -> active connections

### Components
```
Message In -> Router -> Session Registry -> [Device A conn] -> push
                                      -> [Device B conn] -> push
```

### Pros
- Real-time (sub-100ms)
- Efficient for active users

### Cons
- Connection management complexity
- Offline users need separate mechanism
- Connection stateful (harder to scale horizontally)

## Pull Model

### How it works
- Client polls server at intervals (e.g., every 30s)
- Or client uses long-polling (request hangs until data available)

### Variants
1. **Short polling**: periodic GET requests
2. **Long polling**: request hangs until new data or timeout
3. **Incremental sync**: pull only changes since last cursor

### Pros
- Stateless server (easy horizontal scaling)
- Works through firewalls/proxies
- Simple implementation

### Cons
- Latency (poll interval)
- Wasted requests (empty polls)
- Battery impact on mobile

## Hybrid Push-Pull Model (Recommended)

```
Online state:
  New message -> Server pushes via WebSocket -> Client receives instantly

Reconnect / offline:
  Client connects -> sends cursor -> Server pulls missed messages -> Client catches up

Fallback:
  If WebSocket fails -> Client falls back to long-polling
```

### Turms IM Three Models
Turms (open-source IM engine) explicitly supports:
1. **Push model**: server notifies clients of changes
2. **Pull model**: clients query on demand
3. **Push-pull model**: push notification + pull full data

Used for business data change awareness (messages, friend requests, group updates).

## When to Use Which

| Scenario | Recommended Model |
|----------|-------------------|
| Real-time chat | Push-Pull hybrid |
| Low-activity notification center | Pull (long polling) |
| IoT sensor data | Push |
| Email-like async messaging | Pull |
| Mixed workload | Push-Pull hybrid |

---

## Open Source Project Sync Mechanisms

### Matrix: /sync 增量长轮询

Matrix 的客户端同步机制是**基于 token 的增量长轮询**，设计精巧且兼容各种网络环境。

**核心流程：**

```
1. 首次同步：GET /_matrix/client/v3/sync (无 since 参数)
   → 返回全量状态 + next_batch token

2. 增量同步：GET /_matrix/client/v3/sync?since={token}&timeout=30000
   → 服务器挂起连接，直到有新事件或超时(30s)
   → 返回增量更新 + 新的 next_batch token

3. 重复步骤 2，实现"准实时"同步
```

**/sync 响应结构：**
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

**关键设计点：**
1. **Token 机制**：`since` token 标记增量起点，服务端不需要维护客户端会话状态
2. **长轮询**：`timeout` 参数让服务器挂起连接，有新事件立即返回，无事件则超时返回空
3. **分类返回**：timeline（消息）、state（房间状态）、ephemeral（打字/已读）分离
4. **过滤器**：客户端可指定 filter 参数，只接收关心的房间/事件类型
5. **无状态服务端**：服务端不需要维护 WebSocket 连接，水平扩展简单

**为什么 Matrix 不用 WebSocket？**
- 长轮询兼容性更好（穿透所有防火墙/代理）
- 服务端无状态，更容易水平扩展
- 联邦架构下，跨服务器推送复杂，拉模式更简单
- 代价是延迟略高（每次重连有 TCP/TLS 握手开销）

### Rocket.Chat: DDP 订阅-推送模式

Rocket.Chat 使用 Meteor 的 **DDP（Distributed Data Protocol）** over WebSocket：

**DDP 核心操作：**
```
客户端 → 服务端: subscribe("stream-room-messages", roomId)
服务端 → 客户端: added / changed / removed (增量数据)
客户端 → 服务端: method("sendMessage", message)
服务端 → 客户端: result (方法调用结果)
```

**DDP 与 REST 的区别：**

| 维度 | REST + 轮询 | DDP over WebSocket |
|------|------------|-------------------|
| 连接 | 短连接，每次请求新建 | 持久连接 |
| 数据获取 | 客户端主动拉 | 服务端主动推 |
| 增量 | 全量或手动增量 | 自动增量（added/changed/removed） |
| 延迟 | 轮询间隔 | 实时 |
| 服务端状态 | 无状态 | 维护订阅状态 |

**MongoDB OpLog 驱动的实时性：**
```
数据写入 MongoDB → OpLog 记录 → StreamHub 捕获 → DDPStreamer → WebSocket 推送
```
- DDP 的实时性底层依赖 MongoDB OpLog 尾部跟踪
- 任何数据变更自动触发客户端更新，无需业务代码手动推送

### Mattermost: WebSocket 事件 + REST 拉取

Mattermost 采用 **WebSocket 推送 + REST 拉取**的混合模式：

**WebSocket 事件类型：**
- `posted` - 新消息
- `typing` - 打字指示器
- `user_updated` - 用户资料变更
- `channel_updated` - 频道元数据变更
- `status_change` - 在线状态变更

**事件作用域优化（v11 永久启用）：**
- `typing` 和 `reaction` 事件只发给**打开了对应频道/线程**的客户端
- 不是所有频道成员都收到，减少不必要的流量

**同步流程：**
```
1. 客户端 REST API 获取历史消息（分页拉取）
2. WebSocket 接收实时事件
3. 收到 posted 事件 → 增量更新本地状态
4. 重连后 → REST 拉取断开期间的消息（按 last 时间）
```

### Chat21: MQTT 主题订阅

Chat21 使用 **MQTT 协议**实现实时通信：

**订阅模型：**
```
客户端订阅自己的 inbox 主题:
  /apps/{appId}/users/{userId}/+/messages/clientadded

收到消息时，从主题路径解析发送者和消息类型
```

- MQTT 是轻量级发布/订阅协议，适合移动设备
- QoS 1（至少一次）保证消息不丢
- Last Will 消息处理异常断开
- 保留消息（Retained）支持新订阅者获取最新状态

### 同步机制对比

| 项目 | 机制 | 传输 | 服务端状态 | 延迟 | 扩展性 |
|------|------|------|-----------|------|--------|
| Turms | Push 通知 + Pull 内容 | TCP/WebSocket | 有（会话） | 极低 | 高（无状态网关） |
| Mattermost | WebSocket 事件 + REST | WebSocket + HTTP | 有（WS连接） | 低 | 中（需粘性会话） |
| Rocket.Chat | DDP 订阅 | WebSocket | 有（订阅状态） | 低 | 中（DDPStreamer 可扩展） |
| Matrix | /sync 长轮询 | HTTP 长轮询 | **无** | 中 | **高**（无状态） |
| Chat21 | MQTT 主题订阅 | MQTT | 有（订阅） | 低 | 中（RabbitMQ 为中心） |
| OpenChat | 轮询 + 更新 | HTTP 轮询 | 无 | 高 | 高（canister 并行） |

### CBOL 项目同步策略建议

基于 CBOL 的接回话/回话管理/回话转发场景：

1. **推荐 WebSocket 推送 + REST 拉取**（Mattermost 模式）
   - 实时消息通过 WebSocket 推送
   - 历史消息、会话列表通过 REST 分页拉取
   - 重连后按 last_seq 增量拉取

2. **考虑事件作用域优化**
   - 打字指示器只推送给查看该会话的用户
   - 减少大群的不必要推送

3. **如果需要极高扩展性**
   - 参考 Matrix 的无状态长轮询模式
   - 服务端不维护 WebSocket 连接，水平扩展更简单
   - 代价是延迟略高

4. **如果是移动优先**
   - 参考 Chat21 的 MQTT 模式
   - MQTT 更省电、更适合弱网
   - 但需要维护 MQTT broker（如 RabbitMQ+MQTT 插件或 EMQX）
