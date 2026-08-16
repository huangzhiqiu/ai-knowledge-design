# Java Implementation Guide

> Java technology stack and implementation patterns for IM systems.

## Recommended Tech Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 17+ (LTS) | Core language |
| Framework | Spring Boot | 3.x | Application framework |
| Networking | Netty | 4.1.x | WebSocket/TCP server |
| ORM | MyBatis-Plus / JPA | - | Data access |
| Cache | Spring Data Redis | - | Redis integration |
| Message Queue | Spring Kafka / RocketMQ | - | Async messaging |
| Database | MySQL 8.0 / MongoDB | - | Persistence |
| API Docs | SpringDoc OpenAPI | - | REST API documentation |
| Build | Maven / Gradle | - | Build tool |
| Container | Docker + Kubernetes | - | Deployment |

## Project Structure (Maven Multi-Module)

```
cbol-im/
├── pom.xml                          # Parent POM
├── cbol-common/                     # Shared utilities
│   └── src/main/java/com/cbol/common/
│       ├── constant/                # Constants, enums
│       ├── exception/               # Custom exceptions
│       ├── model/                   # Common DTOs, VO
│       ├── util/                    # Utility classes
│       └── config/                  # Shared config
├── cbol-domain/                     # Domain entities
│   └── src/main/java/com/cbol/domain/
│       ├── entity/                  # JPA entities / POJOs
│       ├── repository/              # Repository interfaces
│       └── event/                   # Domain events
├── cbol-gateway/                    # Netty WebSocket gateway
│   └── src/main/java/com/cbol/gateway/
│       ├── server/                  # Netty server bootstrap
│       ├── handler/                 # Channel handlers
│       ├── codec/                   # Protocol encode/decode
│       ├── session/                 # Session management
│       └── config/                  # Gateway config
├── cbol-service/                    # Business services
│   └── src/main/java/com/cbol/service/
│       ├── user/                    # User service
│       ├── conversation/            # Conversation service
│       ├── message/                 # Message service
│       ├── group/                   # Group service
│       └── push/                    # Push service
├── cbol-api/                        # REST API layer
│   └── src/main/java/com/cbol/api/
│       ├── controller/              # REST controllers
│       ├── dto/                     # Request/Response DTOs
│       └── interceptor/             # Auth, logging
└── cbol-bootstrap/                  # Application entry point
    └── src/main/java/com/cbol/
        └── CbolApplication.java
```

## Key Design Principles

### 1. Layered Architecture
```
Controller (API) -> Service (Business) -> Repository (Data) -> Database
                          ^
                          |
                    Domain Events
```

### 2. DDD-Oriented
- Domain entities in `cbol-domain`
- Business logic in services
- Infrastructure concerns isolated

### 3. Event-Driven Internal Communication
- Services communicate via events (Spring ApplicationEvent or Kafka)
- Decouples modules
- Enables async processing

### 4. Interface Segregation
- Define interfaces in domain module
- Implementations in service module
- Easy to mock and test

## Core Class Hierarchy

### Message Processing
```
MessageController (REST)
    └── MessageService
        ├── MessageRepository (DB)
        ├── SessionRegistry (Redis)
        ├── MessagePublisher (Kafka)
        └── PushService (APNs/FCM)

MessageHandler (WebSocket)
    └── MessageService (shared)
```

### Connection Management
```
NettyServer
    └── ChannelInitializer
        ├── AuthHandler (authentication)
        ├── HeartbeatHandler (idle detection)
        ├── MessageCodec (protocol)
        └── BusinessHandler (message dispatch)
```

## Dependency Injection Strategy

- Use constructor injection (never field injection)
- Use `@RequiredArgsConstructor` from Lombok
- Mark beans with `@Service`, `@Component`, `@Repository`
- Use `@Configuration` for bean definitions

## Error Handling Strategy

- Global exception handler (`@RestControllerAdvice`)
- Custom exception hierarchy: `BusinessException` -> specific exceptions
- Error code enum (see [coding guidelines](../../04-Coding-Guidelines/))
- Unified response format: `Result<T>` with code, message, data

## Reference: Turms IM (Java)
Turms is a Java-based open-source IM engine. Uses:
- Spring Boot + Netty for gateway
- MongoDB for message storage
- Redis for session registry
- Kafka for async processing
- Supports 100K~10M concurrent users
