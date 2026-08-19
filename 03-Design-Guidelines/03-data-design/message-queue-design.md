# Message Queue Design Guidelines

> Best practices for message queue architecture design in CBOL Messaging Hub. Covers topic design, partitioning, ordering guarantees, dead letter queues, idempotent consumers, and event-driven integration.

## Message Queue Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                         Producers                              │
│  (Conversation Service, Message Service, AI Service, etc.)   │
└──────────────────────────────┬───────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │    Message Queue      │
                    │   (Kafka / RocketMQ)  │
                    │                        │
                    │  Topics:               │
                    │  - cbol-message-sent   │
                    │  - cbol-conversation-  │
                    │    created             │
                    │  - cbol-notification   │
                    │  - cbol-ai-response    │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
  │ Notification  │    │  Analytics    │    │  AI Service  │
  │   Service     │    │   Service     │    │              │
  │ (push/email)  │    │ (metrics)     │    │ (processing) │
  └──────────────┘    └──────────────┘    └──────────────┘
```

## When to Use Message Queue

```
✅ Use message queue when:
  - Decoupling producers and consumers
  - Async processing (don't want to wait)
  - Fanout (one event → many consumers)
  - Load leveling (smooth traffic spikes)
  - Reliability (retry failed processing)
  - Event sourcing / audit log
  - Cross-service communication

❌ Don't use message queue when:
  - Need synchronous response (use REST/gRPC)
  - Simple in-process call (no need for distribution)
  - Low latency requirement (< 1ms)
  - Tiny amount of messages (overkill)
```

## Topic Design

### Topic Naming Convention

```
✅ Good: {domain}-{event-name}
  cbol-message-sent
  cbol-message-delivered
  cbol-message-read
  cbol-conversation-created
  cbol-conversation-closed
  cbol-conversation-transferred
  cbol-agent-connected
  cbol-notification-push
  cbol-ai-response-generated

❌ Bad:
  messages          (too generic)
  msg_sent          (inconsistent naming)
  cbolMessageSent   (camelCase, not kebab-case)
  test              (no meaning)
```

### Topic Configuration

```yaml
# ✅ Good - topic configuration by type
topics:
  # High-throughput, real-time critical
  cbol-message-sent:
    partitions: 12
    replication-factor: 3
    retention: 7d
    cleanup-policy: delete
    min-insync-replicas: 2
    compression: lz4

  # Event log, long retention
  cbol-conversation-events:
    partitions: 6
    replication-factor: 3
    retention: 30d
    cleanup-policy: delete
    min-insync-replicas: 2

  # Notification (can afford some loss)
  cbol-notification-push:
    partitions: 6
    replication-factor: 2
    retention: 3d
    cleanup-policy: delete
    min-insync-replicas: 1
```

### Partition Key Selection

| Data | Partition Key | Why |
|------|---------------|-----|
| Message sent | `conversationId` | Messages in same conversation ordered |
| Conversation events | `conversationId` | State changes ordered per conversation |
| User notifications | `userId` | Notifications ordered per user |
| AI responses | `conversationId` | AI responses ordered per conversation |
| Analytics events | `userId` or random | Per-user analytics, or round-robin |

```java
// ✅ Good - partition key for ordering
public void publishMessageSent(MessageSentEvent event) {
    // Use conversationId as key → all messages for same conversation go to same partition
    kafkaTemplate.send("cbol-message-sent",
        event.getConversationId().toString(),  // Key = partition key
        event);
}

// ❌ Bad - no key (round-robin, no ordering)
kafkaTemplate.send("cbol-message-sent", event);  // No key → random partition
```

## Message Format

### Envelope Format

```json
{
  "id": "evt-abc123",
  "type": "message.sent",
  "version": "1.0",
  "timestamp": "2026-08-19T10:30:00Z",
  "source": "message-service",
  "correlationId": "corr-xyz789",
  "payload": {
    "messageId": 12345,
    "conversationId": 678,
    "senderId": 999,
    "content": "Hello",
    "contentType": "TEXT",
    "createdAt": "2026-08-19T10:30:00Z"
  }
}
```

### Schema Evolution

```
✅ Backward compatible changes (same topic):
  - Add optional field
  - Add new enum value
  - Deprecate field (keep for compatibility)

❌ Breaking changes (new topic version):
  - Remove required field
  - Change field type
  - Rename field
  - Change field semantics
  → Use new topic: cbol-message-sent-v2
```

```java
// ✅ Good - schema version in message
@Value
public class MessageSentEvent {
    String eventId;
    String eventType;
    String schemaVersion;  // "1.0", "1.1", "2.0"
    Instant timestamp;
    String source;
    String correlationId;
    MessageSentPayload payload;
}

// Consumer handles multiple versions
public void onMessage(MessageSentEvent event) {
    if ("1.0".equals(event.getSchemaVersion())) {
        handleV1(event);
    } else if ("2.0".equals(event.getSchemaVersion())) {
        handleV2(event);
    } else {
        log.warn("Unknown schema version: {}", event.getSchemaVersion());
    }
}
```

## Producer Design

### Reliable Producer Configuration

```java
// ✅ Good - reliable producer config
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, DomainEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability
        props.put(ProducerConfig.ACKS_CONFIG, "all");  // Wait for all in-sync replicas
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // No duplicates
        props.put(ProducerConfig.RETRIES_CONFIG, 3);  // Retry 3 times
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);  // 1s backoff

        // Performance
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 16KB batch
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);  // Wait 5ms for batch
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");  // Compression

        // Ordering
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return new DefaultKafkaProducerFactory<>(props);
    }
}
```

### Async Send with Callback

```java
// ✅ Good - async send with callback for error handling
public void publish(DomainEvent event) {
    ListenableFuture<SendResult<String, DomainEvent>> future =
        kafkaTemplate.send(event.getTopic(), event.getKey(), event);

    future.addCallback(
        result -> log.debug("Event published: {} to partition {}",
            event.getType(), result.getRecordMetadata().partition()),
        ex -> {
            log.error("Failed to publish event: {}", event.getType(), ex);
            // Handle failure: retry, store in outbox, or alert
            outboxService.storeFailed(event, ex.getMessage());
        }
    );
}
```

### Transactional Producer

```java
// ✅ Good - transactional producer for atomicity
@Transactional
public void sendMessageAndPublish(SendMessageCommand command) {
    // 1. Save message to DB
    Message message = messageRepository.save(buildMessage(command));

    // 2. Publish event (in same transaction if using Kafka transactions)
    kafkaTemplate.executeInTransaction(operations -> {
        operations.send("cbol-message-sent",
            message.getConversationId().toString(),
            new MessageSentEvent(message));
        return true;
    });
}
```

## Consumer Design

### Consumer Group Configuration

```java
// ✅ Good - consumer group config
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, MessageSentEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Reliability
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual commit
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // Start from beginning if no offset

        // Performance
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);  // Batch size
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);  // 5min processing time

        // Isolation
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return new DefaultKafkaConsumerFactory<>(props);
    }
}
```

### Idempotent Consumer

```java
// ✅ Good - idempotent consumer (at-least-once delivery)
@Service
@RequiredArgsConstructor
public class NotificationConsumer {
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;

    @KafkaListener(topics = "cbol-message-sent", groupId = "notification-service")
    public void onMessageSent(MessageSentEvent event,
                                Acknowledgment ack) {
        // 1. Check if already processed (idempotency)
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            ack.acknowledge();
            return;
        }

        try {
            // 2. Process
            notificationService.sendPushNotification(event);

            // 3. Mark as processed (in same transaction)
            processedEventRepository.save(
                new ProcessedEvent(event.getEventId(), Instant.now()));

            // 4. Acknowledge
            ack.acknowledge();
        } catch (Exception e) {
            // Don't ack - will be retried
            log.error("Failed to process event: {}", event.getEventId(), e);
            throw e;
        }
    }
}
```

### Batch Consumer

```java
// ✅ Good - batch consumer for high throughput
@KafkaListener(topics = "cbol-analytics-events",
               groupId = "analytics-service",
               batch = "true")
public void onBatch(List<AnalyticsEvent> events,
                    Acknowledgment ack) {
    try {
        // Batch process (e.g., bulk insert to DB)
        analyticsService.batchProcess(events);

        // Commit all at once
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Batch processing failed", e);
        // Don't ack - will retry entire batch
        throw e;
    }
}
```

## Dead Letter Queue (DLQ)

### DLQ Pattern

```
Main Topic → Consumer → Success? → Ack
                    ↓
                 Failed (3 retries)
                    ↓
                 DLQ Topic → DLQ Consumer → Alert / Manual review / Fix and replay
```

```java
// ✅ Good - DLQ with retry limit
@Service
public class RetryableConsumer {
    private static final int MAX_RETRIES = 3;

    @KafkaListener(topics = "cbol-message-sent", groupId = "notification-service")
    public void onMessage(MessageSentEvent event,
                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                            Acknowledgment ack) {
        try {
            process(event);
            ack.acknowledge();
        } catch (Exception e) {
            int retryCount = getRetryCount(event);
            if (retryCount < MAX_RETRIES) {
                // Retry with backoff
                scheduleRetry(event, retryCount + 1);
            } else {
                // Send to DLQ
                sendToDlq(event, e);
            }
            ack.acknowledge();  // Ack even on failure (handled via retry/DLQ)
        }
    }

    private void sendToDlq(MessageSentEvent event, Exception error) {
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalTopic("cbol-message-sent")
            .originalEvent(event)
            .errorMessage(error.getMessage())
            .errorType(error.getClass().getSimpleName())
            .failedAt(Instant.now())
            .build();

        kafkaTemplate.send("cbol-dlq", event.getEventId(), dlqMessage);
        log.error("Event sent to DLQ: {}", event.getEventId());
    }
}
```

### DLQ Monitoring

```
DLQ alerts:
  - DLQ message count > 0 → PagerDuty alert
  - DLQ message count > 100 in 5min → Critical alert
  - DLQ consumer lag > 1000 → Warning

DLQ operations:
  - View DLQ messages: kafka-console-consumer --topic cbol-dlq
  - Replay DLQ: kafka-console-producer --topic cbol-message-sent (after fix)
  - Purge DLQ: kafka-delete-records (after manual review)
```

## Ordering Guarantees

### Kafka Ordering

```
✅ Guaranteed ordering:
  - Within a single partition
  - For messages with same key (same partition)
  - With idempotent producer (no duplicates)

❌ Not guaranteed:
  - Across partitions
  - For messages with different keys
  - With retries (unless idempotent + max.in.flight <= 5)
```

```java
// ✅ Good - ordering per conversation
public void publishMessage(Message message) {
    // All messages for same conversation → same partition → ordered
    kafkaTemplate.send("cbol-message-sent",
        message.getConversationId().toString(),  // Key = conversationId
        message);
}

// Consumer processes messages in order within partition
@KafkaListener(topics = "cbol-message-sent", concurrency = "3")
// concurrency = number of partitions consumed in parallel
// Each thread consumes one or more partitions, preserving order within partition
public void onMessage(MessageSentEvent event) {
    // Messages for same conversation arrive in order
}
```

## Backpressure & Flow Control

```
Problem: Consumer can't keep up with producer → lag grows → OOM

Solution:
  1. Max poll records (limit batch size)
  2. Max poll interval (avoid consumer rebalance)
  3. Scale consumer instances (more partitions = more consumers)
  4. Batch processing (higher throughput)
  5. Flow control (pause consumption if backlog too large)
```

```java
// ✅ Good - flow control with pause/resume
@KafkaListener(topics = "cbol-message-sent", groupId = "slow-consumer")
public class FlowControlledConsumer {
    private final BlockingQueue<MessageSentEvent> processingQueue =
        new LinkedBlockingQueue<>(1000);  // Bounded queue

    @KafkaHandler
    public void onMessage(MessageSentEvent event, Consumer<?, ?> consumer) {
        // If queue is full, pause consumption
        if (processingQueue.remainingCapacity() < 100) {
            consumer.pause(consumer.assignment());
            log.warn("Consumer paused due to backlog");
        }

        // Add to processing queue
        processingQueue.offer(event);

        // If queue has capacity, resume consumption
        if (processingQueue.remainingCapacity() > 500) {
            consumer.resume(consumer.paused());
        }
    }
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| One topic for everything | No ordering, hard to scale, consumer complexity | Multiple topics by event type |
| No partition key | No ordering, random distribution | Use meaningful key (conversationId, userId) |
| Too few partitions | Can't scale consumers, throughput limit | Plan for future: partitions = max consumers * 2 |
| Too many partitions | Overhead, slow rebalance | Start with 6-12, increase as needed |
| Auto-commit offset | Lose messages on crash, commit before processing | Manual commit (ack after processing) |
| No idempotency | Duplicate processing on retry | Track processed event IDs, idempotent operations |
| No DLQ | Failed messages lost forever | DLQ with retry limit, monitoring, replay |
| Infinite retry | Poison message blocks consumer | Max retries (3), then DLQ |
| Large messages | Slow, memory pressure | Keep messages small (< 1MB), use reference to large data |
| No schema versioning | Breaking changes break consumers | Schema version in message, backward compatible changes |
| No monitoring | Can't detect lag, failures | Monitor lag, consumer rate, error rate, DLQ count |
| Synchronous send in request path | Adds latency to user request | Async send with callback, or Outbox pattern |
| No compression | High network bandwidth, storage | Use lz4/snappy compression |
| Consumer does too much | Slow processing, lag grows | Single responsibility, split into multiple consumers |
| No backpressure | Consumer OOM, crash | Bounded queue, pause/resume, scale consumers |
| Cross-partition transactions | Complex, performance hit | Design topics/keys to avoid cross-partition needs |
| No retention policy | Disk fills up | Set retention by time/size, compact for key-value topics |

## References

- Apache Kafka Documentation: https://kafka.apache.org/documentation/
- RocketMQ Documentation: https://rocketmq.apache.org/docs/
- Kafka Design Patterns: https://www.confluent.io/resources/kafka-patterns/
- Idempotent Consumer: https://microservices.io/patterns/communication-style/idempotent-consumer.html
- Dead Letter Queue: https://learn.microsoft.com/en-us/azure/architecture/patterns/competing-consumers
- Event-Driven Architecture: https://martinfowler.com/articles/201701-event-driven.html
- Kafka Exactly-Once Semantics: https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/
- Turms (IM reference): https://github.com/turms-im/turms
