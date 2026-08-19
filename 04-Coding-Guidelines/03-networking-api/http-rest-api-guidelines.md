# HTTP & REST API Guidelines

> Best practices for HTTP API design, REST conventions, and HTTP client usage.

## REST API Design Principles

### Resource-Oriented Design

```
# ✅ Good - nouns, plural, hierarchical
GET    /api/v1/conversations              # List conversations
GET    /api/v1/conversations/{id}         # Get conversation
POST   /api/v1/conversations              # Create conversation
PUT    /api/v1/conversations/{id}         # Update conversation (full)
PATCH  /api/v1/conversations/{id}         # Partial update
DELETE /api/v1/conversations/{id}         # Delete conversation

# Sub-resources
GET    /api/v1/conversations/{id}/messages    # List messages in conversation
POST   /api/v1/conversations/{id}/messages    # Send message to conversation
GET    /api/v1/conversations/{id}/messages/{msgId}

# ❌ Bad - verbs in URL, inconsistent
GET    /api/getConversations
POST   /api/createConversation
GET    /api/conversation/{id}/getMessages
POST   /api/sendMessageToConversation
```

### HTTP Methods Semantics

| Method | Idempotent | Safe | Use For |
|--------|-----------|------|---------|
| GET | ✅ | ✅ | Retrieve resources |
| POST | ❌ | ❌ | Create resources, actions |
| PUT | ✅ | ❌ | Full update (replace) |
| PATCH | ❌ | ❌ | Partial update |
| DELETE | ✅ | ❌ | Delete resources |
| HEAD | ✅ | ✅ | Headers only (no body) |
| OPTIONS | ✅ | ✅ | CORS preflight, capabilities |

### HTTP Status Codes

| Code | Name | Use For |
|------|------|---------|
| 200 | OK | Successful GET/PUT/PATCH/DELETE |
| 201 | Created | Successful POST (resource created) |
| 202 | Accepted | Async operation accepted |
| 204 | No Content | Successful DELETE, no body |
| 301 | Moved Permanently | Resource moved permanently |
| 304 | Not Modified | Conditional GET (ETag/Last-Modified) |
| 400 | Bad Request | Validation error, malformed request |
| 401 | Unauthorized | Missing/invalid authentication |
| 403 | Forbidden | Authenticated but not authorized |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Version conflict, duplicate |
| 422 | Unprocessable Entity | Semantic validation error |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |
| 502 | Bad Gateway | Upstream service error |
| 503 | Service Unavailable | Service overloaded/maintenance |
| 504 | Gateway Timeout | Upstream service timeout |

### API Versioning

```java
// ✅ Good - URL path versioning (most common, cache-friendly)
@RestController
@RequestMapping("/api/v1/messages")
public class MessageControllerV1 { }

@RestController
@RequestMapping("/api/v2/messages")
public class MessageControllerV2 { }

// Alternative: header versioning
// Accept: application/vnd.cbol.v1+json
// Accept: application/vnd.cbol.v2+json

// Alternative: query parameter
// GET /api/messages?version=1
// GET /api/messages?version=2

// ❌ Bad - no versioning (breaking changes affect all clients)
@RestController
@RequestMapping("/api/messages")
public class MessageController { }
```

### Request/Response Format

```json
// ✅ Good - consistent envelope, camelCase, timestamps ISO-8601
{
  "data": {
    "id": "msg_12345",
    "conversationId": "conv_67890",
    "content": "Hello",
    "senderId": "user_111",
    "status": "SENT",
    "createdAt": "2026-08-19T10:30:00Z",
    "updatedAt": "2026-08-19T10:30:01Z"
  },
  "meta": {
    "requestId": "req_abc123",
    "timestamp": "2026-08-19T10:30:01Z"
  }
}

// Error response
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      { "field": "content", "message": "must not be blank" },
      { "field": "conversationId", "message": "must not be null" }
    ],
    "requestId": "req_abc123"
  }
}

// ❌ Bad - inconsistent, snake_case, no envelope
{
  "msg_id": "12345",
  "conv_id": "67890",
  "content": "Hello",
  "created_at": 1724063400000  // Unix timestamp (not ISO-8601)
}
```

### Pagination

```java
// ✅ Good - cursor-based pagination (stable for real-time data)
GET /api/v1/conversations?limit=20&cursor=eyJpZCI6MTIzfQ==

Response:
{
  "data": [...],
  "meta": {
    "nextCursor": "eyJpZCI6NDU2fQ==",
    "hasMore": true,
    "limit": 20
  }
}

// Offset-based (acceptable for stable datasets)
GET /api/v1/conversations?page=1&size=20

Response:
{
  "data": [...],
  "meta": {
    "page": 1,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}

// ❌ Bad - no pagination, returns all records
GET /api/v1/conversations  // returns 100K records!
```

### Filtering & Sorting

```
# ✅ Good - consistent query parameters
GET /api/v1/messages?status=SENT&senderId=user_111&sort=createdAt,desc&limit=50

# Multi-value filters
GET /api/v1/messages?status=SENT,DELIVERED,READ

# Date range
GET /api/v1/messages?createdAfter=2026-08-01T00:00:00Z&createdBefore=2026-08-31T23:59:59Z
```

## HTTP Client Best Practices

### Timeout Configuration (Mandatory)

```java
// ✅ Good - explicit timeouts for all HTTP clients
@Bean
public RestTemplate restTemplate() {
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(2000);   // 2s connect
    factory.setReadTimeout(5000);       // 5s read
    return new RestTemplate(factory);
}

// ✅ Better - WebClient (reactive)
@Bean
public WebClient webClient() {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
        .responseTimeout(Duration.ofSeconds(5));

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl("https://api.external-service.com")
        .build();
}

// ❌ Bad - no timeouts (connection can hang forever)
RestTemplate restTemplate = new RestTemplate(); // default: infinite timeout!
```

### Retry with Backoff

```java
// ✅ Good - exponential backoff with jitter
@Retryable(
    retryFor = {ResourceAccessException.class, TimeoutException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 500, multiplier = 2, maxDelay = 5000)
)
public ExternalResponse callExternalService(String id) {
    return webClient.get()
        .uri("/resources/{id}", id)
        .retrieve()
        .bodyToMono(ExternalResponse.class)
        .block();
}

@Recover
public ExternalResponse recover(ResourceAccessException ex, String id) {
    log.error("External service failed for {} after retries", id, ex);
    throw new ExternalServiceUnavailableException(id);
}

// ❌ Bad - no retry, no backoff
public ExternalResponse callExternalService(String id) {
    return restTemplate.getForObject("/resources/{id}", ExternalResponse.class, id);
    // transient failure = immediate user-facing error
}
```

### Circuit Breaker

```java
// ✅ Good - circuit breaker for external dependencies
@CircuitBreaker(name = "externalService", fallbackMethod = "fallback")
public ExternalResponse callExternalService(String id) {
    return webClient.get()
        .uri("/resources/{id}", id)
        .retrieve()
        .bodyToMono(ExternalResponse.class)
        .block();
}

private ExternalResponse fallback(String id, Exception ex) {
    log.warn("Circuit breaker open for external service, using fallback for {}", id);
    return ExternalResponse.cachedOrEmpty(id);
}
```

## Caching Headers

```java
// ✅ Good - proper cache headers
@GetMapping("/api/v1/conversations/{id}")
public ResponseEntity<ConversationDTO> getById(@PathVariable Long id) {
    ConversationDTO dto = service.getById(id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
        .eTag("\"" + dto.getVersion() + "\"")
        .body(dto);
}

// Conditional GET with ETag
@GetMapping("/api/v1/conversations/{id}")
public ResponseEntity<ConversationDTO> getById(
        @PathVariable Long id,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    ConversationDTO dto = service.getById(id);
    String etag = "\"" + dto.getVersion() + "\"";

    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
        .eTag(etag)
        .body(dto);
}
```

## Rate Limiting

```java
// ✅ Good - rate limiting on API endpoints
@RestController
@RequestMapping("/api/v1/messages")
@RateLimit(key = "messages", limit = 100, window = 60) // 100 req/min per user
public class MessageController {

    @PostMapping
    @RateLimit(key = "send-message", limit = 10, window = 60) // stricter for send
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody SendMessageRequest req) {
        // ...
    }
}

// Response headers on rate limit
// X-RateLimit-Limit: 100
// X-RateLimit-Remaining: 0
// X-RateLimit-Reset: 1724063460
// Retry-After: 42
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Verbs in URL (`/getMessages`) | Not RESTful, inconsistent | Use nouns + HTTP methods |
| No API versioning | Breaking changes affect all clients | URL/header versioning |
| No timeouts on HTTP clients | Hanging connections, resource leaks | Always set connect/read timeouts |
| Returning 200 for errors | Client can't distinguish success/failure | Use proper status codes |
| No pagination | Performance degradation, OOM | Cursor/offset pagination |
| `GET` with body | Not supported by all proxies/caches | Use query params or `POST` for search |
| Ignoring `Idempotency-Key` | Duplicate operations on retry | Support idempotency keys for `POST` |
| Returning stack traces in errors | Security risk, info leakage | Return error code + message, log stack trace server-side |
| No `requestId` / `traceId` | Hard to debug distributed systems | Propagate trace IDs, return in response |

## References

- REST API Design Guide: https://github.com/Highflyer/REST-API-Design-Guide
- RESTful API Guidelines: https://github.com/apifactory-org/restful-api-guidelines
- API Design Cheat Sheet: https://github.com/RestCheatSheet/api-cheat-sheet
- HTTP Status Codes: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
- Spring WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- Resilience4j (Circuit Breaker/Retry): https://resilience4j.readme.io/
