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

---

## Open Source Project: Turms Lock-Free Concurrency

Turms 的并发模型是 IM 领域的极致优化案例，其核心设计哲学是**"几乎无锁，只有 CAS"**。

### 核心原则：线程数恒定 = CPU 核心数

```
传统思路：线程池大小 = CPU * 2 或更多，用队列缓冲
Turms 思路：线程数 = CPU 核心数，不额外创建业务线程
```

**为什么线程数恒定？**
- 线程切换有开销，过多线程导致上下文切换浪费 CPU
- IM 业务是 IO 密集型（网络+数据库），全异步模式下不需要阻塞等待
- 恒定线程数避免了线程池的动态调整开销

### 全异步 Reactive 模型

Turms 基于 **Project Reactor** 实现全异步：
- 所有 IO 操作（网络、数据库、Redis）都返回 `Mono`/`Flux`
- 业务逻辑在 Netty EventLoop 线程上链式调用，不阻塞
- 数据库调用也异步（R2DBC / 异步 MongoDB 驱动）
- 没有 `threadPool.submit()` 这种模式，不需要业务线程池

```java
// Turms 风格：全异步，不阻塞 EventLoop
public Mono<Message> sendMessage(Message msg) {
    return messageRepository.save(msg)           // 异步写 DB
        .flatMap(saved -> pushService.push(saved)) // 异步推送
        .subscribeOn(Schedulers.immediate());     // 在当前线程执行，不切换
}
```

### 无锁设计：CAS 替代 synchronized

| 场景 | 传统方案 | Turms 方案 |
|------|---------|-----------|
| Session 注册 | `synchronized` 或 `ReentrantLock` | `ConcurrentHashMap.computeIfAbsent` (CAS) |
| 计数器 | `AtomicLong.incrementAndGet()` | `LongAdder` (高并发下更优) |
| 状态变更 | `synchronized` 块 | `AtomicReference.compareAndSet()` |
| 缓存更新 | `putIfAbsent` + 锁 | `ConcurrentHashMap.compute` (CAS) |
| 连接计数 | `AtomicInteger` | `LongAdder` |

**关键洞察**：在全异步模型中，同一个 Channel 的所有操作都在同一个 EventLoop 线程上执行，天然线程安全，不需要锁。跨 Channel 的共享状态用 `ConcurrentHashMap` 和 CAS 原子操作。

### 内存优化

Turms 在内存层面做了深度优化：
1. **智能堆内/堆外分配**：根据数据生命周期选择堆内（短生命周期）或堆外（长生命周期、IO buffer）
2. **重构 MongoDB/Redis 客户端**：消除客户端内部的冗余对象分配
3. **对象池**：高频创建的对象（如消息包装器）使用对象池复用
4. **零拷贝**：网络传输使用 `CompositeByteBuf` 避免内存拷贝

### 与传统线程池模型的对比

| 维度 | 传统线程池模型 | Turms 无锁模型 |
|------|--------------|---------------|
| 线程数 | CPU*2 + 业务线程池 | = CPU 核心数 |
| 锁 | 大量 synchronized/lock | 几乎无锁，只有 CAS |
| 阻塞 | 线程池等待 IO | 全异步，无阻塞 |
| 上下文切换 | 频繁（线程多） | 极少（线程恒定） |
| 实现复杂度 | 低 | 高（Reactive 编程门槛） |
| 适用场景 | 通用业务 | 高并发 IM/网关 |

### 实践建议

对于 CBOL 项目：
- **Gateway 层**：参考 Turms，Netty EventLoop 上不阻塞，全异步处理
- **业务层**：如果业务逻辑复杂（如 AI 接回话、状态机），可以保留业务线程池，但确保 Gateway→Business 的边界清晰
- **共享状态**：优先用 `ConcurrentHashMap` + CAS，避免显式锁
- **数据库**：评估是否使用 R2DBC 异步驱动，避免阻塞 Netty 线程
- **不要盲目模仿**：Turms 的无锁模型适合纯 IM 网关，CBOL 有 AI 处理、回话转发等复杂业务，全 Reactive 可能增加开发成本
