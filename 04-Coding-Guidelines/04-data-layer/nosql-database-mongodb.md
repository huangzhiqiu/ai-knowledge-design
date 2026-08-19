# NoSQL Database Guidelines (MongoDB)

> Best practices for MongoDB 6.x development, schema design, and indexing. Reference: Turms MongoDB sharding design.

## Schema Design

### Document Structure

```javascript
// ✅ Good - embedded documents for one-to-few relationships
db.conversations.insertOne({
    _id: ObjectId("..."),
    userId: NumberLong(12345),
    type: "DIRECT",
    status: "ACTIVE",
    participants: [
        { userId: NumberLong(12345), joinedAt: ISODate("2026-08-01T00:00:00Z") },
        { userId: NumberLong(67890), joinedAt: ISODate("2026-08-01T00:00:00Z") }
    ],
    lastMessage: {
        messageId: NumberLong(999),
        content: "Hello",
        senderId: NumberLong(12345),
        createdAt: ISODate("2026-08-19T10:30:00Z")
    },
    unreadCount: 2,
    createdAt: ISODate("2026-08-01T00:00:00Z"),
    updatedAt: ISODate("2026-08-19T10:30:00Z")
});

// ✅ Good - separate collection for one-to-many (messages)
db.messages.insertOne({
    _id: ObjectId("..."),
    messageId: NumberLong(999),
    conversationId: ObjectId("..."),
    senderId: NumberLong(12345),
    recipientId: NumberLong(67890),  // for read diffusion (Turms pattern)
    content: "Hello",
    contentType: "TEXT",
    status: "DELIVERED",
    seqId: NumberLong(42),
    createdAt: ISODate("2026-08-19T10:30:00Z"),
    updatedAt: ISODate("2026-08-19T10:30:01Z")
});

// ❌ Bad - embedding unbounded arrays
db.conversations.insertOne({
    _id: ObjectId("..."),
    messages: [  // can grow to millions! 16MB document limit
        { messageId: 1, content: "..." },
        { messageId: 2, content: "..." }
        // ... millions more
    ]
});
```

### Data Type Best Practices

| Data | Recommended Type | Avoid | Reason |
|------|-----------------|-------|--------|
| ID (custom) | `NumberLong(64-bit)` or `ObjectId` | `Number` (32-bit float) | 64-bit integer, no precision loss |
| Timestamp | `ISODate()` | `NumberLong(epoch_ms)` | Native date type, queryable, indexable |
| Boolean | `true`/`false` | `"true"`/`"false"` | Native boolean, smaller, indexable |
| Enum | String (`"SENT"`) | Integer (`1`) | Readable, no lookup table |
| Money | `NumberDecimal("10.50")` | `Number(10.5)` | Exact decimal, no floating error |
| Array (bounded) | Array | Separate collection | Embedded is faster for <100 items |
| Array (unbounded) | Separate collection | Array | Avoid 16MB document limit |

## Read Diffusion Pattern (Reference: Turms)

```javascript
// ✅ Turms-style: store message per recipient (read diffusion)
// Each recipient has their own copy of the message
// Query: all messages for a user = simple indexed query

db.messages.insertMany([
    // Message from user 123 to user 456
    {
        _id: ObjectId("..."),
        messageId: NumberLong(1001),
        senderId: NumberLong(123),
        recipientId: NumberLong(456),  // key for sharding + query
        conversationId: ObjectId("..."),
        content: "Hello",
        status: "DELIVERED",
        createdAt: ISODate("2026-08-19T10:30:00Z")
    },
    // Copy for sender's outbox (optional)
    {
        _id: ObjectId("..."),
        messageId: NumberLong(1001),
        senderId: NumberLong(123),
        recipientId: NumberLong(123),  // sender's own copy
        conversationId: ObjectId("..."),
        content: "Hello",
        status: "SENT",
        createdAt: ISODate("2026-08-19T10:30:00Z")
    }
]);

// Query: get messages for user 456 (fast, indexed)
db.messages.find({
    recipientId: NumberLong(456),
    createdAt: { $gte: ISODate("2026-08-01T00:00:00Z") }
}).sort({ createdAt: -1 }).limit(50);

// Index: recipientId + createdAt (Turms pattern)
db.messages.createIndex({ recipientId: 1, createdAt: -1 });
```

## Indexing Best Practices

### Index Types

```javascript
// ✅ B-tree (default) - equality, range, sort
db.messages.createIndex({ recipientId: 1, createdAt: -1 });

// ✅ Unique index
db.conversations.createIndex({ userId: 1, type: 1 }, { unique: true });

// ✅ Partial index (index only matching documents)
db.messages.createIndex(
    { recipientId: 1, createdAt: -1 },
    { partialFilterExpression: { status: { $ne: "DELETED" } } }
);
// Smaller index, faster writes

// ✅ TTL index (auto-expire documents)
db.offline_messages.createIndex(
    { createdAt: 1 },
    { expireAfterSeconds: 86400 * 7 }  // expire after 7 days
);

// ✅ Text index (full-text search)
db.messages.createIndex({ content: "text" });
// Usage: db.messages.find({ $text: { $search: "hello world" } })

// ✅ Hashed index (for sharding)
db.messages.createIndex({ recipientId: "hashed" });
// Even distribution across shards
```

### Compound Index Order

```javascript
// ✅ Good - equality first, then sort
db.messages.createIndex({ recipientId: 1, createdAt: -1 });
// Query: { recipientId: 456 } sort { createdAt: -1 }
// → index covers both filter and sort (no in-memory sort)

// ✅ Good - most selective field first
db.messages.createIndex({ conversationId: 1, status: 1, createdAt: -1 });
// conversationId (high cardinality) → status (low) → createdAt (sort)

// ❌ Bad - sort field before equality
db.messages.createIndex({ createdAt: -1, recipientId: 1 });
// Query: { recipientId: 456 } sort { createdAt: -1 }
// → can't use index efficiently for filter
```

### Index Usage Verification

```javascript
// ✅ Use explain() to verify
db.messages.find({ recipientId: NumberLong(456) })
    .sort({ createdAt: -1 })
    .limit(50)
    .explain("executionStats");

// Look for:
// executionStats.totalKeysExamined: 50 (index entries scanned)
// executionStats.totalDocsExamined: 50 (documents fetched)
// executionStats.executionTimeMillis: 2 (fast)
// queryPlanner.winningPlan.stage: "FETCH" / "IXSCAN" (index used)

// Check index sizes
db.messages.stats().indexSizes;
// { _id_: 16384, recipientId_1_createdAt_-1: 32768, ... }

// Find unused indexes (MongoDB 4.2+)
db.messages.aggregate([
    { $indexStats: {} },
    { $match: { accesses.ops: { $eq: 0 } } },
    { $project: { name: 1, key: 1 } }
]);
```

## Query Best Practices

### Efficient Queries

```javascript
// ✅ Good - projection, limit, indexed query
db.messages.find(
    { recipientId: NumberLong(456), createdAt: { $gte: since } },
    { messageId: 1, senderId: 1, content: 1, createdAt: 1 }  // projection
).sort({ createdAt: -1 }).limit(50);

// ✅ Good - aggregation with pipeline optimization
db.messages.aggregate([
    { $match: { recipientId: NumberLong(456), createdAt: { $gte: since } } },  // filter first (use index)
    { $sort: { createdAt: -1 } },
    { $limit: 50 },
    { $group: { _id: "$senderId", count: { $sum: 1 } } },
    { $sort: { count: -1 } }
]);

// ❌ Bad - no projection, no limit, no index
db.messages.find({ content: /hello/ });
// Returns ALL fields, ALL matching documents, collection scan!
```

### Avoid Expensive Operations

```javascript
// ❌ Bad - $where (JavaScript evaluation, no index)
db.messages.find({ $where: "this.senderId === this.recipientId" });

// ✅ Good - $expr (can use index in some cases)
db.messages.find({ $expr: { $eq: ["$senderId", "$recipientId"] } });

// ❌ Bad - $ne (often can't use index efficiently)
db.messages.find({ status: { $ne: "DELETED" } });

// ✅ Good - list allowed values instead
db.messages.find({ status: { $in: ["SENT", "DELIVERED", "READ"] } });

// ❌ Bad - regex leading wildcard (can't use index)
db.messages.find({ content: /hello/ });

// ✅ Good - text index or regex without leading wildcard
db.messages.find({ content: /^hello/ });  // prefix regex can use index
```

## Sharding (Reference: Turms)

```javascript
// ✅ Turms-style: shard by recipientId (read diffusion)
sh.shardCollection("cbol.messages", { recipientId: "hashed" });

// Shard key selection criteria:
// 1. High cardinality (many distinct values)
// 2. Even distribution (no hot shards)
// 3. Query isolation (most queries include shard key)
// 4. Non-monotonic (avoid monotonically increasing keys like createdAt)

// ✅ Good shard keys:
// - recipientId (hashed): even distribution, queries by recipient
// - conversationId (hashed): even distribution, queries by conversation

// ❌ Bad shard keys:
// - createdAt (monotonic): all writes go to one shard (hot shard)
// - status (low cardinality): only a few chunks, uneven distribution
// - _id (ObjectId, monotonic): hot shard for inserts
```

## Connection & Pool Configuration

```yaml
# ✅ Good - MongoDB connection configuration
spring:
  data:
    mongodb:
      uri: mongodb://user:password@host1:27017,host2:27017/cbol?replicaSet=rs0
      auto-index-creation: false  # disable auto-index in production
    mongodb:
      properties:
        min-connection-per-host: 5
        max-connection-per-host: 100
        max-wait-time: 120000       # 2min
        connect-timeout: 10000       # 10s
        socket-timeout: 60000        # 60s
        server-selection-timeout: 30000  # 30s
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Embedding unbounded arrays | 16MB document limit | Separate collection for one-to-many |
| `$where` queries | No index, slow, security risk | Use `$expr` or native operators |
| No projection | Returns all fields, more memory/network | Explicit projection |
| No limit | Can return millions of docs | Always limit, use cursor pagination |
| Leading wildcard regex | Can't use index | Text index or prefix regex |
| Monotonic shard key | Hot shard for inserts | Hashed shard key or high-cardinality key |
| `auto-index-creation: true` | Unexpected index creation in prod | Create indexes explicitly in migrations |
| Large documents (>1MB) | Slow queries, high memory | Normalize, reference, GridFS for large files |
| Using `Number` for IDs | 32-bit float, precision loss | `NumberLong` (64-bit) or `ObjectId` |
| No TTL for temporary data | Unbounded growth | TTL index for offline messages, temp data |
| `$ne` queries | Poor index usage | Use `$in` with allowed values |
| Sort without index | In-memory sort (32MB limit) | Compound index including sort fields |

## References

- MongoDB Manual: https://www.mongodb.com/docs/manual/
- Turms (MongoDB sharding): https://github.com/turms-im/turms
- MongoDB Indexing Strategies: https://www.mongodb.com/docs/manual/applications/indexes/
- MongoDB Schema Design: https://www.mongodb.com/developer/products/mongodb/schema-design-patterns/
- Spring Data MongoDB: https://docs.spring.io/spring-data/mongodb/reference/
