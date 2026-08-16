# Exception Handling & Logging Guidelines

> Standard practices for exception handling and logging in Java applications.

## Exception Handling

### Exception Hierarchy

```
Throwable
├── Error (never catch)
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── NoClassDefFoundError
└── Exception
    ├── RuntimeException (unchecked)
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   ├── IllegalStateException
    │   └── BusinessException (custom)
    └── Checked Exception
        ├── IOException
        ├── SQLException
        └── InterruptedException
```

### [Mandatory] Never catch Error or Throwable
```java
// Bad - catches OOM, StackOverflow, etc.
try {
    doSomething();
} catch (Throwable t) { ... }

// Good - catch specific exceptions
try {
    doSomething();
} catch (IOException e) { ... }
```

### [Mandatory] Never swallow exceptions
```java
// Bad - empty catch
try {
    doSomething();
} catch (Exception e) {
    // nothing here
}

// Bad - only print stack trace
try {
    doSomething();
} catch (Exception e) {
    e.printStackTrace();  // goes to stderr, not logs
}

// Good - log and handle appropriately
try {
    doSomething();
} catch (SpecificException e) {
    log.error("Failed to do something for user: {}", userId, e);
    throw new BusinessException(ErrorCode.OPERATION_FAILED, e);
}
```

### [Mandatory] Catch specific exceptions
```java
// Bad - too broad
try {
    doSomething();
} catch (Exception e) { ... }

// Good - specific
try {
    doSomething();
} catch (IOException e) {
    // handle IO error
} catch (SQLException e) {
    // handle DB error
}
```

### [Recommended] Use custom business exceptions
```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}

// Usage
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
```

### Error Code Pattern
```java
public enum ErrorCode {
    SUCCESS(0, "Success"),
    USER_NOT_FOUND(1001, "User not found"),
    INVALID_TOKEN(1002, "Invalid or expired token"),
    MESSAGE_SEND_FAILED(2001, "Failed to send message"),
    INTERNAL_ERROR(5000, "Internal server error");
    
    private final int code;
    private final String message;
    // constructor, getters
}
```

### Exception Translation
- Translate low-level exceptions to business exceptions at service boundary
- Don't expose internal exceptions (SQL, stack traces) to clients
```java
@Service
public class UserService {
    public User findById(String id) {
        try {
            return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        } catch (DataAccessException e) {
            log.error("Database error finding user: {}", id, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
```

### Global Exception Handler (Spring)
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}
```

### NullPointerException Prevention
```java
// Use Objects.requireNonNull
public void process(String input) {
    Objects.requireNonNull(input, "input must not be null");
    // ...
}

// Use Optional for nullable returns
public Optional<User> findById(String id) {
    return Optional.ofNullable(userRepository.findById(id));
}

// Use null-safe methods
String name = Objects.toString(user.getName(), "unknown");
String upper = Optional.ofNullable(name).map(String::toUpperCase).orElse("");
```

## Logging Guidelines

### Log Framework
- Use SLF4J as facade (allows switching implementations)
- Implementation: Logback or Log4j2
- Never use System.out.println or e.printStackTrace()

### Log Levels

| Level | Purpose | Example |
|-------|---------|---------|
| ERROR | System errors, failures requiring attention | DB connection failed, message delivery failed |
| WARN | Potential problems, degraded operation | Retry triggered, slow query, deprecated API used |
| INFO | Important business events, normal operation | User logged in, message sent, service started |
| DEBUG | Detailed diagnostic info | Method entry/exit, variable values, SQL queries |
| TRACE | Very detailed, full request/response | Full payload, stack traces |

### [Mandatory] Use parameterized logging
```java
// Good - no string concatenation, lazy evaluation
log.info("User {} sent message to conversation {}", userId, conversationId);

// Bad - string concatenation even if log level disabled
log.info("User " + userId + " sent message to conversation " + conversationId);
```

### [Mandatory] Include exception in error logs
```java
// Good - full stack trace
log.error("Failed to send message for user: {}", userId, e);

// Bad - no stack trace
log.error("Failed to send message: " + e.getMessage());
```

### [Mandatory] Never log sensitive data
```java
// Bad - logs password, token
log.info("User login: username={}, password={}", username, password);
log.info("Auth token: {}", token);

// Good - mask or omit
log.info("User login: username={}", username);
log.debug("Auth token length: {}", token.length());
```

### Sensitive Data Masking
| Data Type | Masking | Example |
|-----------|---------|---------|
| Password | Never log | - |
| Token/Secret | Never log | - |
| Email | mask middle | `u***@example.com` |
| Phone | mask middle | `138****1234` |
| ID card | mask middle | `110***********1234` |
| Credit card | show last 4 | `****-****-****-1234` |

### Structured Logging
```java
// Include correlation ID / trace ID in every log
MDC.put("traceId", traceId);
MDC.put("userId", userId);
try {
    log.info("Processing request");
} finally {
    MDC.clear();
}

// Logback pattern includes MDC
// %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId}] - %msg%n
```

### Log Format (JSON for production)
```json
{
  "timestamp": "2026-08-16T23:00:00.000Z",
  "level": "ERROR",
  "service": "cbol-im",
  "traceId": "abc-123",
  "userId": "user_001",
  "logger": "com.cbol.im.MessageService",
  "message": "Failed to send message",
  "exception": "java.lang.RuntimeException: ..."
}
```

### What to Log

| Always Log | Never Log |
|-----------|-----------|
| Service startup/shutdown | Passwords, tokens, secrets |
| Authentication success/failure | Full request bodies (may have secrets) |
| Business errors with context | Personal data (PII) unmasked |
| External API calls (URL, status, latency) | Credit card numbers |
| State transitions (state machine) | Encryption keys |
| Configuration changes | Full stack traces in production (use ERROR with cause) |

### Performance Considerations
- Check log level before expensive operations:
```java
if (log.isDebugEnabled()) {
    log.debug("Large object: {}", expensiveSerialization(object));
}
```
- Async appenders for high-throughput logging
- Log rotation: max file size, max history
- Don't log in tight loops (use sampling)

### Log Retention
| Environment | Retention |
|-------------|-----------|
| Development | 7 days |
| Staging | 30 days |
| Production | 90-365 days (compliance dependent) |

## References
- SLF4J Manual: https://www.slf4j.org/manual.html
- Logback Documentation: https://logback.qos.ch/
- Effective Java by Joshua Bloch (Exception chapter)
- Google Logging Best Practices
