# Java Coding Standards

> Synthesized from Google Java Style Guide and Alibaba Java Development Manual.

## Naming Conventions

### General Rules
- Names should be descriptive, avoid abbreviation (except well-known: id, url, dto, vo)
- Use English, no pinyin
- Class/Interface: UpperCamelCase
- Method/Variable: lowerCamelCase
- Constant: UPPER_SNAKE_CASE
- Package: all lowercase, no underscores

### Detailed Rules

| Element | Convention | Example |
|---------|-----------|---------|
| Class | UpperCamelCase, noun or noun phrase | `UserService`, `MessageHandler` |
| Interface | UpperCamelCase, adjective or noun | `Runnable`, `Serializable` |
| Method | lowerCamelCase, verb or verb phrase | `sendMessage()`, `getUserById()` |
| Variable | lowerCamelCase | `userId`, `messageContent` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| Package | lowercase, reverse domain | `com.cbol.im.service` |
| Boolean variable | prefix with is/has/can/should | `isValid`, `hasPermission` |
| Test class | ClassName + Test | `UserServiceTest` |

### [Mandatory] Long type suffix
- Use uppercase `L`, never lowercase `l` (looks like 1)
- Good: `Long value = 100L;`
- Bad: `Long value = 100l;`

## Code Formatting

### Indentation
- 4 spaces per indent level (no tabs)
- Continuation line: 8 spaces (or align with opening paren)

### Line Length
- Maximum 100 characters (Google) / 120 (Alibaba)
- Break long lines at operators

### Blank Lines
- One blank line between methods
- One blank line between logical sections within method
- No consecutive blank lines

### Braces
- K&R style (opening brace same line)
- Even single-line if/for must use braces
```java
// Good
if (condition) {
    doSomething();
}

// Bad
if (condition)
    doSomething();
```

### Imports
- No wildcard imports (`import com.example.*`)
- Order: static imports, then regular imports, grouped by package
- Remove unused imports

## Object-Oriented Programming

### [Mandatory] Avoid using static variables to store mutable state
- Static variables are shared across all instances and threads
- Use instance variables or dependency injection instead

### [Mandatory] Equals and hashCode
- Override both together (contract: equal objects must have same hashCode)
- Use `Objects.equals()` for null-safe comparison
- For entity classes, use business key (not database ID) for equals

### [Mandatory] BigDecimal for monetary calculations
- Never use float/double for money (precision loss)
- Use `BigDecimal` with `RoundingMode.HALF_UP`
```java
// Good
BigDecimal price = new BigDecimal("19.99");
BigDecimal total = price.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

// Bad
double total = 19.99 * 3; // precision loss
```

### [Recommended] Use composition over inheritance
- Favor has-a over is-a
- Inheritance for "is-a" relationship only
- Use interfaces for capability (Runnable, Serializable)

### [Mandatory] Utility classes
- Private constructor (prevent instantiation)
- All methods static
- Final class (prevent extension)

## Collection Handling

### [Mandatory] Specify initial capacity for collections
```java
// Good
List<String> list = new ArrayList<>(100);
Map<String, Object> map = new HashMap<>(256);

// Bad (triggers multiple resizes)
List<String> list = new ArrayList<>();
```

### [Mandatory] Use entrySet() for Map iteration
```java
// Good
for (Map.Entry<String, Object> entry : map.entrySet()) {
    String key = entry.getKey();
    Object value = entry.getValue();
}

// Bad (two lookups per entry)
for (String key : map.keySet()) {
    Object value = map.get(key);
}
```

### [Mandatory] Don't use List.remove() in foreach
```java
// Bad - throws ConcurrentModificationException
for (String item : list) {
    if (condition) list.remove(item);
}

// Good - use Iterator
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (condition) it.remove();
}

// Good - Java 8+
list.removeIf(item -> condition);
```

### [Recommended] Use isEmpty() instead of size() == 0
```java
// Good
if (list.isEmpty()) { ... }

// Bad
if (list.size() == 0) { ... }
```

## Concurrent Programming

### [Mandatory] ThreadLocal must be removed
- Especially in thread pools (threads are reused)
- Use try-finally to clean up
```java
try {
    threadLocal.set(value);
    // business logic
} finally {
    threadLocal.remove(); // MANDATORY
}
```

### [Mandatory] Use thread pools, not new Thread()
- Use `ThreadPoolExecutor` or `Executors` factory
- Specify meaningful thread names for debugging
```java
ExecutorService executor = new ThreadPoolExecutor(
    coreSize, maxSize, keepAlive, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),
    new ThreadFactoryBuilder().setNameFormat("msg-worker-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

### [Mandatory] Avoid shared mutable state
- Use immutable objects
- Use concurrent collections (ConcurrentHashMap, CopyOnWriteArrayList)
- Use atomic variables (AtomicInteger, AtomicReference)

### [Recommended] Prefer synchronized blocks over methods
- More granular locking
- Avoid locking on `this` (use private lock object)

## Exception Handling

### [Mandatory] Don't catch NullPointerException
- Prevent NPE with null checks (`Objects.requireNonNull`, `Optional`)
- NPE is a bug, not a business condition

### [Mandatory] Don't swallow exceptions
```java
// Bad
try {
    doSomething();
} catch (Exception e) {
    // empty - swallows exception
}

// Good
try {
    doSomething();
} catch (SpecificException e) {
    log.error("Failed to do something", e);
    throw new BusinessException(ErrorCode.XXX, e);
}
```

### [Mandatory] Catch specific exceptions, not Exception/Throwable
- Catch the most specific exception type
- Never catch Throwable (includes Error, OOM)

### [Recommended] Use custom exception hierarchy
```
BusinessException (checked or runtime)
├── UserNotFoundException
├── PermissionDeniedException
└── MessageDeliveryException
```

## Comments

### [Mandatory] Javadoc for public APIs
- Class, interface, public methods
- Include: description, params, return, throws
```java
/**
 * Sends a message to a conversation.
 *
 * @param conversationId target conversation ID
 * @param message message content to send
 * @return the created message with assigned seq_id
 * @throws MessageDeliveryException if delivery fails
 */
Message sendMessage(String conversationId, Message message);
```

### [Recommended] Explain WHY, not WHAT
- Code should be self-documenting for "what"
- Comments explain "why" this approach
- Remove commented-out code (use Git history)

## References
- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- Alibaba Java Development Manual: https://github.com/alibaba/Alibaba-Java-Coding-Guidelines
- Effective Java by Joshua Bloch
