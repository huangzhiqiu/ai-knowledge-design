# 07 — Message Queue & Event Driven

> Reference projects and skills for Kafka, RabbitMQ, event-driven architecture, messaging patterns, and integration testing.

## Project Index

| # | Project | Author | Type |
|---|---------|--------|------|
| 1 | [Kafka Producer Consumer Skill](#1-kafka-producer-consumer-skill) | jeremylongshore | Backend dev skill |
| 2 | [Kafka Engineer Skill](#2-kafka-engineer-skill) | belokonm | Kafka Avro + anti-patterns |
| 3 | [Messaging Testing RabbitMQ](#3-messaging-testing-rabbitmq) | skillselion.com | RabbitMQ integration testing |
| 4 | [Event-Driven Messaging Architecture](#4-event-driven-messaging-architecture) | Cortadai | Kafka + RabbitMQ 6 projects |
| 5 | [RabbitMQ AMQP Guide](#5-rabbitmq-amqp-guide) | claudify.tech | RabbitMQ best practices |

---

## 1. Kafka Producer Consumer Skill

**URL**: https://github.com/jeremylongshore/claude-code-plugins-plus-skills/blob/main/planned-skills/generated/06-backend-dev/kafka-producer-consumer/SKILL.md
**Author**: jeremylongshore
**Published**: 2026-01-03

### Description
Skill providing automated assistance for Kafka producer consumer tasks within backend development domain. Activates when mentioning Kafka producer consumer, patterns, or best practices.

### Key Features
- Step-by-step guidance for Kafka producer consumer
- Backend development domain coverage: Node.js, Python, Go, database design, caching, messaging, microservices
- Auto-activation on keyword mention

### CBOL Relevance
- **Kafka producer/consumer patterns**: Reference if CBOL uses Kafka for message queuing
- **Backend dev skill structure**: Reference for how to package messaging skills
- **Auto-activation**: Keyword-based skill triggering pattern
- **Multi-language**: Patterns applicable to Java/Spring Boot CBOL project

---

## 2. Kafka Engineer Skill

**URL**: https://github.com/belokonm/claude-supercode-skills/blob/HEAD/kafka-engineer-skill/SKILL.md
**Author**: belokonm
**Last updated**: 2026-05-29

### Description
Kafka engineer skill with Avro serialization, Schema Registry, and anti-patterns documentation.

### Key Features
- **KafkaAvroSerializer**: For Java producer
- **Schema Registry**: URL configuration (`http://schema-registry:8081`)
- **Anti-patterns & gotchas**: Documented common mistakes
- **Large messages anti-pattern**: Sending 10MB images in Kafka message

### Anti-Patterns
| Anti-Pattern | What it looks like | Why it fails |
|-------------|-------------------|-------------|
| Large Messages | Sending 10MB images as Kafka payload | Kafka is designed for small messages, large payloads cause performance issues |
| No Schema Registry | Using StringSerializer for complex objects | No schema evolution, compatibility issues |
| Synchronous Send | `producer.send().get()` for every message | Blocks thread, kills throughput |
| No Retry Config | Default retries=0 | Messages lost on transient failures |

### CBOL Relevance
- **Kafka Avro + Schema Registry**: Reference if CBOL uses Kafka for event-driven message processing
- **Anti-patterns**: Common mistakes to avoid in CBOL messaging implementation
- **Java producer**: Directly applicable to CBOL Java/Spring Boot stack
- **Schema evolution**: Pattern for evolving message schemas in CBOL

---

## 3. Messaging Testing RabbitMQ

**URL**: https://skillselion.com/skills/claude-dev-suite/claude-dev-suite/messaging-testing-rabbitmq
**Source**: skillselion.com
**Published**: 2026-07-17

### Description
Skill for writing RabbitMQ integration tests with @SpringRabbitTest, RabbitListenerTestHarness, TestRabbitTemplate, and Testcontainers.

### Key Features
- **@SpringRabbitTest**: Spring Boot test annotation for RabbitMQ
- **RabbitListenerTestHarness**: Test harness for listener containers
- **TestRabbitTemplate**: Test template for sending test messages
- **Testcontainers**: Docker-based RabbitMQ for integration tests
- **Quick references**: `spring-rabbit-test.md`, `testcontainers-rabbitmq.md`

### Test Example
```java
@SpringRabbitTest
class MessageConsumerTest {

    @Autowired
    private RabbitListenerTestHarness harness;

    @Autowired
    private TestRabbitTemplate template;

    @Test
    void shouldProcessMessage() throws Exception {
        template.convertAndSend("test.queue", "{\"id\": 1, \"content\": \"hello\"}");

        LatchCountDownAndCallRealMethodAnswer answer =
            harness.getLatchAnswerFor("messageListener", 1);
        assertThat(answer.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
```

### CBOL Relevance
- **RabbitMQ integration testing**: Reference if CBOL uses RabbitMQ for message queuing
- **@SpringRabbitTest**: Directly applicable to CBOL Spring Boot stack
- **Testcontainers**: Pattern for integration testing with real RabbitMQ
- **Listener testing**: Pattern for testing message consumers in CBOL
- **TestRabbitTemplate**: Sending test messages in integration tests

---

## 4. Event-Driven Messaging Architecture

**URL**: https://github.com/Cortadai/event-driven-messaging-architecture
**Author**: Cortadai
**Published**: 2025-12-01

### Description
Hub of event-driven architecture with Apache Kafka and RabbitMQ. 6 projects from basic tutorials to production pipelines. Includes microservices, Wikimedia EventStreams real-time data.

### Key Features
- **6 projects**: From tutorials to production pipelines
- **Kafka + RabbitMQ**: Both messaging systems covered
- **Wikimedia EventStreams**: Real-world data source (public, no auth)
- **Kafka Consumer → Database**: Listen topic, receive events, persist with Spring Data JPA
- **Microservices**: Event-driven microservice patterns

### Architecture Example
```
Wikimedia EventStreams (real-time)
        ↓
Kafka Producer (wikimedia-producer)
        ↓
Kafka Topic (wikimedia-recent-changes)
        ↓
Kafka Consumer (kafka-consumer-database)
        ↓
Spring Data JPA → MySQL (event history)
```

### CBOL Relevance
- **Event-driven architecture**: Reference for CBOL message processing pipeline
- **Kafka consumer → database**: Pattern for persisting message history in CBOL
- **Real-time data source**: Pattern for handling real-time message streams
- **6 progressive projects**: Learning path from basic to production
- **Microservices patterns**: If CBOL evolves to microservice architecture

---

## 5. RabbitMQ AMQP Guide

**URL**: https://claudify.tech/blog/claude-code-rabbitmq
**Author**: claudify.tech
**Published**: 2026-06-01

### Description
Guide to using Claude Code with RabbitMQ. Covers common mistakes: fresh connection per publish, non-durable queues, noAck consumers, no prefetch limit.

### Common Mistakes
| Mistake | Consequence | Fix |
|---------|------------|-----|
| Fresh connection per publish | Connection overhead, poor performance | Reuse connection, channel per thread |
| Non-durable queues | Queues lost on broker restart | `durable: true` |
| `noAck: true` | Messages lost if consumer crashes mid-handler | Manual ack, `noAck: false` |
| No prefetch limit | Consumer overwhelmed, unfair dispatch | `prefetch: 1` or appropriate limit |
| No DLX (Dead Letter Exchange) | Failed messages lost | Configure DLX for retry/error handling |

### CBOL Relevance
- **RabbitMQ best practices**: Reference if CBOL uses RabbitMQ for message queuing
- **Connection reuse**: Pattern for efficient message publishing in CBOL
- **Durable queues**: Message persistence for CBOL reliability
- **Manual ack**: Reliable message processing (no lost messages)
- **Prefetch limit**: Fair dispatch and consumer protection
- **Dead Letter Exchange**: Pattern for handling failed messages in CBOL
- **Direct application**: Message forwarding and retry logic in CBOL

---

## Summary: Message Queue Patterns for CBOL

| Pattern | Source | CBOL Application |
|---------|--------|-----------------|
| Kafka producer/consumer | jeremylongshore, belokonm | If CBOL uses Kafka for event-driven messaging |
| Kafka Avro + Schema Registry | belokonm | Schema evolution for CBOL message formats |
| Kafka anti-patterns | belokonm | Avoid common mistakes in CBOL messaging |
| RabbitMQ integration testing | skillselion | Test CBOL message consumers with @SpringRabbitTest |
| Testcontainers RabbitMQ | skillselion | Real RabbitMQ for CBOL integration tests |
| Event-driven architecture | Cortadai | CBOL message processing pipeline design |
| Kafka consumer → database | Cortadai | Persist CBOL message history |
| RabbitMQ connection reuse | claudify | Efficient CBOL message publishing |
| Durable queues | claudify | CBOL message persistence |
| Manual ack + prefetch | claudify | Reliable CBOL message processing |
| Dead Letter Exchange | claudify | CBOL failed message handling |
| Real-time event streams | Cortadai | CBOL real-time message handling |
| Microservice messaging | Cortadai | If CBOL evolves to microservices |

---

*Message Queue & Event Driven Reference — 2026-08-24*
