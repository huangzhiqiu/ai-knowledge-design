# Observability Design Guidelines

> Best practices for designing observable systems in CBOL Messaging Hub. Covers logging, metrics, distributed tracing, alerting, dashboards, and SLO monitoring.

## Observability Fundamentals

### What is Observability?

```
Observability = ability to understand system internal state from external outputs

Three Pillars:
  1. Logs: Discrete events, rich context, high cardinality
  2. Metrics: Aggregated numerical data, low cardinality, real-time
  3. Traces: Request flow across services, timing, dependencies

Relationship:
  - Metrics tell you something is wrong
  - Traces tell you where it's wrong
  - Logs tell you why it's wrong

Additional Signals:
  - Events: Important state changes (deployment, config change, alert)
  - Profiles: CPU/memory profiling for performance optimization
  - RUM (Real User Monitoring): Frontend performance and errors
```

### Observability Maturity Levels

| Level | Description | Capabilities |
|-------|-------------|-------------|
| **L0: Reactive** | No monitoring, respond to user complaints | SSH into server, read logs manually |
| **L1: Basic** | Basic metrics and logs | CPU/memory graphs, log aggregation, basic alerts |
| **L2: Proactive** | Structured logs, custom metrics, alerting | Dashboards, SLO monitoring, on-call rotation |
| **L3: Advanced** | Distributed tracing, APM, anomaly detection | End-to-end visibility, automated root cause analysis |
| **L4: Predictive** | ML-based prediction, automated remediation | Predict failures, auto-remediation, chaos engineering |

**CBOL Target: L3 (Advanced)**

## Logging

### Logging Principles

```
1. Structured logging:
   - JSON format (machine-parseable)
   - Consistent field names
   - Include context (traceId, userId, requestId)

2. Log levels:
   - ERROR: System failure, needs immediate attention
   - WARN: Potential problem, may need investigation
   - INFO: Important business events (user login, message sent)
   - DEBUG: Detailed debugging info (not in production)
   - TRACE: Very detailed (development only)

3. What to log:
   - ✅ Business events (message sent, conversation created, user login)
   - ✅ Errors and exceptions (with stack trace)
   - ✅ State changes (conversation state transition)
   - ✅ External API calls (request/response summary, latency)
   - ✅ Performance metrics (slow queries, long operations)
   - ❌ Sensitive data (passwords, tokens, PII, message content)
   - ❌ Debug info in production
   - ❌ Logging in tight loops (high volume)
   - ❌ Personal data without masking

4. Log context (MDC):
   - traceId: Distributed trace identifier
   - spanId: Current span identifier
   - requestId: Unique request identifier
   - userId: Authenticated user ID
   - conversationId: Current conversation context
   - sessionId: WebSocket session ID
```

```java
// ✅ Good - structured logging with MDC context
@Service
@RequiredArgsConstructor
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    public MessageResponse sendMessage(SendMessageRequest request, Long userId) {
        // Add context to MDC (automatically included in all logs)
        MDC.put("userId", String.valueOf(userId));
        MDC.put("conversationId", String.valueOf(request.getConversationId()));

        try {
            log.info("Sending message: type={}, contentLength={}",
                request.getType(), request.getContent().length());

            long start = System.currentTimeMillis();
            Message message = messageRepository.save(toMessage(request, userId));
            long duration = System.currentTimeMillis() - start;

            if (duration > 100) {
                log.warn("Slow message persist: duration={}ms, messageId={}", duration, message.getId());
            }

            log.info("Message sent: messageId={}, recipients={}",
                message.getId(), message.getRecipientIds().size());

            return toResponse(message);

        } catch (Exception e) {
            log.error("Failed to send message: error={}, message={}",
                e.getClass().getSimpleName(), e.getMessage(), e);  // Include exception as last arg for stack trace
            throw e;
        } finally {
            // Clear MDC to prevent leakage
            MDC.remove("userId");
            MDC.remove("conversationId");
        }
    }
}

// ✅ Good - logback configuration for structured JSON logging
// logback-spring.xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>conversationId</includeMdcKeyName>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <version>[ignore]</version>
            </fieldNames>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON" />
    </root>
</configuration>
```

### Log Format Example

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.selfdevelopment.ai.messaging.message.MessageService",
  "message": "Message sent: messageId=12345, recipients=3",
  "thread": "http-nio-8080-exec-5",
  "traceId": "abc123def456",
  "spanId": "span789",
  "requestId": "req-001",
  "userId": "1001",
  "conversationId": "2002"
}
```

### Sensitive Data Masking

```java
// ✅ Good - sensitive data masking in logs
public class LogMaskingUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");
    private static final Pattern CARD_PATTERN = Pattern.compile("(\\d{4})\\d{8,12}(\\d{4})");

    public static String mask(String input) {
        if (input == null) return null;

        // Mask email: j***@example.com
        input = EMAIL_PATTERN.matcher(input).replaceAll(m -> {
            String user = m.group(1);
            String domain = m.group(2);
            return user.charAt(0) + "***@" + domain;
        });

        // Mask phone: 138****5678
        input = PHONE_PATTERN.matcher(input).replaceAll("$1****$2");

        // Mask credit card: 4111********1111
        input = CARD_PATTERN.matcher(input).replaceAll("$1********$2");

        return input;
    }
}
```

## Metrics

### Metrics Types

| Type | Description | Example |
|------|-------------|---------|
| **Counter** | Monotonically increasing value | Total requests, total errors, total messages |
| **Gauge** | Point-in-time value (can go up/down) | Active connections, queue depth, memory usage |
| **Histogram** | Distribution of values (percentiles) | Request latency, response size, DB query time |
| **Summary** | Similar to histogram, client-side calculated | Latency percentiles, request count |

### Key Metrics for CBOL

```
Business Metrics:
  - messages_sent_total (counter): Total messages sent
  - messages_delivered_total (counter): Total messages delivered
  - messages_failed_total (counter): Total failed messages
  - active_conversations (gauge): Current active conversations
  - online_users (gauge): Current online users
  - message_delivery_rate (gauge): Delivery success rate

System Metrics:
  - http_requests_total (counter): Total HTTP requests
  - http_request_duration_seconds (histogram): HTTP request latency
  - http_errors_total (counter): HTTP errors by status code
  - websocket_connections (gauge): Active WebSocket connections
  - websocket_messages_received_total (counter): WebSocket messages received
  - websocket_messages_sent_total (counter): WebSocket messages sent

Database Metrics:
  - db_connections_active (gauge): Active DB connections
  - db_connections_idle (gauge): Idle DB connections
  - db_query_duration_seconds (histogram): DB query latency
  - db_slow_queries_total (counter): Slow queries (>1s)

Cache Metrics:
  - cache_hits_total (counter): Cache hits
  - cache_misses_total (counter): Cache misses
  - cache_hit_ratio (gauge): Cache hit ratio
  - cache_evictions_total (counter): Cache evictions

Message Queue Metrics:
  - mq_messages_produced_total (counter): Messages produced
  - mq_messages_consumed_total (counter): Messages consumed
  - mq_consumer_lag (gauge): Consumer lag (messages not yet consumed)
  - mq_dlq_messages_total (counter): Dead letter queue messages

JVM Metrics:
  - jvm_memory_used_bytes (gauge): Heap/non-heap memory usage
  - jvm_gc_pause_seconds (histogram): GC pause duration
  - jvm_threads_active (gauge): Active threads
  - jvm_classes_loaded (gauge): Loaded classes
```

```java
// ✅ Good - metrics with Micrometer
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MeterRegistry meterRegistry;
    private final MessageRepository messageRepository;

    // Counters
    private final Counter messagesSentCounter = Counter.builder("messages_sent_total")
        .description("Total messages sent")
        .tag("type", "text")
        .register(meterRegistry);

    private final Counter messagesFailedCounter = Counter.builder("messages_failed_total")
        .description("Total failed messages")
        .tag("reason", "unknown")
        .register(meterRegistry);

    // Timer (histogram for latency)
    private final Timer messagePersistTimer = Timer.builder("message_persist_duration_seconds")
        .description("Message persistence latency")
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(meterRegistry);

    // Gauge (registered once, value supplier)
    private final AtomicInteger activeConversations = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        Gauge.builder("active_conversations", activeConversations, AtomicInteger::get)
            .description("Current active conversations")
            .register(meterRegistry);
    }

    public MessageResponse sendMessage(SendMessageRequest request, Long userId) {
        try {
            // Timer records latency automatically
            Message message = messagePersistTimer.record(() ->
                messageRepository.save(toMessage(request, userId))
            );

            messagesSentCounter.increment();
            activeConversations.incrementAndGet();

            return toResponse(message);
        } catch (Exception e) {
            messagesFailedCounter.increment();
            throw e;
        }
    }
}

// ✅ Good - Spring Boot Actuator + Prometheus configuration
// application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
      show-components: always
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
```

## Distributed Tracing

### Tracing Concepts

```
Trace:
  - Complete request flow from start to end
  - Identified by traceId (unique per request)
  - Spans multiple services

Span:
  - Single unit of work within a trace
  - Identified by spanId (unique within trace)
  - Has parentSpanId (forms tree structure)
  - Has start time, duration, tags, logs

Example Trace Tree:
  traceId: abc123
  ├── span1: POST /api/messages (API Gateway, 200ms)
  │   ├── span2: validateToken (Auth Service, 10ms)
  │   ├── span3: saveMessage (Message Service, 150ms)
  │   │   ├── span4: MongoDB insert (50ms)
  │   │   └── span5: Redis cache update (10ms)
  │   └── span6: fanoutToRecipients (Message Service, 40ms)
  │       ├── span7: WebSocket push user1 (5ms)
  │       ├── span8: WebSocket push user2 (5ms)
  │       └── span9: WebSocket push user3 (5ms)
```

```java
// ✅ Good - distributed tracing with OpenTelemetry
// Spring Boot auto-instruments with micrometer-tracing

// Manual span creation for custom operations
@Service
@RequiredArgsConstructor
public class MessageFanoutService {
    private final Tracer tracer;
    private final WebSocketSessionManager sessionManager;

    public void fanoutToRecipients(Message message) {
        // Create a new span for fanout operation
        Span fanoutSpan = tracer.nextSpan()
            .name("fanoutToRecipients")
            .tag("messageId", String.valueOf(message.getId()))
            .tag("recipientCount", String.valueOf(message.getRecipientIds().size()))
            .start();

        try (Tracer.SpanInScope ws = tracer.withSpan(fanoutSpan)) {
            for (Long recipientId : message.getRecipientIds()) {
                // Create child span for each recipient
                Span pushSpan = tracer.nextSpan()
                    .name("pushToRecipient")
                    .tag("recipientId", String.valueOf(recipientId))
                    .start();

                try (Tracer.SpanInScope ws2 = tracer.withSpan(pushSpan)) {
                    sessionManager.sendToUser(recipientId, message);
                } catch (Exception e) {
                    pushSpan.tag("error", "true");
                    pushSpan.error(e);
                } finally {
                    pushSpan.end();
                }
            }
        } finally {
            fanoutSpan.end();
        }
    }
}

// ✅ Good - propagate trace context across async boundaries
@Service
@RequiredArgsConstructor
public class AsyncMessageService {
    private final Tracer tracer;
    private final Executor executor;

    public CompletableFuture<Void> processAsync(Message message) {
        // Capture current trace context
        Span currentSpan = tracer.currentSpan();

        return CompletableFuture.runAsync(() -> {
            // Restore trace context in async thread
            try (Tracer.SpanInScope ws = tracer.withSpan(currentSpan)) {
                // Create child span
                Span asyncSpan = tracer.nextSpan()
                    .name("processMessageAsync")
                    .tag("messageId", String.valueOf(message.getId()))
                    .start();

                try (Tracer.SpanInScope ws2 = tracer.withSpan(asyncSpan)) {
                    doProcessing(message);
                } finally {
                    asyncSpan.end();
                }
            }
        }, executor);
    }
}
```

### Trace Context Propagation

```
W3C Trace Context (standard):
  traceparent header: 00-{traceId}-{spanId}-{traceFlags}
  Example: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01

  - version: 00
  - traceId: 32 hex chars (16 bytes)
  - spanId: 16 hex chars (8 bytes)
  - traceFlags: 2 hex chars (00 = not sampled, 01 = sampled)

tracestate header: Vendor-specific trace info
  Example: congo=t61rcWkgMzE, rojo=00f067aa0ba902b7

Propagation:
  - Incoming request: extract traceparent from headers
  - Outgoing request: inject traceparent into headers
  - Async: capture and restore context
  - Message queue: inject into message headers/properties
```

## Alerting

### Alerting Principles

```
1. Alert on symptoms, not causes:
   - ✅ Alert: "Error rate > 5% for 5 minutes" (symptom)
   - ❌ Alert: "CPU usage > 80%" (cause, may be normal)

2. Use SLO-based alerting:
   - Alert when SLO burn rate exceeds threshold
   - Multi-window, multi-burn-rate alerts

3. Severity levels:
   - P1 (Critical): Service down, data loss, security breach → page immediately, 24/7
   - P2 (High): Major feature degraded, high error rate → page during hours
   - P3 (Medium): Minor issue, partial degradation → ticket, next business day
   - P4 (Low): Informational, cosmetic → log, no action

4. Alert quality:
   - Actionable: Every alert should have a clear runbook
   - Specific: Include service, metric, threshold, duration
   - Timely: Alert before users notice, not after
   - Not noisy: Avoid alert fatigue, tune thresholds
```

### SLO Burn Rate Alerting

```
SLO: 99.9% availability per month (43.2 min allowed downtime)

Burn rate = error rate / allowed error rate
  - If error rate = 0.1% (allowed), burn rate = 1
  - If error rate = 1%, burn rate = 10 (consuming budget 10x faster)

Multi-window, multi-burn-rate alerts:
  - Fast burn (high severity): 14.4x burn for 5min, also true for 1h
  - Medium burn (medium severity): 6x burn for 30min, also true for 6h
  - Slow burn (low severity): 3x burn for 2h, also true for 24h

Example:
  Alert 1 (page): burn_rate > 14.4 for 5m AND 1h
    - Consumes entire error budget in ~2 days
    - Needs immediate attention

  Alert 2 (ticket): burn_rate > 6 for 30m AND 6h
    - Consumes entire error budget in ~5 days
    - Needs attention soon
```

```yaml
# ✅ Good - Prometheus alert rules for CBOL
groups:
- name: cbol-slo
  rules:
  # Fast burn alert (page)
  - alert: HighErrorRateFastBurn
    expr: |
      (
        sum(rate(http_requests_total{status=~"5.."}[5m]))
        /
        sum(rate(http_requests_total[5m]))
      ) > 0.0144
      AND
      (
        sum(rate(http_requests_total{status=~"5.."}[1h]))
        /
        sum(rate(http_requests_total[1h]))
      ) > 0.0144
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High error rate fast burn"
      description: "Error rate > 1.44% for 5m and 1h. SLO burn rate > 14.4x."
      runbook: "https://wiki.cbol.com/runbooks/high-error-rate"

  # Latency alert
  - alert: HighLatency
    expr: |
      histogram_quantile(0.99,
        sum(rate(http_request_duration_seconds_bucket[5m])) by (le, service)
      ) > 0.5
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High P99 latency on {{ $labels.service }}"
      description: "P99 latency > 500ms for 5 minutes."

  # WebSocket connection drop alert
  - alert: WebSocketConnectionDrop
    expr: |
      delta(websocket_connections[5m]) < -1000
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Mass WebSocket disconnection"
      description: "More than 1000 WebSocket connections dropped in 5 minutes."

  # Consumer lag alert
  - alert: HighConsumerLag
    expr: mq_consumer_lag > 10000
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "High message queue consumer lag"
      description: "Consumer lag > 10000 messages for 10 minutes."
```

## Dashboards

### Dashboard Design Principles

```
1. Hierarchy:
   - Overview dashboard: All services, key metrics (executive view)
   - Service dashboard: Single service, detailed metrics (engineering view)
   - Feature dashboard: Specific feature, business metrics (product view)

2. Layout:
   - Top: Key metrics (availability, latency, error rate, throughput)
   - Middle: Detailed metrics (by endpoint, by status code)
   - Bottom: Resource metrics (CPU, memory, JVM, DB)

3. Visualization:
   - Use appropriate chart types:
     - Time series: Trends over time (line chart)
     - Distribution: Latency percentiles (heatmap, histogram)
     - Current state: Gauges, single stats
     - Top N: Table sorted by value
   - Consistent colors (green = good, yellow = warning, red = critical)
   - Annotations for deployments, config changes, incidents

4. What to include:
   - SLO status (error budget remaining)
   - Latency percentiles (P50, P95, P99)
   - Error rate (by status code, by endpoint)
   - Throughput (requests/sec, messages/sec)
   - Resource usage (CPU, memory, connections)
   - Dependency health (DB, cache, MQ)
```

### Dashboard Example (Grafana)

```
Overview Dashboard:
  ┌─────────────────────────────────────────────────────────────────┐
  │  Availability  │  P99 Latency  │  Error Rate  │  Throughput    │
  │    99.95%      │    120ms      │    0.05%     │   5,234 req/s  │
  └─────────────────────────────────────────────────────────────────┘
  ┌──────────────────────────┐  ┌──────────────────────────────────┐
  │  Request Rate (all svc)  │  │  Error Rate (by service)         │
  │  [line chart]            │  │  [stacked area chart]            │
  └──────────────────────────┘  └──────────────────────────────────┘
  ┌──────────────────────────┐  ┌──────────────────────────────────┐
  │  P99 Latency (by svc)    │  │  SLO Error Budget               │
  │  [line chart]            │  │  [gauge: 85% remaining]         │
  └──────────────────────────┘  └──────────────────────────────────┘

Service Dashboard (Message Service):
  ┌─────────────────────────────────────────────────────────────────┐
  │  Availability  │  P99 Latency  │  Error Rate  │  Active Conns  │
  │    99.98%      │     85ms      │    0.02%     │    12,450      │
  └─────────────────────────────────────────────────────────────────┘
  ┌──────────────────────────┐  ┌──────────────────────────────────┐
  │  HTTP Requests (by path) │  │  Latency Distribution            │
  │  [stacked area]          │  │  [heatmap]                       │
  └──────────────────────────┘  └──────────────────────────────────┘
  ┌──────────────────────────┐  ┌──────────────────────────────────┐
  │  MongoDB Query Latency    │  │  Redis Cache Hit Ratio          │
  │  [line chart]            │  │  [gauge + trend]                │
  └──────────────────────────┘  └──────────────────────────────────┘
  ┌──────────────────────────┐  ┌──────────────────────────────────┐
  │  JVM Heap Usage          │  │  GC Pause Duration              │
  │  [area chart]            │  │  [histogram]                     │
  └──────────────────────────┘  └──────────────────────────────────┘
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Unstructured logs (plain text) | Hard to parse, search, aggregate | Structured JSON logging |
| Logging sensitive data | Privacy violation, security risk | Mask/redact PII, tokens, message content |
| Logging everything (DEBUG in prod) | High volume, high cost, noise | Appropriate log levels, sampling for high volume |
| No log correlation | Can't trace request across services | MDC with traceId, requestId, userId |
| No metrics | Can't measure performance or detect issues | Micrometer + Prometheus, RED/USE metrics |
| Too many metrics (cardinality explosion) | High cost, slow queries | Low cardinality labels, avoid user IDs in labels |
| No distributed tracing | Can't debug cross-service issues | OpenTelemetry, W3C trace context |
| Trace context lost in async | Broken traces, can't correlate | Capture/restore context in async, use instrumentation |
| Alert on everything (alert fatigue) | Ignore all alerts, miss real ones | SLO-based alerting, actionable alerts only |
| Alert on causes not symptoms | False positives, noisy | Alert on user-facing symptoms (error rate, latency) |
| No runbooks for alerts | Don't know what to do when alerted | Every alert has runbook with diagnosis and remediation |
| No dashboards | Can't see system state at a glance | Hierarchical dashboards (overview → service → feature) |
| Dashboard clutter | Can't find important info | Clean layout, key metrics at top, consistent design |
| No SLO monitoring | Don't know if meeting availability targets | SLO definition, error budget, burn rate alerting |
| Ignoring golden signals | Miss key performance indicators | Monitor RED (Rate, Errors, Duration) + saturation |
| No anomaly detection | Only threshold-based alerts, miss gradual degradation | ML-based anomaly detection, baseline comparison |
| Logs without timestamps | Can't correlate events across systems | ISO 8601 timestamps with timezone, synchronized clocks (NTP) |
| No log retention policy | Storage costs grow indefinitely | Tiered storage (hot/warm/cold), retention by compliance needs |
| Tracing without sampling | High cost for high-volume services | Head-based or tail-based sampling, 100% for errors |

## References

- Google SRE Book (Monitoring Distributed Systems): https://sre.google/sre-book/monitoring-distributed-systems/
- Google SRE Workbook (Practical Alerting): https://sre.google/workbook/practical-alerting/
- OpenTelemetry: https://opentelemetry.io/
- Prometheus: https://prometheus.io/
- Grafana: https://grafana.com/
- Micrometer: https://micrometer.io/
- The RED Method: https://www.weave.works/blog/the-red-method-key-metrics-for-microservices-architecture/
- The USE Method: http://www.brendangregg.com/usemethod.html
- Four Golden Signals (Google): https://sre.google/sre-book/monitoring-distributed-systems/#xref_monitoring_golden-signals
- SLO Burn Rate Alerting: https://sre.google/workbook/alerting-on-slos/
- Logstash Logback Encoder: https://github.com/logfellow/logstash-logback-encoder
- Jaeger (Distributed Tracing): https://www.jaegertracing.io/
- Zipkin (Distributed Tracing): https://zipkin.io/
- Loki (Log Aggregation): https://grafana.com/oss/loki/
