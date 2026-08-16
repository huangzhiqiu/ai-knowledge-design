# Cursor Design

## Cursor Types

| Cursor | Scope | Purpose | Storage |
|--------|-------|---------|---------|
| last_delivered_seq | Per device | Highest seq acknowledged as received | Device record |
| last_read_seq | Per user per conversation | Highest seq actively viewed | Membership record |
| conversation_max_seq | Per conversation | Latest seq in conversation | Conversation record |
| sync_cursor | Per device per conversation | Resume point for incremental sync | Device record |

## Unread Count Derivation

```
unread_count = conversation.max_seq - membership.last_read_seq
```

- Computed on the fly or cached
- Updates when: new message arrives OR user reads messages

## Cursor-based Sync Protocol

### Client -> Server (on connect)
```json
{
  "action": "sync",
  "device_id": "dev_xxx",
  "cursors": [
    {"conversation_id": "conv_1", "last_delivered_seq": 120},
    {"conversation_id": "conv_2", "last_delivered_seq": 45}
  ]
}
```

### Server -> Client (response)
```json
{
  "conversations": [
    {
      "conversation_id": "conv_1",
      "messages": [/* seq 121..130 */],
      "new_max_seq": 130
    }
  ],
  "has_more": false
}
```

## Cursor Persistence

### Server-side
- `last_delivered_seq`: stored per device, updated on ACK
- `last_read_seq`: stored per membership, updated on read receipt

### Client-side
- Local cache of cursors for fast resume
- Persist to local storage (SQLite/CoreData)
- Validate with server on reconnect (server is source of truth)

## Edge Cases

### Clock skew
- Use server-assigned seq_id, not client timestamp
- Cursors are monotonic, no clock dependency

### Message gaps
- seq_id should be gap-free (or gaps handled gracefully)
- If gap detected: client requests full re-sync from last known good seq

### Conversation deletion
- Remove cursor entry
- Keep tombstone for sync consistency (optional)

## Reference: Educative Chat System Design
Cursor semantics: `last_delivered_seq` (per device), `last_read_seq` (per user), unread = `conversation_max_seq - last_read_seq`.
