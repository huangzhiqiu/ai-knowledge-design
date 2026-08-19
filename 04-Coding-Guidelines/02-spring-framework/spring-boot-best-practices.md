# Spring Boot Best Practices

> Best practices for Spring Boot 3.x application development, based on Spring official docs and community standards.

## Project Structure

### Layered Architecture (Mandatory)

```
com.selfdevelopment.ai.messaging
├── CbolApplication.java           # @SpringBootApplication (root package)
├── domain/                        # Domain entities, value objects, domain services
│   ├── model/                     # Entities, enums
│   ├── repository/                # Repository interfaces (Spring Data)
│   └── service/                   # Domain services (business logic)
├── application/                   # Application services, use cases, DTOs
│   ├── service/                   # Application services (orchestration)
│   ├── dto/                       # Request/Response DTOs
│   └── mapper/                    # Entity ↔ DTO mappers (MapStruct)
├── infrastructure/                # External integrations
│   ├── persistence/               # JPA entities, repository implementations
│   ├── messaging/                 # Kafka/RocketMQ producers/consumers
│   ├── cache/                     # Redis implementations
│   └── external/                  # REST clients, third-party integrations
├── interfaces/                    # Entry points
│   ├── rest/                      # REST controllers
│   ├── websocket/                 # WebSocket handlers
│   └── config/                    # Web/MVC config
└── config/                        # Spring configuration classes
    ├── SecurityConfig.java
    ├── RedisConfig.java
    └── WebSocketConfig.java
```

### Key Rules

1. **Root package = `@SpringBootApplication` location** — component scan covers all sub-packages
2. **Controllers don't contain business logic** — only validation + delegation
3. **Services don't depend on web types** — no `HttpServletRequest` in service layer
4. **Services return DTOs, not entities** — avoid leaking persistence details
5. **Repository interfaces extend `JpaRepository`** — no implementation needed

## Dependency Injection

### Constructor Injection (Mandatory)

```java
// ✅ Good - constructor injection, immutable, testable
@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }
}

// ✅ Better - Lombok @RequiredArgsConstructor
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
}

// ❌ Bad - field injection, not testable, not immutable
@Service
public class MessageService {
    @Autowired
    private MessageRepository messageRepository; // can't mock easily in tests
}
```

### Avoid Circular Dependencies

```java
// ❌ Bad - circular dependency
@Service
public class ServiceA {
    private final ServiceB serviceB; // A → B
}
@Service
public class ServiceB {
    private final ServiceA serviceA; // B → A (circular!)
}

// ✅ Good - extract common logic to ServiceC
@Service
public class ServiceC { /* shared logic */ }
@Service
public class ServiceA { private final ServiceC serviceC; }
@Service
public class ServiceB { private final ServiceC serviceC; }
```

## Controller Best Practices

### Thin Controllers

```java
// ✅ Good - thin controller, validation + delegation
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @Valid @RequestBody SendMessageRequest request) {
        MessageResponse response = messageService.send(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(messageService.getById(id));
    }
}

// ❌ Bad - fat controller with business logic
@RestController
public class MessageController {
    @Autowired
    private MessageRepository repo;

    @PostMapping("/send")
    public Message send(@RequestBody Message msg) {
        if (msg.getContent() == null) { // validation in controller
            throw new RuntimeException("Content required");
        }
        msg.setCreatedAt(LocalDateTime.now()); // business logic in controller
        return repo.save(msg);
    }
}
```

### Global Exception Handling

```java
// ✅ Good - centralized exception handling
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", details));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }
}
```

## Service Layer Best Practices

### Transaction Boundaries

```java
// ✅ Good - transaction at service layer, read-only for queries
@Service
@RequiredArgsConstructor
public class MessageService {

    @Transactional(readOnly = true)
    public MessageResponse getById(Long id) {
        return messageRepository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Message", id));
    }

    @Transactional
    public MessageResponse send(SendMessageRequest request) {
        Message message = mapper.toEntity(request);
        message.setStatus(MessageStatus.SENT);
        Message saved = messageRepository.save(message);
        eventPublisher.publishEvent(new MessageSentEvent(saved.getId()));
        return mapper.toResponse(saved);
    }
}
```

### DTO Mapping

```java
// ✅ Good - MapStruct for compile-time mapping
@Mapper(componentModel = "spring")
public interface MessageMapper {
    MessageResponse toResponse(Message message);
    Message toEntity(SendMessageRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Message toEntityForUpdate(UpdateMessageRequest request);
}

// ❌ Bad - manual mapping, error-prone
public MessageResponse toResponse(Message m) {
    MessageResponse r = new MessageResponse();
    r.setId(m.getId());
    r.setContent(m.getContent());
    // ... 20 more fields, easy to miss one
    return r;
}
```

## Configuration Best Practices

### Type-Safe Configuration Properties

```java
// ✅ Good - type-safe, validated, documented
@ConfigurationProperties(prefix = "cbol.messaging")
@Validated
@Data
public class MessagingProperties {
    @NotNull
    private Integer maxRetries = 3;

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration retryDelay = Duration.ofSeconds(1);

    @NotEmpty
    private List<String> allowedOrigins;

    @Valid
    private WebSocketConfig websocket = new WebSocketConfig();

    @Data
    public static class WebSocketConfig {
        @Min(1) @Max(65535)
        private int port = 8080;
        private Duration heartbeat = Duration.ofSeconds(30);
    }
}

// Enable in main class or config
@EnableConfigurationProperties(MessagingProperties.class)
@SpringBootApplication
public class CbolApplication { }
```

### application.yml Structure

```yaml
# application.yml - shared defaults
spring:
  application:
    name: cbol-messaging
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

cbol:
  messaging:
    max-retries: 3
    retry-delay: 1s
    allowed-origins:
      - http://localhost:3000
    websocket:
      port: 8080
      heartbeat: 30s

logging:
  level:
    com.selfdevelopment: INFO
    org.springframework: WARN

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:mysql://localhost:3306/cbol_dev
  redis:
    host: localhost
    port: 6379

logging:
  level:
    com.selfdevelopment: DEBUG

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
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
```

## Actuator & Monitoring

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| `@Autowired` field injection | Not testable, not immutable | Constructor injection |
| Business logic in controllers | Hard to test, tight coupling | Move to service layer |
| `@Transactional` on repositories | Wrong layer, no composition | Put on service methods |
| Catching exceptions in controllers | Inconsistent error handling | Use `@RestControllerAdvice` |
| Returning JPA entities from controllers | Lazy loading issues, data leakage | Return DTOs |
| `System.out.println` | No log levels, no structure | Use SLF4J |
| Hardcoded config values | Environment-specific changes need code edits | Use `@ConfigurationProperties` |
| `new` keyword for Spring beans | Bypasses DI, not testable | Inject dependencies |
| `@SpringBootApplication` in default package | Component scan issues | Put in proper root package |

## References

- Spring Boot Reference: https://docs.spring.io/spring-boot/docs/current/reference/html/
- Spring Framework Best Practices: https://github.com/kunaljainflair/Springboot_dev_best_practices
- Java Spring Boot Microservices Spec: https://github.com/Emersondll/java-spec
- Spring Boot Best Practices That Should Fail Your Build: https://protsenko.dev/spring-boot-best-practices-that-should-fail-your-build/
