# Database Design Guidelines

> Best practices for database architecture design in CBOL Messaging Hub. Covers relational vs NoSQL selection, sharding, replication, connection pooling, transaction design, and polyglot persistence.

## Polyglot Persistence Strategy

```
CBOL Messaging Hub Database Architecture:

  ┌─────────────────────────────────────────────────────┐
  │                    API Gateway                        │
  └──────────────┬──────────────────┬───────────────────┘
                 │                  │
    ┌────────────▼─────┐  ┌─────────▼──────────┐
    │   MySQL 8.0       │  │   MongoDB 6.x       │
    │   (Relational)    │  │   (Document)        │
    │                   │  │                      │
    │ - Users           │  │ - Messages (read    │
    │ - Conversations   │  │   diffusion model)  │
    │ - Agents          │  │ - Message metadata  │
    │ - Auth/audit      │  │ - Unstructured data │
    └───────────────────┘  └──────────────────────┘
                 │                  │
    ┌────────────▼──────────────────▼─────┐
    │         Redis 6.x (Cluster)          │
    │  - Session cache                      │
    │  - Hot data cache                     │
    │  - Rate limiting                      │
    │  - Distributed locks                  │
    │  - Pub/Sub                            │
    └───────────────────────────────────────┘
```

### Data Store Selection Matrix

| Data Type | Store | Why |
|-----------|-------|-----|
| User accounts, auth | MySQL | ACID, relational, strong consistency |
| Conversation metadata | MySQL | Relations, transactions, structured |
| Agent profiles, skills | MySQL | Structured, relational |
| Message content (write) | MySQL | ACID on write, source of truth |
| Message content (read) | MongoDB | Read diffusion, sharding by recipient, flexible schema |
| Message metadata, attachments | MongoDB | Unstructured, variable schema |
| User sessions | Redis | Fast access, TTL, in-memory |
| Hot conversations/messages | Redis | Cache, reduce DB load |
| Rate limiting counters | Redis | Atomic increments, TTL |
| Distributed locks | Redis | Redlock algorithm |
| Real-time notifications | Redis Pub/Sub | Low latency, fanout |
| Audit logs | MySQL (append-only) | Immutable, queryable |
| Analytics/events | MongoDB or ClickHouse | Write-heavy, aggregation |

## Relational Database Design (MySQL)

### Replication Topology

```
                    ┌──────────────┐
                    │   Primary    │  (writes)
                    │   (MySQL)    │
                    └──────┬───────┘
                           │ binlog replication
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Replica 1│ │ Replica 2│ │ Replica 3│
        │ (reads)  │ │ (reads)  │ │ (backup) │
        └──────────┘ └──────────┘ └──────────┘
```

```yaml
# ✅ Good - MySQL replication configuration
# Primary (my.cnf)
[mysqld]
server-id=1
log-bin=mysql-bin
binlog-format=ROW  # Row-based replication (safest)
binlog-row-image=MINIMAL
expire-logs-days=7
max-binlog-size=1G

# Replica (my.cnf)
[mysqld]
server-id=2
relay-log=relay-bin
read-only=ON
super-read-only=ON
replicate-do-db=cbol
```

### Read-Write Splitting

```java
// ✅ Good - read-write splitting with Spring
@Configuration
public class DataSourceConfig {
    @Bean
    @Primary
    public DataSource routingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("primary", primaryDataSource());
        targetDataSources.put("replica", replicaDataSource());

        AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return DataSourceContextHolder.getDataSourceType();
            }
        };
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        routingDataSource.setTargetDataSources(targetDataSources);
        return routingDataSource;
    }
}

// Aspect to route read methods to replica
@Aspect
@Component
public class ReadOnlyAspect {
    @Before("@annotation(transactional) && execution(* com.cbol..*.*(..))")
    public void setReadOnly(JoinPoint joinPoint, Transactional transactional) {
        if (transactional.readOnly()) {
            DataSourceContextHolder.setReplica();
        } else {
            DataSourceContextHolder.setPrimary();
        }
    }
}
```

### Connection Pooling

```yaml
# ✅ Good - HikariCP configuration (Spring Boot default)
spring:
  datasource:
    hikari:
      maximum-pool-size: 20          # Core count * 2 + effective spindle count
      minimum-idle: 5
      connection-timeout: 30000      # 30s
      idle-timeout: 600000            # 10min
      max-lifetime: 1800000           # 30min (less than DB wait_timeout)
      leak-detection-threshold: 60000 # 60s (detect connection leaks)
      pool-name: CBOL-HikariPool
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
```

### Pool Sizing Formula

```
Pool size = ((core_count * 2) + effective_spindle_count)

For 8-core server with SSD:
  Pool size = (8 * 2) + 1 = 17 → round to 20

For 4-core server with SSD:
  Pool size = (4 * 2) + 1 = 9 → round to 10

⚠️  Don't set pool size too large:
  - More connections = more memory, more context switching
  - MySQL default max_connections = 151
  - Each connection uses ~1-3MB memory
```

## NoSQL Database Design (MongoDB)

### Document Structure

```json
// ✅ Good - message document (read diffusion model)
{
  "_id": ObjectId("..."),
  "messageId": NumberLong(12345),
  "conversationId": NumberLong(678),
  "senderId": NumberLong(999),
  "recipientId": NumberLong(111),  // Shard key: recipientId
  "content": "Hello, I need help",
  "contentType": "TEXT",
  "status": "DELIVERED",
  "metadata": {
    "attachments": [],
    "replyTo": null,
    "forwardedFrom": null
  },
  "createdAt": ISODate("2026-08-19T10:30:00Z"),
  "deliveredAt": ISODate("2026-08-19T10:30:01Z"),
  "readAt": null
}
```

### Read Diffusion Pattern (Turms Reference)

```
Write path:
  1. Client sends message
  2. Store in MySQL (source of truth)
  3. For each recipient, create a copy in MongoDB with recipientId as shard key
  4. Notify recipient via WebSocket

Read path:
  1. Client requests message history
  2. Query MongoDB by recipientId (shard key) + conversationId
  3. Return messages (already filtered by recipient, no join needed)

Benefits:
  - Read is O(1) per recipient (no join, no filtering)
  - Sharding by recipientId distributes load
  - Each user's messages are on their shard
```

### Sharding Strategy

```javascript
// ✅ Good - shard by recipientId (read diffusion)
sh.shardCollection("cbol.messages", { recipientId: "hashed" });

// Compound shard key for better query isolation
sh.shardCollection("cbol.messages", {
  recipientId: 1,      // First: distribute by user
  conversationId: 1,   // Second: group by conversation
  createdAt: -1        // Third: sort by time
});

// Indexes
db.messages.createIndex({ recipientId: 1, conversationId: 1, createdAt: -1 });
db.messages.createIndex({ messageId: 1 }, { unique: true });
db.messages.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 }); // TTL 30 days
```

### Embedding vs Referencing

| Pattern | Use When | Example |
|---------|----------|---------|
| **Embed** | 1:few, always accessed together, bounded size | Message attachments in message document |
| **Reference** | 1:many, unbounded, accessed separately | User profile referenced from message |
| **Hybrid** | Mix of both | Embed summary, reference full details |

```json
// ✅ Good - embed bounded subdocuments
{
  "messageId": 123,
  "content": "Check this file",
  "attachments": [  // Embed: bounded (max 10), always accessed with message
    { "type": "FILE", "url": "...", "name": "report.pdf", "size": 1024000 }
  ]
}

// ✅ Good - reference unbounded data
{
  "messageId": 123,
  "senderId": 999,  // Reference: user profile accessed separately, can change
  "content": "Hello"
}
// Don't embed full user profile in every message (would be huge, hard to update)
```

## Transaction Design

### Transaction Boundaries

```java
// ✅ Good - transaction at application service layer
@Service
public class ConversationApplicationService {

    @Transactional  // Transaction boundary here
    public ConversationResponse create(CreateConversationCommand command) {
        // 1. Load aggregates (within transaction)
        User user = userRepository.findById(command.getUserId()).orElseThrow();

        // 2. Domain logic (no transaction needed here)
        Conversation conversation = Conversation.create(user, command.getType());

        // 3. Persist (within transaction)
        Conversation saved = conversationRepository.save(conversation);

        // 4. Publish events (after transaction commit, or use Outbox)
        // Don't publish inside transaction - might be rolled back
        return ConversationResponse.from(saved);
    }
}

// ❌ Bad - transaction in domain layer
@Transactional  // Domain layer shouldn't know about transactions
public class Conversation {
    public void addMessage(Message message) {
        // ...
    }
}
```

### Transaction Isolation Levels

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Use For |
|-------|-----------|---------------------|-------------|---------|
| READ UNCOMMITTED | ✅ | ✅ | ✅ | Never (dirty reads) |
| READ COMMITTED | ❌ | ✅ | ✅ | Most OLTP (MySQL default) |
| REPEATABLE READ | ❌ | ❌ | ✅ | Financial, MySQL InnoDB default |
| SERIALIZABLE | ❌ | ❌ | ❌ | Critical, rare (performance hit) |

```java
// ✅ Good - specify isolation level when needed
@Transactional(isolation = Isolation.READ_COMMITTED)  // Default for most
public void processMessage(...) { }

@Transactional(isolation = Isolation.REPEATABLE_READ)  // For financial/billing
public void transferCredits(...) { }

@Transactional(isolation = Isolation.SERIALIZABLE)  // Only for critical, low-volume
public void allocateLimitedResource(...) { }
```

### Optimistic Locking

```java
// ✅ Good - optimistic locking for concurrent updates
@Entity
@Table(name = "conversations")
public class ConversationEntity {
    @Id
    private Long id;

    @Version  // JPA optimistic locking
    private Integer version;

    private String status;
    // ...
}

// On concurrent update:
// Thread 1: UPDATE conversations SET status=?, version=version+1 WHERE id=? AND version=1
// Thread 2: UPDATE conversations SET status=?, version=version+1 WHERE id=? AND version=1
// → Thread 2 gets 0 rows updated → OptimisticLockingFailureException
```

### Pessimistic Locking

```java
// ✅ Good - pessimistic locking for high-contention resources
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") Long id);

    // Or use native query with SKIP LOCKED
    @Query(value = "SELECT * FROM conversations WHERE status = 'PENDING' " +
                    "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    Optional<Conversation> findNextPendingForUpdate();
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| One database for everything | Wrong tool for the job | Polyglot persistence (MySQL + MongoDB + Redis) |
| No read replicas | Primary overloaded with reads | Read replicas + read-write splitting |
| Connection pool too large | Memory waste, DB overload | Use formula: core_count * 2 + 1 |
| Long-running transactions | Lock contention, deadlocks | Keep transactions short, load data first |
| Transaction in domain layer | Leaky abstraction | Transactions at application service layer |
| No locking strategy | Lost updates, race conditions | Optimistic locking (@Version) for most, pessimistic for high contention |
| Cross-database transactions | 2PC overhead, complexity | Use eventual consistency, Saga pattern, Outbox |
| No sharding plan | Single DB becomes bottleneck | Plan sharding early, choose good shard keys |
| Bad shard key | Hot spots, uneven distribution | Choose high-cardinality, evenly distributed key |
| MongoDB as relational database | No joins, no transactions | Use proper document model, embedding vs referencing |
| No TTL on time-series data | Unbounded growth | Use TTL indexes for messages, logs, sessions |
| No connection pool monitoring | Can't detect leaks | Monitor active/idle connections, leak detection |
| N+1 query problem | Performance degradation | Use JOIN FETCH, EntityGraph, batch fetching |
| SELECT * | Unnecessary data transfer, can't use covering index | Select only needed columns |

## References

- MySQL Reference: https://dev.mysql.com/doc/refman/8.0/en/
- MongoDB Manual: https://www.mongodb.com/docs/manual/
- Redis Documentation: https://redis.io/docs/
- HikariCP: https://github.com/brettwooldridge/HikariCP
- Database Sharding: https://aws.amazon.com/what-is/database-sharding/
- Polyglot Persistence: https://martinfowler.com/bliki/PolyglotPersistence.html
- Turms (IM reference): https://github.com/turms-im/turms
- CAP Theorem: https://en.wikipedia.org/wiki/CAP_theorem
