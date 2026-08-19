# REST API Design Guidelines

> Best practices for designing RESTful APIs in CBOL Messaging Hub. Covers resource modeling, HTTP methods, status codes, pagination, filtering, and error handling.

## Core Principles

### API First

```
✅ API First workflow:
  1. Design API (OpenAPI spec)
  2. Review API with stakeholders
  3. Implement API
  4. Test against spec

❌ Code First workflow:
  1. Write code
  2. Generate docs from code
  3. Hope API is consistent
```

### Resource-Oriented Design

```
✅ Good - nouns for resources, verbs for actions

Resources (nouns):
  /conversations          - Collection of conversations
  /conversations/{id}     - Specific conversation
  /conversations/{id}/messages - Messages in a conversation
  /messages/{id}           - Specific message
  /users/{id}              - Specific user

Actions (verbs, only when not CRUD):
  /conversations/{id}/close    - Close conversation (state change)
  /conversations/{id}/transfer - Transfer to agent
  /messages/{id}/read           - Mark as read

❌ Bad - verbs in resource paths
  /getConversations       - Verb in path
  /createConversation     - Verb in path
  /deleteMessage/{id}     - Verb in path
  /sendMessage            - Verb in path
```

## HTTP Methods

| Method | Purpose | Idempotent | Safe | Request Body | Response Body |
|--------|---------|-----------|------|-------------|---------------|
| GET | Retrieve resource | ✅ | ✅ | ❌ | ✅ |
| POST | Create resource / action | ❌ | ❌ | ✅ | ✅ |
| PUT | Replace resource | ✅ | ❌ | ✅ | ✅ |
| PATCH | Partial update | ❌ | ❌ | ✅ | ✅ |
| DELETE | Delete resource | ✅ | ❌ | ❌ | Optional |

```java
// ✅ Good - proper HTTP method usage
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> list(Pageable pageable) { }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> get(@PathVariable Long id) { }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest req) { }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationResponse> update(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateConversationRequest req) { }

    @PatchMapping("/{id}")
    public ResponseEntity<ConversationResponse> partialUpdate(@PathVariable Long id,
                                                                @RequestBody Map<String, Object> updates) { }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { }

    // Action endpoint (not CRUD)
    @PostMapping("/{id}/close")
    public ResponseEntity<ConversationResponse> close(@PathVariable Long id) { }
}
```

## HTTP Status Codes

### Success Codes

| Code | Name | Use For |
|------|------|---------|
| 200 | OK | Successful GET, PUT, PATCH, DELETE |
| 201 | Created | Successful POST (resource created) |
| 202 | Accepted | Async operation accepted (processing later) |
| 204 | No Content | Successful DELETE, no response body |

### Client Error Codes

| Code | Name | Use For |
|------|------|---------|
| 400 | Bad Request | Invalid input, validation error |
| 401 | Unauthorized | Missing/invalid authentication |
| 403 | Forbidden | Authenticated but no permission |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Resource conflict (duplicate, version mismatch) |
| 410 | Gone | Resource permanently deleted |
| 422 | Unprocessable Entity | Semantic validation error |
| 429 | Too Many Requests | Rate limit exceeded |

### Server Error Codes

| Code | Name | Use For |
|------|------|---------|
| 500 | Internal Server Error | Unexpected server error |
| 502 | Bad Gateway | Upstream service returned invalid response |
| 503 | Service Unavailable | Service temporarily unavailable (maintenance, overload) |
| 504 | Gateway Timeout | Upstream service timed out |

```java
// ✅ Good - proper status codes
@PostMapping
public ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest req) {
    ConversationResponse response = service.create(req);
    return ResponseEntity.status(HttpStatus.CREATED)  // 201 for create
        .header("Location", "/api/v1/conversations/" + response.getId())
        .body(response);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();  // 204 for delete
}

// ❌ Bad - always return 200
@PostMapping
public ConversationResponse create(@RequestBody CreateConversationRequest req) {
    return service.create(req);  // Always 200, even for create!
}
```

## Error Response Format

```json
// ✅ Good - consistent error response
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "content",
        "code": "NOT_BLANK",
        "message": "content must not be blank"
      },
      {
        "field": "conversationId",
        "code": "NOT_NULL",
        "message": "conversationId must not be null"
      }
    ],
    "traceId": "abc123-def456",
    "timestamp": "2026-08-19T10:30:00Z"
  }
}

// ❌ Bad - inconsistent error formats
// Sometimes: { "error": "message" }
// Sometimes: { "message": "error" }
// Sometimes: "Error string"
// Sometimes: HTML error page
```

```java
// ✅ Good - global exception handler with consistent format
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getCode(), error.getDefaultMessage()))
            .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Request validation failed")
            .details(details)
            .traceId(MDC.get("traceId"))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ErrorResponse response = ErrorResponse.builder()
            .code("NOT_FOUND")
            .message(ex.getMessage())
            .traceId(MDC.get("traceId"))
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}
```

## Pagination

### Offset Pagination (Default)

```
GET /api/v1/conversations?page=0&size=20&sort=lastMessageAt,desc

Response:
{
  "content": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 156,
    "totalPages": 8
  }
}
```

### Cursor Pagination (For Large Datasets / Infinite Scroll)

```
GET /api/v1/messages?conversationId=123&limit=20&cursor=eyJpZCI6NTAwfQ==

Response:
{
  "data": [...],
  "nextCursor": "eyJpZCI6NTIwfQ==",
  "hasMore": true
}

// When hasMore = false, no more data
```

```java
// ✅ Good - cursor pagination implementation
@GetMapping("/messages")
public ResponseEntity<CursorPageResponse<MessageResponse>> listMessages(
        @RequestParam Long conversationId,
        @RequestParam(defaultValue = "20") @Max(100) int limit,
        @RequestParam(required = false) String cursor) {

    CursorPage<Message> page = messageService.findByConversationId(conversationId, limit, cursor);

    CursorPageResponse<MessageResponse> response = CursorPageResponse.<MessageResponse>builder()
        .data(page.getContent().stream().map(MessageResponse::from).collect(Collectors.toList()))
        .nextCursor(page.getNextCursor())
        .hasMore(page.isHasMore())
        .build();

    return ResponseEntity.ok(response);
}
```

### Pagination Rules

| Rule | Value |
|------|-------|
| Default page size | 20 |
| Max page size | 100 |
| Default sort | `createdAt,desc` |
| Offset pagination | For admin dashboards, small datasets |
| Cursor pagination | For user-facing lists, infinite scroll, large datasets |
| Never return all | Always paginate, even if client asks for `size=999999` |

## Filtering & Sorting

```
✅ Good - consistent filtering syntax

# Equality
GET /conversations?status=ACTIVE

# Multiple values (OR)
GET /conversations?status=ACTIVE,TRANSFERRING

# Range
GET /messages?createdAtAfter=2026-01-01&createdAtBefore=2026-08-19

# Search
GET /conversations?q=hello

# Sorting
GET /conversations?sort=lastMessageAt,desc
GET /conversations?sort=priority,asc&sort=lastMessageAt,desc

❌ Bad - inconsistent filtering
GET /conversations?status_eq=ACTIVE
GET /conversations?status=active&type=support
GET /conversations?filter[status]=ACTIVE
```

## API Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Base path | `/api/v{version}/{resource}` | `/api/v1/conversations` |
| Resource names | Plural nouns, kebab-case | `/conversations`, `/message-templates` |
| Path parameters | camelCase or snake_case | `/{conversationId}` or `/{conversation_id}` |
| Query parameters | camelCase | `?pageSize=20&sortBy=createdAt` |
| Request/response fields | camelCase | `{"conversationId": 123, "lastMessageAt": "..."}` |
| Action endpoints | Verb after resource | `/conversations/{id}/close` |
| Headers | `X-` prefix, kebab-case | `X-Request-Id`, `X-Trace-Id` |

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Verbs in URLs | Not RESTful, inconsistent | Use nouns + HTTP methods |
| Always return 200 | Can't distinguish create/update/delete | Use 201 for create, 204 for delete |
| No pagination | Performance issues, huge responses | Always paginate list endpoints |
| Inconsistent error format | Hard for clients to handle errors | Standard error response with code/message/details |
| No versioning | Breaking changes affect all clients | Version APIs (URI, header, or media type) |
| Too much nesting | Deep paths, complex queries | Limit nesting to 2 levels, use query params |
| No rate limiting | Abuse, DoS | Rate limit all endpoints |
| No idempotency | Retries cause duplicates | Use idempotency keys for POST |
| Returning internal errors | Leaks implementation details | Generic 500 message, log details server-side |
| No HATEOAS links | Clients hardcode URLs | Include relevant links in responses (optional) |
| Mixing auth and business logic | Hard to test, maintain | Separate auth filter/annotation from controller |
| No request ID | Can't trace requests | Add X-Request-Id header, propagate through services |

## References

- Zalando RESTful API Guidelines: https://opensource.zalando.com/restful-api-guidelines/
- Microsoft REST API Guidelines: https://github.com/microsoft/api-guidelines
- REST API Design Handbook: https://github.com/HouariZegai/REST-API-Design
- HTTP Status Codes: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
- OpenAPI Specification: https://swagger.io/specification/
- JSON:API: https://jsonapi.org/
