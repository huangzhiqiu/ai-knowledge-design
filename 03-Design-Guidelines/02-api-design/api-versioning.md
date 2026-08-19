# API Versioning Guidelines

> Best practices for API versioning in CBOL Messaging Hub. Covers versioning strategies, deprecation policy, backward compatibility, and migration.

## Versioning Strategies

### Strategy Comparison

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| **URI Versioning** | `/api/v1/conversations` | Simple, visible, cache-friendly | URL changes, clutters URI |
| **Header Versioning** | `Accept: application/vnd.cbol.v1+json` | Clean URIs | Less visible, harder to debug |
| **Query Param** | `/conversations?version=1` | Simple | Not RESTful, caching issues |
| **Host Versioning** | `v1.api.cbol.com/conversations` | Clear separation | DNS complexity, CORS |

### Recommended: URI Versioning (Default)

```
✅ Default strategy: URI versioning

/api/v1/conversations
/api/v1/messages
/api/v2/conversations  (when breaking changes needed)
```

```java
// ✅ Good - URI versioning with Spring
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationControllerV1 { }

@RestController
@RequestMapping("/api/v2/conversations")
public class ConversationControllerV2 { }

// Or with versioned packages
// com.selfdevelopment.cbol.api.v1.controller
// com.selfdevelopment.cbol.api.v2.controller
```

### Header Versioning (For Internal APIs)

```java
// ✅ Good - header versioning for internal/gRPC-style APIs
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @GetMapping(headers = "X-API-Version=1")
    public ResponseEntity<ConversationV1Response> getV1(@PathVariable Long id) { }

    @GetMapping(headers = "X-API-Version=2")
    public ResponseEntity<ConversationV2Response> getV2(@PathVariable Long id) { }
}

// Client sends:
// GET /api/conversations/123
// X-API-Version: 2
```

## What Constitutes a Breaking Change?

### Breaking Changes (Require New Version)

| Change | Example | Why Breaking |
|--------|---------|-------------|
| Remove endpoint | `DELETE /api/v1/messages/{id}` removed | Clients calling it get 404 |
| Remove field | Remove `priority` from response | Clients relying on it break |
| Change field type | `id: number` → `id: string` | Type mismatch in clients |
| Change field name | `conversationId` → `convId` | Clients can't find field |
| Change status code | 200 → 201 for GET | Clients expect 200 |
| Add required request field | New required `metadata` field | Old clients fail validation |
| Change enum values | `ACTIVE` → `OPEN` | Old clients don't recognize new value |
| Change error format | Different error response structure | Clients can't parse errors |

### Non-Breaking Changes (Same Version)

| Change | Example | Why Safe |
|--------|---------|---------|
| Add new endpoint | `POST /api/v1/conversations/{id}/archive` | Old clients don't use it |
| Add optional field | New optional `metadata` in response | Old clients ignore it |
| Add optional request param | New `?include=messages` | Old clients don't send it |
| Add new enum value | New `ARCHIVED` status | Old clients handle unknown gracefully |
| Improve documentation | Better descriptions | No code impact |
| Performance optimization | Faster response | Same behavior |
| Add response headers | New `X-Request-Id` | Old clients ignore it |
| Bug fix (same behavior) | Fix incorrect calculation | Should match expected behavior |

## Deprecation Policy

### Deprecation Timeline

```
Phase 1: Announce (Day 0)
  - Add Deprecation header to responses
  - Update documentation with sunset date
  - Notify API consumers

Phase 2: Deprecate (Day 0 - Day 180)
  - API still works, but marked deprecated
  - Warning logs on server side
  - Monitor usage, contact remaining consumers

Phase 3: Sunset (Day 180)
  - Remove API or return 410 Gone
  - Migration guide available
```

### Deprecation Headers

```java
// ✅ Good - standard deprecation headers
@GetMapping("/api/v1/conversations")
public ResponseEntity<List<ConversationResponse>> list() {
    List<ConversationResponse> data = service.list();

    return ResponseEntity.ok()
        .header("Deprecation", "true")
        .header("Sunset", "Wed, 19 Feb 2027 23:59:59 GMT")
        .header("Link", "</api/v2/conversations>; rel=\"successor-version\"")
        .body(data);
}

// Response headers:
// Deprecation: true
// Sunset: Wed, 19 Feb 2027 23:59:59 GMT
// Link: </api/v2/conversations>; rel="successor-version"
```

### Deprecation in OpenAPI

```yaml
paths:
  /conversations:
    get:
      deprecated: true
      summary: List conversations (deprecated)
      description: |
        **Deprecated**: Use `/api/v2/conversations` instead.
        This endpoint will be removed on 2027-02-19.
      parameters: [...]
      responses:
        '200':
          description: Successful response
          headers:
            Deprecation:
              schema: { type: string, example: "true" }
            Sunset:
              schema: { type: string, format: date-time }
```

## Version Support Policy

| Version | Support Duration | Maintenance |
|---------|-----------------|-------------|
| Current (vN) | Full support | Bug fixes, new features |
| Previous (vN-1) | 6 months after vN release | Bug fixes only, no new features |
| Older (vN-2) | 0 days after vN-1 deprecation | No support, may return 410 |

```
Example timeline:
  v1 released: 2026-01-01
  v2 released: 2026-08-01
    → v1 enters deprecation (6 months)
    → v1 sunset: 2027-02-01
  v3 released: 2027-03-01
    → v2 enters deprecation (6 months)
    → v2 sunset: 2027-09-01
```

## Migration Guide Template

```markdown
# Migration Guide: v1 → v2

## Overview
Summary of changes and why they were made.

## Breaking Changes

### 1. Conversation status enum changed
- **v1**: `ACTIVE`, `CLOSED`, `PENDING`
- **v2**: `INIT`, `AI_PROCESSING`, `TRANSFERRING`, `AGENT_CONNECTED`, `AGENT_HANDLING`, `CLOSED`, `ERROR`
- **Action**: Update client to handle new status values

### 2. Message response structure changed
- **v1**: `{ "id": 123, "text": "Hello" }`
- **v2**: `{ "id": 123, "content": "Hello", "contentType": "TEXT" }`
- **Action**: Rename `text` to `content`, add `contentType` handling

## New Features
- List new features in v2

## Code Examples

### v1 (old)
```java
// Old code
```

### v2 (new)
```java
// New code
```

## FAQ
Common questions and answers
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| No versioning | Breaking changes affect all clients silently | Always version APIs from day one |
| Infinite version support | Maintenance burden, technical debt | Deprecate old versions after 6 months |
| Version in code but not URL | Hard to route, cache | Use URI versioning for external APIs |
| Changing v1 behavior | "Silent breaking change" | Don't change existing version behavior, create v2 |
| No deprecation period | Clients break suddenly | 6-month deprecation with headers and docs |
| No migration guide | Clients don't know how to upgrade | Provide migration guide with code examples |
| Version per endpoint | Inconsistent, confusing | Version the entire API, not individual endpoints |
| Removing without 410 | Clients get 404, don't know if removed or not found | Return 410 Gone for removed endpoints |
| No usage monitoring | Don't know who's using old versions | Monitor API usage by version, contact consumers |
| Adding required field to v1 | Breaks old clients | Add optional fields only, required fields need new version |

## References

- API Versioning (Microsoft): https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md#versioning
- REST API Versioning (Zalando): https://opensource.zalando.com/restful-api-guidelines/#versioning
- Deprecation Header (RFC 8594): https://datatracker.ietf.org/doc/html/rfc8594
- Sunset Header (RFC 8594): https://datatracker.ietf.org/doc/html/rfc8594#section-3
- API Versioning Strategies: https://www.xmatters.com/blog/api-versioning-strategies
- Semantic Versioning: https://semver.org/
