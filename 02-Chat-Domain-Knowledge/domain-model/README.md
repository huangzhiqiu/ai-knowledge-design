# Domain Model Overview

> Core domain entities and their relationships in the IM system.

## Entity Relationship

```
User 1──N Device
User N──N Conversation (via Membership)
Conversation 1──N Message
User 1──N Session
Conversation N──1 Group (optional)
```

## Core Entities

| Entity | Description | Key Identifiers |
|--------|-------------|-----------------|
| [User](./user-model.md) | Account holder | user_id |
| [Device](./device-session-model.md) | Physical device bound to user | device_id |
| [Session](./device-session-model.md) | Connection session | session_id |
| [Conversation](./conversation-model.md) | Chat thread (1-on-1 or group) | conversation_id |
| [Message](./message-model.md) | Single message unit | msg_id + seq_id |
| [Group](./group-model.md) | Group conversation metadata | group_id |
| Membership | User-Conversation relationship | user_id + conversation_id |

## Design Principles
- **Separation of concerns**: Each entity has a single responsibility
- **Immutable events**: Message content is immutable; edits create new versions
- **Event sourcing**: State changes are captured as ordered events
