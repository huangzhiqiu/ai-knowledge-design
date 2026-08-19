# Domain-Driven Design (DDD) Guidelines

> Best practices for applying Domain-Driven Design in CBOL Messaging Hub. Covers bounded contexts, ubiquitous language, aggregates, value objects, domain services, and context mapping.

## Core Concepts

### Strategic Design

```
┌─────────────────────────────────────────────────────────┐
│                    Domain (Business)                       │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │   Core       │  │  Supporting  │  │   Generic    │ │
│  │   Domain     │  │   Domain     │  │   Domain     │ │
│  │              │  │              │  │              │ │
│  │ Conversation │  │ Agent        │  │ User (Auth)  │ │
│  │ Message      │  │ Notification │  │ AI (3rd party)│ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                                           │
│  Each subdomain = one or more Bounded Contexts           │
└─────────────────────────────────────────────────────────┘
```

### Bounded Contexts for CBOL

| Bounded Context | Subdomain Type | Description |
|----------------|---------------|-------------|
| Conversation | Core | Conversation lifecycle, state machine, routing |
| Message | Core | Message creation, storage, delivery, read diffusion |
| Agent | Supporting | Human agent management, assignment, skills |
| AI | Supporting | AI processing, intent recognition, auto-reply |
| Notification | Supporting | Push, email, SMS notifications |
| User | Generic | User identity, authentication, profile |
| Billing | Generic | Subscription, payment (if applicable) |

## Ubiquitous Language

```
✅ Good - use business terms everywhere (code, DB, API, docs)

// Domain model
public class Conversation {
    private ConversationStatus status;  // Business term: "status"
    private Sla sla;                     // Business term: "SLA"
    private Agent assignedAgent;         // Business term: "assigned agent"
}

// Database column
// conversation_status VARCHAR(20), sla_minutes INT, assigned_agent_id BIGINT

// API
// GET /api/v1/conversations/{id}/status
// { "status": "AI_PROCESSING", "slaRemaining": 120 }

❌ Bad - technical terms mixed with business terms

public class ChatSession {  // Developer term, not business term
    private String state;    // Generic "state", not business "status"
    private Integer ttl;     // Technical "ttl", not business "sla"
}
```

### Language Rules

1. **One term per concept** - Don't use "conversation", "chat", "session" interchangeably
2. **No synonyms** - If two terms mean the same thing, pick one
3. **No homonyms** - If one term means two things, use two terms
4. **Use in all layers** - Domain, DB, API, UI, documentation
5. **Evolve with domain** - Refactor language as understanding deepens

## Tactical Design Patterns

### Aggregate Root

```java
// ✅ Good - aggregate root with invariants
public class Conversation extends AggregateRoot<ConversationId> {
    private ConversationId id;
    private UserId userId;
    private ConversationStatus status;
    private Priority priority;
    private Duration sla;
    private List<Message> messages = new ArrayList<>();  // Part of aggregate
    private AgentId assignedAgent;  // Reference to another aggregate (by ID only)

    // Invariant: can only add message if conversation is active
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

    // Invariant: can only close if not already closed
    public void close(UserId closedBy) {
        if (this.status == ConversationStatus.CLOSED) {
            return; // idempotent
        }
        this.status = ConversationStatus.CLOSED;
        this.registerEvent(new ConversationClosedEvent(this.id, closedBy, Instant.now()));
    }

    // Factory method enforces invariants on creation
    public static Conversation create(User user, ConversationType type) {
        if (!user.isActive()) {
            throw new InactiveUserException(user.getId());
        }
        Conversation conv = new Conversation();
        conv.id = ConversationId.generate();
        conv.userId = user.getId();
        conv.status = ConversationStatus.ACTIVE;
        conv.priority = type.defaultPriority();
        conv.sla = type.defaultSla();
        conv.registerEvent(new ConversationCreatedEvent(conv.id, user.getId(), type, Instant.now()));
        return conv;
    }

    // Private constructor - must use factory method
    private Conversation() {}
}

// ❌ Bad - anemic aggregate, no invariants, public setters
public class Conversation {
    public Long id;
    public String status;
    public List<Message> messages;
    // Public setters - anyone can set status to anything, no invariants!
    public void setStatus(String status) { this.status = status; }
    public void addMessage(Message m) { this.messages.add(m); }  // No validation!
}
```

### Aggregate Rules

| Rule | Description |
|------|-------------|
| **One transaction per aggregate** | Don't modify multiple aggregates in one transaction |
| **Reference by ID** | Aggregates reference other aggregates by ID, not object |
| **Small aggregates** | Prefer small aggregates over large ones |
| **Invariants inside** | All business rules enforced inside aggregate boundaries |
| **Eventual consistency** | Cross-aggregate consistency via events, not transactions |

### Value Object

```java
// ✅ Good - immutable value object with equality by value
@Value  // Lombok: final fields, equals/hashCode by all fields, no setters
public class Money implements ValueObject {
    BigDecimal amount;
    Currency currency;

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(this.currency, other.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }
}

// ✅ Good - value object for conversation ID (type-safe, not just Long)
@Value
public class ConversationId implements ValueObject {
    Long value;

    public static ConversationId generate() {
        return new ConversationId(SnowflakeIdGenerator.nextId());
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

// Usage - type-safe, can't pass UserId where ConversationId expected
public Conversation findById(ConversationId id) { ... }
// findById(123L) - compile error! Must use ConversationId.of(123L)

// ❌ Bad - using primitive types for IDs (not type-safe)
public Conversation findById(Long id) { ... }
// findById(userId) - compiles but wrong! UserId passed as conversation ID
```

### Domain Service

```java
// ✅ Good - domain service for logic that doesn't belong in a single aggregate
@Service
public class RoutingDomainService {
    private final AgentRepository agentRepository;
    private final SkillMatchingService skillMatchingService;

    /**
     * Route conversation to best available agent.
     * This logic spans multiple aggregates (Conversation + Agent + Skill),
     * so it belongs in a domain service, not in Conversation aggregate.
     */
    public Optional<AgentId> routeToAgent(Conversation conversation) {
        // 1. Find available agents with matching skills
        List<Agent> availableAgents = agentRepository.findAvailableBySkills(
            conversation.getRequiredSkills());

        if (availableAgents.isEmpty()) {
            return Optional.empty();
        }

        // 2. Select best agent based on load, skill match, priority
        Agent bestAgent = selectBestAgent(availableAgents, conversation);

        return Optional.of(bestAgent.getId());
    }

    private Agent selectBestAgent(List<Agent> agents, Conversation conversation) {
        return agents.stream()
            .max(Comparator.comparingDouble(a -> calculateMatchScore(a, conversation)))
            .orElseThrow();
    }
}

// ❌ Bad - logic that belongs in aggregate is in domain service
@Service
public class ConversationService {
    public void addMessage(Conversation conv, Message msg) {
        // This validation belongs in Conversation aggregate!
        if (conv.getStatus() != ACTIVE) {
            throw new RuntimeException("Conversation not active");
        }
        conv.getMessages().add(msg);
    }
}
```

### Repository Pattern

```java
// ✅ Good - repository interface in domain layer, impl in infrastructure
// Domain layer (interface)
public interface ConversationRepository {
    Optional<Conversation> findById(ConversationId id);
    Conversation save(Conversation conversation);
    List<Conversation> findByUserId(UserId userId);
    boolean existsById(ConversationId id);
}

// Infrastructure layer (implementation)
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
}

// ❌ Bad - domain layer depends on JPA
@Entity
@Table(name = "conversations")
public class Conversation {  // Domain entity with JPA annotations - leaky abstraction!
    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "status")
    private String status;
    // Domain model is now coupled to persistence technology
}
```

## Context Mapping

### Relationship Patterns

| Pattern | Description | Use When |
|---------|-------------|----------|
| **Partnership** | Two contexts collaborate, teams coordinate | Closely related business capabilities |
| **Customer-Supplier** | One context depends on another's API | Upstream/downstream relationship |
| **Conformist** | Follow upstream's model as-is | No control over upstream, can't change it |
| **Anti-Corruption Layer (ACL)** | Translate between models | Upstream model doesn't fit your domain |
| **Open Host Service** | Expose a well-defined API | Other contexts need to integrate with you |
| **Published Language** | Shared event schema/contract | Event-driven integration between contexts |
| **Separate Ways** | No integration at all | Contexts are truly independent |
| **Shared Kernel** | Share a common model | Two contexts share core domain concepts |

### Anti-Corruption Layer Example

```java
// ✅ Good - ACL translates external model to domain model
// External service (User service) returns UserDTO with different model
@Service
@RequiredArgsConstructor
public class UserAntiCorruptionLayer {
    private final UserServiceClient userClient;

    public Optional<User> findUser(UserId userId) {
        // 1. Call external service
        UserDTO externalUser = userClient.getUser(userId.getValue());

        if (externalUser == null) {
            return Optional.empty();
        }

        // 2. Translate external model to domain model
        User domainUser = User.builder()
            .id(UserId.of(externalUser.getId()))
            .name(externalUser.getFullName())  // External: fullName, Domain: name
            .status(mapStatus(externalUser.getAccountStatus()))  // Different status enum
            .email(externalUser.getEmailAddress())  // Different field name
            .build();

        return Optional.of(domainUser);
    }

    private UserStatus mapStatus(String externalStatus) {
        return switch (externalStatus) {
            case "ACTIVE" -> UserStatus.ACTIVE;
            case "SUSPENDED" -> UserStatus.SUSPENDED;
            case "DELETED" -> UserStatus.DELETED;
            default -> throw new UnknownUserStatusException(externalStatus);
        };
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Anemic domain model | Domain objects are just data, logic in services | Add behavior to entities, enforce invariants |
| God aggregate | One aggregate contains everything, huge transaction boundary | Split into smaller aggregates, reference by ID |
| Aggregate references other aggregate by object | Large object graph, loading everything eagerly | Reference by ID only, lazy load via repository |
| Domain logic in application service | Business rules scattered, hard to test | Move business rules to domain model/service |
| Domain model with JPA annotations | Leaky abstraction, domain coupled to persistence | Use separate entity classes, map between layers |
| No ubiquitous language | Different terms for same concept, confusion | Establish and enforce ubiquitous language |
| Bounded context too large | One context handles multiple subdomains | Split into smaller contexts by business capability |
| Bounded context too small | Nano-services, excessive overhead | Merge related contexts |
| Cross-aggregate transaction | Distributed transaction, tight coupling | Use eventual consistency via domain events |
| No ACL for external integration | External model pollutes domain | Use Anti-Corruption Layer to translate models |
| Value object as entity | Mutable value objects, identity confusion | Make value objects immutable, no identity |
| Repository returns DTO | Domain layer depends on API contracts | Repository returns domain entities, map at boundaries |

## References

- Domain-Driven Design (Eric Evans): https://domainlanguage.com/ddd/
- Implementing DDD (Vaughn Vernon): https://www.amazon.com/Implementing-Domain-Driven-Design-Vaughn-Vernon/dp/0321834577
- DDD Reference: https://www.domainlanguage.com/ddd/reference/
- Context Mapping: https://domainlanguage.com/ddd/context-mapping/
- DDD Community: https://dddcommunity.org/
- Event Modeling: https://eventmodeling.org/
- COLA (DDD framework): https://github.com/alibaba/COLA
