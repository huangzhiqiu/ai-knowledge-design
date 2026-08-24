---
name: mysql-patterns
description: MySQL database patterns for schema design, query optimization, indexing, and common gotchas with JDBC and ORM drivers. Use when writing MySQL queries, migrations, or configuring a MySQL connection.
---

# MySQL Patterns

Quick reference for MySQL best practices. For a new integration, run `/db-researcher` first.

## When to Activate

- Writing MySQL queries or migrations
- Designing schemas for MySQL
- Configuring JDBC/Hibernate/Prisma/Drizzle for MySQL
- Debugging slow queries or unexpected type behavior

## Data Types

| Use Case | MySQL Type | Notes |
|----------|-----------|-------|
| Primary key | `BIGINT UNSIGNED AUTO_INCREMENT` | Prefer over `INT` for large tables |
| Short strings | `VARCHAR(255)` | Variable length, indexed efficiently |
| Long text | `TEXT` / `LONGTEXT` | Cannot be fully indexed |
| Boolean | `TINYINT(1)` | **Gotcha**: JDBC returns `Int` not `Boolean` unless `tinyInt1isBit=true` |
| Decimal money | `DECIMAL(19,4)` | Never use `FLOAT`/`DOUBLE` for money |
| Timestamps | `DATETIME` / `TIMESTAMP` | `TIMESTAMP` auto-converts to UTC; `DATETIME` stores as-is |
| UUID | `CHAR(36)` or `BINARY(16)` | `BINARY(16)` more efficient for indexing |
| JSON | `JSON` | Available from MySQL 5.7.8+ |
| Enum | `ENUM('a','b','c')` | Cheap for reads, expensive to alter |

## JDBC Gotchas

```
# Connection URL — always set these
jdbc:mysql://host:3306/db
  ?useSSL=false
  &serverTimezone=UTC
  &tinyInt1isBit=true          # TINYINT(1) → Boolean
  &transformedBitIsBoolean=true
  &zeroDateTimeBehavior=convertToNull  # '0000-00-00' → null instead of error
  &allowPublicKeyRetrieval=true
  &characterEncoding=UTF-8
```

**`tinyInt1isBit` default is `true`** — omitting it means `TINYINT(1)` columns come back as `Boolean`. Set explicitly so behavior is documented and intentional.

## Index Cheat Sheet

| Query Pattern | Index Type |
|--------------|------------|
| `WHERE col = value` | Single column B-tree |
| `WHERE a = x AND b = y` | Composite `(a, b)` |
| `WHERE a = x ORDER BY b` | Composite `(a, b)` |
| `LIKE 'prefix%'` | B-tree (prefix match only) |
| `LIKE '%suffix'` | Full-text or no index |
| `FULLTEXT MATCH()` | FULLTEXT index |
| JSON path `->>'$.key'` | Generated column + index |

**Composite index column order matters**: put equality columns first, range columns last.

```sql
-- Good: equality first, range last
CREATE INDEX idx ON orders (status, created_at);
-- WHERE status = 'active' AND created_at > '2025-01-01'
```

## Common Queries

```sql
-- Pagination (keyset — faster than LIMIT/OFFSET for large tables)
SELECT * FROM orders
WHERE id > :lastId
ORDER BY id ASC
LIMIT 20;

-- Upsert
INSERT INTO users (id, name, email)
VALUES (:id, :name, :email)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  email = VALUES(email);

-- Batch insert
INSERT INTO items (name, price) VALUES
  ('a', 1.00),
  ('b', 2.00),
  ('c', 3.00);

-- Get generated key after INSERT
-- JDBC: statement.getGeneratedKeys()
-- Hibernate: @GeneratedValue(strategy = GenerationType.IDENTITY)

-- JSON field query (MySQL 5.7.8+)
SELECT * FROM products
WHERE JSON_EXTRACT(metadata, '$.category') = 'electronics';
-- or with ->> operator
WHERE metadata->>'$.category' = 'electronics';
```

## Schema Design

```sql
CREATE TABLE orders (
  id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT UNSIGNED NOT NULL,
  status     ENUM('pending','active','closed') NOT NULL DEFAULT 'pending',
  total      DECIMAL(19,4) NOT NULL DEFAULT 0,
  metadata   JSON,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX idx_user_status (user_id, status),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Always use `utf8mb4`** — `utf8` in MySQL is broken (only 3 bytes, no emoji support).

## Query Optimization

```sql
-- Check query plan
EXPLAIN SELECT * FROM orders WHERE user_id = 1 AND status = 'active';

-- Check slow queries
SHOW VARIABLES LIKE 'slow_query_log%';
SHOW VARIABLES LIKE 'long_query_time';

-- Find missing indexes
SELECT * FROM sys.statements_with_full_table_scans LIMIT 10;

-- Table size
SELECT
  table_name,
  ROUND(data_length / 1024 / 1024, 2) AS data_mb,
  ROUND(index_length / 1024 / 1024, 2) AS index_mb
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY data_length DESC;
```

## Transactions

```sql
START TRANSACTION;
  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
-- or ROLLBACK on error
```

```java
// Spring / Hibernate — use @Transactional
@Transactional
public void transfer(Long from, Long to, BigDecimal amount) { ... }
```

## HikariCP Configuration

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      # MySQL keepalive
      keepalive-time: 60000
      connection-test-query: SELECT 1
```

## Known Gotchas

| Gotcha | Fix |
|--------|-----|
| `TINYINT(1)` comes back as `int` | Add `tinyInt1isBit=true` to JDBC URL |
| `'0000-00-00'` throws exception | Add `zeroDateTimeBehavior=convertToNull` |
| Emoji breaks insert | Use `utf8mb4` not `utf8` |
| `TIMESTAMP` converts timezone | Use `DATETIME` if you want raw storage |
| `FLOAT`/`DOUBLE` money rounding errors | Use `DECIMAL(19,4)` |
| `LIKE '%value%'` ignores indexes | Use FULLTEXT index or search engine |
| `AUTO_INCREMENT` gap after rollback | Expected behavior, not a bug |
| Deadlock on bulk update | Update rows in consistent ID order |
