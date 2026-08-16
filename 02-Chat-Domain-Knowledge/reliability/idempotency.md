# Idempotency

## What is Idempotency?

An operation is idempotent if performing it multiple times has the same effect as performing it once.

```
f(f(x)) = f(x)
```

## Why IM Needs Idempotency

- Network retries: client may resend if ACK is lost
- Server crashes: message persisted but ACK not sent
- Push duplicates: same message pushed twice to client
- At-least-once delivery: duplicates are expected

## Idempotency Keys

### msg_id as Idempotency Key

**Send message flow:**
```
1. Client generates msg_id (UUID or Snowflake) BEFORE sending
2. Client sends message with msg_id
3. Server checks: has msg_id been seen?
   - Yes: skip processing, return existing result
   - No: process, store, return result
4. Server records msg_id in dedup cache (with TTL)
```

### Server-side Dedup

```sql
-- Unique constraint on msg_id prevents duplicates
CREATE TABLE messages (
    msg_id VARCHAR(64) PRIMARY KEY,
    ...
);

-- Insert or ignore (idempotent)
INSERT IGNORE INTO messages (msg_id, ...) VALUES (?, ...);
```

### Dedup Cache (Redis)

```
Key: dedup:{msg_id}
Value: 1
TTL: 24 hours (longer than max retry window)
```

- Fast path: check Redis before DB
- Fallback: DB unique constraint is ultimate guard
- TTL prevents unbounded growth

## Client-side Dedup

Even with server dedup, client may receive duplicates:
- Push retry after ACK lost
- Sync overlap (push + pull same message)

**Client dedup strategy:**
```
1. Maintain local set of seen msg_id (or LRU cache)
2. On receiving message: check if msg_id exists locally
3. If exists: ignore duplicate
4. If new: render, add to seen set
```

## Idempotent Operations Checklist

| Operation | Idempotency Key | Safe Method |
|-----------|----------------|-------------|
| Send message | msg_id | INSERT IGNORE / UPSERT |
| Edit message | msg_id + version | Compare-and-swap |
| Delete message | msg_id | Soft delete (idempotent) |
| Mark read | user_id + conversation_id + seq | Set max (last_read_seq = MAX(existing, new)) |
| Join group | user_id + group_id | INSERT IGNORE |
| Leave group | user_id + group_id | DELETE (idempotent) |
| Add reaction | user_id + msg_id + emoji | INSERT IGNORE |
| Remove reaction | user_id + msg_id + emoji | DELETE |

## Non-Idempotent Operations (Need Care)

| Operation | Risk | Mitigation |
|-----------|------|------------|
| Increment counter | Double increment | Use SET with absolute value, or dedup key |
| Create resource (no client ID) | Duplicate resources | Client-generated ID, or dedup by content hash |
| Send notification | Double notification | Dedup by notification_id |

## Idempotency in HTTP APIs

For REST API calls (not just WebSocket):

```http
POST /api/messages
Idempotency-Key: uuid-xxx
Content-Type: application/json

{"conversation_id": "...", "content": "..."}
```

- Server stores response keyed by `Idempotency-Key`
- Retry with same key returns cached response
- TTL: 24 hours typical

## Reference: Message Queue Idempotency
Kafka/RabbitMQ consumers must be idempotent. Pattern: use message ID as dedup key, check before processing, use DB unique constraint as safety net.
