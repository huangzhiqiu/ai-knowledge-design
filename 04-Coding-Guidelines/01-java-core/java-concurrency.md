# Concurrency Guidelines

> Java concurrency best practices for building safe, high-performance concurrent systems.

## Thread Pool Design

### [Mandatory] Use thread pools, not new Thread()
- Thread creation is expensive (~1MB stack each)
- Thread pools reuse threads, control concurrency
- Never use `Executors.newFixedThreadPool()` without understanding queue size (unbounded queue = OOM risk)

### Thread Pool Configuration
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,           // minimum threads kept alive
    maximumPoolSize,        // maximum threads
    keepAliveTime,          // idle thread timeout
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(capacity),  // BOUNDED queue
    new ThreadFactoryBuilder()
        .setNameFormat("msg-worker-%d")
        .setDaemon(false)
        .build(),
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejection policy
);
```

### Sizing Guidelines
| Task Type | corePoolSize | queueCapacity |
|-----------|-------------|---------------|
| CPU-bound | CPU cores | small (10-100) |
| IO-bound | CPU cores * 2 | large (1000-10000) |
| Mixed | Profile and adjust | based on latency |

### Rejection Policies
| Policy | Behavior | Use Case |
|--------|----------|----------|
| AbortPolicy | Throw RejectedExecutionException | Default, fail fast |
| CallerRunsPolicy | Caller thread executes | Backpressure, graceful |
| DiscardPolicy | Silently discard | Non-critical tasks |
| DiscardOldestPolicy | Discard oldest in queue | Real-time, drop stale |

## Thread Safety

### [Mandatory] ThreadLocal must be cleaned up
```java
try {
    UserContext.set(currentUser);
    processRequest();
} finally {
    UserContext.remove();  // MANDATORY - prevents memory leak in thread pool
}
```

### [Mandatory] Use concurrent collections
| Need | Use | Avoid |
|------|-----|-------|
| Concurrent Map | ConcurrentHashMap | HashMap |
| Concurrent List | CopyOnWriteArrayList (read-heavy) | ArrayList, Vector |
| Concurrent Set | ConcurrentHashMap.newKeySet() | HashSet |
| Concurrent Queue | LinkedBlockingQueue, ArrayBlockingQueue | LinkedList |
| Atomic counter | AtomicInteger, AtomicLong | int/long with synchronized |

### Immutable Objects
- Immutable objects are inherently thread-safe
- Make fields final, no setters
- Use `Collections.unmodifiableList()` for defensive copies
- Preferred for shared state

### Volatile
- Use for visibility (not atomicity)
- One thread writes, multiple threads read
- Does NOT make i++ atomic (use AtomicInteger)
```java
private volatile boolean running = true;

public void stop() {
    running = false;  // visible to all threads immediately
}
```

### Synchronized
- Use for mutual exclusion (atomicity + visibility)
- Prefer synchronized blocks over methods (smaller critical section)
- Use private lock object, not `this`
```java
private final Object lock = new Object();

public void doWork() {
    synchronized (lock) {
        // critical section
    }
}
```

## Common Concurrency Pitfalls

### 1. Deadlock
- Circular wait between locks
- **Prevention**: always acquire locks in same order, use tryLock with timeout
```java
// Bad - can deadlock if A and B call each other
synchronized(lockA) {
    synchronized(lockB) { ... }
}

// Good - timeout prevents permanent deadlock
if (lockA.tryLock(5, TimeUnit.SECONDS)) {
    try {
        if (lockB.tryLock(5, TimeUnit.SECONDS)) {
            try { ... }
            finally { lockB.unlock(); }
        }
    } finally { lockA.unlock(); }
}
```

### 2. Race Condition
- Check-then-act is not atomic
- Use atomic operations or synchronization
```java
// Bad - check then act (race condition)
if (!map.containsKey(key)) {
    map.put(key, value);  // another thread may have put in between
}

// Good - atomic operation
map.putIfAbsent(key, value);
```

### 3. Visibility Problem
- Changes by one thread not visible to another
- Use volatile, synchronized, or concurrent collections
- Never assume variable changes are visible without synchronization

### 4. False Sharing
- Threads on different cores modify adjacent variables in same cache line
- Use `@Contended` or padding to separate
- Mainly affects high-performance code

## Asynchronous Programming

### CompletableFuture
```java
// Chain async operations
CompletableFuture<User> userFuture = CompletableFuture
    .supplyAsync(() -> userService.findById(userId), executor)
    .thenApplyAsync(user -> enrichUser(user), executor)
    .exceptionally(ex -> {
        log.error("Failed to load user", ex);
        return fallbackUser();
    });

// Combine multiple futures
CompletableFuture.allOf(future1, future2, future3).join();
```

### Timeout Handling
```java
// Always set timeouts for async operations
userFuture.orTimeout(5, TimeUnit.SECONDS);
// or
userFuture.completeOnTimeout(defaultUser, 5, TimeUnit.SECONDS);
```

### Don't block in async code
- Never call `.get()` or `.join()` in a thread pool worker (deadlock risk)
- Use `thenApply`, `thenCompose`, `thenCombine` for chaining
- Use dedicated thread pool for blocking operations

## Lock-Free Programming

### CAS (Compare-And-Swap)
```java
AtomicInteger counter = new AtomicInteger(0);

// Lock-free increment
while (true) {
    int current = counter.get();
    int next = current + 1;
    if (counter.compareAndSet(current, next)) {
        break;  // success
    }
    // retry if value changed
}

// Simpler:
counter.incrementAndGet();
```

### When to use locks vs CAS
| Scenario | Recommendation |
|----------|---------------|
| Simple counter | AtomicInteger |
| Single variable update | CAS / AtomicReference |
| Multiple variables | synchronized / ReentrantLock |
| Long critical section | ReentrantLock (with timeout) |
| Read-heavy, write-rare | ReadWriteLock / StampedLock |

## Performance Considerations

### Reduce Lock Contention
- Shrink critical sections
- Use lock striping (ConcurrentHashMap has 16 segments)
- Use read-write locks for read-heavy
- Consider lock-free algorithms

### Avoid Shared Mutable State
- Best concurrency strategy: no shared state
- Thread confinement (each thread has own data)
- Immutable objects passed between threads
- Message passing instead of shared memory

### Thread Pool Monitoring
- Monitor: active count, queue size, completed tasks, rejected tasks
- Alert on: queue growing, rejection count increasing
- Tune based on metrics, not guesswork

## References
- Java Concurrency in Practice by Brian Goetz
- Oracle Java Concurrent Programming Guide
- https://docs.oracle.com/javase/tutorial/essential/concurrency/
