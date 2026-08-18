# Message Storage

## Storage Requirements

| Requirement | Description |
|-------------|-------------|
| High write throughput | Millions of messages/sec at scale |
| Range queries | Fetch messages by conversation + seq range |
| Time-ordered | Messages ordered by seq_id |
| Immutable | Message content rarely changes (edit = new version) |
| TTL / archiving | Old messages may expire or move to cold storage |

## Schema Design

### Message Table (MySQL / PostgreSQL)

```sql
CREATE TABLE messages (
    msg_id        VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    seq_id        BIGINT NOT NULL,
    sender_id     VARCHAR(64) NOT NULL,
    type          VARCHAR(32) NOT NULL,
    content       JSON NOT NULL,
    timestamp     BIGINT NOT NULL,
    status        VARCHAR(16) DEFAULT 'sent',
    edited_at     BIGINT NULL,
    deleted_at    BIGINT NULL,
    INDEX idx_conv_seq (conversation_id, seq_id),
    INDEX idx_sender (sender_id, timestamp)
);
```

### Sharding Strategy

**Shard by conversation_id** (recommended):
- All messages in one conversation on same shard
- Efficient range queries within conversation
- Hot conversation may overload single shard

**Alternative: Shard by (conversation_id, time bucket)**:
- Time-based partitioning (e.g., monthly tables)
- Prevents unbounded table growth
- Cross-time queries need aggregation

## Cassandra for Message History

For massive scale, Cassandra is preferred over relational DB:

```
CREATE TABLE messages (
    conversation_id text,
    seq_id bigint,
    msg_id text,
    sender_id text,
    type text,
    content text,
    timestamp bigint,
    PRIMARY KEY (conversation_id, seq_id)
) WITH CLUSTERING ORDER BY (seq_id DESC);
```

- Partition key: conversation_id (all messages in one conversation)
- Clustering key: seq_id (ordered retrieval)
- Linear write scalability
- Tunable consistency (ONE for writes, QUORUM for critical reads)

## Indexing Strategy

| Index | Purpose |
|-------|---------|
| (conversation_id, seq_id) | Primary message fetch |
| (conversation_id, timestamp) | Time-range queries |
| (sender_id, timestamp) | User message search |
| msg_id (global) | Deduplication, direct lookup |

## Media Storage

- Message metadata in DB, media binary in object storage (S3/MinIO)
- Generate thumbnail for images/videos
- CDN for delivery
- Signed URLs for private media

## Reference: Alibaba Cloud IM Storage
Timeline model: each message has seq_id, seq in rear > seq in front. Message database design based on timeline characteristics.

---

## Open Source Project References

### Turms: MongoDB Sharding & Index Design

Turms 以 MongoDB 为主要存储，其索引设计基于分布式数据库分片特性，有一套系统方法论：

**核心原则：索引设计必须考虑分片特性**

| 索引方案 | 查询模式 | 适用场景 |
|---------|---------|---------|
| `{recipient_id: 1, sending_time: -1}` | 按收件人+时间范围查询 | **默认推荐**，匹配读扩散的收件箱查询 |
| `{sender_id: 1, sending_time: -1}` | 按发件人查询 | 可选，用于发件箱/审计 |
| `{conversation_id: 1, sending_time: -1}` | 按会话查询 | 群组消息场景 |

**Turms 索引设计要点：**
1. **不用 Hashed 索引**：Hashed 索引不支持范围查询，而消息查询几乎都是时间范围查询
2. **基于分片键设计索引**：如果按 `recipient_id` 分片，则索引前缀必须包含 `recipient_id`，否则 scatter-gather 性能差
3. **可选索引默认关闭**：如 `sender_id` 索引只在需要发件箱查询时启用，避免写入开销
4. **冷热分离**：热数据（近 30 天）存高性能分片，冷数据归档到低成本存储
5. **消息不可变**：消息内容不更新，编辑=新消息+标记原消息，避免分布式事务

**MongoDB 分片键选择：**
```
推荐：{ recipient_id: 1, sending_time: -1 }
  - 按收件人分片，同一用户的消息在同一分片
  - 收件箱查询（读扩散）命中单分片
  - sending_time 作为分片键后缀，支持时间范围裁剪
```

### Mattermost: PostgreSQL Schema & Online Migration

Mattermost 以 PostgreSQL 为主，其 schema 演进策略值得参考：

**Schema 版本管理：**
- Schema 版本存储在 `Configurations` 表的 JSON 配置中（`SchemaVersion` 字段）
- 启动时检查版本，自动执行迁移脚本
- 支持**在线迁移**（不锁表），如 v7.1 给 Reactions 表加列+索引：
  - 1200万 Posts + 250万 Reactions，约 1分34秒（8核16GB）
  - 使用 `CREATE INDEX CONCURRENTLY` 避免锁表

**MySQL 兼容注意事项：**
- MySQL 用 `text` 类型，PostgreSQL 用 `varchar`，迁移时需检查长度
- MySQL 大小写不敏感，PostgreSQL 大小写敏感，webhook 频道名需统一小写
- 生产环境推荐 PostgreSQL，MySQL 仅兼容

### Rocket.Chat: MongoDB OpLog Tailing

Rocket.Chat 的实时更新核心机制是 **MongoDB OpLog 尾部跟踪**：

```
数据写入 MongoDB → OpLog 记录变更 → StreamHub 捕获 → 广播给订阅者 → DDPStreamer 推送给客户端
```

- 所有服务实例监听 MongoDB OpLog（需要副本集）
- 数据变更自动推送到订阅客户端，无需轮询
- StreamHub 是单实例（当前架构瓶颈），负责统一分发
- 这种模式将数据库作为消息总线，简化了架构但强依赖 MongoDB

### OpenChat: Canister Per User/Group

OpenChat 采用完全不同的存储范式——每个用户和群组一个独立 canister：
- 无中心化数据库，数据分布在数千个 canister 中
- 每个 canister 有独立的稳定内存（stable memory），升级时不丢数据
- 无限扩展：用户增长只需创建新 canister
- 但跨 canister 查询需要聚合，延迟较高

### Storage Technology Comparison

| 项目 | 主存储 | 实时机制 | 分片/扩展 |
|------|--------|---------|----------|
| Turms | MongoDB | 内存+推 | 按 recipient_id 分片 |
| Mattermost | PostgreSQL | WebSocket | 主从复制+集群 |
| Rocket.Chat | MongoDB | OpLog tailing | 副本集+微服务 |
| Matrix/Synapse | PostgreSQL | /sync 长轮询 | Worker+Replication |
| Chat21 | RabbitMQ+MongoDB | MQTT | Observer 水平扩展 |
| OpenChat | ICP Canister | 轮询+更新 | 每用户/群组独立 canister |
