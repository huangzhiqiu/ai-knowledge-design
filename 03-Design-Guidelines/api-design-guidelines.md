# RESTful API Design Guidelines

> Industry-standard REST API design best practices (synthesized from Stripe, GitHub, Google, Postman guidelines).

## Core Principles

1. **Resource-Oriented**: Model APIs around resources (nouns), not actions (verbs)
2. **Stateless**: Each request contains all information needed; no server session
3. **Consistent**: Predictable naming, structure, and behavior
4. **API First**: Design contract (OpenAPI) before writing code

## Resource Naming

### Use Nouns, Not Verbs
| Good | Bad |
|------|-----|
| `GET /users` | `GET /getUsers` |
| `POST /orders` | `POST /createOrder` |
| `DELETE /messages/{id}` | `GET /deleteMessage?id=123` |

### Use Plural Nouns for Collections
- `GET /users` — list users
- `GET /users/{id}` — get specific user
- `POST /users` — create user
- `PUT /users/{id}` — update user (full)
- `PATCH /users/{id}` — partial update
- `DELETE /users/{id}` — delete user

### Hierarchical Resources
```
GET /conversations/{conversationId}/messages
POST /conversations/{conversationId}/messages
GET /groups/{groupId}/members
DELETE /groups/{groupId}/members/{userId}
```

### Avoid Too Deep Nesting
- Maximum depth: 2-3 levels
- Deeper: use query params or sub-resources
- `GET /messages?conversationId=123` instead of `/a/{a}/b/{b}/c/{c}/d`

## HTTP Methods

| Method | Purpose | Idempotent | Safe |
|--------|---------|-----------|------|
| GET | Retrieve resource | Yes | Yes |
| POST | Create resource | No | No |
| PUT | Full update/replace | Yes | No |
| PATCH | Partial update | No | No |
| DELETE | Delete resource | Yes | No |
| HEAD | Get headers only | Yes | Yes |
| OPTIONS | Get allowed methods | Yes | Yes |

## HTTP Status Codes

### Success (2xx)
| Code | Meaning | Use Case |
|------|---------|----------|
| 200 OK | Success | GET, PUT, PATCH success |
| 201 Created | Resource created | POST success (include Location header) |
| 202 Accepted | Request accepted, processing async | Long-running operations |
| 204 No Content | Success, no body | DELETE success |

### Client Error (4xx)
| Code | Meaning | Use Case |
|------|---------|----------|
| 400 Bad Request | Invalid input | Validation failure |
| 401 Unauthorized | Not authenticated | Missing/invalid token |
| 403 Forbidden | Authenticated but no permission | Insufficient role |
| 404 Not Found | Resource doesn't exist | Invalid ID |
| 409 Conflict | Resource conflict | Duplicate, version conflict |
| 422 Unprocessable Entity | Semantic errors | Valid syntax, bad business logic |
| 429 Too Many Requests | Rate limited | Exceeded quota |

### Server Error (5xx)
| Code | Meaning |
|------|---------|
| 500 Internal Server Error | Unexpected server error |
| 502 Bad Gateway | Upstream server error |
| 503 Service Unavailable | Overloaded or maintenance |
| 504 Gateway Timeout | Upstream timeout |

## Request/Response Format

### Unified Response Structure
```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "requestId": "uuid-trace-id"
}
```

### Error Response Structure
```json
{
  "code": 40001,
  "message": "Validation failed",
  "details": [
    { "field": "email", "issue": "must be a valid email" }
  ],
  "requestId": "uuid-trace-id"
}
```

### Pagination
```
GET /messages?page=1&size=20&sort=createdAt,desc
```
Response:
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "size": 20,
    "total": 156,
    "totalPages": 8
  }
}
```

### Filtering & Sorting
```
GET /messages?type=text&status=sent&sort=createdAt,desc
```

## API Versioning

### Strategies
| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| URI Path | `/api/v1/users` | Explicit, simple | URL changes |
| Query Param | `/api/users?version=1` | Simple | Not standard |
| Header | `Accept: application/vnd.app.v1+json` | Clean URLs | Complex |
| **Recommended** | **URI Path** | **Most popular (Stripe, GitHub)** | |

### Versioning Rules
- Increment major version for breaking changes
- Support at least N-1 version
- Deprecate with `Sunset` header and documentation
- Non-breaking changes (add fields) don't need version bump

## Idempotency

- GET, PUT, DELETE are naturally idempotent
- POST should support idempotency key for critical operations
```
POST /payments
Idempotency-Key: unique-uuid
```
- Server caches response by key, returns same result on retry

## Authentication & Security

- Use HTTPS always (TLS 1.2+)
- Bearer token in Authorization header: `Authorization: Bearer <jwt>`
- Never put tokens in URL query params (logged)
- Rate limiting: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- CORS properly configured

## Caching

- `Cache-Control: max-age=3600` for stable resources
- `ETag` / `If-None-Match` for conditional GET
- `Last-Modified` / `If-Modified-Since`
- Never cache authenticated responses

## Documentation

- Use OpenAPI 3.0 (Swagger) specification
- Auto-generate docs from code (SpringDoc)
- Include: endpoints, request/response schemas, error codes, examples
- Version docs alongside API

## API Design Checklist

- [ ] Resources named with plural nouns
- [ ] HTTP methods used correctly
- [ ] Appropriate status codes returned
- [ ] Unified response/error format
- [ ] Pagination for list endpoints
- [ ] API versioning strategy
- [ ] Authentication required
- [ ] Rate limiting implemented
- [ ] HTTPS enforced
- [ ] OpenAPI documentation exists
- [ ] Idempotency for critical POSTs
- [ ] Request IDs for tracing

## References
- Postman REST API Best Practices
- GitHub REST API Design
- Stripe API Design
- https://github.com/Robinyo/restful-api-design-guidelines
- https://github.com/apifactory-org/restful-api-guidelines
