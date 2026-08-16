# Event-Driven Architecture

## Core Idea

Everything that happens in the system is an **event**. Events are immutable, ordered, and replayable. State is derived from events.

```
Command -> Event -> State Change -> Notification
```

## Event Sourcing

### Instead of storing current state, store all events

**Traditional:**
```
UPDATE balance SET amount = amount - 10 WHERE user = 'Alice'
```
(Only final state known, history lost)

**Event Sourcing:**
```
APPEND event: {type: 'withdraw', user: 'Alice', amount: 10, timestamp: ...}
```
(All history preserved, state = fold of events)

### IM Events

| Event Type | Description |
|-----------|-------------|
| message.sent | New message created |
| message.edited | Message content edited |
| message.deleted | Message deleted/recalled |
| message.delivered | Message delivered to device |
| message.read | User read messages |
| conversation.created | New conversation |
| member.joined | User joined group |
| member.left | User left group |
| presence.changed | User online/offline |
| typing.started / typing.stopped | Typing indicator |

### Event Structure

```json
{
  "event_id": "uuid",
  "type": "message.sent",
  "aggregate_id": "conversation_xxx",
  "version": 123,
  "timestamp": 1718438400000,
  "payload": {
    "msg_id": "msg_xxx",
    "sender_id": "user_xxx",
    "content": "Hello"
  },
  "metadata": {
    "source": "gateway-node-3",
    "correlation_id": "req_xxx"
  }
}
```

## CQRS (Command Query Responsibility Segregation)

Separate write and read models:

```
Write Side (Commands)          Read Side (Queries)
┌─────────────────┐           ┌─────────────────┐
│  Command Handler│──events──>│  Event Handler  │
│  (validation)   │           │  (build views)  │
└────────┬────────┘           └────────┬────────┘
         │                             │
         v                             v
   Event Store                    Read Models
   (append-only)              (denormalized, fast)
```

### Write Model
- Validates commands
- Appends events to event store
- Source of truth

### Read Model
- Subscribes to events
- Builds denormalized views optimized for queries
- Example: conversation list with last message preview, unread counts

## Matrix Protocol: Event DAG (Reference)

Matrix takes event sourcing to a federated level:
- Every message, state change, membership is a signed `Event`
- Events form a **Directed Acyclic Graph (DAG)** referencing parent events
- Events replicated across all participating homeservers
- No central sequencer - servers resolve state via algorithm
- State Resolution v2 handles conflicting events deterministically

**Event types in Matrix:**
- `m.room.message`: Timeline event (chat message)
- `m.room.member`: State event (join/leave/invite)
- `m.room.name`: State event (room name)
- `m.room.encryption`: State event (encryption algorithm)

## Benefits
1. **Audit trail**: Complete history of all changes
2. **Time travel**: Replay events to any point in time
3. **Decoupling**: Producers and consumers independent
4. **Scalability**: Events can be processed asynchronously
5. **Debugging**: Replay events to reproduce issues

## Challenges
1. **Event schema evolution**: Need versioning and migration
2. **Read model eventual consistency**: Views may lag behind writes
3. **Complexity**: Higher learning curve, more infrastructure
4. **Storage**: Event store grows unbounded (need snapshots)
