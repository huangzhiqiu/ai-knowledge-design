# Layered Architecture Guidelines

> Best practices for designing and implementing layered architecture in CBOL Messaging Hub. Covers classic layered architecture, hexagonal architecture (ports & adapters), and clean architecture.

## Architecture Styles

### Classic Layered Architecture

```
┌─────────────────────────────────────────┐
│         Presentation Layer (Controller)  │  ← REST API, WebSocket handlers
├─────────────────────────────────────────┤
│         Application Layer (Service)      │  ← Use cases, orchestration, transactions
├─────────────────────────────────────────┤
│         Domain Layer (Model)             │  ← Entities, value objects, domain logic
├─────────────────────────────────────────┤
│         Infrastructure Layer (DAO)       │  ← Repositories, external APIs, config
└─────────────────────────────────────────┘
```

**Dependency rule**: Higher layers depend on lower layers. Domain layer has no external dependencies.

### Hexagonal Architecture (Ports & Adapters)

```
                    ┌─────────────────────┐
                    │                     │
          ┌─────────►     Domain Core    ◄─────────┐
          │         │   (business logic)  │         │
          │         └─────────────────────┘         │
          │                    ▲                      │
          │                    │                      │
    ┌─────┴─────┐       ┌─────┴─────┐         ┌─────┴─────┐
    │  Driving   │       │   Ports   │         │  Driven    │
    │  Adapters  │──────►│ (interfaces)│◄──────│  Adapters  │
    │  (input)   │       │           │         │  (output)  │
    └───────────┘       └───────────┘         └───────────┘
    - REST Controller     - MessageService      - Repository impl
    - WebSocket Handler   - NotificationPort    - External API client
    - CLI                 - EventPublisher       - Message queue producer
```

**Key principle**: Domain core defines ports (interfaces). Adapters implement ports. Core depends on nothing external.

## Layer Responsibilities

### Presentation Layer

```java
// ✅ Good - thin controller, delegates to application service
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationApplicationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse response = conversationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getById(id));
    }
}

// ❌ Bad - fat controller with business logic
@RestController
public class ConversationController {
    @Autowired private ConversationRepository repo;
    @Autowired private UserRepository userRepo;

    @PostMapping("/conversations")
    public Conversation create(@RequestBody Map<String, Object> body) {
        // Business logic in controller!
        Long userId = Long.valueOf(body.get("userId").toString());
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User not active");
        }
        Conversation conv = new Conversation();
        conv.setUser(user);
        conv.setStatus(ConversationStatus.ACTIVE);
        conv.setCreatedAt(Instant.now());
        return repo.save(conv);
    }
}
```

### Application Layer

```java
// ✅ Good - application service orchestrates, delegates domain logic
@Service
@RequiredArgsConstructor
public class ConversationApplicationService {
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ConversationDomainService domainService;
    private final EventPublisher eventPublisher;

    @Transactional
    public ConversationResponse create(CreateConversationRequest request) {
        // 1. Load aggregates
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new UserNotFoundException(request.getUserId()));

        // 2. Delegate domain logic
        Conversation conversation = domainService.createConversation(user, request.getType());

        // 3. Persist
        Conversation saved = conversationRepository.save(conversation);

        // 4. Publish domain events
        eventPublisher.publish(new ConversationCreatedEvent(saved.getId(), saved.getCreatedAt()));

        // 5. Return response
        return ConversationResponse.from(saved);
    }
}

// ❌ Bad - application service with domain logic embedded
@Service
public class ConversationService {
    public Conversation create(Long userId, String type) {
        Conversation conv = new Conversation();
        // Domain logic mixed with application orchestration
        if (type.equals("SUPPORT")) {
            conv.setPriority(Priority.HIGH);
            conv.setSlaMinutes(30);
        } else {
            conv.setPriority(Priority.NORMAL);
            conv.setSlaMinutes(240);
        }
        // ... more domain rules
        return conversationRepository.save(conv);
    }
}
```

### Domain Layer

```java
// ✅ Good - rich domain model with behavior
public class Conversation extends AggregateRoot {
    private ConversationId id;
    private UserId userId;
    private ConversationStatus status;
    private Priority priority;
    private Duration sla;
    private List<Message> messages = new ArrayList<>();

    // Factory method with business rules
    public static Conversation create(User user, ConversationType type) {
        if (!user.isActive()) {
            throw new InactiveUserException(user.getId());
        }
        Conversation conv = new Conversation();
        conv.userId = user.getId();
        conv.status = ConversationStatus.ACTIVE;
        conv.priority = type.defaultPriority();
        conv.sla = type.defaultSla();
        conv.registerEvent(new ConversationCreatedEvent(conv.id, Instant.now()));
        return conv;
    }

    // Behavior method
    public void addMessage(Message message) {
        if (this.status != ConversationStatus.ACTIVE) {
            throw new ConversationClosedException(this.id);
        }
        if (message.getContent().length() > 5000) {
            throw new MessageTooLongException(message.getId());
        }
        this.messages.add(message);
        this.registerEvent(new MessageAddedEvent(this.id, message.getId()));
    }

    public void close(UserId closedBy) {
        if (this.status == ConversationStatus.CLOSED) {
            return; // idempotent
        }
        this.status = ConversationStatus.CLOSED;
        this.registerEvent(new ConversationClosedEvent(this.id, closedBy, Instant.now()));
    }
}

// ❌ Bad - anemic domain model (just getters/setters, no behavior)
public class Conversation {
    private Long id;
    private Long userId;
    private String status;
    private String priority;
    // getters and setters only - no behavior!
    // All business logic lives in services, leading to procedural code
}
```

### Infrastructure Layer

```java
// ✅ Good - repository implements port interface defined in domain
@Repository
@RequiredArgsConstructor
public class ConversationRepositoryImpl implements ConversationRepository {
    private final ConversationJpaRepository jpaRepository;
    private final ConversationMapper mapper;

    @Override
    public Optional<Conversation> findById(ConversationId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Conversation save(Conversation conversation) {
        ConversationEntity entity = mapper.toEntity(conversation);
        ConversationEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<Conversation> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}

// Port interface defined in domain layer
public interface ConversationRepository {
    Optional<Conversation> findById(ConversationId id);
    Conversation save(Conversation conversation);
    List<Conversation> findByUserId(UserId userId);
}
```

## Dependency Injection Rules

```
✅ Allowed:
  Presentation → Application (controller calls service)
  Application → Domain (service uses domain model)
  Application → Infrastructure Ports (service calls repository interface)
  Infrastructure → Domain Ports (repository implements interface)

❌ Forbidden:
  Domain → Application (domain never calls services)
  Domain → Infrastructure (domain never depends on DB/HTTP)
  Presentation → Domain directly (controller never calls repository)
  Presentation → Infrastructure directly (controller never calls JPA)
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Fat controller | Business logic in presentation layer | Move logic to application/domain services |
| Anemic domain model | Domain objects are just data containers | Add behavior methods to domain entities |
| Service layer as god object | One service does everything | Split by use case or aggregate |
| Leaky abstractions | Domain layer knows about DB/HTTP | Use ports/interfaces, invert dependencies |
| Circular dependencies | Layer A → B → A | Use events or extract shared module |
| Skipping application layer | Controller calls repository directly | Add application service for orchestration |
| Domain logic in infrastructure | Business rules in repository/DAO | Move rules to domain model/service |
| Transaction in domain | Domain methods annotated @Transactional | Transactions belong in application layer |
| DTOs in domain | Domain layer depends on API DTOs | Use separate domain models, map at boundaries |
| Shared kernel bloat | Everything in common module | Only put truly shared abstractions in common |

## References

- Clean Architecture (Robert C. Martin): https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- Hexagonal Architecture: https://alistair.cockburn.us/hexagonal-architecture/
- Domain-Driven Design (Eric Evans): https://domainlanguage.com/ddd/
- Spring Boot Best Practices: https://github.com/kunaljainflair/Springboot_dev_best_practices
- Ports & Adapters Pattern: https://www.dossier.dev/blog/hexagonal-architecture
