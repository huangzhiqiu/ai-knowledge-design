# Spring Configuration & Transaction/AOP Guidelines

> Best practices for Spring configuration management, transaction management, and AOP.

## Configuration Management

### Profile Strategy

```
resources/
├── application.yml           # Shared defaults
├── application-dev.yml       # Development environment
├── application-staging.yml   # Staging environment
├── application-prod.yml      # Production environment
└── application-local.yml     # Local developer overrides (gitignored)
```

### Configuration Hierarchy

```yaml
# application.yml - shared defaults (committed)
spring:
  application:
    name: cbol-messaging
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false

cbol:
  messaging:
    max-retries: 3
    retry-delay: 1s

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:mysql://localhost:3306/cbol_dev
    username: root
    password: root
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    com.selfdevelopment: DEBUG
    org.hibernate.SQL: DEBUG

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    show-sql: false

logging:
  level:
    com.selfdevelopment: INFO
```

### Externalized Configuration (Priority Order)

Spring Boot resolves properties in this order (higher wins):
1. Command line arguments (`--server.port=8081`)
2. OS environment variables (`SERVER_PORT=8081`)
3. `application-{profile}.yml` outside jar
4. `application-{profile}.yml` inside jar
5. `application.yml` outside jar
6. `application.yml` inside jar
7. `@PropertySource` annotations
8. Default values

### Secrets Management

```yaml
# ✅ Good - use environment variables for secrets
spring:
  datasource:
    password: ${DB_PASSWORD}  # from env var
  redis:
    password: ${REDIS_PASSWORD}

cbol:
  messaging:
    api-key: ${EXTERNAL_API_KEY}

# ❌ Bad - hardcoded secrets in committed files
spring:
  datasource:
    password: "SuperSecret123!"  # committed to git!
```

### Type-Safe Configuration Properties

```java
@ConfigurationProperties(prefix = "cbol.messaging")
@Validated
@Data
public class MessagingProperties {

    @NotNull
    @Min(1) @Max(10)
    private Integer maxRetries = 3;

    @NotNull
    private Duration retryDelay = Duration.ofSeconds(1);

    @NotEmpty
    private List<String> allowedOrigins;

    @Valid
    private WebSocketConfig websocket = new WebSocketConfig();

    @Valid
    private RetryConfig retry = new RetryConfig();

    @Data
    public static class WebSocketConfig {
        @Min(1) @Max(65535)
        private int port = 8080;
        private Duration heartbeat = Duration.ofSeconds(30);
        private int maxFrameSize = 65536;
    }

    @Data
    public static class RetryConfig {
        @Min(1) @Max(10)
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(500);
        private double multiplier = 2.0;
    }
}
```

## Transaction Management

### Transaction Boundaries

```java
// ✅ Good - transaction at service layer
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public ConversationDTO getById(Long id) {
        return conversationRepository.findById(id)
            .map(mapper::toDTO)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
    }

    @Transactional
    public ConversationDTO create(CreateConversationRequest request) {
        Conversation conversation = mapper.toEntity(request);
        conversation.setStatus(ConversationStatus.ACTIVE);
        Conversation saved = conversationRepository.save(conversation);

        // Send welcome message in same transaction
        Message welcome = Message.builder()
            .conversationId(saved.getId())
            .content("Welcome!")
            .build();
        messageRepository.save(welcome);

        eventPublisher.publishEvent(new ConversationCreatedEvent(saved.getId()));
        return mapper.toDTO(saved);
    }
}
```

### Transaction Propagation

| Propagation | Behavior | Use Case |
|-------------|----------|----------|
| `REQUIRED` (default) | Join existing, create if none | Most operations |
| `REQUIRES_NEW` | Always new transaction, suspend existing | Audit logs, independent operations |
| `NESTED` | Savepoint within existing | Sub-operations that can roll back independently |
| `MANDATORY` | Must have existing, error if none | Methods that must be called transactionally |
| `NEVER` | Must NOT have existing, error if one | Non-transactional operations |
| `NOT_SUPPORTED` | Suspend existing, run non-transactionally | Read-only operations with no DB write |
| `SUPPORTS` | Join if exists, run non-transactionally if none | Flexible read operations |

### Transaction Isolation Levels

```java
// ✅ Good - explicit isolation level for specific needs
@Transactional(isolation = Isolation.READ_COMMITTED)
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    // READ_COMMITTED prevents dirty reads
    // Good default for most financial operations
}

@Transactional(isolation = Isolation.SERIALIZABLE)
public void seatReservation(Long eventId, int seats) {
    // SERIALIZABLE for high-contention, critical operations
    // Slower but prevents all concurrency anomalies
}
```

### Common Transaction Pitfalls

```java
// ❌ Pitfall 1: @Transactional on private method (not applied)
@Service
public class MessageService {
    public void process() {
        saveMessage(); // @Transactional NOT applied!
    }
    @Transactional
    private void saveMessage() { } // private = proxy can't intercept
}

// ✅ Fix: make method public or call from another bean

// ❌ Pitfall 2: Self-invocation (this.method())
@Service
public class MessageService {
    @Transactional
    public void methodA() {
        this.methodB(); // @Transactional on methodB NOT applied!
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void methodB() { }
}

// ✅ Fix: inject self or use TransactionTemplate

// ❌ Pitfall 3: Swallowing exceptions in @Transactional
@Transactional
public void save() {
    try {
        repository.save(entity);
    } catch (Exception e) {
        log.error("Failed", e); // exception swallowed = no rollback!
    }
}

// ✅ Fix: rethrow or use rollbackFor
@Transactional(rollbackFor = Exception.class)
public void save() {
    repository.save(entity); // any Exception triggers rollback
}

// ❌ Pitfall 4: open-in-view = true (default)
// Causes lazy loading outside transaction, holding DB connections
spring.jpa.open-in-view: true  # ❌ Bad

// ✅ Fix: disable and use fetch joins / DTO projections
spring.jpa.open-in-view: false  # ✅ Good
```

## AOP Best Practices

### When to Use AOP

| Use Case | Aspect Type | Example |
|----------|------------|---------|
| Logging | `@Around` | Method entry/exit logging with timing |
| Security | `@Before` | Authentication/authorization check |
| Caching | `@Around` | Cache lookup/put around method |
| Transactions | `@Around` | Spring's `@Transactional` |
| Metrics | `@AfterReturning` / `@AfterThrowing` | Count success/failure |
| Retry | `@Around` | Retry on specific exceptions |

### Aspect Example

```java
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // ✅ Good - specific pointcut, not too broad
    @Around("execution(* com.selfdevelopment.ai.messaging.application.service..*(..))")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();

        log.debug("Entering: {} with args: {}", methodName, joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.debug("Exiting: {} returned: {} in {}ms", methodName, result, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("Exception in: {} after {}ms: {}", methodName, duration, ex.getMessage());
            throw ex;
        }
    }
}
```

### AOP Anti-Patterns

```java
// ❌ Bad - overly broad pointcut (performance impact, hard to debug)
@Around("execution(* *(..))")  // intercepts EVERY method!
public Object logEverything(ProceedingJoinPoint pjp) { }

// ❌ Bad - business logic in aspect
@Around("execution(* *.send(..))")
public Object applyBusinessRule(ProceedingJoinPoint pjp) {
    // Business logic should be in service, not aspect!
}

// ❌ Bad - too many nested aspects (ordering confusion)
@Aspect @Order(1) public class AspectA { }
@Aspect @Order(2) public class AspectB { }
@Aspect @Order(3) public class AspectC { }
// ... 10 more aspects = debugging nightmare
```

## References

- Spring Transaction Management: https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- Spring AOP: https://docs.spring.io/spring-framework/reference/core/aop.html
- Spring Boot Externalized Configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Spring Boot Best Practices: https://github.com/kunaljainflair/Springboot_dev_best_practices
