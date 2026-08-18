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

## Reference: WeChat
WeChat uses write-fanout for small groups and 1-on-1, read-fanout for official accounts and large groups. The threshold is around 100-500 members.

---

## Open Source Project Deep Dive

### Turms: Read Fanout Architecture (Production-Grade Implementation)

Turms is a typical production-grade implementation of read fanout, with a design philosophy of **minimalism and anti-over-engineering**.

**Turms read fanout core flow:**

```
1. Sender sends message -> write to Message collection (once only)
2. Message indexed by {recipient_id, sending_time}
3. Recipient pulls inbox -> query Message collection where recipient_id = ? order by sending_time desc
4. No need to write a copy for each recipient
```

**Why does Turms choose read fanout?**

| Factor | Read Fanout Advantage |
|--------|----------------------|
| Large group scenario | A message to a 1000-person group writes only once, vs. 1000 writes for write fanout |
| Storage cost | Message stored once, no redundancy |
| Consistency | Single message record, no multi-replica inconsistency |
| Implementation complexity | No inbox table to maintain, no history sync on membership changes |

**Key optimizations in Turms read fanout:**
1. **Index as inbox**: The `{recipient_id, sending_time}` index directly serves as the inbox, no extra inbox table needed
2. **Shard by recipient**: MongoDB shards by `recipient_id`, inbox queries hit a single shard
3. **Push notification, not content**: For large groups, only push "new message" notification; clients pull actual content when opening the conversation
4. **Conversation unread count**: Redis maintains unread count per user per conversation, incrementally updated

**Turms push-pull model:**
- **Push**: Server notifies client of "new message" (lightweight notification)
- **Pull**: Client pulls actual message content based on notification (on-demand)
- This hybrid model avoids bandwidth waste of pushing full messages to large groups

### Chat21: Inbox Pattern (SMTP/POP3-style)

Chat21 proposes a unique Inbox pattern inspired by email protocols:

**Core idea: Messages are not delivered P2P directly, but relayed through an Observer to the recipient's Inbox**

```
Sender -> MQTT publish to own /outgoing path
            ↓
Observer (Chat21 Server) subscribes to all /outgoing
            ↓
Observer publishes via AMQP to recipient's /clientadded path
            ↓
Recipient MQTT subscribes to own inbox path, receives message
```

**MQTT path design:**
```
Sender writes: /apps/{appId}/users/{senderId}/{recipientId}/messages/outgoing
Recipient receives: /apps/{appId}/users/{recipientId}/{senderId}/messages/clientadded
```

**Advantages of the Inbox pattern:**
1. **Security policy point**: Observer can enforce blocking, filtering, and content auditing during relay
2. **Fine-grained permissions**: RabbitMQ JWT Token restricts users to read/write only their own paths, cannot touch others' inboxes
3. **Persistence**: Observer can persist messages before forwarding
4. **Offline support**: Messages written to RabbitMQ persistent queue, automatically delivered when user comes online
5. **Decoupling**: Sender does not need to know if recipient is online

**Differences from traditional fanout:**

| Dimension | Traditional Write Fanout | Chat21 Inbox |
|-----------|-------------------------|-------------|
| Write location | Directly write to recipient inbox | Write to own outgoing, Observer forwards |
| Security policy | No central policy point | Observer is a natural policy point |
| Permission model | Application-layer control | MQTT topic-level JWT control |
| Scale | Small-medium | Small-medium (RabbitMQ-centric) |

### Mattermost: WebSocket Event Scoping (Push Optimization)

Mattermost implements fine-grained event scoping at the push layer:
- `typing` and `reaction` events are**only sent to clients that have the corresponding channel/thread open**
- Not all channel members receive typing indicators, only users currently viewing that channel
- Significantly reduces unnecessary network traffic and client processing overhead
- Since v11, this optimization is permanently enabled (previously behind a feature flag)

### Cross-Project Fanout Strategy Comparison

| Project | Storage Model | Online Push | Large Group Strategy |
|---------|--------------|-------------|---------------------|
| Turms | Read fanout (index as inbox) | Push notification + Pull content | Read fanout naturally supports |
| Mattermost | Write fanout (PostgreSQL) | WebSocket full push | Event scoping optimization |
| Rocket.Chat | Write fanout (MongoDB) | DDP subscription push | OpLog broadcast |
| Matrix | Full replication (one copy per homeserver) | /sync incremental pull | Federation replication |
| Chat21 | Inbox pattern (Observer relay) | MQTT push | Per-recipient forwarding |
| WeChat | Hybrid (write fanout for small groups / read fanout for large) | Push + Pull | 100-500 member threshold switch |
