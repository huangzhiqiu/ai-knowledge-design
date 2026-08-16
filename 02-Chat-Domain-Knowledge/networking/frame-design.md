# IM Frame Design

> Application-level message frame format on top of WebSocket.

## Why Application-Level Framing?

WebSocket only provides transport framing (text/binary/ping/pong). IM needs:
- Message type discrimination (chat, read receipt, typing, etc.)
- Request/response correlation
- Acknowledgment and flow control
- Version negotiation
- Error handling

## Frame Envelope (JSON Format)

```json
{
  "id": "msg_01HXYZ123",
  "type": "chat.message",
  "version": 1,
  "timestamp": 1718438400000,
  "payload": { },
  "ack": false,
  "replyTo": null
}
```

### Frame Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | string | Yes | Unique frame ID (for ack/correlation) |
| type | string | Yes | Message type (see below) |
| version | int | Yes | Protocol version |
| timestamp | int64 | Yes | Client or server timestamp (ms) |
| payload | object | Yes | Type-specific content |
| ack | bool | No | Whether server should ACK this frame |
| replyTo | string | No | ID of frame this responds to |

## Message Type Taxonomy

### Client -> Server

| Type | Description | Ack Required |
|------|-------------|-------------|
| `auth.login` | Authenticate after connect | Yes |
| `sync.request` | Request missed messages | Yes |
| `chat.message` | Send a chat message | Yes |
| `chat.read` | Mark messages as read | No |
| `chat.typing` | Typing indicator | No |
| `chat.recall` | Recall a message | Yes |
| `presence.update` | Update presence status | No |
| `ping` | Heartbeat | No (pong instead) |

### Server -> Client

| Type | Description |
|------|-------------|
| `auth.success` | Authentication successful |
| `auth.failed` | Authentication failed |
| `sync.response` | Missed messages batch |
| `chat.message` | Incoming chat message |
| `chat.delivery` | Delivery receipt |
| `chat.read` | Read receipt from others |
| `chat.typing` | Typing indicator from others |
| `chat.recall` | Message recalled |
| `presence.update` | Contact presence changed |
| `error` | Error response |
| `pong` | Heartbeat response |
| `kicked` | Kicked by another device |

## Payload Examples

### chat.message (Client -> Server)
```json
{
  "id": "msg_01HXYZ",
  "type": "chat.message",
  "version": 1,
  "timestamp": 1718438400000,
  "ack": true,
  "payload": {
    "conversationId": "conv_abc",
    "msgId": "uuid-client-generated",
    "type": "text",
    "content": { "text": "Hello" },
    "replyTo": null
  }
}
```

### chat.message (Server -> Client)
```json
{
  "id": "srv_01HXYZ",
  "type": "chat.message",
  "version": 1,
  "timestamp": 1718438400001,
  "payload": {
    "conversationId": "conv_abc",
    "msgId": "uuid-client-generated",
    "seqId": 12345,
    "senderId": "user_123",
    "type": "text",
    "content": { "text": "Hello" },
    "timestamp": 1718438400000
  }
}
```

### sync.request
```json
{
  "id": "sync_001",
  "type": "sync.request",
  "version": 1,
  "timestamp": 1718438400000,
  "ack": true,
  "payload": {
    "deviceId": "dev_abc",
    "cursors": [
      { "conversationId": "conv_1", "lastSeq": 120 },
      { "conversationId": "conv_2", "lastSeq": 45 }
    ],
    "fullSync": false
  }
}
```

### sync.response
```json
{
  "id": "srv_sync_001",
  "type": "sync.response",
  "version": 1,
  "timestamp": 1718438400002,
  "replyTo": "sync_001",
  "payload": {
    "conversations": [
      {
        "conversationId": "conv_1",
        "messages": [ /* messages seq 121-130 */ ],
        "newMaxSeq": 130
      }
    ],
    "hasMore": false
  }
}
```

### error
```json
{
  "id": "err_001",
  "type": "error",
  "version": 1,
  "timestamp": 1718438400000,
  "replyTo": "msg_01HXYZ",
  "payload": {
    "code": 4001,
    "message": "Message too large",
    "details": { "maxSize": 65536 }
  }
}
```

## Acknowledgment Mechanism

### Flow
```
Client -> Server: chat.message (ack=true, id=msg_001)
Server -> Client: ack (replyTo=msg_001, success=true, seqId=12345)
```

### Ack Frame
```json
{
  "id": "ack_001",
  "type": "ack",
  "version": 1,
  "timestamp": 1718438400001,
  "replyTo": "msg_001",
  "payload": {
    "success": true,
    "seqId": 12345,
    "serverMsgId": "srv_generated_id"
  }
}
```

### Ack Timeout & Retry
- Client waits 5s for ack
- If no ack: retry with same msgId (idempotent)
- Max 3 retries, then mark failed
- Server dedups by msgId

## Binary Frame Format (Protobuf)

For high-throughput scenarios, use binary frames:

```protobuf
message ImFrame {
    string id = 1;
    string type = 2;
    int32 version = 3;
    int64 timestamp = 4;
    bytes payload = 5;  // serialized type-specific message
    bool ack = 6;
    string reply_to = 7;
}
```

- Payload is serialized type-specific Protobuf message
- 30-50% smaller than JSON
- Faster serialization/deserialization
- Use WebSocket binary frames (opcode 0x2)

## Frame Size Limits

| Frame Type | Max Size | Reason |
|-----------|----------|--------|
| chat.message (text) | 64KB | Reasonable text limit |
| chat.message (file) | N/A | Use HTTP upload, send URL in message |
| sync.response | 1MB | Batch of messages |
| typing | 1KB | Tiny |
| ping/pong | 128B | Tiny |

If frame exceeds limit:
- Server sends error (code 1009 = message too big)
- Client should split or use HTTP

## Flow Control

### Server -> Client Backpressure
- Server tracks client's unacknowledged frames
- If > 100 unacked frames: pause sending
- Resume when client acks
- Prevents slow client from being overwhelmed

### Client -> Server Rate Limiting
- Per-user message rate limit: e.g., 10 messages/sec
- Exceed -> error (code 429) + Retry-After
- Prevents spam and abuse

## Reference: Matrix Transport
Matrix uses JSON over WebSocket with `event` type field. Events have `event_id`, `type`, `sender`, `content`, `origin_server_ts`. Similar envelope pattern.
