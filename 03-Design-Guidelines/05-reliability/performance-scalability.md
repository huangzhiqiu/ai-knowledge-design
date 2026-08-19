# Performance & Scalability Design Guidelines

> Best practices for designing high-performance, scalable systems in CBOL Messaging Hub. Covers performance optimization, scalability patterns, capacity planning, benchmarking, and anti-patterns.

## Performance Fundamentals

### Latency Numbers (2024 Reference)

| Operation | Latency | Relative (if 1s = 1 day) |
|-----------|---------|--------------------------|
| L1 cache reference | 0.5 ns | 1 second |
| L2 cache reference | 7 ns | 14 seconds |
| L3 cache reference | 20 ns | 40 seconds |
| Main memory reference | 100 ns | 3.3 minutes |
| SSD random read | 10,000 ns (10 μs) | 5.5 hours |
| HDD random read | 1,000,000 ns (1 ms) | 23 days |
| Network round trip (same DC) | 500,000 ns (0.5 ms) | 11.5 days |
| Network round trip (cross-continent) | 150,000,000 ns (150 ms) | 9.5 years |
| Disk seek | 10,000,000 ns (10 ms) | 7.7 months |

### Performance Metrics

| Metric | Definition | Target (CBOL) |
|--------|-----------|----------------|
| **Latency** | Time to process one request | P50 < 50ms, P99 < 500ms |
| **Throughput** | Requests processed per second | 10,000+ msg/s per node |
| **Concurrency** | Simultaneous active connections | 100,000+ WebSocket per node |
| **Availability** | Uptime percentage | 99.99% |
| **Error Rate** | Failed requests / total | < 0.1% |
| **Resource Utilization** | CPU/memory/network usage | CPU < 70%, memory < 80% |

### Latency Budget

```
Total API request latency budget: 500ms (P99)

Breakdown:
  - Network (client → gateway): 50ms
  - API Gateway processing: 10ms
  - Auth/JWT validation: 5ms
  - Service processing: 200ms
    - Business logic: 50ms
    - Database query: 100ms
    - Cache lookup: 20ms
    - Message queue: 30ms
  - Network (gateway → client): 50ms
  - Buffer/overhead: 185ms

If any component exceeds budget, optimize or add cache.
```

## Scalability Patterns

### Vertical Scaling (Scale Up)

```
Increase resources of existing node:
  - More CPU cores
  - More RAM
  - Faster storage (NVMe SSD)
  - Faster network (10Gbps+)

Pros:
  - Simple, no architecture change
  - No distributed system complexity
  - Lower operational overhead

Cons:
  - Hardware limit (can't scale infinitely)
  - Single point of failure
  - Expensive (diminishing returns)
  - Downtime for upgrade

Use for: Initial growth, databases (hard to scale horizontally)
```

### Horizontal Scaling (Scale Out)

```
Add more nodes to distribute load:
  - More application servers
  - More database shards
  - More cache nodes
  - More message queue partitions

Pros:
  - Can scale (almost) infinitely
  - Fault tolerance (no single point)
  - Cost-effective (commodity hardware)
  - No downtime for scaling

Cons:
  - Distributed system complexity
  - Data consistency challenges
  - Network overhead
  - Higher operational overhead

Use for: Stateless services, high-growth systems
```

### Scaling Cube (AKF Scale Cube)

```
                    X-Axis (Horizontal Duplication)
                    ┌─────────────────────────────┐
                    │  Load balance across clones  │
                    │  + Simple, fault tolerant    │
                    │  - Stateful data challenge   │
                    └─────────────────────────────┘
                               │
                               │
          ┌────────────────────┼────────────────────┐
          │                                         │
          ▼                                         ▼
┌─────────────────────┐              ┌─────────────────────┐
│ Y-Axis (Functional  │              │ Z-Axis (Data        │
│ Decomposition)      │              │ Partitioning)       │
│                     │              │                     │
│ Split by service/   │              │ Split by data key   │
│ function            │              │ (sharding)          │
│ + Independent teams │              │ + Linear scaling    │
│ + Technology choice │              │ + Fault isolation   │
│ per service         │              │ - Complex routing   │
└─────────────────────┘              └─────────────────────┘

CBOL Strategy:
  - X-Axis: Multiple instances of each service (load balanced)
  - Y-Axis: Microservices (auth, message, conversation, user)
  - Z-Axis: Database sharding (by userId/recipientId)
```

### Stateless vs Stateful Scaling

```
Stateless Services (easy to scale):
  - No session data stored on server
  - Any instance can handle any request
  - Scale by adding instances
  - Example: REST API, auth service

Stateful Services (hard to scale):
  - Session/connection data on server
  - Request must go to same instance (sticky)
  - Scale requires data migration
  - Example: WebSocket gateway, in-memory cache

CBOL Approach:
  - WebSocket gateway: stateful (connection), but message routing is stateless
  - Use consistent hashing to route to correct node
  - On node failure, clients reconnect to other node
  - Session data in Redis (distributed), not in memory
```

## Performance Optimization

### Database Optimization

```
1. Indexing Strategy:
   - Index columns used in WHERE, JOIN, ORDER BY
   - Composite indexes in correct order (most selective first)
   - Avoid over-indexing (slows writes)
   - Use covering indexes (avoid table lookup)
   - Monitor index usage (remove unused indexes)

2. Query Optimization:
   - Avoid SELECT *, select only needed columns
   - Use pagination (LIMIT/OFFSET or keyset)
   - Avoid N+1 queries (use JOIN or batch fetch)
   - Use EXPLAIN to analyze query plans
   - Avoid functions on indexed columns (prevents index use)

3. Connection Pooling:
   - HikariCP (default in Spring Boot)
   - Pool size = ((core_count * 2) + effective_spindle_count)
   - Typically 10-20 connections per service instance
   - Monitor pool usage (active vs idle)

4. Read Replicas:
   - Write to master, read from replicas
   - Read-heavy workloads benefit most
   - Use read-after-write consistency for critical reads
   - Load balance reads across replicas
```

```java
// ✅ Good - database query optimization
@Repository
public class MessageRepository {

    // Use keyset pagination (better than OFFSET for large offsets)
    public List<Message> findByConversationId(Long conversationId,
                                                 Long lastMessageId,
                                                 int limit) {
        return jdbcTemplate.query(
            "SELECT id, sender_id, content, created_at " +
            "FROM messages " +
            "WHERE conversation_id = ? AND id < ? " +
            "ORDER BY id DESC " +
            "LIMIT ?",
            (rs, rowNum) -> Message.builder()
                .id(rs.getLong("id"))
                .senderId(rs.getLong("sender_id"))
                .content(rs.getString("content"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .build(),
            conversationId, lastMessageId, limit
        );
    }

    // Batch insert for better performance
    public void batchInsert(List<Message> messages) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO messages (conversation_id, sender_id, content, created_at) " +
            "VALUES (?, ?, ?, ?)",
            messages,
            100,  // batch size
            (ps, message) -> {
                ps.setLong(1, message.getConversationId());
                ps.setLong(2, message.getSenderId());
                ps.setString(3, message.getContent());
                ps.setTimestamp(4, Timestamp.from(message.getCreatedAt()));
            }
        );
    }
}
```

### Caching Strategy

```
Cache Hierarchy:
  L1: In-process cache (Caffeine) - sub-microsecond, small, ephemeral
  L2: Distributed cache (Redis) - sub-millisecond, large, shared
  L3: Database - milliseconds, persistent, source of truth

Cache Patterns:
  - Cache-Aside (Lazy Loading): App checks cache, on miss loads from DB and caches
  - Write-Through: Write to cache and DB simultaneously
  - Write-Behind (Write-Back): Write to cache, async write to DB
  - Refresh-Ahead: Refresh cache before expiration

CBOL Default: Cache-Aside + TTL + cache invalidation on write
```

```java
// ✅ Good - multi-level caching with Caffeine + Redis
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, User> localUserCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class UserService {
    private final Cache<String, User> localCache;
    private final RedisTemplate<String, User> redisTemplate;
    private final UserRepository userRepository;

    private static final String USER_CACHE_PREFIX = "user:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    public User getUserById(Long userId) {
        String key = USER_CACHE_PREFIX + userId;

        // L1: Check local cache (Caffeine)
        User user = localCache.getIfPresent(key);
        if (user != null) {
            return user;
        }

        // L2: Check Redis
        user = redisTemplate.opsForValue().get(key);
        if (user != null) {
            localCache.put(key, user);  // Populate L1
            return user;
        }

        // L3: Load from database
        user = userRepository.findById(userId).orElseThrow();

        // Populate L2 and L1
        redisTemplate.opsForValue().set(key, user, REDIS_TTL);
        localCache.put(key, user);

        return user;
    }

    public void updateUser(User user) {
        // Update database first (source of truth)
        userRepository.save(user);

        // Invalidate cache (not update - avoids race conditions)
        String key = USER_CACHE_PREFIX + user.getId();
        localCache.invalidate(key);
        redisTemplate.delete(key);
    }
}
```

### Asynchronous Processing

```
Synchronous (blocking):
  Request → Process → DB → MQ → Response
  Total latency = sum of all operations

Asynchronous (non-blocking):
  Request → Queue → Response (immediate)
              ↓
         Background worker processes
  Total latency = queue + response (fast)

When to use async:
  - Operations that don't need immediate result
  - Long-running operations
  - Operations that can be retried
  - Operations that can be batched

CBOL async operations:
  - Message persistence (ack immediately, persist async)
  - Push notifications
  - Analytics/metrics
  - Email notifications
  - File processing
```

```java
// ✅ Good - async processing with message queue
@Service
@RequiredArgsConstructor
public class MessageService {
    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;
    private final MessageRepository messageRepository;

    // Fast path: validate + queue + ack
    public CompletableFuture<MessageAck> sendMessage(SendMessageRequest request, Long userId) {
        // 1. Validate (fast, in-memory)
        validate(request);

        // 2. Create message event
        MessageEvent event = MessageEvent.builder()
            .id(generateId())
            .conversationId(request.getConversationId())
            .senderId(userId)
            .content(request.getContent())
            .timestamp(Instant.now())
            .build();

        // 3. Send to queue (fast, async)
        return kafkaTemplate.send("messages", event.getConversationId().toString(), event)
            .thenApply(result -> new MessageAck(event.getId(), event.getTimestamp()));
    }

    // Slow path: persist + fanout (background worker)
    @KafkaListener(topics = "messages", groupId = "message-persister")
    public void persistMessage(MessageEvent event) {
        // 1. Persist to MongoDB
        messageRepository.save(toMessage(event));

        // 2. Fanout to recipients (read diffusion)
        fanoutService.fanoutToRecipients(event);

        // 3. Push to online users via WebSocket
        pushService.pushToOnlineUsers(event);
    }
}
```

### Connection Pooling & Resource Management

```
Resource Pooling:
  - Database connections (HikariCP)
  - HTTP connections (Apache HttpClient / OkHttp)
  - Thread pools (ExecutorService)
  - Redis connections (Lettuce)

Pool Sizing Formula:
  pool_size = ((core_count * 2) + effective_spindle_count)
  For SSD: effective_spindle_count ≈ 1
  For 8-core: pool_size ≈ 17 (round to 15-20)

Thread Pool Types:
  - Fixed: Fixed number of threads (predictable)
  - Cached: Grow/shrink as needed (risk: too many threads)
  - Scheduled: For delayed/periodic tasks
  - Work-stealing: ForkJoinPool (for CPU-bound parallel tasks)

CBOL Default:
  - HTTP server: Tomcat with 200 threads
  - Async processing: Fixed thread pool (CPU * 2)
  - Database: HikariCP with 15-20 connections
  - WebSocket: Netty event loop (CPU cores)
```

## Capacity Planning

### Capacity Planning Process

```
1. Define Workload:
   - Peak concurrent users
   - Requests per second (RPS)
   - Message throughput (msg/s)
   - Data growth rate (GB/day)
   - Connection count (WebSocket)

2. Estimate Resource Needs:
   - CPU: RPS × CPU per request
   - Memory: Connections × memory per connection + cache size
   - Storage: Data growth × retention period
   - Network: RPS × request/response size × 2

3. Add Redundancy:
   - N+1 for stateless services
   - Multi-AZ for critical services
   - 30-50% headroom for traffic spikes

4. Monitor and Adjust:
   - Track actual resource usage
   - Set alerts at 70% utilization
   - Auto-scale based on metrics
   - Regular capacity reviews
```

### CBOL Capacity Estimates

| Resource | Per User | Per 10K Users | Per 100K Users |
|----------|----------|----------------|-----------------|
| WebSocket connection | 50 KB | 500 MB | 5 GB |
| Messages/day | 100 | 1M | 10M |
| Message storage/day | 50 KB | 500 MB | 5 GB |
| API RPS (peak) | 0.1 | 1K | 10K |
| CPU cores | 0.001 | 10 | 100 |
| RAM | 1 MB | 10 GB | 100 GB |

### Load Testing

```
Load Testing Types:
  - Smoke test: Minimal load, verify system works
  - Load test: Expected peak load, verify performance
  - Stress test: Beyond peak, find breaking point
  - Soak test: Sustained load over time, find memory leaks
  - Spike test: Sudden traffic spike, test auto-scaling

Tools:
  - JMeter: GUI-based, versatile
  - k6: Scriptable, modern, developer-friendly
  - Gatling: Scala-based, high performance
  - Locust: Python-based, distributed
  - wrk: Simple, fast HTTP benchmarking

CBOL Load Test Scenarios:
  1. Normal load: 10K concurrent users, 1K msg/s
  2. Peak load: 50K concurrent users, 5K msg/s
  3. Stress: 100K concurrent users, 10K msg/s
  4. Soak: 20K concurrent users for 24 hours
  5. Spike: 0 → 50K users in 10 seconds
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Premature optimization | Complex code, no measurable benefit | Profile first, optimize bottlenecks only |
| No caching | Every request hits DB, high latency | Multi-level caching (Caffeine + Redis) |
| Cache without TTL | Stale data, memory growth | Always set TTL, invalidate on write |
| N+1 queries | O(n) DB calls, slow | Use JOIN, batch fetch, or ORM fetch strategy |
| SELECT * | Unnecessary data transfer, can't use covering index | Select only needed columns |
| OFFSET pagination | Slow for large offsets (scans all rows) | Keyset pagination (WHERE id < last_id) |
| No connection pooling | New connection per request (expensive) | HikariCP with proper pool size |
| Synchronous everything | High latency, blocking threads | Async for non-critical operations |
| Too many threads | Context switching overhead, OOM | Thread pool sized to CPU cores, not unlimited |
| No load testing | Unknown capacity, surprises in production | Regular load testing, capacity planning |
| No monitoring | Can't detect performance degradation | Metrics, APM, alerting on latency/error rate |
| Ignoring network latency | Cross-region calls are slow (150ms) | Co-locate services, use CDN, async cross-region |
| No graceful degradation | Dependency failure = total failure | Circuit breaker, fallback, cached data |
| Over-indexing | Slow writes, wasted storage | Index only needed columns, monitor usage |
| No batch operations | One-by-one inserts/updates, slow | Batch operations (JDBC batch, bulk API) |
| Ignoring GC pauses | Long GC pauses = latency spikes | Proper heap sizing, GC tuning, avoid object churn |
| No rate limiting | Traffic spike overwhelms system | Rate limiting per user/IP, auto-scaling |
| Single large DB | Can't scale writes, single point | Sharding, read replicas, polyglot persistence |

## References

- Designing Data-Intensive Applications (Martin Kleppmann): https://dataintensive.net/
- High Performance Browser Networking (Ilya Grigorik): https://hpbn.co/
- Google SRE Book (Chapter on Handling Overload): https://sre.google/sre-book/handling-overload/
- AWS Well-Architected Framework (Performance Efficiency): https://docs.aws.amazon.com/wellarchitected/latest/performance-efficiency-pillar/welcome.html
- Java Performance (O'Reilly): https://www.oreilly.com/library/view/java-performance-2nd/9781492056102/
- Caffeine Cache: https://github.com/ben-manes/caffeine
- HikariCP: https://github.com/brettwooldridge/HikariCP
- k6 Load Testing: https://k6.io/
- JMeter: https://jmeter.apache.org/
- Latency Numbers Every Programmer Should Know: https://colin-scott.github.io/personal_website/research/interactive_latency.html
