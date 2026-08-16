# Message Types

## Type Taxonomy

### 1. Content Messages (user-generated)

| Type | Content Schema | Description |
|------|---------------|-------------|
| text | `{ text: string, mentions: [] }` | Plain text with @mentions |
| image | `{ url, width, height, thumbnail }` | Image message |
| file | `{ url, filename, size, mime }` | File attachment |
| voice | `{ url, duration, format }` | Voice message |
| video | `{ url, duration, thumbnail, format }` | Video message |
| location | `{ lat, lng, label, address }` | Location share |
| sticker | `{ pack_id, sticker_id }` | Sticker / emoji |
| rich_card | `{ title, desc, image, buttons: [] }` | Rich media card |
| custom | `{ type: string, payload: json }` | App-specific custom type |

### 2. System Messages (server-generated)

| Type | Trigger |
|------|---------|
| group_created | Group creation |
| member_joined | User joins group |
| member_left | User leaves group |
| member_kicked | Admin removes member |
| role_changed | Role promotion/demotion |
| group_renamed | Group name change |
| message_recalled | Message recall/delete |
| conversation_created | New conversation |

### 3. Control Messages (protocol-level)

| Type | Purpose |
|------|---------|
| typing_indicator | User is typing |
| read_receipt | Read acknowledgment |
| delivery_receipt | Delivery acknowledgment |
| presence_update | Online status change |

## Content Versioning

Message content should support versioning for forward/backward compatibility:

```json
{
  "type": "rich_card",
  "version": 2,
  "content": { ... }
}
```

- Old clients ignore unknown fields
- New clients can fall back to `version: 1` rendering
- `fallback_text` field for unsupported types

## Reference: Rocket.Chat Message Model
Rocket.Chat `Message` has `t` (type) field for system messages: `uj` (user joined), `ul` (user left), `r` (room name changed), `message_pinned`, etc. Content messages use `msg` field plus attachments array.
