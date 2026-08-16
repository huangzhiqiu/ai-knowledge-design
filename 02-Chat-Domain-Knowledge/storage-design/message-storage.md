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
