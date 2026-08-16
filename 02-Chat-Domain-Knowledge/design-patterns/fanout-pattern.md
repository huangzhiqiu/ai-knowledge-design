# Fanout Pattern

## What is Fanout?

Fanout is the process of replicating a single message to multiple recipients. In IM, every group message is a fanout operation.

```
                    [Message]
                        |
          ┌─────────────┼─────────────┐
          v             v             v
      Recipient 1   Recipient 2   Recipient N
```

## Two Fanout Directions

### Write Fanout (Fan-out on Write)

**On message send:**
1. Persist message
2. Write copy to EACH recipient's inbox / timeline
3. Push to online recipients

```
Message -> Message Store
          -> User A inbox (write)
          -> User B inbox (write)
          -> User C inbox (write)
          -> ...
```

**Complexity:** O(N) writes per message (N = recipients)

**Read:** O(1) - just read your inbox

**Best for:** Small groups (< 100), 1-on-1 chat, read-heavy

### Read Fanout (Fan-out on Read)

**On message send:**
1. Persist message ONCE to conversation store
2. No per-recipient write

**On read:**
1. User opens conversation
2. Query messages from conversation store
3. Merge with other conversations for inbox view

```
Message -> Conversation Store (write once)

User reads -> Query conversation -> Get messages
```

**Complexity:** O(1) write, O(N) read (N = conversations)

**Best for:** Large groups, channels, write-heavy

## Hybrid Fanout (Recommended)

Use different strategy based on group size:

| Group Size | Strategy | Reason |
|-----------|----------|--------|
| 1-on-1 (2) | Write fanout | Only 2 writes, fast reads |
| Small (< 100) | Write fanout | 100 writes acceptable |
| Medium (100-1000) | Write fanout with batching | Batch inbox writes |
| Large (> 1000) | Read fanout | 1000+ writes too expensive |
| Channel (10K+) | Read fanout + push notification | Store once, notify, pull on open |

## Write Fanout Implementation

### Inbox Table
```sql
CREATE TABLE user_inbox (
    user_id VARCHAR(64),
    seq_id BIGINT,
    msg_id VARCHAR(64),
    conversation_id VARCHAR(64),
    sender_id VARCHAR(64),
    preview TEXT,
    created_at BIGINT,
    PRIMARY KEY (user_id, seq_id)
);
```

### Batch Write
```
For group message to N members:
1. Get member list
2. Batch INSERT into user_inbox (multi-row insert)
3. Push to online members
```

### Optimizations
- Batch database inserts (multi-row INSERT)
- Async inbox writes (message bus)
- Skip inbox write for users who left
- Deduplicate if user gets message from multiple paths

## Read Fanout Implementation

### Conversation Message Store
```sql
CREATE TABLE conversation_messages (
    conversation_id VARCHAR(64),
    seq_id BIGINT,
    msg_id VARCHAR(64),
    sender_id VARCHAR(64),
    content JSON,
    timestamp BIGINT,
    PRIMARY KEY (conversation_id, seq_id)
);
```

### Inbox Aggregation (on read)
```
1. Get user's conversation list
2. For each conversation: get last message (MAX seq_id)
3. Sort conversations by last message time
4. Compute unread counts
```

### Optimizations
- Cache conversation list with last message preview
- Pre-compute unread counts (increment on message, decrement on read)
- Use materialized views for inbox

## Push Fanout

Even with read fanout for storage, online delivery needs push fanout:

```
Message -> Online members: push via WebSocket
        -> Offline members: push notification (APNs/FCM)
```

For very large groups:
- Don't push message content to all online users
- Push "new message" notification only
- Clients pull actual message when they open the conversation

## Reference: Turms IM
Turms uses fanout read design for creating inboxes (message timelines). Supports push, pull, and push-pull models. Most design details come from commercial IM projects.

## Reference: WeChat
WeChat uses write-fanout for small groups and 1-on-1, read-fanout for official accounts and large groups. The threshold is around 100-500 members.
