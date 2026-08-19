# Redis Cache Guidelines

> Best practices for Redis 6.x development, caching patterns, and data structure selection.

## Core Principles

1. **Redis is a cache, not a primary database** — data can be evicted, plan for cache misses
2. **Always set TTL** — no permanent cache entries (except specific use cases like config)
3. **Weak dependency** — Redis down must not bring down the application (graceful degradation)
4. **Use the right data structure** — don't use String for everything
5. **Avoid O(n) commands in production** — `KEYS`, `HGETALL` on large hashes, `SMEMBERS` on large sets

## Key Design

### Naming Conventions

```
# ✅ Good - colon-separated, descriptive, with entity type and ID
cbol:user:12345:profile          # user profile
cbol:user:12345:sessions         # user sessions
cbol:conversation:67890:messages # conversation messages
cbol:conversation:67890:members  # conversation members
cbol:cache:api:conversations:list # API response cache
cbol:rate_limit:user:12345:send  # rate limiting
cbol:lock:conversation:67890      # distributed lock
cbol:session:websocket:abc123     # WebSocket session

# ❌ Bad - vague, no namespace, inconsistent
user12345
data
temp_key
mykey
cache_123
```

### Key Design Rules

```java
// ✅ Good - use constants for key patterns
public final class RedisKeys {
    private static final String PREFIX = "cbol";

    public static String userProfile(Long userId) {
        return String.format("%s:user:%d:profile", PREFIX, userId);
    }

    public static String userSessions(Long userId) {
        return String.format("%s:user:%d:sessions", PREFIX, userId);
    }

    public static String conversationMessages(Long conversationId) {
        return String.format("%s:conversation:%d:messages", PREFIX, conversationId);
    }

    public static String rateLimitSend(Long userId) {
        return String.format("%s:rate_limit:user:%d:send", PREFIX, userId);
    }

    public static String distributedLock(String resource) {
        return String.format("%s:lock:%s", PREFIX, resource);
    }
}

// ❌ Bad - hardcoded keys everywhere
redis.opsForValue().set("user:" + userId, user);
redis.opsForValue().set("conv_" + convId + "_msgs", messages);
```

## Data Structure Selection

| Use Case | Recommended Type | Commands |
|----------|-----------------|----------|
| Simple key-value cache | String | `GET`, `SET`, `SETEX` |
| Counter / rate limiting | String (INCR) | `INCR`, `INCRBY`, `EXPIRE` |
| Object with fields | Hash | `HSET`, `HGET`, `HMGET`, `HINCRBY` |
| Timeline / recent items | Sorted Set | `ZADD`, `ZRANGE`, `ZREVRANGE`, `ZREM` |
| Unique items / tags | Set | `SADD`, `SISMEMBER`, `SREM`, `SCARD` |
| Message queue | List | `LPUSH`, `BRPOP`, `RPUSH`, `BLPOP` |
| Leaderboard | Sorted Set | `ZADD`, `ZREVRANK`, `ZRANGEBYSCORE` |
| Distributed lock | String (SET NX) | `SET NX EX`, `DEL` (with Lua for safe unlock) |
| Geo location | Geo | `GEOADD`, `GEORADIUS` |
| Bitmaps (user active days) | String (bitmap) | `SETBIT`, `GETBIT`, `BITCOUNT` |
| HyperLogLog (unique count) | HyperLogLog | `PFADD`, `PFCOUNT` |

### Hash vs String for Objects

```java
// ✅ Good - Hash for objects with multiple fields (partial updates)
String key = RedisKeys.userProfile(userId);
redis.opsForHash().putAll(key, Map.of(
    "name", user.getName(),
    "avatar", user.getAvatar(),
    "status", user.getStatus(),
    "lastSeen", user.getLastSeen().toString()
));
redis.expire(key, Duration.ofHours(1));

// Partial update (only update changed field)
redis.opsForHash().put(key, "status", "ONLINE");

// Get specific fields (no need to fetch all)
String name = (String) redis.opsForHash().get(key, "name");

// ❌ Bad - String for objects (full overwrite, full fetch)
redis.opsForValue().set(key, objectMapper.writeValueAsString(user), Duration.ofHours(1));
// To update one field: read entire object, modify, write back (race condition!)
```

### Sorted Set for Timelines

```java
// ✅ Good - Sorted Set for message timeline (score = timestamp)
String key = RedisKeys.conversationMessages(conversationId);

// Add message (score = epoch millis)
redis.opsForZSet().add(key, messageId, System.currentTimeMillis());

// Get recent messages (paginated by score)
Set<ZSetOperations.TypedTuple<Object>> messages = redis.opsForZSet()
    .reverseRangeByScoreWithScores(key, 0, maxTimestamp, 0, 50);

// Remove old messages (keep last 1000)
redis.opsForZSet().removeRangeByRank(key, 0, -1001);

// TTL
redis.expire(key, Duration.ofDays(7));
```

## Caching Patterns

### Cache-Aside (Read-Through)

```java
// ✅ Good - cache-aside pattern
public User getUser(Long userId) {
    String key = RedisKeys.userProfile(userId);

    // 1. Try cache
    User cached = (User) redis.opsForHash().entries(key);
    if (cached != null && !cached.isEmpty()) {
        return mapToUser(cached);
    }

    // 2. Cache miss → load from DB
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    // 3. Write to cache (with TTL)
    redis.opsForHash().putAll(key, mapFromUser(user));
    redis.expire(key, Duration.ofMinutes(30));

    return user;
}

// ✅ Good - invalidate cache on update
@Transactional
public User updateUser(Long userId, UpdateUserRequest request) {
    User user = userRepository.findById(userId).orElseThrow();
    user.update(request);
    userRepository.save(user);

    // Invalidate cache (not update - avoids stale data from concurrent updates)
    redis.delete(RedisKeys.userProfile(userId));

    return user;
}
```

### Write-Through (for critical data)

```java
// ✅ Good - write-through for session data (always in cache)
public void updateSession(Long userId, SessionData session) {
    String key = RedisKeys.userSessions(userId);
    // Write to cache AND database atomically (or cache first, then DB)
    redis.opsForValue().set(key, session, Duration.ofHours(24));
    sessionRepository.save(userId, session);
}
```

### Cache Stampede Prevention

```java
// ✅ Good - prevent cache stampede with distributed lock
public List<Message> getRecentMessages(Long conversationId) {
    String key = RedisKeys.conversationMessages(conversationId);
    String lockKey = RedisKeys.distributedLock("rebuild:" + key);

    // 1. Try cache
    List<Message> cached = getFromCache(key);
    if (cached != null) return cached;

    // 2. Try to acquire lock (only one thread rebuilds)
    Boolean locked = redis.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 3. Double-check cache (another thread might have rebuilt)
            cached = getFromCache(key);
            if (cached != null) return cached;

            // 4. Rebuild from DB
            List<Message> messages = messageRepository.findRecent(conversationId);
            setCache(key, messages, Duration.ofMinutes(5));
            return messages;
        } finally {
            redis.delete(lockKey);
        }
    } else {
        // 5. Another thread is rebuilding → wait and retry
        Thread.sleep(100);
        return getFromCache(key); // might be populated now
    }
}
```

## Rate Limiting

```java
// ✅ Good - sliding window rate limiter with Lua script
public boolean isAllowed(Long userId, String action, int limit, Duration window) {
    String key = String.format("cbol:rate_limit:%s:%d:%s", action, userId,
        Instant.now().getEpochSecond() / window.getSeconds());

    // Use Lua for atomic INCR + EXPIRE
    String luaScript = """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """;

    Long count = redis.execute(
        new DefaultRedisScript<>(luaScript, Long.class),
        List.of(key),
        String.valueOf(window.getSeconds())
    );

    return count != null && count <= limit;
}

// Usage: 10 messages per minute per user
if (!isAllowed(userId, "send", 10, Duration.ofMinutes(1))) {
    throw new RateLimitExceededException("send", userId);
}
```

## Distributed Locks

```java
// ✅ Good - safe distributed lock with Lua unlock
public <T> T withLock(String resource, Duration timeout, Supplier<T> action) {
    String lockKey = RedisKeys.distributedLock(resource);
    String lockValue = UUID.randomUUID().toString(); // unique owner ID

    // Acquire lock (SET NX EX = atomic)
    Boolean acquired = redis.opsForValue()
        .setIfAbsent(lockKey, lockValue, timeout);

    if (!Boolean.TRUE.equals(acquired)) {
        throw new LockAcquisitionException(resource);
    }

    try {
        return action.get();
    } finally {
        // Safe unlock with Lua (only delete if value matches)
        String unlockScript = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """;
        redis.execute(new DefaultRedisScript<>(unlockScript, Long.class),
            List.of(lockKey), lockValue);
    }
}
```

## Connection Configuration

```yaml
# ✅ Good - Redis connection configuration
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 2000ms          # 2s command timeout
      lettuce:
        pool:
          max-active: 16        # max connections
          max-idle: 8           # max idle connections
          min-idle: 2           # min idle connections
          max-wait: 2000ms      # max wait for connection
      shutdown-timeout: 100ms

# ❌ Bad - no timeout, huge pool
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # no timeout = can hang forever!
      lettuce:
        pool:
          max-active: 100  # too many = Redis overload
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| `KEYS *` in production | O(n), blocks Redis, can crash | Use `SCAN` or Redis Search |
| No TTL on cache keys | Memory fills up, OOM | Always set TTL (except specific use cases) |
| Using String for everything | Can't do partial updates, inefficient | Use Hash, Set, Sorted Set, List |
| `HGETALL` on large hashes | O(n), returns all fields | Use `HMGET` for specific fields |
| Large values (>100KB) | Slow, memory fragmentation | Compress, split, or use different storage |
| Redis as primary DB | Data loss on crash/eviction | Use MySQL/MongoDB as source of truth |
| No graceful degradation | Redis down = app down | Catch exceptions, fallback to DB |
| `DEL` for lock without value check | Can delete someone else's lock | Use Lua script with value check |
| Hot key (all traffic to one key) | Single shard overload | Shard the key, use local cache |
| `INCR` without `EXPIRE` | Counter grows forever, no rate limit reset | Use Lua for atomic INCR+EXPIRE |
| Storing JSON as String | Can't query fields, full read/write | Use Hash for fields, RedisJSON for nested |
| Long-running Lua scripts | Blocks Redis (single-threaded) | Keep scripts short, use pipelining for batch |

## References

- Redis Documentation: https://redis.io/docs/
- Redis Best Practices: https://github.com/simba-git/redis-best-practices
- Redis Anti-Patterns: https://redis.io/tutorials/redis-anti-patterns-every-developer-should-avoid/
- Spring Data Redis: https://docs.spring.io/spring-data/redis/reference/
- Turms (Redis usage): https://github.com/turms-im/turms
