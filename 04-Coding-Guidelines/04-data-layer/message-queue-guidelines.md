# Message Queue Guidelines (Kafka / RocketMQ)

> Best practices for message queue development, producer/consumer patterns, and reliability.

## Core Principles

1. **Idempotent consumers** — messages can be redelivered, consumers must handle duplicates
2. **At-least-once delivery** — plan for duplicates, don't rely on exactly-once
3. **Backpressure** — consumers must signal when overwhelmed, don't drop messages
4. **Monitoring** — lag, error rate, throughput must be monitored
5. **Schema evolution** — message schemas must be backward/forward compatible

## Topic Design

### Naming Conventions

```
# ✅ Good - descriptive, environment-prefixed, with domain
cbol-dev-message-sent          # message sent event
cbol-dev-conversation-created  # conversation created event
cbol-dev-user-status-changed   # user status changed event
cbol-dev-notification-email    # email notification command
cbol-dev-dlq-message-sent      # dead letter queue for message-sent

# Topic naming pattern: {project}-{env}-{domain}-{event/command}
# event: something that happened (past tense)
# command: something to do (imperative)

# ❌ Bad - vague, no env, inconsistent
messages
test_topic
my-topic
data
```

### Topic Configuration

```yaml
# ✅ Good - Kafka topic configuration
# For events (high throughput, retention for replay)
cbol-dev-message-sent:
  partitions: 12              # parallelism = consumers in group
  replication.factor: 3      # durability
  retention.ms: 604800000    # 7 days retention (for replay)
  cleanup.policy: delete
  min.insync.replicas: 2     # ack=all requires 2 in-sync
  compression.type: lz4       # compression for throughput

# For commands (lower throughput, shorter retention)
cbol-dev-notification-email:
  partitions: 4
  replication.factor: 3
  retention.ms: 86400000     # 1 day
  cleanup.policy: delete

# For compacted topics (latest state)
cbol-dev-user-status:
  partitions: 6
  replication.factor: 3
  cleanup.policy: compact     # keep latest per key
```

### Partition Key Selection

```java
// ✅ Good - partition key for ordering + parallelism
// Key = entity ID that requires ordering
// Messages with same key go to same partition = ordered per entity

// Messages for a conversation must be ordered → key = conversationId
ProducerRecord<String, MessageSentEvent> record = new ProducerRecord<>(
    "cbol-dev-message-sent",
    String.valueOf(conversationId),  // key = conversationId (ordered per conversation)
    event
);
kafkaTemplate.send(record);

// User status changes must be ordered per user → key = userId
ProducerRecord<String, UserStatusEvent> record = new ProducerRecord<>(
    "cbol-dev-user-status-changed",
    String.valueOf(userId),  // key = userId (ordered per user)
    event
);

// ❌ Bad - null key (round-robin, no ordering)
kafkaTemplate.send("cbol-dev-message-sent", event);
// Messages for same conversation can go to different partitions = out of order!

// ❌ Bad - constant key (all messages to one partition = no parallelism)
kafkaTemplate.send("cbol-dev-message-sent", "constant", event);
// All messages to partition 0 = single consumer bottleneck
```

## Producer Best Practices

### Reliable Producer Configuration

```yaml
# ✅ Good - Kafka producer for reliability
spring:
  kafka:
    producer:
      acks: all                    # wait for all in-sync replicas (durability)
      retries: 3                  # retry on transient failure
      properties:
        enable.idempotence: true  # idempotent producer (no duplicates on retry)
        max.in.flight.requests.per.connection: 5  # with idempotence, preserves order
        linger.ms: 10             # batch for 10ms (throughput)
        batch.size: 16384         # batch size (16KB)
        compression.type: lz4     # compression
        delivery.timeout.ms: 30000  # 30s total timeout
        request.timeout.ms: 10000   # 10s per request
        retries: 3

# ❌ Bad - fire-and-forget, no durability
spring:
  kafka:
    producer:
      acks: 0       # no ack = message can be lost!
      retries: 0    # no retry = transient failure = lost message
```

### Producer Patterns

```java
// ✅ Good - async send with callback for error handling
ListenableFuture<SendResult<String, MessageSentEvent>> future =
    kafkaTemplate.send("cbol-dev-message-sent", key, event);

future.addCallback(
    result -> log.debug("Message sent: {} to partition {}",
        event.getMessageId(), result.getRecordMetadata().partition()),
    ex -> {
        log.error("Failed to send message: {}", event.getMessageId(), ex);
        // Handle failure: retry, store to outbox, alert
        messageOutboxService.saveFailed(event, ex.getMessage());
    }
);

// ✅ Good - transactional producer (atomic with DB)
@Transactional
public void sendMessage(Message message) {
    // 1. Save to DB
    messageRepository.save(message);

    // 2. Send to Kafka (in same transaction)
    kafkaTemplate.executeInTransaction(ops ->
        ops.send("cbol-dev-message-sent", key, event)
    );

    // If either fails, both roll back (atomic)
}

// ❌ Bad - fire and forget, no error handling
kafkaTemplate.send("cbol-dev-message-sent", event);
// If send fails, message is lost silently!
```

### Outbox Pattern (Reliable)

```java
// ✅ Good - Transactional Outbox pattern
@Transactional
public void createConversation(Conversation conversation) {
    // 1. Save entity
    conversationRepository.save(conversation);

    // 2. Save event to outbox table (same transaction)
    outboxRepository.save(OutboxEvent.builder()
        .topic("cbol-dev-conversation-created")
        .key(String.valueOf(conversation.getId()))
        .payload(objectMapper.writeValueAsString(event))
        .status("PENDING")
        .build());
}

// 3. Separate relay process polls outbox and sends to Kafka
@Scheduled(fixedDelay = 1000)
public void relayOutbox() {
    List<OutboxEvent> pending = outboxRepository.findPending(100);
    for (OutboxEvent event : pending) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload()).get();
            outboxRepository.markAsSent(event.getId());
        } catch (Exception e) {
            log.error("Failed to relay outbox event: {}", event.getId(), e);
            // Will retry on next schedule
        }
    }
}
```

## Consumer Best Practices

### Consumer Group & Configuration

```yaml
# ✅ Good - Kafka consumer configuration
spring:
  kafka:
    consumer:
      group-id: cbol-message-processor
      auto-offset-reset: earliest     # start from beginning if no offset
      enable-auto-commit: false        # manual commit (at-least-once)
      properties:
        max.poll.records: 100          # batch size
        max.poll.interval.ms: 300000   # 5min to process batch
        session.timeout.ms: 45000       # 45s heartbeat timeout
        heartbeat.interval.ms: 15000     # 15s heartbeat
        fetch.min.bytes: 1024            # wait for 1KB before returning
        fetch.max.wait.ms: 500           # max wait 500ms
        isolation.level: read_committed  # for transactional topics

# ❌ Bad - auto-commit, huge batch, no timeout
spring:
  kafka:
    consumer:
      enable-auto-commit: true   # auto-commit = can lose messages on crash
      max.poll.records: 10000    # huge batch = long processing = rebalance
      max.poll.interval.ms: 60000 # too short = rebalance on slow processing
```

### Consumer Patterns

```java
// ✅ Good - manual commit after processing (at-least-once)
@KafkaListener(topics = "cbol-dev-message-sent", groupId = "cbol-message-processor")
public void processMessage(ConsumerRecord<String, MessageSentEvent> record,
                           Acknowledgment ack) {
    try {
        // 1. Process message
        MessageSentEvent event = record.value();
        messageService.process(event);

        // 2. Acknowledge after successful processing
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Failed to process message: {}", record.key(), e);
        // 3. Don't ack → message will be redelivered
        // 4. After max retries → send to DLQ
        if (record.headers().lastHeader("x-retry-count") != null
            && Integer.parseInt(new String(record.headers().lastHeader("x-retry-count").value())) >= 3) {
            sendToDlq(record);
            ack.acknowledge(); // ack to move past poison message
        }
    }
}

// ✅ Good - idempotent consumer (handle duplicates)
public void process(MessageSentEvent event) {
    // Check if already processed (using message ID as idempotency key)
    if (processedMessageRepository.existsById(event.getMessageId())) {
        log.debug("Message already processed: {}", event.getMessageId());
        return; // duplicate, skip
    }

    // Process message
    doProcess(event);

    // Mark as processed (in same transaction)
    processedMessageRepository.save(new ProcessedMessage(event.getMessageId(), Instant.now()));
}

// ❌ Bad - no error handling, auto-commit
@KafkaListener(topics = "cbol-dev-message-sent", groupId = "cbol-message-processor")
public void processMessage(MessageSentEvent event) {
    messageService.process(event); // if throws, message is lost (auto-commit already done)
}
```

### Batch Consumer

```java
// ✅ Good - batch consumer for high throughput
@KafkaListener(topics = "cbol-dev-message-sent", groupId = "cbol-batch-processor",
               containerFactory = "batchListenerContainerFactory")
public void processBatch(List<ConsumerRecord<String, MessageSentEvent>> records,
                         Acknowledgment ack) {
    try {
        // Process batch (e.g., bulk insert to DB)
        List<MessageSentEvent> events = records.stream()
            .map(ConsumerRecord::value)
            .collect(Collectors.toList());
        messageService.batchProcess(events);

        ack.acknowledge(); // commit entire batch
    } catch (Exception e) {
        log.error("Failed to process batch of {} records", records.size(), e);
        // Don't ack → entire batch redelivered
        // Consider: process individually, skip poison messages
    }
}
```

## Dead Letter Queue (DLQ)

```java
// ✅ Good - DLQ pattern for poison messages
public void handleFailure(ConsumerRecord<String, MessageSentEvent> record, Exception e) {
    int retryCount = getRetryCount(record);

    if (retryCount < 3) {
        // Retry with backoff (reproduce to retry topic with delay)
        ProducerRecord<String, MessageSentEvent> retryRecord = new ProducerRecord<>(
            "cbol-dev-retry-message-sent",
            record.key(),
            record.value()
        );
        retryRecord.headers().add("x-retry-count", String.valueOf(retryCount + 1).getBytes());
        retryRecord.headers().add("x-original-topic", record.topic().getBytes());
        kafkaTemplate.send(retryRecord);
    } else {
        // Max retries exceeded → send to DLQ
        ProducerRecord<String, MessageSentEvent> dlqRecord = new ProducerRecord<>(
            "cbol-dev-dlq-message-sent",
            record.key(),
            record.value()
        );
        dlqRecord.headers().add("x-original-topic", record.topic().getBytes());
        dlqRecord.headers().add("x-error", e.getMessage().getBytes());
        dlqRecord.headers().add("x-failed-at", Instant.now().toString().getBytes());
        kafkaTemplate.send(dlqRecord);
        log.error("Message sent to DLQ: {}", record.key());
    }
}
```

## RocketMQ Specifics

```java
// ✅ Good - RocketMQ producer
@Bean
public DefaultMQProducer messageProducer() {
    DefaultMQProducer producer = new DefaultMQProducer("cbol-message-producer-group");
    producer.setNamesrvAddr("rocketmq:9876");
    producer.setRetryTimesWhenSendFailed(3);
    producer.setSendMsgTimeout(3000);
    producer.start();
    return producer;
}

// Send message
Message msg = new Message(
    "cbol-dev-message-sent",  // topic
    "sent",                    // tag (filtering)
    String.valueOf(messageId), // key (ordering + search)
    objectMapper.writeValueAsBytes(event)
);
SendResult result = producer.send(msg);

// ✅ Good - RocketMQ consumer (concurrent)
@Bean
public DefaultMQPushConsumer messageConsumer() {
    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("cbol-message-processor-group");
    consumer.setNamesrvAddr("rocketmq:9876");
    consumer.subscribe("cbol-dev-message-sent", "*"); // all tags
    consumer.setConsumeThreadMin(5);
    consumer.setConsumeThreadMax(20);
    consumer.setConsumeTimeout(15, TimeUnit.MINUTES);
    consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
        for (MessageExt msg : msgs) {
            try {
                MessageSentEvent event = objectMapper.readValue(msg.getBody(), MessageSentEvent.class);
                messageService.process(event);
            } catch (Exception e) {
                log.error("Failed to process message: {}", msg.getKeys(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER; // retry
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    });
    consumer.start();
    return consumer;
}
```

## Monitoring

```yaml
# ✅ Good - monitor these metrics
# Consumer lag (most important)
kafka_consumer_lag{topic="cbol-dev-message-sent", group="cbol-message-processor"} > 1000

# Error rate
kafka_consumer_errors_total{topic="cbol-dev-message-sent"} / kafka_consumer_messages_total > 0.01

# DLQ size
kafka_topic_messages_total{topic="cbol-dev-dlq-message-sent"} > 0

# Producer latency
kafka_producer_send_latency_ms{topic="cbol-dev-message-sent"} > 1000

# Consumer processing time
kafka_consumer_processing_time_ms{topic="cbol-dev-message-sent"} > 5000
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| `acks=0` producer | Messages can be lost silently | `acks=all` + idempotent producer |
| Auto-commit consumer | Messages lost on crash | Manual commit after processing |
| No idempotency | Duplicate messages cause duplicate processing | Idempotency key (message ID) |
| No DLQ | Poison messages block consumer forever | DLQ after max retries |
| One partition topic | No parallelism, single consumer bottleneck | Multiple partitions (12+ for high throughput) |
| Null key for ordered events | Messages out of order per entity | Key = entity ID (conversationId, userId) |
| Infinite retry | Poison message blocks forever | Max retries → DLQ |
| No monitoring | Issues discovered late (by users) | Monitor lag, error rate, DLQ, latency |
| Processing in consumer thread (blocking) | Slow processing → rebalance | Offload to thread pool, or batch processing |
| Large messages (>1MB) | Kafka performance degradation | Store payload in object storage, send reference |
| No schema versioning | Incompatible changes break consumers | Schema registry, backward/forward compatible |
| `enable.auto.commit=true` + long processing | Offset committed before processing done | Manual commit, `max.poll.interval.ms` tuned |

## References

- Kafka Documentation: https://kafka.apache.org/documentation/
- RocketMQ Documentation: https://rocketmq.apache.org/docs/
- Kafka Best Practices: https://github.com/apache/kafka/blob/trunk/docs/streams/developer-guide/processor-api.md
- RocketMQ Best Practices: https://github.com/apache/rocketmq/blob/master/docs/en/best_practice.md
- Spring Kafka: https://docs.spring.io/spring-kafka/reference/
- Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html
