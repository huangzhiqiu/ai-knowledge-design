# Concurrency Model

> Threading and concurrency design for Java IM systems.

## Thread Pool Strategy

### Gateway (Netty)
```
Boss Group: 1 thread (accept connections)
Worker Group: CPU * 2 threads (IO processing)
```

### Business Logic
```
@Bean("messageExecutor")
public ThreadPoolTaskExecutor messageExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
    executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
    executor.setQueueCapacity(10000);
    executor.setThreadNamePrefix("msg-worker-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

### Thread Pool Inventory

| Pool | Core Size | Max Size | Queue | Purpose |
|------|-----------|----------|-------|---------|
| Netty boss | 1 | 1 | - | Accept connections |
| Netty worker | CPU*2 | CPU*2 | - | IO processing |
| messageExecutor | CPU | CPU*2 | 10000 | Message processing |
| pushExecutor | 4 | 8 | 5000 | Push notifications |
| dbExecutor | CPU | CPU*2 | 2000 | Async DB writes |
| scheduledExecutor | 2 | 4 | - | Heartbeat, cleanup |

## Shared State Management

### Session Registry (Concurrent)
```java
// Thread-safe session registry
@Component
public class SessionRegistry {
    // user_id -> device_id -> Channel
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Channel>> sessions = 
        new ConcurrentHashMap<>();
    
    public void register(String userId, String deviceId, Channel channel) {
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(deviceId, channel);
    }
    
    public void unregister(String userId, String deviceId) {
        sessions.computeIfPresent(userId, (k, devices) -> {
            devices.remove(deviceId);
            return devices.isEmpty() ? null : devices;
        });
    }
    
    public List<Channel> getOnlineChannels(String userId) {
        ConcurrentHashMap<String, Channel> devices = sessions.get(userId);
        return devices == null ? List.of() : new ArrayList<>(devices.values());
    }
}
```

### Message Deduplication
```java
@Component
public class MessageDeduplicator {
    // Caffeine cache for recent msg_ids
    private final Cache<String, Boolean> seenMessages = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(24))
        .maximumSize(1_000_000)
        .build();
    
    public boolean isDuplicate(String msgId) {
        return seenMessages.getIfPresent(msgId) != null;
    }
    
    public void markSeen(String msgId) {
        seenMessages.put(msgId, Boolean.TRUE);
    }
}
```

## Locking Strategy

### When to Use Locks

| Scenario | Mechanism | Reason |
|----------|-----------|--------|
| Session registry | ConcurrentHashMap | No explicit lock needed |
| Seq ID allocation | Redis INCR / DB auto-increment | Atomic operation |
| User balance update | Distributed lock (Redis) | Prevent double spend |
| Group member count | Atomic counter (Redis) | Increment/decrement |
| Message order | Single-threaded per conversation | Natural ordering |

### Distributed Lock (Redis)
```java
@Component
public class DistributedLock {
    private final StringRedisTemplate redisTemplate;
    
    public boolean tryLock(String key, String value, Duration timeout) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, timeout);
        return Boolean.TRUE.equals(result);
    }
    
    public void unlock(String key, String value) {
        // Lua script: check value then delete (atomic)
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                       "return redis.call('del', KEYS[1]) else return 0 end";
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), 
            List.of(key), value);
    }
}
```

## Per-Conversation Serialization

To guarantee message ordering within a conversation, process messages for the same conversation sequentially:

```java
// Route messages by conversation hash to fixed thread
int threadIndex = Math.abs(conversationId.hashCode()) % threadCount;
executors[threadIndex].submit(() -> processMessage(message));
```

- Messages in same conversation always processed by same thread
- No lock needed for ordering
- Different conversations processed in parallel

## Async Processing Patterns

### CompletableFuture
```java
public CompletableFuture<Message> sendMessage(Message msg) {
    return CompletableFuture.supplyAsync(() -> {
        messageRepository.save(msg);
        return msg;
    }, messageExecutor).thenApplyAsync(saved -> {
        pushToOnlineUsers(saved);
        return saved;
    }, pushExecutor);
}
```

### Event-Driven with Spring Events
```java
// Publisher
applicationEventPublisher.publishEvent(new MessageSentEvent(message));

// Listener (async)
@Async("messageExecutor")
@EventListener
public void onMessageSent(MessageSentEvent event) {
    pushService.sendPush(event.getMessage());
}
```

## Backpressure Handling

- Use bounded queues with CallerRunsPolicy
- Monitor queue depth, alert when > 80%
- Rate limit incoming messages per user
- Shed load gracefully when overloaded

## Reference: Netty Threading Model
Netty uses EventLoopGroup: one boss group accepts, one worker group handles IO. Each Channel bound to one EventLoop (thread) for its lifetime -> no synchronization needed for Channel operations.
