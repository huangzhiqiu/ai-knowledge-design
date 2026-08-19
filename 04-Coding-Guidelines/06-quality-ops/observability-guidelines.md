# Observability Guidelines

> Best practices for logging, metrics, distributed tracing, and monitoring in CBOL messaging system.

## The Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────────────┐
│                      Observability                                │
├─────────────────────┬─────────────────────┬─────────────────────┤
│      Logging        │      Metrics        │      Tracing        │
│                     │                     │                     │
│  - What happened?   │  - How many?        │  - Where did it     │
│  - When?            │  - How fast?        │    happen?          │
│  - Who?             │  - How often?       │  - What was the     │
│  - Error details    │  - Trends/anomalies │    call chain?      │
│                     │                     │  - Latency per step │
│  Tools: ELK, Loki   │  Tools: Prometheus, │  Tools: Jaeger,     │
│          CloudWatch │          Grafana    │          SkyWalking  │
└─────────────────────┴─────────────────────┴─────────────────────┘
```

## Logging

### Logging Framework

```java
// ✅ Good - SLF4J with Logback (Spring Boot default)
@Service
@RequiredArgsConstructor
@Slf4j  // Lombok generates: private static final Logger log = LoggerFactory.getLogger(...)
public class MessageService {

    public MessageResponse sendMessage(SendMessageRequest request) {
        log.debug("Sending message: conversationId={}, senderId={}",
            request.getConversationId(), request.getSenderId());

        try {
            Message message = messageRepository.save(buildMessage(request));
            log.info("Message sent successfully: messageId={}, conversationId={}",
                message.getId(), message.getConversationId());
            return mapper.toResponse(message);
        } catch (DataAccessException e) {
            log.error("Failed to send message: conversationId={}, error={}",
                request.getConversationId(), e.getMessage(), e);  // include stack trace
            throw new MessageSendException(request.getConversationId(), e);
        }
    }
}

// ❌ Bad - System.out.println, no levels, no context
public void sendMessage(SendMessageRequest request) {
    System.out.println("Sending message: " + request);  // no level, no MDC
    try {
        messageRepository.save(buildMessage(request));
        System.out.println("Message sent");
    } catch (Exception e) {
        e.printStackTrace();  // raw stack trace, no context
    }
}
```

### Log Levels

| Level | Use For | Example | Production Default |
|-------|---------|---------|-------------------|
| ERROR | Unrecoverable errors, exceptions | "Failed to connect to database", "Message processing failed" | ✅ Enabled |
| WARN | Recoverable issues, unexpected but handled | "Cache miss, falling back to DB", "Retry attempt 2/3" | ✅ Enabled |
| INFO | Important business events, state changes | "Message sent", "User logged in", "Conversation closed" | ✅ Enabled |
| DEBUG | Detailed diagnostic info | "Message content: ...", "Cache key: ...", "Query params: ..." | ❌ Disabled (enable per request) |
| TRACE | Very detailed, method entry/exit | "Entering method sendMessage", "Variable x = 5" | ❌ Disabled |

### Structured Logging (MDC)

```java
// ✅ Good - MDC for structured logging (traceId, userId, conversationId)
@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // Set MDC context
            String traceId = generateTraceId();
            MDC.put("traceId", traceId);
            MDC.put("spanId", generateSpanId());

            // Add user info if authenticated
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long userId) {
                MDC.put("userId", String.valueOf(userId));
            }

            chain.doFilter(request, response);
        } finally {
            // Clear MDC after request (important for thread pools!)
            MDC.clear();
        }
    }
}

// logback-spring.xml - include MDC fields in log pattern
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - traceId=%X{traceId} userId=%X{userId} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- JSON appender for production (ELK/Loki) -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>conversationId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
        <springProfile name="prod">
            <appender-ref ref="JSON" />
        </springProfile>
    </root>
</configuration>
```

### Logging Best Practices

```java
// ✅ Good - parameterized logging (no string concatenation)
log.debug("Processing message: id={}, type={}", messageId, messageType);

// ❌ Bad - string concatenation (performance overhead even if debug disabled)
log.debug("Processing message: id=" + messageId + ", type=" + messageType);

// ✅ Good - include exception as last parameter (stack trace)
log.error("Failed to process message: id={}", messageId, e);

// ❌ Bad - log exception message only (no stack trace)
log.error("Failed to process message: " + e.getMessage());

// ✅ Good - log at appropriate level
log.info("User logged in: userId={}", userId);  // business event = INFO
log.warn("Cache miss for key={}, falling back to DB", key);  // recoverable = WARN
log.error("Database connection failed", e);  // error = ERROR

// ❌ Bad - wrong log level
log.info("Database connection failed", e);  // should be ERROR
log.error("Cache miss for key=" + key);  // should be WARN/DEBUG

// ✅ Good - don't log sensitive data
log.info("User logged in: userId={}", userId);  // log ID, not password/token
// NEVER log: passwords, tokens, API keys, PII (email, phone, address)

// ❌ Bad - logging sensitive data
log.info("User logged in: email={}, password={}", email, password);  // PII + secret!
```

## Metrics

### Micrometer + Prometheus

```java
// ✅ Good - custom metrics with Micrometer
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter messagesSentCounter = Counter.builder("cbol.messages.sent.total")
        .description("Total messages sent")
        .tag("type", "text")
        .register(meterRegistry);

    private final Counter messagesFailedCounter = Counter.builder("cbol.messages.failed.total")
        .description("Total messages failed")
        .tag("reason", "unknown")
        .register(meterRegistry);

    // Timers
    private final Timer messageProcessingTimer = Timer.builder("cbol.message.processing.duration")
        .description("Message processing duration")
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(meterRegistry);

    // Gauges
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        Gauge.builder("cbol.connections.active", activeConnections, AtomicInteger::get)
            .description("Active WebSocket connections")
            .register(meterRegistry);
    }

    public MessageResponse sendMessage(SendMessageRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Message message = processMessage(request);
            messagesSentCounter.increment();
            return mapper.toResponse(message);
        } catch (Exception e) {
            messagesFailedCounter.increment();
            throw e;
        } finally {
            sample.stop(messageProcessingTimer);
        }
    }
}
```

### Key Metrics to Monitor

| Metric | Type | Description | Alert Threshold |
|--------|------|-------------|-----------------|
| `cbol.messages.sent.total` | Counter | Messages sent | - |
| `cbol.messages.failed.total` | Counter | Messages failed | > 5% of sent |
| `cbol.message.processing.duration` | Timer | Message processing time | p99 > 500ms |
| `cbol.connections.active` | Gauge | Active WebSocket connections | > 90% of capacity |
| `cbol.connections.established.total` | Counter | New connections | - |
| `cbol.connections.dropped.total` | Counter | Dropped connections | > 10/min |
| `cbol.api.requests.total` | Counter | API requests | - |
| `cbol.api.requests.duration` | Timer | API request duration | p99 > 1s |
| `cbol.api.errors.total` | Counter | API errors (4xx/5xx) | > 5% of requests |
| `cbol.db.query.duration` | Timer | DB query duration | p99 > 100ms |
| `cbol.redis.cache.hit.rate` | Gauge | Cache hit rate | < 80% |
| `cbol.queue.lag` | Gauge | Message queue lag | > 1000 messages |
| `jvm.memory.used` | Gauge | JVM heap used | > 80% of max |
| `jvm.gc.pause` | Timer | GC pause duration | p99 > 1s |
| `process.cpu.usage` | Gauge | CPU usage | > 80% |

### Prometheus Configuration

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
      environment: ${spring.profiles.active}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      sla:
        http.server.requests: 50ms, 100ms, 200ms, 500ms, 1s, 2s
```

### Grafana Dashboards

```
Dashboard: CBOL Messaging Overview
├── Message Throughput (messages/sec)
├── Message Processing Latency (p50, p95, p99)
├── Message Failure Rate (%)
├── Active WebSocket Connections
├── API Request Rate (req/sec)
├── API Error Rate (4xx, 5xx)
├── API Latency (p50, p95, p99)
├── JVM Heap Usage (%)
├── JVM GC Pause (p99)
├── CPU Usage (%)
├── Database Query Latency (p99)
├── Redis Cache Hit Rate (%)
└── Message Queue Lag (messages)
```

## Distributed Tracing

### OpenTelemetry / Micrometer Tracing

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling in dev, 10% in prod
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans

# Or use OpenTelemetry agent
# java -javaagent:opentelemetry-javaagent.jar \
#   -Dotel.service.name=cbol-messaging \
#   -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
#   -jar app.jar
```

### Trace Propagation

```java
// ✅ Good - trace context propagation across threads/services
@Service
@RequiredArgsConstructor
public class MessageService {
    private final Tracer tracer;
    private final ExecutorService executorService;

    public void processAsync(Message message) {
        // Capture current span
        Span currentSpan = tracer.currentSpan();

        executorService.submit(() -> {
            // Restore trace context in new thread
            try (Scope scope = currentSpan.makeCurrent()) {
                // Create child span
                Span childSpan = tracer.nextSpan().name("processMessage").start();
                try (Scope childScope = childSpan.makeCurrent()) {
                    doProcess(message);
                } finally {
                    childSpan.end();
                }
            }
        });
    }
}

// ✅ Good - trace context in WebSocket messages
@Component
public class TracingWebSocketHandler extends TextWebSocketHandler {
    private final Tracer tracer;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextWebSocketFrame message) {
        // Extract trace context from message headers or payload
        String traceId = (String) session.getAttributes().get("traceId");
        SpanContext parentContext = extractParentContext(traceId);

        Span span = tracer.spanBuilder()
            .setParent(Context.current().with(Span.wrap(parentContext)))
            .setName("handleWebSocketMessage")
            .start();

        try (Scope scope = span.makeCurrent()) {
            // process message
        } finally {
            span.end();
        }
    }
}
```

### Trace Semantic Conventions

| Attribute | Description | Example |
|-----------|-------------|---------|
| `service.name` | Service name | `cbol-messaging` |
| `service.version` | Service version | `1.2.3` |
| `service.environment` | Environment | `prod` |
| `http.method` | HTTP method | `POST` |
| `http.url` | Full URL | `https://api.cbol.com/v1/messages` |
| `http.status_code` | HTTP status | `201` |
| `http.request.body.size` | Request body size | `128` |
| `db.system` | Database type | `mysql` |
| `db.name` | Database name | `cbol` |
| `db.operation` | DB operation | `SELECT`, `INSERT` |
| `db.sql.table` | Table name | `messages` |
| `messaging.system` | Messaging system | `kafka`, `rocketmq` |
| `messaging.destination` | Topic/queue name | `cbol-message-sent` |
| `messaging.operation` | Operation | `send`, `receive` |
| `cbol.conversation.id` | Conversation ID | `12345` |
| `cbol.message.id` | Message ID | `67890` |
| `cbol.user.id` | User ID | `111` |

## Alerting

### Alert Rules (Prometheus)

```yaml
# prometheus-alerts.yml
groups:
  - name: cbol-messaging-alerts
    rules:
      - alert: HighMessageFailureRate
        expr: |
          rate(cbol_messages_failed_total[5m])
          / rate(cbol_messages_sent_total[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High message failure rate (>5%)"
          description: "Message failure rate is {{ $value | humanizePercentage }} for the last 5 minutes"

      - alert: HighMessageLatency
        expr: histogram_quantile(0.99, rate(cbol_message_processing_duration_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High message processing latency (p99 > 500ms)"
          description: "p99 latency is {{ $value }}s"

      - alert: HighConnectionCount
        expr: cbol_connections_active > 9000
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "High active connection count (>90%)"
          description: "Active connections: {{ $value }}"

      - alert: HighAPIErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          / rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High API error rate (>5%)"

      - alert: HighJVMHeapUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High JVM heap usage (>85%)"

      - alert: ServiceDown
        expr: up{job="cbol-messaging"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "CBOL Messaging service is down"

      - alert: HighMessageQueueLag
        expr: cbol_queue_lag > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High message queue lag (>1000 messages)"
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| `System.out.println` | No levels, no structure, performance | SLF4J logger with proper levels |
| String concatenation in log | Performance overhead even if disabled | Parameterized logging (`{}`) |
| Logging sensitive data | Data breach, compliance violation | Never log passwords, tokens, PII |
| No MDC context | Can't correlate logs across services | MDC with traceId, userId, conversationId |
| Logging exception message only | No stack trace, hard to debug | Include exception as last parameter |
| No metrics | Blind to performance issues | Micrometer + Prometheus for key metrics |
| Metrics without tags | Can't slice/dice by dimension | Tag by type, status, endpoint, etc. |
| No distributed tracing | Can't trace requests across services | OpenTelemetry / Micrometer Tracing |
| No alerting | Issues discovered by users, not monitoring | Prometheus Alertmanager with severity levels |
| `e.printStackTrace()` | Raw output, no context | `log.error("message", e)` |
| Logging at INFO for everything | Too noisy, important logs buried | Use appropriate levels (DEBUG/INFO/WARN/ERROR) |
| No log rotation | Disk fills up | Configure logback rolling policy |
| Tracing 100% in production | High overhead, cost | Sample 10-20% in prod, 100% in dev |
| No health checks | Can't detect unhealthy instances | Spring Boot Actuator health + liveness/readiness probes |

## References

- SLF4J: https://www.slf4j.org/manual.html
- Logback: https://logback.qos.ch/manual/
- Micrometer: https://micrometer.io/docs
- Prometheus: https://prometheus.io/docs/introduction/overview/
- Grafana: https://grafana.com/docs/
- OpenTelemetry: https://opentelemetry.io/docs/
- Jaeger: https://www.jaegertracing.io/docs/
- SkyWalking: https://skywalking.apache.org/docs/
- Spring Boot Actuator: https://docs.spring.io/spring-boot/reference/actuator/
- The Twelve-Factor App (Logs): https://12factor.net/logs
