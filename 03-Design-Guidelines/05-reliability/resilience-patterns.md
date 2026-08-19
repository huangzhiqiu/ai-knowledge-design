# Resilience Patterns Guidelines

> Best practices for designing resilient systems in CBOL Messaging Hub. Covers circuit breakers, retries, fallbacks, bulkheads, rate limiting, graceful degradation, and chaos engineering.

## Resilience Fundamentals

### What is Resilience?

```
Resilience = ability to handle and recover from failures gracefully

Key properties:
  - Fault tolerance: Continue operating despite component failure
  - Recovery: Return to normal operation after failure
  - Degradation: Reduce functionality rather than total failure
  - Isolation: Failure in one component doesn't cascade

Resilience != High Availability:
  - HA: Prevent downtime (redundancy, failover)
  - Resilience: Handle downtime when it happens (degrade, recover)
  - Both needed for reliable systems
```

### Failure Modes

| Failure Type | Description | Example |
|-------------|-------------|---------|
| **Crash failure** | Process stops completely | OOM kill, hardware failure |
| **Omission failure** | Process doesn't respond | Deadlock, infinite loop, slow I/O |
| **Timing failure** | Response too late | GC pause, network congestion |
| **Response failure** | Response is incorrect | Bug, data corruption |
| **Byzantine failure** | Arbitrary/malicious behavior | Security breach, hardware fault |

### Resilience Strategies

```
1. Prevent: Reduce likelihood of failure
   - Redundancy, health checks, auto-scaling

2. Detect: Identify failure quickly
   - Monitoring, alerting, circuit breakers

3. Contain: Limit blast radius
   - Bulkheads, rate limiting, isolation

4. Degrade: Reduce functionality gracefully
   - Fallbacks, cached data, read-only mode

5. Recover: Return to normal operation
   - Retries, failover, auto-healing

6. Learn: Prevent recurrence
   - Post-mortems, chaos engineering, runbooks
```

## Circuit Breaker Pattern

### How It Works

```
Closed State (normal):
  - Requests pass through to dependency
  - Track failure rate
  - If failure rate > threshold → Open

Open State (failure):
  - Requests fail immediately (no call to dependency)
  - Return fallback or error
  - After timeout → Half-Open

Half-Open State (testing):
  - Allow limited requests through
  - If successful → Closed (recovered)
  - If failed → Open (still failing)

State Machine:
  ┌────────┐  failure rate > threshold  ┌────────┐
  │ Closed │ ──────────────────────────► │  Open  │
  └────────┘                              └───┬────┘
       ▲                                       │ timeout
       │                                       ▼
       │                                  ┌──────────┐
       │    success (limited requests)   │ Half-Open│
       └──────────────────────────────────┤          │
                                          └────┬─────┘
                                               │ failure
                                               ▼
                                          ┌────────┐
                                          │  Open  │
                                          └────────┘
```

```java
// ✅ Good - circuit breaker with Resilience4j
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker messageServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)           // Open if 50% requests fail
            .slowCallRateThreshold(50)          // Open if 50% calls are slow
            .slowCallDurationThreshold(Duration.ofSeconds(2))  // Slow = >2s
            .permittedNumberOfCallsInHalfOpenState(3)  // 3 test calls in half-open
            .maxWaitDurationInHalfOpenState(Duration.ofSeconds(10))
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)              // Last 10 calls
            .minimumNumberOfCalls(5)             // Need at least 5 calls before calculating
            .waitDurationInOpenState(Duration.ofSeconds(30))  // Stay open 30s
            .recordExceptions(IOException.class, TimeoutException.class)
            .ignoreExceptions(BusinessException.class)  // Don't count business errors
            .build();

        return CircuitBreaker.of("messageService", config);
    }
}

@Service
@RequiredArgsConstructor
public class MessageService {
    private final CircuitBreaker circuitBreaker;
    private final ExternalMessageClient externalClient;

    public MessageResponse sendWithFallback(SendMessageRequest request) {
        return circuitBreaker.executeSupplier(() -> {
            // Try external service
            return externalClient.send(request);
        }).orElseGet(() -> {
            // Fallback: queue for later delivery
            queueForRetry(request);
            return MessageResponse.accepted(request.getId());
        });
    }

    private void queueForRetry(SendMessageRequest request) {
        // Send to dead letter queue or retry queue
        kafkaTemplate.send("message-retry", request);
    }
}
```

### Circuit Breaker Configuration Guidelines

| Parameter | Default | Recommended | Notes |
|-----------|---------|-------------|-------|
| failureRateThreshold | 50% | 30-50% | Lower = more sensitive, more false positives |
| slowCallRateThreshold | 100% | 50% | Count slow calls as failures |
| slowCallDurationThreshold | 60s | P99 latency × 2 | Define what "slow" means |
| slidingWindowSize | 100 | 10-50 | Smaller = faster detection, more false positives |
| minimumNumberOfCalls | 100 | 5-10 | Don't open with too few data points |
| waitDurationInOpenState | 60s | 15-60s | Too short = thundering herd, too long = slow recovery |
| permittedCallsInHalfOpen | 10 | 3-5 | Limited test calls to avoid overwhelming recovering service |

## Retry Pattern

### Retry Strategies

```
1. Immediate Retry:
   - Retry immediately after failure
   - Risk: thundering herd, overwhelm recovering service
   - Use: Transient network blips only

2. Fixed Delay:
   - Wait fixed time between retries
   - Risk: synchronized retries (thundering herd)
   - Use: Simple, predictable scenarios

3. Exponential Backoff:
   - Delay doubles each retry: 1s, 2s, 4s, 8s...
   - Gives service time to recover
   - Risk: still synchronized across clients

4. Exponential Backoff + Jitter:
   - Delay = base * 2^retry + random(0, base * 2^retry)
   - Spreads retries over time, avoids thundering herd
   - ✅ Recommended for most scenarios

5. Incremental Backoff:
   - Delay increases by fixed amount: 1s, 2s, 3s, 4s...
   - More predictable than exponential
```

```java
// ✅ Good - retry with exponential backoff + jitter
@Service
public class RetryService {

    private static final int MAX_RETRIES = 3;
    private static final Duration BASE_DELAY = Duration.ofMillis(100);
    private static final Duration MAX_DELAY = Duration.ofSeconds(10);

    public <T> T executeWithRetry(Supplier<T> operation,
                                   Predicate<Exception> retryable) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                // Don't retry if not retryable or max attempts reached
                if (!retryable.test(e) || attempt == MAX_RETRIES) {
                    break;
                }

                // Calculate delay with exponential backoff + jitter
                Duration delay = calculateDelay(attempt);

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw new RetryExhaustedException("Failed after " + MAX_RETRIES + " retries", lastException);
    }

    private Duration calculateDelay(int attempt) {
        // Exponential backoff: base * 2^attempt
        long exponentialDelay = BASE_DELAY.toMillis() * (1L << attempt);

        // Cap at max delay
        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY.toMillis());

        // Add jitter: random(0, cappedDelay)
        // Full jitter: delay = random(0, base * 2^attempt)
        long jitter = ThreadLocalRandom.current().nextLong(cappedDelay);

        return Duration.ofMillis(jitter);
    }
}

// Usage
@Service
@RequiredArgsConstructor
public class MessageService {
    private final RetryService retryService;
    private final ExternalClient externalClient;

    public MessageResponse send(SendMessageRequest request) {
        return retryService.executeWithRetry(
            () -> externalClient.send(request),
            e -> e instanceof IOException || e instanceof TimeoutException
        );
    }
}
```

### Retry Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Retry on all exceptions | Retry non-retryable errors (validation, auth) | Only retry on transient errors (network, timeout, 5xx) |
| No max retries | Infinite retry, resource exhaustion | Always set max retries (3-5 typical) |
| Immediate retry | Thundering herd, overwhelm service | Exponential backoff + jitter |
| No idempotency | Retry causes duplicate operations | Idempotent operations (idempotency key) |
| Retry without circuit breaker | Retry on already-failing service | Combine retry + circuit breaker |
| Long retry chains | High latency for user | Short retries (3×), then fallback/queue |
| Retry in nested calls | Retry amplification (3×3=9 retries) | Retry at outermost layer only |
| No retry metrics | Can't optimize retry behavior | Track retry count, success rate, delay |

## Bulkhead Pattern

### How It Works

```
Bulkhead = isolate resources so failure in one area doesn't exhaust all resources

Analogy: Ship compartments (bulkheads) contain flooding to one compartment

Implementation:
  - Separate thread pools per dependency/service
  - Separate connection pools per dependency
  - Limit concurrent requests per dependency
  - If one dependency is slow, its pool fills up, but other pools still available

Without Bulkhead:
  - All threads shared
  - Slow dependency consumes all threads
  - Other requests can't be processed
  - Total system failure

With Bulkhead:
  - Thread pool per dependency
  - Slow dependency fills its own pool
  - Other pools still available
  - Graceful degradation (only slow dependency affected)
```

```java
// ✅ Good - bulkhead with Resilience4j
@Configuration
public class BulkheadConfig {

    @Bean
    public Bulkhead userServiceBulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(10)           // Max 10 concurrent calls
            .maxWaitDuration(Duration.ofMillis(100))  // Wait max 100ms for slot
            .build();
        return Bulkhead.of("userService", config);
    }

    @Bean
    public Bulkhead messageServiceBulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(20)
            .maxWaitDuration(Duration.ofMillis(100))
            .build();
        return Bulkhead.of("messageService", config);
    }
}

@Service
@RequiredArgsConstructor
public class AggregationService {
    private final Bulkhead userServiceBulkhead;
    private final Bulkhead messageServiceBulkhead;
    private final UserClient userClient;
    private final MessageClient messageClient;

    public DashboardData getDashboard(Long userId) {
        // Both calls use separate bulkheads
        // If user service is slow, only its bulkhead fills up
        // Message service calls still work

        CompletableFuture<User> userFuture = CompletableFuture.supplyAsync(
            () -> userServiceBulkhead.executeSupplier(() -> userClient.getUser(userId))
        );

        CompletableFuture<List<Message>> messagesFuture = CompletableFuture.supplyAsync(
            () -> messageServiceBulkhead.executeSupplier(() -> messageClient.getRecent(userId))
        );

        return CompletableFuture.allOf(userFuture, messagesFuture)
            .thenApply(v -> DashboardData.builder()
                .user(userFuture.join())
                .recentMessages(messagesFuture.join())
                .build())
            .join();
    }
}
```

## Rate Limiting Pattern

### Rate Limiting Algorithms

| Algorithm | Description | Pros | Cons |
|-----------|-------------|------|------|
| **Fixed Window** | Count requests in fixed time window | Simple | Burst at window boundary |
| **Sliding Window** | Count requests in rolling time window | Accurate | More memory |
| **Token Bucket** | Tokens added at rate, consumed per request | Allows bursts | Slightly complex |
| **Leaky Bucket** | Requests queued, processed at fixed rate | Smooth output | No bursts, queue delay |

### Token Bucket (Recommended)

```
Token Bucket Algorithm:
  - Bucket has capacity (max tokens)
  - Tokens added at fixed rate (e.g., 100 tokens/second)
  - Each request consumes 1 token
  - If tokens available → allow request
  - If no tokens → reject (429 Too Many Requests)
  - Allows bursts up to bucket capacity

Example:
  - Rate: 100 req/s
  - Bucket capacity: 200
  - Normal: 100 req/s (tokens replenished as consumed)
  - Burst: Up to 200 req at once (uses stored tokens)
  - After burst: Must wait for tokens to replenish
```

```java
// ✅ Good - distributed rate limiting with Redis + Lua
@Service
public class RateLimiter {
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    // Lua script for atomic rate limiting (token bucket)
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local rate = tonumber(ARGV[1])      -- tokens per second
        local capacity = tonumber(ARGV[2])  -- bucket capacity
        local now = tonumber(ARGV[3])        -- current timestamp (ms)
        local requested = tonumber(ARGV[4])  -- tokens requested

        -- Get current bucket state
        local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
        local tokens = tonumber(bucket[1]) or capacity
        local timestamp = tonumber(bucket[2]) or now

        -- Calculate tokens to add based on elapsed time
        local delta = math.max(0, now - timestamp) / 1000
        tokens = math.min(capacity, tokens + delta * rate)

        -- Check if enough tokens
        if tokens >= requested then
            tokens = tokens - requested
            redis.call('HMSET', key, 'tokens', tokens, 'timestamp', now)
            redis.call('EXPIRE', key, math.ceil(capacity / rate) + 1)
            return 1  -- Allowed
        else
            return 0  -- Denied
        end
        """;

    public boolean isAllowed(String userId, String resource, int ratePerSecond, int capacity) {
        String key = RATE_LIMIT_PREFIX + resource + ":" + userId;
        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(
            rateLimitScript,
            List.of(key),
            String.valueOf(ratePerSecond),
            String.valueOf(capacity),
            String.valueOf(now),
            "1"
        );

        return result != null && result == 1;
    }
}

// Usage in filter
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String resource = request.getRequestURI();

        // Rate limit: 100 req/s, burst 200
        if (!rateLimiter.isAllowed(String.valueOf(userId), resource, 100, 200)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "1");
            response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

## Graceful Degradation

### Degradation Strategies

```
1. Fallback to cached data:
   - If DB unavailable, return cached data (may be stale)
   - Set appropriate headers (X-Cache: STALE, X-Stale-Age: 300)

2. Return default/simplified response:
   - If recommendation service down, return popular items
   - If profile service down, return basic profile (username only)

3. Disable non-critical features:
   - If analytics service down, still allow messaging
   - If search service down, show "search temporarily unavailable"

4. Read-only mode:
   - If write DB unavailable, allow reads only
   - Queue writes for later processing

5. Reduced functionality:
   - If image service down, text-only messages
   - If push service down, in-app notifications only
```

```java
// ✅ Good - graceful degradation with multiple fallbacks
@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final RedisTemplate<String, Conversation> redisTemplate;
    private final CircuitBreaker circuitBreaker;

    public Conversation getConversation(Long conversationId, Long userId) {
        // Try 1: Database (fresh data)
        try {
            return circuitBreaker.executeSupplier(() ->
                conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new NotFoundException("Conversation not found"))
            );
        } catch (Exception e) {
            // DB failed, try cache
        }

        // Try 2: Cache (may be stale)
        Conversation cached = redisTemplate.opsForValue().get("conversation:" + conversationId);
        if (cached != null && isMember(cached, userId)) {
            // Mark as stale
            cached.setStale(true);
            return cached;
        }

        // Try 3: Fallback - return minimal conversation info
        if (cached != null) {
            return Conversation.builder()
                .id(conversationId)
                .title("Conversation")
                .degraded(true)
                .build();
        }

        throw new ServiceUnavailableException("Conversation service temporarily unavailable");
    }

    public List<Message> getMessages(Long conversationId, Long userId, int limit) {
        // Try DB, fallback to empty list with "loading" state
        try {
            return circuitBreaker.executeSupplier(() ->
                messageRepository.findByConversationId(conversationId, limit)
            );
        } catch (Exception e) {
            // Return empty list, client can retry
            return List.of();
        }
    }
}
```

## Chaos Engineering

### Chaos Engineering Principles

```
Chaos Engineering = disciplined approach to testing system resilience by intentionally injecting failures

Process:
  1. Define steady state (normal behavior metrics)
  2. Hypothesize steady state will continue during failure
  3. Inject real-world failures (kill pod, network latency, disk full)
  4. Observe system behavior
  5. Learn and improve

Failure Injection Types:
  - Infrastructure: Kill pod, kill node, AZ failure
  - Network: Latency, packet loss, DNS failure, partition
  - Application: Crash, memory leak, slow response
  - Data: Disk full, DB failure, corrupted data
  - Dependency: API failure, timeout, rate limiting

Tools:
  - Chaos Monkey (Spring Boot): https://codecentric.github.io/chaos-monkey-spring-boot/
  - Chaos Mesh (Kubernetes): https://chaos-mesh.org/
  - LitmusChaos: https://litmuschaos.io/
  - Gremlin (commercial): https://www.gremlin.com/
```

### Chaos Experiment Example

```yaml
# ✅ Good - Chaos Mesh experiment: kill message service pod
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: message-service-pod-kill
  namespace: cbol
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - cbol
    labelSelectors:
      app: message-service
  scheduler:
    cron: "*/5 * * * *"  # Every 5 minutes
---
# Network latency experiment
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: db-network-latency
  namespace: cbol
spec:
  action: delay
  mode: all
  selector:
    namespaces:
      - cbol
    labelSelectors:
      app: message-service
  delay:
    latency: "200ms"
    correlation: "50"
    jitter: "50ms"
  duration: "5m"
  scheduler:
    cron: "0 2 * * *"  # Daily at 2 AM
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| No resilience at all | Any dependency failure = total failure | Circuit breaker + retry + fallback |
| Retry without backoff | Thundering herd, overwhelm recovering service | Exponential backoff + jitter |
| Circuit breaker too sensitive | False positives, unnecessary degradation | Proper thresholds, minimum calls |
| Circuit breaker not sensitive enough | Slow to detect, prolonged failure | Lower thresholds, slow call detection |
| No fallback | Circuit open = error to user | Meaningful fallback (cached data, reduced features) |
| No bulkhead | Slow dependency consumes all resources | Separate thread pools per dependency |
| No rate limiting | Traffic spike overwhelms system | Rate limiting per user/resource |
| Rate limiting too aggressive | Legitimate users blocked | Proper limits, burst capacity |
| No graceful degradation | Partial failure = total failure | Degrade features, return cached data |
| No idempotency | Retry causes duplicate operations | Idempotency keys, unique constraints |
| No timeout | Requests hang indefinitely, resource leak | Always set timeouts (connect + read) |
| No monitoring | Can't detect failures or measure resilience | Metrics for circuit state, retry count, latency |
| No chaos testing | Unknown failure modes, surprises in production | Regular chaos experiments, game days |
| No runbook | Panic during outage, slow recovery | Documented runbooks, regular drills |
| Retry amplification | Nested retries multiply (3×3=9) | Retry at outermost layer only |
| No error classification | Retry non-retryable errors | Classify errors (transient vs permanent) |
| Ignoring partial failures | Some requests fail, system considered "up" | Track error rate, partial degradation alerts |
| No capacity planning | Traffic spike causes cascading failure | Capacity planning, auto-scaling, load testing |

## References

- Release It! (Michael Nygard): https://pragprog.com/titles/mnee2/release-it-second-edition/
- Resilience4j: https://resilience4j.readme.io/
- Netflix Hystrix (archived, but concepts still valid): https://github.com/Netflix/Hystrix
- Polly (.NET resilience): https://github.com/App-vNext/Polly
- Google SRE Book (Addressing Cascading Failures): https://sre.google/sre-book/addressing-cascading-failures/
- Google SRE Book (Handling Overload): https://sre.google/sre-book/handling-overload/
- Chaos Engineering (Principles): https://principlesofchaos.org/
- Chaos Mesh: https://chaos-mesh.org/
- AWS Well-Architected Framework (Reliability): https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/welcome.html
- Azure Architecture Center (Resilience): https://learn.microsoft.com/en-us/azure/architecture/framework/resiliency/
- Stripe Retry Guide: https://stripe.com/docs/rate-limits
- Exponential Backoff and Jitter (AWS): https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
