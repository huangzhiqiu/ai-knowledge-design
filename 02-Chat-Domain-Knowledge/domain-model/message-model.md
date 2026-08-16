# Message Model

## Entity Definition

| Field | Type | Description |
|-------|------|-------------|
| msg_id | string | Global unique message ID (uniqueness only) |
| seq_id | int64 | Per-conversation sequence number (ordering) |
| conversation_id | string | Parent conversation |
| sender_id | string | Sender user_id |
| type | enum | text / image / file / voice / video / system |
| content | json / string | Message payload |
| timestamp | int64 | Server-accepted time (ms) |
| status | enum | sending / sent / delivered / read / failed |
| reply_to | string | Optional replied message ID |
| edited_at | timestamp | Last edit time (null if never edited) |
| deleted_at | timestamp | Soft delete time |

## Key Design: msg_id vs seq_id Separation

> Inspired by WeChat's architecture: separate uniqueness from ordering.

- **msg_id**: Globally unique, no ordering guarantee. Can be UUID or Snowflake.
- **seq_id**: Monotonically increasing **per conversation**, guarantees message order.

This separation allows:
- msg_id generated client-side (optimistic UI)
- seq_id assigned server-side (global order within conversation)
- Efficient range queries by seq_id

## Message Content Structure

```json
{
  "msg_id": "uuid-or-snowflake",
  "seq_id": 12345,
  "conversation_id": "conv_xxx",
  "sender_id": "user_xxx",
  "type": "text",
  "content": {
    "text": "Hello world",
    "mentions": ["user_1", "user_2"],
    "reply_to": "msg_prev"
  },
  "timestamp": 1718438400000
}
```

## Message Status State Machine

```
[sending] --ack--> [sent] --deliver--> [delivered] --read--> [read]
    |                  |                    |
    +--fail--> [failed] <--retry--+         |
                    |                        |
                    +--resend--> [sending] <-+
```

## Reference: Matrix Event Model
Matrix represents everything as signed `Event` objects in a DAG. Each event references parent events, enabling decentralized state resolution without a central sequencer.
