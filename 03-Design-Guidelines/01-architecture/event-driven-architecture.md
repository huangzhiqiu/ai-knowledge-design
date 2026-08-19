# Event-Driven Architecture

> Best practices for designing event-driven systems in CBOL Messaging Hub. Covers domain events, event sourcing, CQRS, publish-subscribe, and the Outbox pattern.

## Core Concepts

### Event vs Command vs Query

| Type | Purpose | Naming | Direction |
|------|---------|--------|-----------|
| **Event** | Something happened (past tense) | `ConversationCreated`, `MessageSent` | Publisher → Subscribers (fanout) |
| **Command** | Do something (imperative) | `CreateConversation`, `SendMessage` | Sender → Handler (1:1) |
| **Query** | Get data (read-only) | `GetConversation`, `ListMessages` | Caller → Callee (request/response) |

```java
// ✅ Good - domain event (past tense, immutable, contains what happened)
@Value
public class ConversationCreatedEvent implements DomainEvent {
    ConversationId conversationId;
    UserId userId;
    ConversationType type;
    Instant occurredAt;
    Long version;  // for event sourcing
}

// ✅ Good - command (imperative, contains intent)
@Value
public class SendMessageCommand {
    ConversationId conversationId;
    UserId senderId;
    String content;
    MessageType type;
}

// ❌ Bad - event named in present/future tense
public class CreateConversation { }  // Sounds like a command, not event
public class ConversationWillClose { }  // Future tense - events are past tense
```

## Publish-Subscribe Pattern

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Conversation │────►│  Message     │────►│ Notification │
│   Service    │     │    Bus       │     │   Service    │
└──────────────┘     │ (Kafka/     │     └──────────────┘
                      │  RocketMQ)  │     ┌──────────────┐
                      │             │────►│   Audit      │
                      │             │     │   Service    │
                      └──────────────┘     └──────────────┘
```

### Event Publishing

```java
// ✅ Good - domain events published from aggregate root
public class Conversation extends AggregateRoot {
    public static Conversation create(User user, ConversationType type) {
        Conversation conv = new Conversation();
        // ... setup
        conv.registerEvent(new ConversationCreatedEvent(conv.id, user.getId(), type, Instant.now()));
        return conv;
    }
}

// Application service publishes events after save
@Service
@RequiredArgsConstructor
public class ConversationApplicationService {
    private final ConversationRepository repository;
    private final EventPublisher eventPublisher;

    @Transactional
    public ConversationResponse create(CreateConversationCommand command) {
        User user = userRepository.findById(command.getUserId()).orElseThrow();
        Conversation conversation = Conversation.create(user, command.getType());
        Conversation saved = repository.save(conversation);

        // Publish domain events after successful save
        eventPublisher.publish(saved.getDomainEvents());
        saved.clearDomainEvents();

        return ConversationResponse.from(saved);
    }
}

// ❌ Bad - publishing events before save, or in domain layer
public Conversation create(...) {
    Conversation conv = new Conversation();
    eventPublisher.publish(new ConversationCreatedEvent(...));  // Published before save!
    repository.save(conv);  // If save fails, event was already published
    return conv;
}
```

## Outbox Pattern (Reliable Event Publishing)

```
Problem: Need to atomically save entity AND publish event.
         Can't use 2PC with message queue.

Solution: Save event to outbox table in same transaction.
          Background process polls outbox and publishes to queue.

┌──────────────────────────────────────────────┐
│              Transaction                      │
│  ┌─────────────┐    ┌────────────────────┐  │
│  │  Save entity │    │  Save to outbox    │  │
│  │  (conversat- │    │  (outbox_events   │  │
│  │   ions table)│    │   table)           │  │
│  └─────────────┘    └────────────────────┘  │
└──────────────────────────────────────────────┘
                          │
                          ▼ (background poller)
                   ┌──────────────┐
                   │ Message Queue│
                   └──────────────┘
```

```java
// ✅ Good - Outbox pattern implementation
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;  // JSON serialized event
    private Instant createdAt;
    private String status;  // PENDING, PUBLISHED, FAILED
    private Integer retryCount = 0;
    private Instant publishedAt;
}

// Repository with lock for concurrent polling
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);
}

// Background poller
@Component
@RequiredArgsConstructor
public class OutboxPoller {
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)  // Poll every second
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepository.findPendingEvents(PageRequest.of(0, 100));
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getAggregateId(), event.getPayload()).get();
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= 5) {
                    event.setStatus("FAILED");
                }
            }
            outboxRepository.save(event);
        }
    }
}
```

## CQRS (Command Query Responsibility Segregation)

```
                    ┌─────────────────────┐
                    │   Write Model       │
                    │  (Command Side)     │
                    │  - Rich domain      │
                    │  - Business rules   │
                    │  - Normalized DB    │
                    └──────────┬──────────┘
                               │ Domain events
                               ▼
                    ┌─────────────────────┐
                    │   Read Model        │
                    │   (Query Side)      │
                    │  - Denormalized     │
                    │  - Optimized for    │
                    │    read performance  │
                    │  - MongoDB/Redis    │
                    └─────────────────────┘
```

```java
// ✅ Good - separate command and query models
// Command side (write)
@Service
@RequiredArgsConstructor
public class ConversationCommandService {
    private final ConversationRepository repository;

    @Transactional
    public ConversationId create(CreateConversationCommand command) {
        // Rich domain logic, validation, business rules
        User user = userRepository.findById(command.getUserId()).orElseThrow();
        Conversation conversation = Conversation.create(user, command.getType());
        return repository.save(conversation).getId();
    }
}

// Query side (read) - separate service, optimized for reads
@Service
@RequiredArgsConstructor
public class ConversationQueryService {
    private final ConversationReadRepository readRepository;  // MongoDB, denormalized

    public Page<ConversationSummary> listByUser(UserId userId, Pageable pageable) {
        return readRepository.findByUserIdOrderByLastMessageAtDesc(userId, pageable);
    }

    public ConversationDetail getDetail(ConversationId id) {
        return readRepository.findDetailById(id)
            .orElseThrow(() -> new ConversationNotFoundException(id));
    }
}

// Read model (denormalized, optimized for display)
@Document(collection = "conversations_read")
public class ConversationReadModel {
    @Id
    private String conversationId;
    private Long userId;
    private String userName;  // Denormalized - no join needed
    private String userAvatar;
    private String lastMessageContent;  // Denormalized
    private Instant lastMessageAt;
    private Integer unreadCount;
    private String status;
    // ... all fields needed for list/detail views
}
```

## Event Sourcing

```
Instead of storing current state, store all events that led to current state.

State = foldl(apply, initialState, events)

┌──────────────────────────────────────────────┐
│           Event Store (events table)          │
├──────────────────────────────────────────────┤
│ id │ aggregate_id │ version │ event_type    │
├────┼──────────────┼─────────┼───────────────┤
│ 1  │ conv-123     │ 1       │ Created       │
│ 2  │ conv-123     │ 2       │ MessageAdded  │
│ 3  │ conv-123     │ 3       │ AgentAssigned │
│ 4  │ conv-123     │ 4       │ Closed        │
└────┴──────────────┴─────────┴───────────────┘

To get current state: replay events 1-4 in order.
```

```java
// ✅ Good - event-sourced aggregate
public class Conversation extends EventSourcedAggregate {
    private ConversationId id;
    private ConversationStatus status;
    private Long version = 0L;

    // Factory method creates event, applies it
    public static Conversation create(User user, ConversationType type) {
        Conversation conv = new Conversation();
        conv.apply(new ConversationCreatedEvent(
            ConversationId.generate(), user.getId(), type, Instant.now()));
        return conv;
    }

    // Apply method mutates state (no validation here - validation in command methods)
    @Override
    public void apply(DomainEvent event) {
        if (event instanceof ConversationCreatedEvent e) {
            this.id = e.getConversationId();
            this.status = ConversationStatus.ACTIVE;
        } else if (event instanceof ConversationClosedEvent e) {
            this.status = ConversationStatus.CLOSED;
        }
        this.version++;
    }

    // Command method validates, then applies event
    public void close(UserId closedBy) {
        if (this.status == ConversationStatus.CLOSED) {
            return; // idempotent
        }
        apply(new ConversationClosedEvent(this.id, closedBy, Instant.now()));
    }

    // Rebuild from events
    public static Conversation fromEvents(List<DomainEvent> events) {
        Conversation conv = new Conversation();
        events.forEach(conv::apply);
        return conv;
    }
}

// Repository saves events, not state
@Repository
@RequiredArgsConstructor
public class EventSourcedConversationRepository {
    private final EventStore eventStore;

    public void save(Conversation conversation) {
        // Save only new events (since last loaded version)
        for (DomainEvent event : conversation.getNewEvents()) {
            eventStore.save(conversation.getId(), conversation.getVersion(), event);
        }
        conversation.clearNewEvents();
    }

    public Optional<Conversation> findById(ConversationId id) {
        List<DomainEvent> events = eventStore.findByAggregateId(id);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Conversation.fromEvents(events));
    }
}
```

## Idempotent Consumers

```java
// ✅ Good - idempotent event consumer
@Service
@RequiredArgsConstructor
public class NotificationEventListener {
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;

    @KafkaListener(topics = "cbol-conversation-created", groupId = "notification-service")
    public void onConversationCreated(ConversationCreatedEvent event,
                                        @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String messageKey,
                                        Acknowledgment ack) {
        // 1. Check if event already processed (idempotency)
        if (processedEventRepository.existsByEventId(messageKey)) {
            ack.acknowledge();
            return;
        }

        try {
            // 2. Process event
            notificationService.sendWelcomeNotification(event.getUserId());

            // 3. Mark as processed (in same transaction if possible)
            processedEventRepository.save(new ProcessedEvent(messageKey, Instant.now()));

            // 4. Acknowledge
            ack.acknowledge();
        } catch (Exception e) {
            // Don't ack - will be retried
            throw e;
        }
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Event published before save | Event published but entity not persisted (inconsistency) | Use Outbox pattern, publish after save |
| No event schema versioning | Breaking changes break consumers | Version events, support backward compatibility |
| Event contains too much data | Large payload, hard to evolve | Event contains IDs + minimal data, consumers query for details |
| Synchronous event handling | Publisher waits for all subscribers | Use async message queue, fire and forget |
| No idempotency on consumer | Duplicate events cause duplicate side effects | Track processed event IDs, use idempotent operations |
| Event sourcing without need | Complexity overhead for simple domains | Use event sourcing only when audit history/temporal queries needed |
| CQRS without eventual consistency tolerance | Users see stale data and complain | Accept eventual consistency, inform users, use read-your-writes |
| Chatty events | Too many fine-grained events, high overhead | Coarse-grained events, batch when possible |
| No dead letter queue | Failed events lost forever | Use DLQ, monitor, retry manually |
| Events as commands | Event named "CreateConversation" (imperative) | Use past tense: "ConversationCreated" |
| Shared event bus without schema registry | Consumers don't know event format | Use schema registry (Avro/Protobuf), validate schemas |
| No event ordering guarantee | Events processed out of order, state corruption | Use partition key (aggregate ID) to guarantee ordering per aggregate |

## References

- Event-Driven Architecture: https://martinfowler.com/articles/201701-event-driven.html
- Event Sourcing: https://martinfowler.com/eaaDev/EventSourcing.html
- CQRS: https://martinfowler.com/bliki/CQRS.html
- Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html
- Domain Events: https://martinfowler.com/eaaDev/DomainEvent.html
- Apache Kafka: https://kafka.apache.org/documentation/
- Axon Framework: https://axoniq.io/
- Eventuate: https://eventuate.io/
