# Cache Design Guidelines

> Best practices for cache architecture design in CBOL Messaging Hub. Covers caching strategies, cache invalidation, distributed caching, cache consistency, and anti-patterns.

## Cache Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        API Gateway                             │
└──────────────────────────────┬──────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Application Cache   │  (Caffeine, local)
                    │   - Hot data          │
                    │   - Short TTL         │
                    └──────────┬──────────┘
                               │ miss
                    ┌──────────▼──────────┐
                    │   Distributed Cache   │  (Redis Cluster)
                    │   - Session cache     │
                    │   - User profile      │
                    │   - Conversation list │
                    │   - Rate limiting     │
                    └──────────┬──────────┘
                               │ miss
                    ┌──────────▼──────────┐
                    │      Database         │  (MySQL / MongoDB)
                    └─────────────────────┘
```

### Multi-Level Cache

| Level | Technology | TTL | Use For |
|-------|-----------|-----|---------|
| L1 (Local) | Caffeine | 30s - 5min | Hot data, reference data, reduce Redis load |
| L2 (Distributed) | Redis Cluster | 5min - 24h | User sessions, profiles, conversation lists |
| L3 (Database) | MySQL/MongoDB | Permanent | Source of truth |

```java
// ✅ Good - multi-level cache with Caffeine + Redis
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, UserProfile> localCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final Cache<String, UserProfile> localCache;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    private static final String REDIS_KEY_PREFIX = "user:profile:";
    private static final Duration REDIS_TTL = Duration.ofHours(1);

    public UserProfile getProfile(Long userId) {
        String key = REDIS_KEY_PREFIX + userId;

        // L1: Local cache
        UserProfile profile = localCache.getIfPresent(key);
        if (profile != null) return profile;

        // L2: Redis
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            profile = deserialize(json);
            localCache.put(key, profile);
            return profile;
        }

        // L3: Database
        profile = userRepository.findProfileById(userId);
        if (profile != null) {
            redisTemplate.opsForValue().set(key, serialize(profile), REDIS_TTL);
            localCache.put(key, profile);
        }
        return profile;
    }
}
```

## Caching Strategies

### Cache-Aside (Lazy Loading) — Default

```
Read:
  1. Check cache → hit? return
  2. Miss? Load from DB
  3. Store in cache
  4. Return

Write:
  1. Update DB
  2. Invalidate cache (delete key)
```

```java
// ✅ Good - cache-aside pattern
public UserProfile getProfile(Long userId) {
    String key = "user:profile:" + userId;

    // Try cache
    UserProfile cached = redisTemplate.opsForValue().get(key);
    if (cached != null) return cached;

    // Load from DB
    UserProfile profile = userRepository.findProfileById(userId);

    // Store in cache (with TTL)
    if (profile != null) {
        redisTemplate.opsForValue().set(key, profile, Duration.ofHours(1));
    }

    return profile;
}

@Transactional
public void updateProfile(Long userId, UserProfileUpdate update) {
    // 1. Update DB first
    userRepository.updateProfile(userId, update);

    // 2. Invalidate cache (not update - prevents race condition)
    redisTemplate.delete("user:profile:" + userId);
}
```

### Write-Through

```
Write:
  1. Update cache
  2. Cache synchronously updates DB
  3. Return

Read:
  1. Check cache → hit? return
  2. Miss? Load from DB, store in cache
```

```java
// ✅ Good - write-through for critical data
public void updateUserStatus(Long userId, UserStatus status) {
    String key = "user:status:" + userId;

    // 1. Update cache (with TTL)
    redisTemplate.opsForValue().set(key, status, Duration.ofMinutes(30));

    // 2. Update DB (synchronous)
    userRepository.updateStatus(userId, status);
}
```

### Write-Behind (Write-Back)

```
Write:
  1. Update cache
  2. Queue async DB update
  3. Return immediately

Background:
  - Batch flush to DB every N seconds / N items
```

```java
// ✅ Good - write-behind for high-throughput, non-critical data
@Service
public class MessageCounterService {
    private final RedisTemplate<String, String> redisTemplate;
    private final MessageRepository messageRepository;
    private final BlockingQueue<CounterUpdate> pendingUpdates = new LinkedBlockingQueue<>();

    @PostConstruct
    public void init() {
        // Background flusher
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(this::flush, 10, 10, TimeUnit.SECONDS);
    }

    public void incrementUnreadCount(Long userId) {
        String key = "user:unread:" + userId;
        redisTemplate.opsForValue().increment(key);  // Update cache immediately
        pendingUpdates.offer(new CounterUpdate(userId, 1));  // Queue for DB
    }

    private void flush() {
        List<CounterUpdate> updates = new ArrayList<>();
        pendingUpdates.drainTo(updates);
        if (!updates.isEmpty()) {
            messageRepository.batchIncrementUnreadCount(updates);  // Batch DB update
        }
    }
}
```

### Strategy Selection

| Strategy | Consistency | Performance | Use For |
|----------|------------|-------------|---------|
| Cache-Aside | Good | Good | Default, most read-heavy data |
| Write-Through | Strong | Slower writes | Critical data, must be consistent |
| Write-Behind | Eventual | Fast writes | Counters, metrics, non-critical high-throughput |
| Refresh-Ahead | Good | Good | Predictable access patterns |

## Cache Invalidation

### Invalidation Strategies

| Strategy | How | Use For |
|----------|-----|---------|
| **TTL** | Auto-expire after time | Session, temporary data, default |
| **Explicit delete** | Delete key on update | User profile, conversation metadata |
| **Versioning** | Include version in key | Data that changes with version |
| **Event-based** | Subscribe to change events | Distributed cache invalidation |

### TTL Strategy

```java
// ✅ Good - appropriate TTLs by data type
public enum CacheType {
    USER_SESSION(Duration.ofMinutes(30)),
    USER_PROFILE(Duration.ofHours(1)),
    CONVERSATION_LIST(Duration.ofMinutes(10)),
    CONVERSATION_DETAIL(Duration.ofMinutes(5)),
    MESSAGE_HISTORY(Duration.ofMinutes(2)),
    REFERENCE_DATA(Duration.ofDays(1)),
    RATE_LIMIT(Duration.ofMinutes(1)),
    DISTRIBUTED_LOCK(Duration.ofSeconds(30));

    private final Duration ttl;
}

// Usage
redisTemplate.opsForValue().set(key, value, CacheType.USER_PROFILE.getTtl());
```

### Cache-Aside Invalidation (Recommended)

```java
// ✅ Good - invalidate on update, not update cache
@Transactional
public Conversation updateConversation(Long id, ConversationUpdate update) {
    // 1. Update DB
    Conversation updated = conversationRepository.update(id, update);

    // 2. Invalidate all related cache keys
    redisTemplate.delete(
        "conversation:detail:" + id,
        "conversation:list:" + updated.getUserId()
    );

    // 3. Publish invalidation event (for multi-instance local cache)
    redisTemplate.convertAndSend("cache-invalidation",
        new InvalidationEvent("conversation", id));

    return updated;
}

// ❌ Bad - update cache on write (race condition)
@Transactional
public Conversation updateConversation(Long id, ConversationUpdate update) {
    Conversation updated = conversationRepository.update(id, update);
    redisTemplate.opsForValue().set("conversation:detail:" + id, updated);  // Race!
    // If another thread reads between DB update and cache update, gets stale data
    return updated;
}
```

### Distributed Invalidation (Pub/Sub)

```java
// ✅ Good - Redis Pub/Sub for local cache invalidation
@Component
public class CacheInvalidationListener {
    private final Cache<String, Object> localCache;

    @RedisListener(channel = "cache-invalidation")
    public void onInvalidation(InvalidationEvent event) {
        // Delete from local cache when notified
        localCache.invalidate(event.getCacheKey());
    }
}

// Publisher (on any instance)
redisTemplate.convertAndSend("cache-invalidation",
    new InvalidationEvent("conversation:detail:" + id));
```

## Cache Consistency

### Cache-Aside Consistency Window

```
Time →
T1: Thread A reads cache → miss
T2: Thread A reads DB → gets v1
T3: Thread B updates DB → v2
T4: Thread B deletes cache
T5: Thread A writes v1 to cache ← STALE!
T6: Cache has v1 (stale) until TTL or next invalidation

Solution: Short TTL + delayed double-delete
```

### Delayed Double-Delete

```java
// ✅ Good - delayed double-delete for consistency
@Transactional
public void updateConversation(Long id, ConversationUpdate update) {
    // 1. Delete cache before DB update
    redisTemplate.delete("conversation:detail:" + id);

    // 2. Update DB
    conversationRepository.update(id, update);

    // 3. Delete cache again after delay (async)
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(500);  // Wait for any in-flight reads to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        redisTemplate.delete("conversation:detail:" + id);
    });
}
```

### Cache Penetration (Non-Existent Key)

```
Problem: Query for non-existent ID → cache miss → DB miss → cache miss → ...
         Attacker can flood with non-existent IDs, overwhelming DB

Solution: Cache null values with short TTL, or use Bloom filter
```

```java
// ✅ Good - cache null values to prevent penetration
public Conversation getConversation(Long id) {
    String key = "conversation:detail:" + id;

    // Check cache
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        if ("NULL".equals(cached)) return null;  // Cached null
        return deserialize(cached);
    }

    // Load from DB
    Conversation conv = conversationRepository.findById(id).orElse(null);

    // Cache result (even null)
    if (conv == null) {
        redisTemplate.opsForValue().set(key, "NULL", Duration.ofMinutes(1));  // Short TTL for null
    } else {
        redisTemplate.opsForValue().set(key, serialize(conv), Duration.ofMinutes(5));
    }

    return conv;
}
```

### Cache Breakdown (Hot Key)

```
Problem: Single hot key → all traffic hits one Redis node → node overload

Solution:
  1. Local cache (Caffeine) for hot keys
  2. Key splitting (hotkey_0, hotkey_1, ... hotkey_N)
  3. Read replicas
```

```java
// ✅ Good - local cache for hot keys
public Conversation getHotConversation(Long id) {
    String key = "conversation:hot:" + id;

    // L1: Local cache (Caffeine) - handles most hot traffic
    Conversation local = localCache.getIfPresent(key);
    if (local != null) return local;

    // L2: Redis
    Conversation redis = redisTemplate.opsForValue().get(key);
    if (redis != null) {
        localCache.put(key, redis);
        return redis;
    }

    // L3: DB
    Conversation db = conversationRepository.findById(id).orElseThrow();
    redisTemplate.opsForValue().set(key, db, Duration.ofMinutes(5));
    localCache.put(key, db);
    return db;
}
```

### Cache Avalanche (Mass Expiration)

```
Problem: Many keys expire at same time → all traffic hits DB → DB overload

Solution:
  1. Add random jitter to TTL (base + random(0, 300s))
  2. Stagger cache warming
  3. Use mutex/lock to prevent concurrent DB loads
```

```java
// ✅ Good - TTL with jitter
public void setWithJitter(String key, Object value, Duration baseTtl) {
    long jitter = ThreadLocalRandom.current().nextLong(0, 300);  // 0-300 seconds
    Duration ttl = baseTtl.plusSeconds(jitter);
    redisTemplate.opsForValue().set(key, value, ttl);
}

// ✅ Good - distributed lock for cache breakdown
public Conversation getWithLock(Long id) {
    String key = "conversation:" + id;
    String lockKey = "lock:conversation:" + id;

    // Try cache
    Conversation cached = redisTemplate.opsForValue().get(key);
    if (cached != null) return cached;

    // Acquire lock (only one thread loads from DB)
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

    if (Boolean.TRUE.equals(locked)) {
        try {
            // Double-check cache (another thread might have loaded it)
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return cached;

            // Load from DB
            Conversation db = conversationRepository.findById(id).orElseThrow();
            redisTemplate.opsForValue().set(key, db, Duration.ofMinutes(5));
            return db;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // Wait and retry (another thread is loading)
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return getWithLock(id);  // Recursive retry
    }
}
```

## Redis Cluster Design

### Cluster Topology

```
Redis Cluster (6 nodes, 3 masters + 3 replicas):

  Master 1 (slots 0-5460)  ←→  Replica 1
  Master 2 (slots 5461-10922) ←→  Replica 2
  Master 3 (slots 10923-16383) ←→  Replica 3

Key → CRC16(key) mod 16384 → slot → master
```

```yaml
# ✅ Good - Redis Cluster configuration
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-1:6379
          - redis-2:6379
          - redis-3:6379
        max-redirects: 3
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
```

### Key Design for Cluster

```
✅ Good - hash tag for multi-key operations
  {conversation:123}:messages
  {conversation:123}:members
  {conversation:123}:metadata
  → All keys for conversation 123 go to same slot (hash tag = conversation:123)

❌ Bad - no hash tag, multi-key operations fail
  conversation:123:messages
  conversation:123:members
  → Might go to different slots → CROSSSLOT error
```

```java
// ✅ Good - hash tag for related keys
public class CacheKeyBuilder {
    public static String conversationMessages(Long convId) {
        return "{conversation:" + convId + "}:messages";
    }
    public static String conversationMembers(Long convId) {
        return "{conversation:" + convId + "}:members";
    }
    public static String conversationMetadata(Long convId) {
        return "{conversation:" + convId + "}:metadata";
    }
}
// All three keys hash to same slot → can use MGET, pipeline, transactions
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Cache everything | Memory waste, stale data | Cache only hot/read-heavy data |
| No TTL | Stale data forever, memory leak | Always set TTL (except specific use cases) |
| Update cache on write | Race condition, stale data | Invalidate (delete) on write, not update |
| Cache null values with long TTL | Real data added but cached null blocks it | Short TTL (1min) for null values |
| Large cache values | Network overhead, memory waste | Compress, split, or don't cache large objects |
| No cache monitoring | Can't detect hit/miss issues | Monitor hit rate, memory, latency, evictions |
| Serialize entire object graph | Slow, large payload | Cache DTOs, not entities; avoid lazy loading |
| Cache as source of truth | Data loss on cache restart | DB is source of truth, cache is derivative |
| No cache key namespace | Key collisions, hard to manage | Use prefix: `app:entity:id:field` |
| Distributed cache without local cache | All traffic hits Redis, latency | Use L1 (Caffeine) + L2 (Redis) multi-level |
| Hot key on single node | Node overload | Local cache, key splitting, read replicas |
| Mass expiration at same time | Cache avalanche, DB overload | Add random jitter to TTL |
| No Bloom filter for penetration | Non-existent keys hit DB | Use Bloom filter or cache null values |
| Multi-key ops without hash tag | CROSSSLOT error in cluster | Use hash tags `{prefix}` for related keys |
| Blocking Redis operations | Blocks event loop | Use async (Lettuce reactive), timeouts |

## References

- Redis Documentation: https://redis.io/docs/
- Caffeine Cache: https://github.com/ben-manes/caffeine
- Cache-Aside Pattern: https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside
- Redis Cluster: https://redis.io/docs/management/scaling/
- Cache Invalidation: https://martinfowler.com/bliki/TwoHardThings.html
- Redis Best Practices: https://redis.io/docs/management/best-practices/
- Cache Penetration/Breakdown/Avalanche: https://github.com/redisson/redisson/wiki
