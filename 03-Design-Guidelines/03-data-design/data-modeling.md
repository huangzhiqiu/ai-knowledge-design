# Data Modeling Guidelines

> Best practices for data modeling in CBOL Messaging Hub. Covers entity-relationship modeling, normalization, data types, indexing strategy, and schema evolution.

## Modeling Approach

### Conceptual → Logical → Physical

```
Conceptual Model (What)
  ├── Entities: User, Conversation, Message, Agent
  ├── Relationships: User has many Conversations
  └── No technical details

Logical Model (How, tech-agnostic)
  ├── Tables with columns and types
  ├── Primary keys, foreign keys
  ├── Normalization (3NF)
  └── No DB-specific details

Physical Model (Implementation)
  ├── MySQL/MongoDB specific
  ├── Indexes, partitions, sharding
  ├── Storage engines, character sets
  └── Performance optimizations
```

## Entity Design

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Table name | snake_case, plural | `conversations`, `message_recipients` |
| Column name | snake_case, singular | `conversation_id`, `created_at` |
| Primary key | `id` (auto-increment) or `<entity>_id` | `id`, `conversation_id` |
| Foreign key | `<referenced_entity>_id` | `user_id`, `conversation_id` |
| Timestamp | `created_at`, `updated_at`, `deleted_at` | `created_at DATETIME` |
| Boolean | `is_<attribute>` or `has_<attribute>` | `is_active`, `has_attachment` |
| Status enum | `<entity>_status` | `conversation_status VARCHAR(20)` |
| Join table | `<entity1>_<entity2>` | `conversation_members` |

### Primary Key Strategy

```sql
-- ✅ Good - auto-increment BIGINT for most tables
CREATE TABLE conversations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- ...
    PRIMARY KEY (id)
);

-- ✅ Good - UUID for distributed systems / public-facing IDs
CREATE TABLE messages (
    id CHAR(36) NOT NULL,  -- UUID v4
    -- ...
    PRIMARY KEY (id)
);

-- ✅ Good - composite key for join tables
CREATE TABLE conversation_members (
    conversation_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ❌ Bad - VARCHAR primary key, no index
CREATE TABLE conversations (
    conversation_id VARCHAR(255) NOT NULL,  -- Slow joins, large index
    -- ...
    PRIMARY KEY (conversation_id)
);
```

### Snowflake ID (Recommended for Distributed)

```java
// ✅ Good - Snowflake ID generator (time-ordered, distributed)
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1704067200000L;  // 2024-01-01
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + DATACENTER_ID_BITS + SEQUENCE_BITS))
            | (datacenterId << (WORKER_ID_BITS + SEQUENCE_BITS))
            | (workerId << SEQUENCE_BITS)
            | sequence;
    }
}
```

## Normalization

### Normal Forms

| Form | Rule | Example |
|------|------|---------|
| 1NF | Atomic values, no repeating groups | Each cell has one value |
| 2NF | 1NF + no partial dependency | All non-key columns depend on full PK |
| 3NF | 2NF + no transitive dependency | Non-key columns don't depend on other non-key columns |
| BCNF | 3NF + every determinant is a candidate key | Stronger version of 3NF |

```sql
-- ✅ Good - 3NF normalized
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(255) UNIQUE,
    created_at DATETIME
);

CREATE TABLE conversations (
    id BIGINT PRIMARY KEY,
    user_id BIGINT FOREIGN KEY REFERENCES users(id),
    status VARCHAR(20),
    created_at DATETIME
);

CREATE TABLE messages (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT FOREIGN KEY REFERENCES conversations(id),
    sender_id BIGINT FOREIGN KEY REFERENCES users(id),
    content TEXT,
    created_at DATETIME
);

-- ❌ Bad - denormalized, transitive dependency
CREATE TABLE messages (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT,
    conversation_status VARCHAR(20),  -- Depends on conversation_id, not message_id!
    sender_id BIGINT,
    sender_username VARCHAR(50),      -- Depends on sender_id, not message_id!
    content TEXT
);
```

### Denormalization (When to Break Rules)

```
✅ Denormalize when:
  - Read-heavy workload (1000x reads vs writes)
  - Complex joins hurt performance
  - Need to display aggregated data frequently
  - Event sourcing / CQRS read model

❌ Don't denormalize when:
  - Write-heavy workload
  - Data consistency is critical
  - Updates are frequent
  - Can use caching instead
```

```sql
-- ✅ Good - denormalized read model (CQRS)
CREATE TABLE conversations_read (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    user_name VARCHAR(50),       -- Denormalized for display
    user_avatar_url VARCHAR(255),-- Denormalized for display
    last_message_content TEXT,    -- Denormalized for list view
    last_message_at DATETIME,    -- Denormalized for sorting
    unread_count INT,             -- Aggregated for display
    INDEX idx_user_last_message (user_id, last_message_at DESC)
);
-- Updated asynchronously via events, not in the same transaction as writes
```

## Data Type Selection

| Data | MySQL Type | Notes |
|------|-----------|-------|
| ID (auto-increment) | `BIGINT UNSIGNED` | 64-bit, enough for most use cases |
| ID (UUID) | `CHAR(36)` or `BINARY(16)` | Binary is more efficient, CHAR is readable |
| Short string (< 255) | `VARCHAR(n)` | Specify max length, not always 255 |
| Long text | `TEXT` or `LONGTEXT` | For content, descriptions |
| Boolean | `TINYINT(1)` | MySQL doesn't have native BOOLEAN |
| Timestamp | `DATETIME(3)` or `TIMESTAMP` | DATETIME for absolute time, TIMESTAMP for timezone-aware |
| Date only | `DATE` | For birthdays, dates without time |
| Integer (small) | `INT` or `SMALLINT` | Choose based on max value |
| Decimal (money) | `DECIMAL(10,2)` | Never use FLOAT/DOUBLE for money |
| JSON | `JSON` (MySQL 5.7+) | For flexible, semi-structured data |
| Enum | `VARCHAR(n)` with CHECK | Avoid MySQL ENUM (hard to alter), use VARCHAR + lookup |

```sql
-- ✅ Good - appropriate data types
CREATE TABLE messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    metadata JSON,  -- Flexible metadata
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_conversation_created (conversation_id, created_at DESC)
);

-- ❌ Bad - inappropriate data types
CREATE TABLE messages (
    id VARCHAR(255),           -- Should be BIGINT
    content VARCHAR(255),      -- Should be TEXT (messages can be long)
    is_read BOOLEAN,           -- MySQL doesn't have BOOLEAN, use TINYINT(1)
    amount FLOAT,              -- Never use FLOAT for money, use DECIMAL
    created_at VARCHAR(50)     -- Should be DATETIME
);
```

## Indexing Strategy

### Index Types

| Type | Use For | Example |
|------|---------|---------|
| Primary key | Unique row identifier | `id` |
| Unique index | Enforce uniqueness | `email`, `username` |
| Composite index | Multi-column queries | `(conversation_id, created_at)` |
| Covering index | Query only needs index columns | `(conversation_id, id, content)` |
| Prefix index | Long string columns | `content(100)` |
| Full-text index | Text search | `FULLTEXT(content)` |

### Composite Index Order (Leftmost Prefix)

```
Rule: Put equality columns first, then range columns, then columns for ORDER BY

✅ Good:
  WHERE conversation_id = ? AND created_at > ?
  → INDEX (conversation_id, created_at)

  WHERE user_id = ? ORDER BY created_at DESC
  → INDEX (user_id, created_at)

❌ Bad:
  WHERE created_at > ? AND conversation_id = ?
  → INDEX (created_at, conversation_id)  -- Wrong order!
```

```sql
-- ✅ Good - indexes for common query patterns
CREATE TABLE messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    content TEXT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    -- Query: messages by conversation, ordered by time
    INDEX idx_conv_created (conversation_id, created_at DESC),
    -- Query: messages by sender
    INDEX idx_sender_created (sender_id, created_at DESC),
    -- Query: unread messages count
    INDEX idx_conv_read (conversation_id, is_read)
);

-- ❌ Bad - too many indexes, wrong order
CREATE TABLE messages (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT,
    sender_id BIGINT,
    content TEXT,
    created_at DATETIME,
    INDEX idx_conv (conversation_id),       -- Redundant (covered by idx_conv_created)
    INDEX idx_created (created_at),         -- Rarely queried alone
    INDEX idx_content (content(255)),       -- Too long, low selectivity
    INDEX idx_sender (sender_id),           -- Should include created_at
    INDEX id1, INDEX id2, INDEX id3, ...   -- Index write overhead!
);
```

### Index Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Index every column | Slow writes, large storage | Index only columns used in WHERE/JOIN/ORDER BY |
| Redundant indexes | Wasted space, slow writes | Remove indexes covered by composite indexes |
| Function on indexed column | Index not used | `WHERE DATE(created_at) = ?` → `WHERE created_at >= ? AND created_at < ?` |
| Leading wildcard LIKE | Index not used | `LIKE '%abc'` can't use index; use full-text search |
| Implicit type conversion | Index not used | `WHERE varchar_col = 123` converts column to number |
| Low cardinality index | Index scan slower than table scan | Don't index boolean/gender columns alone |
| No EXPLAIN verification | Don't know if index is used | Always run EXPLAIN on slow queries |

## Schema Evolution

### Migration Strategy

```
✅ Safe migrations (no downtime):
  - Add nullable column
  - Add table
  - Add index (use ALGORITHM=INPLACE, LOCK=NONE)
  - Expand VARCHAR (e.g., 50 → 100)

⚠️  Risky migrations (need planning):
  - Change column type
  - Add NOT NULL column (need default + backfill)
  - Drop column
  - Rename column
  - Add unique index (check for duplicates first)

❌ Dangerous migrations:
  - DROP TABLE
  - TRUNCATE TABLE
  - Change primary key
  - Large table ALTER without online DDL
```

```sql
-- ✅ Good - expand column safely
ALTER TABLE messages MODIFY COLUMN content VARCHAR(10000),
    ALGORITHM=INPLACE, LOCK=NONE;

-- ✅ Good - add nullable column with default
ALTER TABLE conversations ADD COLUMN priority VARCHAR(20) NULL DEFAULT 'NORMAL',
    ALGORITHM=INPLACE, LOCK=NONE;

-- ✅ Good - add index online
ALTER TABLE messages ADD INDEX idx_conv_created (conversation_id, created_at),
    ALGORITHM=INPLACE, LOCK=NONE;

-- ⚠️ Add NOT NULL column (3-step process)
-- Step 1: Add nullable column
ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL;
-- Step 2: Backfill existing rows
UPDATE users SET phone = 'unknown' WHERE phone IS NULL;
-- Step 3: Make NOT NULL with default
ALTER TABLE users MODIFY COLUMN phone VARCHAR(20) NOT NULL DEFAULT 'unknown';
```

### Versioned Migrations

```
db/migration/
├── V1__create_users_table.sql
├── V2__create_conversations_table.sql
├── V3__create_messages_table.sql
├── V4__add_message_indexes.sql
├── V5__add_conversation_priority.sql
└── V6__create_conversation_members_table.sql
```

```java
// ✅ Good - Flyway for versioned migrations
// pom.xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

// application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| EAV (Entity-Attribute-Value) | Hard to query, no type safety | Use JSON column or proper tables |
| Polymorphic associations | Can't use foreign keys | Use separate tables or inheritance |
| UUID as primary key (InnoDB) | Random insertion, page splits | Use UUID v7 (time-ordered) or BIGINT |
| Storing comma-separated lists | Can't query/index, violates 1NF | Use join table or JSON array |
| Storing passwords in plaintext | Security breach | Use bcrypt/Argon2 hashing |
| Using FLOAT for money | Precision loss | Use DECIMAL |
| No created_at/updated_at | Can't audit/troubleshoot | Add to every table |
| No soft delete (deleted_at) | Can't recover deleted data | Use `deleted_at DATETIME NULL` |
| Over-normalization | Too many joins, complex queries | Denormalize read models, use caching |
| Under-normalization | Update anomalies, data inconsistency | Normalize to 3NF, denormalize intentionally |
| No foreign keys | Data integrity issues | Add foreign keys (or enforce at app layer for sharded DB) |
| Using ENUM type | Hard to add values, can't reuse | Use VARCHAR + lookup table or CHECK constraint |
| Large TEXT in frequently queried table | Slow table scans | Move to separate table, use lazy loading |

## References

- Database Design (Wikipedia): https://en.wikipedia.org/wiki/Database_design
- MySQL Indexing: https://dev.mysql.com/doc/refman/8.0/en/optimization-indexes.html
- Database Normalization: https://en.wikipedia.org/wiki/Database_normalization
- Snowflake ID: https://github.com/twitter-archive/snowflake
- Flyway: https://flywaydb.org/
- Liquibase: https://www.liquibase.com/
- Use The Index, Luke!: https://use-the-index-luke.com/
