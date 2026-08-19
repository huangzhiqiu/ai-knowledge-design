# Relational Database Guidelines (MySQL)

> Best practices for MySQL 8.0 development, indexing, and schema design.

## Schema Design

### Naming Conventions

```sql
-- ✅ Good - lowercase, snake_case, plural table names
CREATE TABLE conversations (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_message_at DATETIME NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ❌ Bad - inconsistent, reserved words, no charset
CREATE TABLE Conversation (
    ID INT PRIMARY KEY,       -- PascalCase, reserved "ID"
    UserID INT,               -- camelCase
    status VARCHAR(20),       -- no default, no NOT NULL
    created DATETIME          -- reserved-ish, no default
);
```

### Data Type Selection

| Data | Recommended Type | Avoid | Reason |
|------|-----------------|-------|--------|
| ID (primary key) | `BIGINT UNSIGNED AUTO_INCREMENT` | `INT` | >4B rows, unsigned doubles range |
| UUID | `CHAR(36)` or `BINARY(16)` | `VARCHAR(36)` | Fixed length, index friendly |
| Boolean | `TINYINT(1)` | `BIT(1)` | TINYINT is more portable |
| Enum | `VARCHAR(20)` + CHECK | `ENUM` | ENUM requires ALTER to add values |
| Money | `DECIMAL(18,4)` | `FLOAT`/`DOUBLE` | Exact precision, no floating error |
| Timestamp | `DATETIME(3)` | `TIMESTAMP` | TIMESTAMP has 2038 limit, timezone issues |
| JSON | `JSON` (MySQL 5.7+) | `TEXT` | Native JSON, can index generated columns |
| IP address | `VARBINARY(16)` | `VARCHAR(45)` | Compact, supports IPv4/IPv6 |
| URL | `VARCHAR(2048)` | `TEXT` | Indexable, 2048 is browser limit |

### Table Design Rules

```sql
-- ✅ Good - every table has
CREATE TABLE messages (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_id       BIGINT UNSIGNED NOT NULL,
    content         TEXT NOT NULL,
    content_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    status          VARCHAR(20) NOT NULL DEFAULT 'SENT',
    seq_id          BIGINT UNSIGNED NOT NULL,  -- for ordering per conversation
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_seq (conversation_id, seq_id),
    KEY idx_conversation_created (conversation_id, created_at DESC),
    KEY idx_sender_created (sender_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mandatory columns for every table:
-- 1. id (BIGINT UNSIGNED AUTO_INCREMENT)
-- 2. created_at (DATETIME(3), default CURRENT_TIMESTAMP)
-- 3. updated_at (DATETIME(3), default CURRENT_TIMESTAMP ON UPDATE)
-- 4. Proper charset (utf8mb4) and engine (InnoDB)
```

## Indexing Best Practices

### Index Design Principles

```sql
-- ✅ Good - index columns used in WHERE, JOIN, ORDER BY, GROUP BY
CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);
-- Supports: WHERE conversation_id = ? ORDER BY created_at DESC

-- ✅ Good - composite index with most selective column first
CREATE INDEX idx_messages_sender_status_created
    ON messages (sender_id, status, created_at);
-- sender_id (high cardinality) first, status (low) second

-- ✅ Good - covering index (avoids table lookup)
CREATE INDEX idx_messages_covering
    ON messages (conversation_id, created_at DESC, id, sender_id, content_type);
-- SELECT id, sender_id, content_type FROM messages
-- WHERE conversation_id = ? ORDER BY created_at DESC
-- → all columns in index, no table access!

-- ❌ Bad - index each column separately
CREATE INDEX idx_a ON messages (conversation_id);
CREATE INDEX idx_b ON messages (sender_id);
CREATE INDEX idx_c ON messages (status);
-- MySQL can use only ONE index per table access (usually)
-- Composite index is better for multi-column queries

-- ❌ Bad - index on low-cardinality column alone
CREATE INDEX idx_status ON messages (status);
-- status has 5 values → index selectivity is poor → full scan is faster
```

### Index Types

```sql
-- B-tree (default) - for equality, range, ORDER BY
CREATE INDEX idx_created_at ON messages (created_at);

-- Unique - enforce uniqueness
CREATE UNIQUE INDEX uk_conversation_seq ON messages (conversation_id, seq_id);

-- Fulltext - for text search
CREATE FULLTEXT INDEX ft_content ON messages (content);
-- Usage: MATCH(content) AGAINST('+hello +world' IN BOOLEAN MODE)

-- Prefix index - for long strings
CREATE INDEX idx_content_prefix ON messages (content(50));
-- Index first 50 chars only (saves space, good for starts-with queries)

-- Descending index (MySQL 8.0+)
CREATE INDEX idx_created_desc ON messages (created_at DESC);
-- Avoids filesort for ORDER BY created_at DESC
```

### Index Usage Verification

```sql
-- ✅ Always use EXPLAIN to verify index usage
EXPLAIN SELECT * FROM messages
WHERE conversation_id = 123
ORDER BY created_at DESC
LIMIT 20;

-- Look for:
-- type: ref/range (good) vs ALL (full scan, bad)
-- key: idx_conversation_created (index used)
-- rows: 20 (estimated rows examined, low is good)
-- Extra: Using index condition; Using filesort (filesort = bad for large results)

-- Check index usage statistics
SELECT
    table_name,
    index_name,
    rows_examined_per_scan,
    rows_examined_per_scan * 100.0 / NULLIF(rows_read, 0) as efficiency
FROM sys.schema_unused_indexes
WHERE object_schema = 'cbol';

-- Remove unused indexes (saves write overhead + storage)
-- ALTER TABLE messages DROP INDEX idx_unused;
```

## SQL Best Practices

### Query Writing

```sql
-- ✅ Good - explicit columns, LIMIT, parameterized
SELECT id, conversation_id, sender_id, content, created_at
FROM messages
WHERE conversation_id = ?
  AND created_at >= ?
ORDER BY created_at DESC
LIMIT ? OFFSET ?;

-- ❌ Bad - SELECT *, no LIMIT, string concatenation
SELECT * FROM messages WHERE conversation_id = '123';
-- SELECT * = unnecessary columns, no LIMIT = can return millions of rows
-- string concat = SQL injection risk
```

### Avoid Functions on Indexed Columns

```sql
-- ❌ Bad - function on indexed column prevents index usage
SELECT * FROM messages WHERE DATE(created_at) = '2026-08-19';
-- MySQL can't use index on created_at because of DATE() function

-- ✅ Good - range query on indexed column
SELECT * FROM messages
WHERE created_at >= '2026-08-19 00:00:00'
  AND created_at <  '2026-08-20 00:00:00';
```

### Batch Operations

```java
// ✅ Good - batch insert with JDBC
String sql = "INSERT INTO messages (conversation_id, sender_id, content) VALUES (?, ?, ?)";
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {

    for (Message msg : messages) {
        ps.setLong(1, msg.getConversationId());
        ps.setLong(2, msg.getSenderId());
        ps.setString(3, msg.getContent());
        ps.addBatch();

        if (++count % 500 == 0) {
            ps.executeBatch(); // flush every 500
        }
    }
    ps.executeBatch(); // remaining
}

// ✅ Good - Spring Data JPA batch
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Modifying
    @Query("INSERT INTO Message (conversationId, senderId, content) VALUES (:conversationId, :senderId, :content)")
    int batchInsert(@Param("conversationId") Long conversationId,
                     @Param("senderId") Long senderId,
                     @Param("content") String content);
}

// application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

### Pagination for Large Offsets

```sql
-- ❌ Bad - large OFFSET is slow (scans and discards rows)
SELECT * FROM messages ORDER BY created_at DESC LIMIT 20 OFFSET 100000;
-- MySQL scans 100020 rows, discards 100000, returns 20

-- ✅ Good - keyset/cursor pagination
SELECT * FROM messages
WHERE created_at < ?  -- last cursor value
ORDER BY created_at DESC
LIMIT 20;
-- Uses index, only scans 20 rows regardless of "page"
```

## Connection Pool (HikariCP)

```yaml
# ✅ Good - HikariCP configuration
spring:
  datasource:
    hikari:
      maximum-pool-size: 20          # connections = ((core_count * 2) + effective_spindle_count)
      minimum-idle: 5                 # keep some warm connections
      connection-timeout: 30000      # 30s max wait for connection
      idle-timeout: 600000            # 10min idle before eviction
      max-lifetime: 1800000           # 30min max connection lifetime
      leak-detection-threshold: 60000 # 60s = potential connection leak
      pool-name: CbolHikariPool

# ❌ Bad - too large pool (thrashing), no timeout
spring:
  datasource:
    hikari:
      maximum-pool-size: 500  # too many = context switching, DB overload
      connection-timeout: 0    # infinite wait = thread hang
```

## Transactions & Locking

```sql
-- ✅ Good - optimistic locking with version column
ALTER TABLE conversations ADD COLUMN version INT NOT NULL DEFAULT 0;

UPDATE conversations
SET status = 'CLOSED', version = version + 1
WHERE id = ? AND version = ?;
-- If 0 rows updated → concurrent modification → retry or error

-- ✅ Good - SELECT ... FOR UPDATE SKIP LOCKED (queue pattern)
SELECT * FROM message_queue
WHERE status = 'PENDING'
ORDER BY created_at
LIMIT 10
FOR UPDATE SKIP LOCKED;
-- Multiple workers can process concurrently without blocking

-- ❌ Bad - long-running transactions
BEGIN;
SELECT * FROM messages WHERE id = 1 FOR UPDATE;  -- lock row
-- ... 10 seconds of processing (API calls, etc.)
UPDATE messages SET status = 'PROCESSED' WHERE id = 1;
COMMIT;
-- Row locked for 10s = other transactions block

-- ✅ Good - short transactions, lock only when needed
Message msg = messageRepository.findById(1); // no lock
// ... 10 seconds of processing (no DB lock held)
messageRepository.markAsProcessed(msg.getId(), msg.getVersion()); // optimistic lock
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| `SELECT *` | Unnecessary data, can't use covering index | Explicit column list |
| No `LIMIT` | Can return millions of rows | Always LIMIT, use cursor pagination |
| Function on indexed column | Index not used | Rewrite as range query |
| `LIKE '%keyword%'` | Can't use B-tree index | Fulltext index or external search |
| N+1 query problem | 1 query for list + N queries for details | JOIN fetch or batch fetch |
| Large OFFSET pagination | Slow for deep pages | Keyset/cursor pagination |
| No index on FK columns | Slow JOINs | Index all foreign keys |
| Too many indexes | Slow writes, wasted storage | Monitor unused indexes, remove them |
| `ENUM` type | ALTER needed to add values | VARCHAR + CHECK constraint |
| `TIMESTAMP` type | 2038 limit, timezone issues | `DATETIME(3)` |
| Long transactions | Lock contention, deadlocks | Keep transactions short, optimistic locking |
| Connection pool too large | DB thrashing | Pool size = ((cores*2)+spindles) |

## References

- MySQL 8.0 Reference: https://dev.mysql.com/doc/refman/8.0/en/
- MySQL Best Practices: https://github.com/tourze/ai-infra/blob/master/skills/mysql-best-practices/SKILL.md
- MySQL Query Optimization: https://github.com/nipunap/nipunap/blob/main/blogs/2026/mysql_query_optimization_guide.md
- HikariCP: https://github.com/brettwooldridge/HikariCP
- Use The Index, Luke: https://use-the-index-luke.com/
