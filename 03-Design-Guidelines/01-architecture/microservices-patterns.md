# Microservices Patterns

> Best practices for designing and implementing microservices architecture in CBOL Messaging Hub. Covers service decomposition, API gateway, service discovery, configuration, and inter-service communication.

## When to Use Microservices

```
✅ Use microservices when:
  - Teams need to deploy independently
  - Different services have different scaling needs
  - Domain boundaries are well-understood
  - Organization has DevOps maturity (CI/CD, containers, monitoring)
  - System needs polyglot persistence or processing

❌ Avoid microservices when:
  - Small team (< 5 engineers)
  - Domain is not well-understood yet (use modular monolith first)
  - Tight coupling between components
  - No CI/CD pipeline or container platform
  - Premature optimization of a monolith that works
```

## Service Decomposition

### Decompose by Business Capability (Recommended)

```
CBOL Messaging Hub
├── conversation-service    ← Conversation lifecycle, state machine
├── message-service         ← Message sending, storage, delivery
├── user-service            ← User management, authentication
├── agent-service           ← Human agent management, routing
├── ai-service              ← AI processing, NLP, intent
├── notification-service    ← Push notifications, email, SMS
└── gateway-service         ← API gateway, routing, rate limiting
```

### Decompose by Subdomain (DDD)

```
Core Domain (high complexity, business differentiator):
  - conversation-domain
  - message-domain

Supporting Domain (important but not differentiator):
  - agent-domain
  - notification-domain

Generic Domain (standard, can be bought/off-the-shelf):
  - user-domain (identity)
  - ai-domain (third-party AI integration)
```

### Service Boundary Rules

```java
// ✅ Good - each service owns its data, exposes API
// conversation-service
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    // Only conversation-service can read/write conversation table
    // Other services must call this API
}

// ❌ Bad - shared database, tight coupling
// message-service directly queries conversation table
@Query("SELECT c FROM Conversation c WHERE c.userId = :userId")
List<Conversation> findByUserId(Long userId);  // Cross-service DB access!
```

## API Gateway

```
                    ┌──────────────────┐
                    │   API Gateway    │
                    │  (Spring Cloud   │
                    │    Gateway)      │
                    └────────┬─────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
    ┌─────▼─────┐    ┌─────▼─────┐    ┌─────▼─────┐
    │conversation│    │  message   │    │   user    │
    │  service   │    │  service   │    │  service  │
    └───────────┘    └───────────┘    └───────────┘
```

### Gateway Responsibilities

| Responsibility | Description |
|---------------|-------------|
| Routing | Route requests to appropriate backend service |
| Authentication | Validate JWT tokens, extract user identity |
| Rate limiting | Limit requests per user/IP/endpoint |
| Circuit breaking | Stop routing to failing services |
| Request/response transformation | Modify requests/responses if needed |
| Logging & tracing | Add trace IDs, log requests |
| CORS | Handle cross-origin requests |

```java
// ✅ Good - Spring Cloud Gateway configuration
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("conversation-service", r -> r
                .path("/api/v1/conversations/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Id", "cbol-gateway")
                    .requestRateLimiter(c -> c
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(userKeyResolver())))
                .uri("lb://conversation-service"))
            .route("message-service", r -> r
                .path("/api/v1/messages/**")
                .uri("lb://message-service"))
            .build();
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.justOrEmpty(
            exchange.getRequest().getHeaders().getFirst("X-User-Id")
        ).defaultIfEmpty("anonymous");
    }
}
```

## Service Discovery

```java
// ✅ Good - service registration with Spring Cloud Netflix Eureka
@SpringBootApplication
@EnableDiscoveryClient
public class ConversationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConversationServiceApplication.class, args);
    }
}

// application.yml
spring:
  application:
    name: conversation-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: cbol-dev
        group: CBOL_GROUP

// ✅ Good - client-side load balancing with WebClient
@Configuration
public class WebClientConfig {
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

@Service
@RequiredArgsConstructor
public class MessageServiceClient {
    private final WebClient.Builder webClientBuilder;

    public Mono<MessageResponse> getMessage(Long messageId) {
        return webClientBuilder.build()
            .get()
            .uri("http://message-service/api/v1/messages/{id}", messageId)
            .retrieve()
            .bodyToMono(MessageResponse.class);
    }
}
```

## Inter-Service Communication

### Synchronous (REST/gRPC)

```java
// ✅ Good - REST client with timeout, retry, circuit breaker
@Service
@RequiredArgsConstructor
public class UserServiceClient {
    private final WebClient webClient;

    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback")
    @Retry(name = "user-service", fallbackMethod = "getUserFallback")
    @TimeLimiter(name = "user-service")
    public Mono<UserResponse> getUser(Long userId) {
        return webClient.get()
            .uri("/api/v1/users/{id}", userId)
            .retrieve()
            .bodyToMono(UserResponse.class)
            .timeout(Duration.ofSeconds(2));
    }

    private Mono<UserResponse> getUserFallback(Long userId, Exception ex) {
        return Mono.just(UserResponse.cachedOrEmpty(userId));
    }
}

// ❌ Bad - no timeout, no retry, no circuit breaker
public User getUser(Long userId) {
    return restTemplate.getForObject("http://user-service/users/" + userId, User.class);
    // Hangs forever if user-service is down!
}
```

### Asynchronous (Message Queue)

```java
// ✅ Good - event-driven communication via message queue
@Service
@RequiredArgsConstructor
public class ConversationEventPublisher {
    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

    public void publishConversationCreated(ConversationCreatedEvent event) {
        kafkaTemplate.send("cbol-conversation-created", event.getConversationId().toString(), event);
    }
}

// Consumer in another service
@Service
public class NotificationEventListener {
    @KafkaListener(topics = "cbol-conversation-created", groupId = "notification-service")
    public void onConversationCreated(ConversationCreatedEvent event) {
        notificationService.sendWelcomeNotification(event.getUserId());
    }
}
```

### Communication Pattern Selection

| Scenario | Pattern | Why |
|----------|---------|-----|
| Need immediate response | Synchronous REST/gRPC | User waiting for result |
| Fire and forget | Async message queue | Notification, audit log |
| Event notification | Async event | Domain events to multiple consumers |
| Data consistency | Saga pattern | Distributed transaction |
| High throughput, low latency | gRPC | Binary protocol, streaming |

## Configuration Management

```yaml
# ✅ Good - centralized config with Spring Cloud Config / Nacos
# application.yml (shared, committed)
spring:
  application:
    name: conversation-service
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        file-extension: yaml
        namespace: cbol-${SPRING_PROFILES_ACTIVE:dev}

# bootstrap.yml (loaded first)
spring:
  cloud:
    nacos:
      config:
        refresh-enabled: true  # Auto-refresh config changes

# Environment-specific config in Nacos (not committed)
# conversation-service-dev.yaml
# conversation-service-prod.yaml
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Distributed monolith | Services tightly coupled, can't deploy independently | Ensure each service owns its data, use async communication |
| Shared database | Services can't evolve independently, schema coupling | Each service owns its database, communicate via API |
| Nano-services | Too many services, overhead > benefit | Merge related capabilities, use modular monolith |
| Chatty services | Too many inter-service calls, high latency | Aggregate data, use CQRS, cache responses |
| No service discovery | Hardcoded URLs, can't scale | Use Eureka/Nacos/Consul for service discovery |
| No circuit breaker | Cascading failures when one service down | Use Resilience4j/Sentinel circuit breakers |
| Synchronous chains | User waits for 5+ service calls | Use async patterns, parallel calls, caching |
| No distributed tracing | Can't debug cross-service issues | Use OpenTelemetry/Jaeger/SkyWalking |
| Config in code | Can't change config without redeploy | Use config server, environment variables |
| No API versioning | Breaking changes affect all consumers | Version APIs (URI, header, or media type) |
| God service | One service does everything | Decompose by business capability |
| Cross-service joins | Can't join across databases | Use API composition or CQRS with read model |

## References

- Microservices Patterns (Chris Richardson): https://microservices.io/patterns/
- Spring Cloud: https://spring.io/projects/spring-cloud
- Resilience4j: https://resilience4j.readme.io/
- Nacos: https://nacos.io/
- API Gateway Pattern: https://microservices.io/patterns/apigateway.html
- Saga Pattern: https://microservices.io/patterns/data/saga.html
- The Twelve-Factor App: https://12factor.net/
