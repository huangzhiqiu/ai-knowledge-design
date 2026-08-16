# Message ID Design

## Dual-ID Strategy

> Key insight from WeChat architecture: one ID cannot serve both global uniqueness and strict ordering efficiently.

| ID | Scope | Purpose | Generation |
|----|-------|---------|------------|
| msg_id | Global | Uniqueness, idempotency, dedup | Client or server |
| seq_id | Per conversation | Ordering, sync cursor, range query | Server assigned |

## Option A: Snowflake for msg_id

64-bit integer layout:
```
| 1 bit sign | 41 bits timestamp (ms) | 10 bits machine ID | 12 bits sequence |
```

- ~69 years of timestamps from custom epoch
- 4096 IDs per millisecond per machine
- Time-ordered (K-sortable)
- Used by Twitter, Instagram, Discord

### Pros
- Compact (64-bit int)
- Time-ordered for efficient indexing
- No central coordination needed

### Cons
- Clock skew risk
- Machine ID management in dynamic environments (K8s)

## Option B: UUID for msg_id

- UUID v4: random, 128-bit
- UUID v7: time-ordered (new standard)

### Pros
- No coordination, no clock dependency
- Universally unique

### Cons
- 128-bit, larger storage/index
- UUID v4 not time-ordered (index fragmentation)

## seq_id Assignment

### Per-conversation auto-increment
```
conversation.max_seq = COALESCE(MAX(seq_id), 0) + 1
```

### Implementation options:
1. **Database auto-increment** (sharded per conversation)
2. **Redis INCR** per conversation key
3. **Central seq allocator** service

### Guarantees:
- Monotonically increasing within a conversation
- No gaps (or gaps acceptable if idempotent)
- Assigned at message persistence time

## Reference: Turms IM
Turms uses fanout read design with message timelines. Supports push, pull, and push-pull models for business data change awareness.
