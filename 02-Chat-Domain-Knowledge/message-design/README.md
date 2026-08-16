# Message Design Overview

> Message identification, typing, and lifecycle design.

## Sub-topics

| Document | Description |
|----------|-------------|
| [message-id-design](./message-id-design.md) | msg_id + seq_id dual-ID strategy, Snowflake |
| [message-types](./message-types.md) | Message type taxonomy and content schemas |
| [message-states](./message-states.md) | Message lifecycle state machine |

## Design Principles
- **Uniqueness vs Ordering**: Separate global uniqueness (msg_id) from per-conversation ordering (seq_id)
- **Extensibility**: Type field allows new message types without schema migration
- **Idempotency**: msg_id enables safe retries without duplication
