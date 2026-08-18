# Fanout Pattern

## What is Fanout?

Fanout is the process of replicating a single message to multiple recipients. In IM, every group message is a fanout operation.

```
                    [Message]
                        |
          ┌─────────────┼─────────────┐
          v             v             v
      Recipient 1   Recipient 2   Recipient N
```

## Two Fanout Directions

### Write Fanout (Fan-out on Write)

**On message send:**
1. Persist message
2. Write copy to EACH recipient's inbox / timeline
3. Push to online recipients

```
Message -> Message Store
          -> User A inbox (write)
          -> User B inbox (write)
          -> User C inbox (write)
          -> ...
```

**Complexity:** O(N) writes per message (N = recipients)

**Read:** O(1) - just read your inbox

**Best for:** Small groups (< 100), 1-on-1 chat, read-heavy

### Read Fanout (Fan-out on Read)

**On message send:**
1. Persist message ONCE to conversation store
2. No per-recipient write

**On read:**
1. User opens conversation
2. Query messages from conversation store
3. Merge with other conversations for inbox view

```
Message -> Conversation Store (write once)

User reads -> Query conversation -> Get messages
```

**Complexity:** O(1) write, O(N) read (N = conversations)

**Best for:** Large groups, channels, write-heavy

## Hybrid Fanout (Recommended)

Use different strategy based on group size:

| Group Size | Strategy | Reason |
|-----------|----------|--------|
| 1-on-1 (2) | Write fanout | Only 2 writes, fast reads |
| Small (< 100) | Write fanout | 100 writes acceptable |
| Medium (100-1000) | Write fanout with batching | Batch inbox writes |
| Large (> 1000) | Read fanout | 1000+ writes too expensive |
| Channel (10K+) | Read fanout + push notification | Store once, notify, pull on open |

## Write Fanout Implementation

### Inbox Table
```sql
CREATE TABLE user_inbox (
    user_id VARCHAR(64),
    seq_id BIGINT,
    msg_id VARCHAR(64),
    conversation_id VARCHAR(64),
    sender_id VARCHAR(64),
    preview TEXT,
    created_at BIGINT,
    PRIMARY KEY (user_id, seq_id)
);
```

### Batch Write
```
For group message to N members:
1. Get member list
2. Batch INSERT into user_inbox (multi-row insert)
3. Push to online members
```

### Optimizations
- Batch database inserts (multi-row INSERT)
- Async inbox writes (message bus)
- Skip inbox write for users who left
- Deduplicate if user gets message from multiple paths

## Read Fanout Implementation

### Conversation Message Store
```sql
CREATE TABLE conversation_messages (
    conversation_id VARCHAR(64),
    seq_id BIGINT,
    msg_id VARCHAR(64),
    sender_id VARCHAR(64),
    content JSON,
    timestamp BIGINT,
    PRIMARY KEY (conversation_id, seq_id)
);
```

### Inbox Aggregation (on read)
```
1. Get user's conversation list
2. For each conversation: get last message (MAX seq_id)
3. Sort conversations by last message time
4. Compute unread counts
```

### Optimizations
- Cache conversation list with last message preview
- Pre-compute unread counts (increment on message, decrement on read)
- Use materialized views for inbox

## Push Fanout

Even with read fanout for storage, online delivery needs push fanout:

```
Message -> Online members: push via WebSocket
        -> Offline members: push notification (APNs/FCM)
```

For very large groups:
- Don't push message content to all online users
- Push "new message" notification only
- Clients pull actual message when they open the conversation

## Reference: WeChat
WeChat uses write-fanout for small groups and 1-on-1, read-fanout for official accounts and large groups. The threshold is around 100-500 members.

---

## Open Source Project Deep Dive

### Turms: Read Fanout Architecture (生产级实现)

Turms 是读扩散（fanout read）的典型生产级实现，其设计哲学是**极简、反对过度设计**。

**Turms 读扩散核心流程：**

```
1. 发送者发送消息 → 写入 Message 集合（仅一次）
2. 消息索引为 {recipient_id, sending_time}
3. 接收者拉取收件箱 → 查询 Message 集合 where recipient_id = ? order by sending_time desc
4. 不需要为每个接收者写一份副本
```

**Turms 为什么选择读扩散？**

| 因素 | 读扩散优势 |
|------|-----------|
| 大群场景 | 1000人群发消息只写1次，写扩散要写1000次 |
| 存储成本 | 消息只存一份，无冗余 |
| 一致性 | 单条消息记录，不存在多副本不一致 |
| 实现复杂度 | 不需要维护收件箱表，不需要处理成员变更时的历史消息同步 |

**Turms 读扩散的关键优化：**
1. **索引即收件箱**：`{recipient_id, sending_time}` 索引直接充当收件箱，无需额外 inbox 表
2. **按收件人分片**：MongoDB 按 `recipient_id` 分片，收件箱查询命中单分片
3. **推送通知而非推送内容**：大群只推送"有新消息"通知，客户端打开会话时拉取实际内容
4. **会话未读数**：Redis 维护每个用户每个会话的未读数，增量更新

**Turms 的 push-pull 模型：**
- **Push**：服务端通知客户端"有新消息"（轻量通知）
- **Pull**：客户端根据通知拉取实际消息内容（按需拉取）
- 这种混合模式避免了大群推送全量消息的带宽浪费

### Chat21: Inbox Pattern (类 SMTP/POP3)

Chat21 提出了一种独特的 Inbox 模式，灵感来自电子邮件协议：

**核心思想：消息不直接 P2P 传递，通过 Observer 中转到接收者的 Inbox**

```
发件人 → MQTT publish 到自己的 /outgoing 路径
            ↓
Observer (Chat21 Server) 订阅所有 /outgoing
            ↓
Observer 通过 AMQP publish 到收件人的 /clientadded 路径
            ↓
收件人 MQTT subscribe 自己的 inbox 路径，收到消息
```

**MQTT 路径设计：**
```
发件人写出: /apps/{appId}/users/{senderId}/{recipientId}/messages/outgoing
收件人接收: /apps/{appId}/users/{recipientId}/{senderId}/messages/clientadded
```

**Inbox 模式的优势：**
1. **安全策略点**：Observer 可在中转时实施拉黑、过滤、内容审计
2. **细粒度权限**：RabbitMQ JWT Token 限制用户只能读写自己的路径，无法触碰他人 inbox
3. **持久化**：Observer 可在转发前持久化消息
4. **离线支持**：消息写入 RabbitMQ 持久化队列，上线后自动接收
5. **解耦**：发件人不需要知道收件人是否在线

**与传统 fanout 的区别：**

| 维度 | 传统写扩散 | Chat21 Inbox |
|------|-----------|-------------|
| 写入位置 | 直接写接收者 inbox | 写自己 outgoing，Observer 转发 |
| 安全策略 | 无集中策略点 | Observer 是天然策略点 |
| 权限模型 | 应用层控制 | MQTT topic 级 JWT 控制 |
| 适用规模 | 中小规模 | 中小规模（RabbitMQ 为中心） |

### Mattermost: WebSocket Event Scoping (推送优化)

Mattermost 在推送层面做了精细的事件作用域优化：
- `typing` 和 `reaction` 事件**只发送给打开了对应频道/线程的客户端**
- 不是所有频道成员都收到打字指示器，只有正在查看该频道的用户
- 大幅减少不必要的网络流量和客户端处理开销
- v11 起此优化从 feature flag 变为永久启用

### 各项目 Fanout 策略对比

| 项目 | 存储模型 | 在线推送 | 大群策略 |
|------|---------|---------|---------|
| Turms | 读扩散（索引即收件箱） | Push 通知 + Pull 内容 | 读扩散天然支持 |
| Mattermost | 写扩散（PostgreSQL） | WebSocket 全量推送 | 事件作用域优化 |
| Rocket.Chat | 写扩散（MongoDB） | DDP 订阅推送 | OpLog 广播 |
| Matrix | 全量复制（每 homeserver 一份） | /sync 增量拉取 | 联邦复制 |
| Chat21 | Inbox 模式（Observer 转发） | MQTT 推送 | 逐个转发 |
| WeChat | 混合（小群写扩散/大群读扩散） | 推送+拉取 | 100-500 人阈值切换 |
