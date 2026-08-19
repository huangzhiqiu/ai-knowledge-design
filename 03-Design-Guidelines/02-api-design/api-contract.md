# API Contract & OpenAPI Guidelines

> Best practices for API contracts, OpenAPI/Swagger specification, request/response formats, idempotency, and API documentation.

## API First with OpenAPI

```
Workflow:
  1. Write OpenAPI spec (design)
  2. Review spec with team/clients
  3. Generate server stubs and client SDKs
  4. Implement business logic
  5. Validate implementation against spec
```

```yaml
# ✅ Good - OpenAPI 3.1 spec example
openapi: 3.1.0
info:
  title: CBOL Messaging API
  version: 1.0.0
  description: |
    API for CBOL Messaging Hub.
    ## Authentication
    All endpoints require Bearer token authentication.
  contact:
    name: CBOL Team
    email: cbol-team@example.com
  license:
    name: Proprietary

servers:
  - url: https://api.cbol.com/v1
    description: Production
  - url: https://staging-api.cbol.com/v1
    description: Staging

tags:
  - name: Conversations
    description: Conversation lifecycle management
  - name: Messages
    description: Message sending and retrieval

paths:
  /conversations:
    get:
      tags: [Conversations]
      summary: List conversations
      description: Returns a paginated list of conversations for the current user.
      operationId: listConversations
      parameters:
        - $ref: '#/components/parameters/PageParam'
        - $ref: '#/components/parameters/SizeParam'
        - $ref: '#/components/parameters/SortParam'
        - name: status
          in: query
          schema:
            type: string
            enum: [ACTIVE, CLOSED, TRANSFERRING]
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ConversationPage'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '429':
          $ref: '#/components/responses/TooManyRequests'
      security:
        - bearerAuth: []

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  parameters:
    PageParam:
      name: page
      in: query
      schema:
        type: integer
        minimum: 0
        default: 0
    SizeParam:
      name: size
      in: query
      schema:
        type: integer
        minimum: 1
        maximum: 100
        default: 20
    SortParam:
      name: sort
      in: query
      schema:
        type: string
      example: lastMessageAt,desc

  responses:
    Unauthorized:
      description: Missing or invalid authentication
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
    TooManyRequests:
      description: Rate limit exceeded
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

  schemas:
    Conversation:
      type: object
      required: [id, status, createdAt]
      properties:
        id:
          type: integer
          format: int64
          example: 12345
        status:
          type: string
          enum: [INIT, AI_PROCESSING, TRANSFERRING, AGENT_CONNECTED, AGENT_HANDLING, CLOSED, ERROR]
        createdAt:
          type: string
          format: date-time
        lastMessageAt:
          type: string
          format: date-time
          nullable: true
      additionalProperties: false

    ConversationPage:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/Conversation'
        page:
          type: object
          properties:
            number: { type: integer }
            size: { type: integer }
            totalElements: { type: integer }
            totalPages: { type: integer }

    ErrorResponse:
      type: object
      required: [code, message]
      properties:
        code:
          type: string
          example: VALIDATION_ERROR
        message:
          type: string
        details:
          type: array
          items:
            type: object
            properties:
              field: { type: string }
              code: { type: string }
              message: { type: string }
        traceId:
          type: string
        timestamp:
          type: string
          format: date-time
```

## Request/Response Format Rules

### Request Format

```json
// ✅ Good - clear, typed, validated
{
  "conversationId": 123,
  "content": "Hello, I need help with my order",
  "contentType": "TEXT",
  "metadata": {
    "source": "web",
    "userAgent": "Mozilla/5.0..."
  }
}

// ❌ Bad - untyped, ambiguous, no validation
{
  "conv_id": "123",           // String instead of number
  "msg": "Hello",             // Ambiguous field name
  "type": 0,                  // Magic number, no enum
  "extra": "unexpected field" // Undocumented field
}
```

### Response Format

```json
// ✅ Good - consistent envelope, clear data
{
  "data": {
    "id": 12345,
    "status": "AI_PROCESSING",
    "createdAt": "2026-08-19T10:30:00Z"
  },
  "meta": {
    "traceId": "abc123",
    "timestamp": "2026-08-19T10:30:01Z"
  }
}

// For lists:
{
  "data": [...],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 156,
    "totalPages": 8
  }
}
```

## Idempotency

```
✅ Idempotent by design:
  GET, PUT, DELETE - always idempotent

✅ Make POST idempotent with idempotency key:
  Client generates unique key, sends in header
  Server checks if key was processed, returns cached result if so
```

```java
// ✅ Good - idempotency key for POST endpoints
@PostMapping("/conversations")
public ResponseEntity<ConversationResponse> create(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateConversationRequest request) {

    // Check if already processed
    Optional<ConversationResponse> cached = idempotencyCache.get(idempotencyKey);
    if (cached.isPresent()) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cached.get());
    }

    // Process
    ConversationResponse response = service.create(request);

    // Cache result (TTL = 24h)
    idempotencyCache.put(idempotencyKey, response, Duration.ofHours(24));

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

## API Documentation

### What to Document

| Element | Description | Example |
|---------|-------------|---------|
| Endpoint | URL + method | `POST /api/v1/conversations` |
| Summary | One-line description | "Create a new conversation" |
| Description | Detailed explanation | "Creates a conversation for the current user..." |
| Parameters | Query/path params | `page`, `size`, `sort` |
| Request body | Schema + example | `CreateConversationRequest` |
| Response body | Schema + example | `ConversationResponse` |
| Status codes | All possible codes | 201, 400, 401, 403, 429 |
| Authentication | Required auth | Bearer JWT |
| Rate limit | Rate limit info | 100 req/min |
| Examples | Request/response examples | Full JSON examples |

### Auto-Generated Docs

```java
// ✅ Good - SpringDoc OpenAPI auto-generation
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI cbolOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CBOL Messaging API")
                .version("1.0.0")
                .description("API for CBOL Messaging Hub"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}

// Controller annotations
@Operation(summary = "Create conversation", description = "Creates a new conversation for the current user")
@ApiResponses(value = {
    @ApiResponse(responseCode = "201", description = "Conversation created"),
    @ApiResponse(responseCode = "400", description = "Validation error"),
    @ApiResponse(responseCode = "401", description = "Unauthorized")
})
@PostMapping
public ResponseEntity<ConversationResponse> create(...) { }
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| No API spec | No contract, clients guess | Write OpenAPI spec first |
| Inconsistent field naming | camelCase, snake_case, PascalCase mixed | Use camelCase consistently |
| No examples | Hard to understand API | Add request/response examples |
| Undocumented error codes | Clients don't know what to expect | Document all possible status codes |
| Changing spec without version | Breaking changes silently | Version APIs, deprecate old versions |
| No idempotency on POST | Retries cause duplicates | Use idempotency keys |
| Returning stack traces | Leaks internal details, security risk | Generic error message, log details server-side |
| No rate limit info | Clients get surprised by 429 | Document rate limits, return rate limit headers |
| HTML error pages | API clients can't parse HTML | Always return JSON errors |
| No `additionalProperties: false` | Clients can send unexpected fields | Set additionalProperties: false on all schemas |

## References

- OpenAPI Specification: https://swagger.io/specification/
- Swagger Editor: https://editor.swagger.io/
- SpringDoc OpenAPI: https://springdoc.org/
- Zalando RESTful API Guidelines: https://opensource.zalando.com/restful-api-guidelines/
- API Stylebook: http://apistylebook.com/
- HTTP API Design Guide (Gejia): https://github.com/gejia/HTTP-API-Design-Guide
