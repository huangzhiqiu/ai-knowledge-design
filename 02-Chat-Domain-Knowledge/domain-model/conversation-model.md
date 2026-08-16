# Conversation Model

## Entity Definition

| Field | Type | Description |
|-------|------|-------------|
| conversation_id | string | Global unique conversation ID |
| type | enum | direct (1-on-1) / group / channel |
| name | string | Conversation name (group only) |
| owner_id | string | Creator / owner user_id |
| created_at | timestamp | Creation time |
| last_message_id | string | Latest message reference |
| last_message_at | timestamp | Latest message time |
| metadata | json | Extended attributes |

## Conversation Types

### Direct (1-on-1)
- Exactly 2 members
- Implicitly created on first message
- Cannot be renamed (derived from peer identity)

### Group
- 3+ members
- Explicit creation required
- Supports name, avatar, description

### Channel (optional)
- Public / private join policy
- Topic-based
- May have thousands of members

## Membership Entity

| Field | Type | Description |
|-------|------|-------------|
| user_id | string | Member user |
| conversation_id | string | Conversation |
| role | enum | owner / admin / member |
| joined_at | timestamp | Join time |
| muted | bool | Notification mute |
| pinned | bool | Pin to top |
| last_read_seq | int64 | Last read sequence number |

## Unread Count Calculation
```
unread_count = conversation.max_seq - membership.last_read_seq
```

## Reference: Rocket.Chat Room Model
Rocket.Chat uses `Room` as the conversation entity, with `t` (type) field: `d` (direct), `p` (private group), `c` (channel), `l` (livechat).
