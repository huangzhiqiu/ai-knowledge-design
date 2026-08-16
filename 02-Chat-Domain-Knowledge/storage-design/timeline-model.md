# Timeline Model

## What is a Timeline?

A timeline is an ordered sequence of messages (events) for a user or conversation. Each message has a seq_id that monotonically increases.

```
User Inbox Timeline:
[msg1 seq=1] -> [msg2 seq=2] -> [msg3 seq=3] -> ...
```

## Two Fundamental Models

### Write Fanout (Push Model)

**On message send:**
1. Write message to message store
2. Write message to EACH recipient's inbox (timeline)
3. Recipient reads from their own inbox

```
Sender sends msg -> Message Store
                      |
                      +-> User A inbox (write)
                      +-> User B inbox (write)
                      +-> User C inbox (write)
```

**Pros:**
- Read is O(1) - just read your inbox
- Fast timeline loading for active users

**Cons:**
- Write amplification: 1 message = N writes (N = recipients)
- Group chat with 1000 members = 1000 writes
- Storage amplification: message duplicated N times

**Best for:** Small groups, 1-on-1 chat, read-heavy workloads

### Read Fanout (Pull Model)

**On message send:**
1. Write message ONCE to conversation store
2. On read: gather messages from all conversations the user is in

```
Sender sends msg -> Conversation Store (write once)

User reads -> Query all user's conversations -> Merge messages -> Timeline
```

**Pros:**
- Write is O(1) - no fanout
- Storage efficient - message stored once
- Good for large groups / channels

**Cons:**
- Read is expensive - scatter-gather across conversations
- Need to merge and sort messages from multiple sources
- Higher read latency

**Best for:** Large groups, channels, write-heavy workloads

## Hybrid Model (Recommended for most IM systems)

### Conversation-based fanout
- **Small groups (< 100 members)**: Write fanout - write to each member inbox
- **Large groups (> 100 members)**: Read fanout - store once, pull on read
- **1-on-1**: Write fanout (only 2 recipients)

### Turms IM Approach
Turms uses **fanout read design** for creating inboxes (message timelines), and supports push, pull, and push-pull models. Design details inspired by commercial IM projects.

## Timeline Storage

### Per-user inbox (write fanout)
```sql
CREATE TABLE user_inbox (
    user_id     VARCHAR(64),
    seq_id      BIGINT,
    msg_id      VARCHAR(64),
    conversation_id VARCHAR(64),
    PRIMARY KEY (user_id, seq_id)
);
```

### Per-conversation storage (read fanout)
```sql
CREATE TABLE conversation_messages (
    conversation_id VARCHAR(64),
    seq_id      BIGINT,
    msg_id      VARCHAR(64),
    sender_id   VARCHAR(64),
    content     JSON,
    PRIMARY KEY (conversation_id, seq_id)
);
```

## Sync Cursor with Timeline

```
User inbox timeline:
[1] [2] [3] [4] [5] [6] [7] [8] ...
             ^
      last_delivered_seq = 4
      
On reconnect: fetch seq > 4
```

## Reference: WeChat Timeline
WeChat uses write-fanout for small groups and read-fanout for large groups/official accounts. The inbox model enables efficient "pull new messages" by seq range.
