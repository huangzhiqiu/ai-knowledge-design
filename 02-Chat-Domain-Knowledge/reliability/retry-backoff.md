# Retry & Backoff

## Why Retry?

Network is unreliable. Requests fail due to:
- Transient network errors
- Server temporarily overloaded
- Connection reset
- Timeout

Retrying with backoff recovers from most transient failures.

## Retry Strategies

### Immediate Retry
- Retry immediately after failure
- Simple but can overwhelm recovering server
- Use at most 1 immediate retry

### Fixed Interval
- Retry every N seconds
- Predictable but can cause thundering herd

### Exponential Backoff (Recommended)
- Delay doubles each retry: 1s, 2s, 4s, 8s, 16s...
- Gives server time to recover
- Cap at max delay (e.g., 60s)

```
attempt 1: delay = 1s
attempt 2: delay = 2s
attempt 3: delay = 4s
attempt 4: delay = 8s
attempt 5: delay = 16s
attempt 6: delay = 32s (capped)
```

### Exponential Backoff + Jitter
- Add randomness to avoid synchronized retries
- Prevents thundering herd when many clients retry simultaneously

```
delay = min(cap, base * 2^attempt) + random(0, jitter)
```

**Full Jitter (AWS recommended):**
```
delay = random(0, min(cap, base * 2^attempt))
```

## Retryable vs Non-Retryable Errors

| Error Type | Retry? | Example |
|-----------|--------|---------|
| Network timeout | Yes | Connection timeout |
| Connection reset | Yes | TCP RST |
| 5xx server error | Yes | 502, 503, 504 |
| 429 rate limited | Yes (with Retry-After) | Too many requests |
| 4xx client error | No | 400, 401, 403, 404 |
| Validation error | No | Invalid message format |
| Auth failure | No | Token expired (re-auth first) |

## Client Retry for Message Send

```
function sendMessage(msg):
    attempt = 0
    while attempt < MAX_RETRIES:
        try:
            response = await api.send(msg)
            return response  // success
        catch (error):
            if not isRetryable(error):
                throw error  // don't retry
            attempt++
            if attempt >= MAX_RETRIES:
                throw error  // give up
            delay = calculateBackoff(attempt)
            await sleep(delay)
```

### Parameters

| Parameter | Recommended Value |
|-----------|------------------|
| Max retries | 3-5 |
| Base delay | 1 second |
| Max delay | 30-60 seconds |
| Jitter | Full jitter |
| Total timeout | 30-60 seconds |

## Server-side Retry (Push to Client)

When pushing to client connection fails:

```
1. Immediate retry (1x)
2. If still fails: mark device offline
3. Move message to offline queue
4. Deliver when device reconnects
```

Don't aggressively retry push - if connection is dead, retrying wastes resources.

## Circuit Breaker Pattern

For downstream service calls (e.g., push notification providers):

```
Closed (normal) -> failure rate > threshold -> Open (reject immediately)
Open -> after timeout -> Half-Open (test with few requests)
Half-Open -> success -> Closed
Half-Open -> failure -> Open
```

Prevents cascading failures when a dependency is down.

## Dead Letter Queue

Messages that fail after all retries go to DLQ:
- Alert on DLQ growth
- Manual inspection and reprocessing
- Prevents message loss

## Retry Storm Prevention

When many clients experience same failure (e.g., server restart):
1. **Jitter**: spread retries randomly
2. **Exponential backoff**: reduce request rate over time
3. **Circuit breaker**: stop hammering failed services
4. **Rate limiting**: server rejects with 429 + Retry-After

## Reference: AWS Retry Strategy
AWS SDK uses exponential backoff with full jitter: `sleep = random(0, min(cap, base * 2^attempt))`. Proven to reduce retry storms in distributed systems.
