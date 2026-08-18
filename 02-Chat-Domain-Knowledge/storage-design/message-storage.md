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

Turms uses MongoDB as its primary storage, with a systematic methodology for index design based on distributed database sharding characteristics:

**Core principle: Index design must consider sharding characteristics**

| Index Scheme | Query Pattern | Use Case |
|-------------|---------------|----------|
| `{recipient_id: 1, sending_time: -1}` | Query by recipient + time range | **Default recommended**, matches read-fanout inbox queries |
| `{sender_id: 1, sending_time: -1}` | Query by sender | Optional, for outbox/audit |
| `{conversation_id: 1, sending_time: -1}` | Query by conversation | Group message scenarios |

**Turms index design points:**
1. **No Hashed indexes**: Hashed indexes don't support range queries, and message queries are almost always time-range queries
2. **Design indexes based on shard key**: If sharding by `recipient_id`, index prefix must include `recipient_id`, otherwise scatter-gather performance is poor
3. **Optional indexes disabled by default**: e.g., `sender_id` index only enabled when outbox queries are needed, to avoid write overhead
4. **Hot/cold separation**: Hot data (last 30 days) on high-performance shards, cold data archived to low-cost storage
5. **Immutable messages**: Message content is never updated; editing = new message + flag original, avoiding distributed transactions

**MongoDB shard key selection:**
```
Recommended: { recipient_id: 1, sending_time: -1 }
  - Shard by recipient, same user's messages on same shard
  - Inbox queries (read fanout) hit a single shard
  - sending_time as shard key suffix supports time-range pruning
```

### Mattermost: PostgreSQL Schema & Online Migration

Mattermost uses PostgreSQL as primary, with a noteworthy schema evolution strategy:

**Schema version management:**
- Schema version stored in `Configurations` table's JSON config (`SchemaVersion` field)
- On startup, check version and auto-run migration scripts
- Supports **online migration** (no table lock), e.g., v7.1 adding column+index to Reactions table:
  - 12M Posts + 2.5M Reactions, ~1min 34sec (8-core 16GB)
  - Uses `CREATE INDEX CONCURRENTLY` to avoid locking

**MySQL compatibility notes:**
- MySQL uses `text` type, PostgreSQL uses `varchar`, check lengths during migration
- MySQL is case-insensitive, PostgreSQL is case-sensitive; webhook channel names must be lowercase
- Production recommends PostgreSQL, MySQL only for compatibility

### Rocket.Chat: MongoDB OpLog Tailing

Rocket.Chat's real-time update core mechanism is **MongoDB OpLog tailing**:

```
Data write to MongoDB -> OpLog records change -> StreamHub captures -> broadcast to subscribers -> DDPStreamer pushes to clients
```

- All service instances listen to MongoDB OpLog (requires replica set)
- Data changes automatically pushed to subscribed clients, no polling needed
- StreamHub is single-instance (current architecture bottleneck), responsible for unified distribution
- This pattern uses the database as a message bus, simplifying architecture but strongly dependent on MongoDB

### OpenChat: Canister Per User/Group

OpenChat adopts a completely different storage paradigm - one independent canister per user and group:
- No centralized database, data distributed across thousands of canisters
- Each canister has independent stable memory, no data loss on upgrade
- Infinite scaling: user growth simply creates new canisters
- But cross-canister queries require aggregation, higher latency

### Storage Technology Comparison

| Project | Primary Storage | Real-time Mechanism | Sharding/Scaling |
|---------|----------------|--------------------|-----------------|
| Turms | MongoDB | In-memory + push | Shard by recipient_id |
| Mattermost | PostgreSQL | WebSocket | Master-slave replication + cluster |
| Rocket.Chat | MongoDB | OpLog tailing | Replica set + microservices |
| Matrix/Synapse | PostgreSQL | /sync long polling | Worker + Replication |
| Chat21 | RabbitMQ+MongoDB | MQTT | Observer horizontal scaling |
| OpenChat | ICP Canister | Poll + update | Per user/group independent canister |
