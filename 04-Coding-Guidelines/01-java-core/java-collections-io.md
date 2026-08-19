# Java Collections & I/O Guidelines

> Best practices for Java Collections Framework, I/O operations, and resource management.

## Collections Framework

### Choosing the Right Collection

| Use Case | Recommended | Avoid |
|----------|------------|-------|
| Random access by index | `ArrayList` | `LinkedList` |
| Frequent insert/delete in middle | `LinkedList` | `ArrayList` |
| Sorted unique elements | `TreeSet` | `HashSet` (if ordering needed) |
| Fast lookup, no ordering | `HashMap` / `HashSet` | `TreeMap` / `TreeSet` |
| Thread-safe map | `ConcurrentHashMap` | `Collections.synchronizedMap` |
| Bounded buffer | `ArrayBlockingQueue` | `LinkedList` as queue |
| Priority ordering | `PriorityQueue` | Manual sorting |

### Collection Best Practices

1. **Program to interfaces, not implementations**
```java
// ✅ Good
List<String> list = new ArrayList<>();
Map<String, User> map = new ConcurrentHashMap<>();

// ❌ Bad
ArrayList<String> list = new ArrayList<>();
HashMap<String, User> map = new HashMap<>();
```

2. **Specify initial capacity for known sizes**
```java
// ✅ Good - avoids resizing
List<User> users = new ArrayList<>(expectedSize);
Map<String, User> userMap = new HashMap<>(expectedSize * 2); // load factor 0.75

// ❌ Bad - causes multiple resizes
List<User> users = new ArrayList<>();
for (int i = 0; i < 10000; i++) { users.add(...); }
```

3. **Use `ConcurrentHashMap` for thread-safe maps**
```java
// ✅ Good - lock-free reads, segmented writes
ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

// ❌ Bad - coarse-grained locking
Map<String, Session> sessions = Collections.synchronizedMap(new HashMap<>());
```

4. **Use `CopyOnWriteArrayList` for read-heavy, write-rare lists**
```java
// ✅ Good for event listeners, configuration
List<EventListener> listeners = new CopyOnWriteArrayList<>();

// ❌ Bad for write-heavy scenarios
List<Message> messages = new CopyOnWriteArrayList<>(); // O(n) writes!
```

5. **Avoid `null` in collections**
```java
// ✅ Good - use Optional or empty collection
Optional<User> user = Optional.ofNullable(map.get(key));
return Collections.emptyList(); // instead of null

// ❌ Bad - causes NullPointerException
List<String> list = null;
list.size(); // NPE!
```

6. **Use `EnumSet` / `EnumMap` for enum keys**
```java
// ✅ Good - bit vector internally, O(1) operations
Set<Status> activeStatuses = EnumSet.of(Status.ACTIVE, Status.PENDING);
Map<Status, List<Message>> messagesByStatus = new EnumMap<>(Status.class);
```

7. **Prefer `Arrays.asList` for fixed-size lists, but beware**
```java
// ✅ Good - immutable view
List<String> fixed = List.of("a", "b", "c"); // Java 9+, truly immutable

// ⚠️ Arrays.asList returns fixed-size, but NOT immutable
List<String> fixed = Arrays.asList("a", "b", "c");
fixed.set(0, "x"); // ✅ works
fixed.add("d");    // ❌ UnsupportedOperationException
```

### Stream API Best Practices

1. **Prefer streams for declarative collection processing**
```java
// ✅ Good - declarative, readable
Map<String, List<Message>> bySender = messages.stream()
    .filter(m -> !m.isDeleted())
    .collect(Collectors.groupingBy(Message::getSenderId));

// ❌ Bad - imperative, error-prone
Map<String, List<Message>> bySender = new HashMap<>();
for (Message m : messages) {
    if (!m.isDeleted()) {
        bySender.computeIfAbsent(m.getSenderId(), k -> new ArrayList<>()).add(m);
    }
}
```

2. **Avoid stateful intermediate operations**
```java
// ❌ Bad - stateful lambda, non-deterministic in parallel
AtomicInteger counter = new AtomicInteger();
list.stream()
    .map(e -> e.transform(counter.incrementAndGet())) // stateful!
    .collect(toList());

// ✅ Good - use IntStream.range or index-based approach
IntStream.range(0, list.size())
    .mapToObj(i -> list.get(i).transform(i))
    .collect(toList());
```

3. **Use `parallelStream()` only for CPU-bound, stateless operations**
```java
// ✅ Good - CPU-bound, stateless, large dataset
List<Result> results = largeList.parallelStream()
    .map(this::cpuIntensiveTransform)
    .collect(toList());

// ❌ Bad - I/O bound, shared state, small dataset
messages.parallelStream()
    .forEach(this::sendToDatabase); // connection pool exhaustion!
```

## I/O Operations

### Try-with-Resources (Mandatory)

```java
// ✅ Good - auto-closes all resources
try (FileInputStream fis = new FileInputStream(file);
     BufferedInputStream bis = new BufferedInputStream(fis);
     ObjectInputStream ois = new ObjectInputStream(bis)) {
    return ois.readObject();
}

// ❌ Bad - resource leak on exception
FileInputStream fis = new FileInputStream(file);
Object obj = new ObjectInputStream(fis).readObject();
fis.close(); // not reached if readObject throws!
```

### NIO.2 (java.nio.file) Best Practices

1. **Use `Files` utility class**
```java
// ✅ Good - NIO.2
byte[] data = Files.readAllBytes(path);
Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

// ❌ Bad - old File I/O
FileInputStream fis = new FileInputStream(file);
// ... manual buffer management
```

2. **Use `Path` instead of `File`**
```java
// ✅ Good
Path path = Paths.get("data", "messages", "2026.log");
Path resolved = basePath.resolve("subdir").resolve("file.txt");

// ❌ Bad
File file = new File("data/messages/2026.log");
```

3. **Stream directory contents lazily**
```java
// ✅ Good - lazy, auto-closing
try (Stream<Path> files = Files.walk(basePath)) {
    files.filter(Files::isRegularFile)
         .filter(p -> p.toString().endsWith(".log"))
         .forEach(this::processLogFile);
}

// ❌ Bad - eager, memory-heavy
File[] files = baseDir.listFiles(); // loads all into memory
```

### Serialization

1. **Prefer JSON/Protobuf over Java Serialization**
```java
// ✅ Good - cross-language, version-tolerant
byte[] data = objectMapper.writeValueAsBytes(message); // Jackson
Message msg = objectMapper.readValue(data, Message.class);

// ⚠️ Java Serialization - only for internal, trusted, short-lived data
// - Security risk (deserialization attacks)
// - Version fragility
// - Not cross-language
```

2. **If using Java Serialization, use `serialVersionUID`**
```java
// ✅ Good
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    // fields...
}
```

## Resource Management

### Connection Pools

```java
// ✅ Good - use connection pools, never create connections per request
@Bean
public RedisConnectionFactory redisConnectionFactory() {
    LettuceClientConfiguration config = LettuceClientConfiguration.builder()
        .commandTimeout(Duration.ofSeconds(2))
        .build();
    return new LettuceConnectionFactory(redisStandaloneConfig, config);
}

// ❌ Bad - connection per request
public void save(String key, String value) {
    try (Jedis jedis = new Jedis("localhost", 6379)) { // new connection!
        jedis.set(key, value);
    }
}
```

### Thread Pools

```java
// ✅ Good - bounded, named, monitored
ExecutorService executor = new ThreadPoolExecutor(
    corePoolSize,
    maxPoolSize,
    keepAliveTime, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(queueCapacity),
    new ThreadFactoryBuilder().setNameFormat("message-worker-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy() // backpressure
);

// ❌ Bad - unbounded, unmonitored
ExecutorService executor = Executors.newFixedThreadPool(100); // unbounded queue!
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| `KeySet` iteration for map lookup | O(n) instead of O(1) | Use `map.get(key)` directly |
| `list.contains()` in loop | O(n²) | Use `HashSet` for O(1) lookup |
| `String` concatenation in loop | O(n²) memory | Use `StringBuilder` |
| `System.out.println` in production | No log levels, no structure | Use SLF4J logger |
| `new File()` for path operations | Legacy API | Use `Path` / `Files` |
| Catching `Exception` / `Throwable` | Swallows unexpected errors | Catch specific exceptions |
| Ignoring return value of `add`/`remove` | Silent failures | Check return value or use `assert` |

## References

- Java Collections Framework docs: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/doc-files/coll-reference.html
- Java I/O docs: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/doc-files/io.html
- Effective Java (Joshua Bloch): Item 57 (minimize scope), Item 68 (executors), Item 69 (wait/notify)
