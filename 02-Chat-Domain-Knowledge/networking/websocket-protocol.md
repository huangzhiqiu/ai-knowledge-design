# WebSocket Protocol Design

> WebSocket protocol layer design for IM systems.

## Protocol Stack

```
┌─────────────────────────────────┐
│  Application Message (JSON/Protobuf) │
├─────────────────────────────────┤
│  IM Frame (type, seq, ack)      │
├─────────────────────────────────┤
│  WebSocket Frame (RFC 6455)     │
├─────────────────────────────────┤
│  TCP / TLS                      │
└─────────────────────────────────┘
```

## WebSocket Handshake

### Client -> Server (Upgrade Request)
```http
GET /ws?token=jwt_token HTTP/1.1
Host: im.cbol.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
Sec-WebSocket-Protocol: im.json.v1, im.protobuf.v1
Origin: https://app.cbol.com
```

### Server -> Client (Upgrade Response)
```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
Sec-WebSocket-Protocol: im.json.v1
```

### Authentication Strategies

| Strategy | Location | Pros | Cons |
|----------|----------|------|------|
| Query param | `?token=xxx` | Simple | Token in URL logs |
| Header | `Authorization` | Clean | Not all clients support custom headers in WS handshake |
| First message | Auth frame after connect | Flexible | Extra round trip |
| Cookie | HttpOnly cookie | Secure | Same-origin only |

**Recommended**: Query param for initial connect + first message auth for re-auth.

## WebSocket Frame Format (RFC 6455)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+
```

### Opcode Types

| Opcode | Meaning | Direction |
|--------|---------|-----------|
| 0x0 | Continuation frame | Both |
| 0x1 | Text frame (UTF-8) | Both |
| 0x2 | Binary frame | Both |
| 0x8 | Connection close | Both |
| 0x9 | Ping | Both |
| 0xA | Pong | Both |

## Subprotocol Negotiation

Client offers multiple subprotocols, server picks one:

```
Sec-WebSocket-Protocol: im.json.v1, im.protobuf.v1
```

Server responds with selected:
```
Sec-WebSocket-Protocol: im.protobuf.v1
```

### Subprotocol Versions

| Subprotocol | Format | Use Case |
|-------------|--------|----------|
| `im.json.v1` | JSON text frames | Web client, debugging |
| `im.protobuf.v1` | Protobuf binary frames | Mobile, high throughput |
| `im.raw.v1` | Custom binary | Native clients |

## Ping/Pong Heartbeat

WebSocket protocol has built-in ping/pong:

- Server sends `Ping` frame periodically
- Client must respond with `Pong` (same payload)
- If no pong within timeout -> close connection

**Alternative**: Application-level ping/pong in text/binary frames (more control, can carry metadata).

## Connection Close

### Close Frame Format
- Opcode 0x8
- Payload: 2-byte status code + optional reason (UTF-8)

### Status Codes

| Code | Meaning | When to Use |
|------|---------|-------------|
| 1000 | Normal closure | Client logs out |
| 1001 | Going away | Server shutdown, client navigates away |
| 1002 | Protocol error | Malformed frame |
| 1003 | Unsupported data | Received binary when only text supported |
| 1007 | Invalid payload | Invalid UTF-8 |
| 1008 | Policy violation | Auth failed, banned |
| 1009 | Message too big | Exceeds max frame size |
| 1011 | Internal error | Server exception |
| 4000-4999 | Custom | App-specific (e.g., 4001 = token expired, 4002 = kicked) |

### Custom Close Codes (IM)

| Code | Meaning |
|------|---------|
| 4001 | Authentication failed |
| 4002 | Token expired |
| 4003 | Kicked by another login |
| 4004 | Server maintenance |
| 4005 | Too many connections |
| 4006 | Rate limited |

## Max Frame Size

- Default WebSocket max frame: configurable
- IM recommendation: 64KB for text, 1MB for binary
- Large messages (files, images) should use HTTP upload, not WebSocket
- Fragmentation: use continuation frames for large messages

## Compression (permessage-deflate)

WebSocket extension for per-message compression:

```
Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits
```

- Reduces bandwidth for text messages
- CPU overhead for compression/decompression
- Recommended for text-heavy IM, optional for binary

## Reference: RFC 6455
The WebSocket Protocol standard. Key sections: handshake (Section 4), framing (Section 5), closing (Section 7), ping/pong (Section 5.5).

---

## Open Source Project Protocol Designs

### Turms: TCP + WebSocket Dual Protocol + Custom Binary Encoding

Turms supports both TCP and WebSocket, with **Protobuf** for client communication and **custom binary RPC** for inter-service communication.

**TCP protocol frame format:**
```
┌──────────────┬──────────────────────┐
│  Length      │  Protobuf Payload     │
│  (ZigZag)    │  (serialized message) │
└──────────────┴──────────────────────┘
```
- Length uses **ZigZag variable-length encoding**, small messages take only 1-2 bytes
- Payload is Protobuf-serialized business message
- More compact than WebSocket frame header (WebSocket frame header is 2-14 bytes)

**WebSocket protocol:**
- Uses WebSocket **binary frames** (opcode 0x2)
- Payload is also Protobuf
- Browser clients use WebSocket, native clients use TCP

**Heartbeat mechanism:**
- TCP: send single byte `[0]` as heartbeat (extremely lightweight)
- WebSocket: use standard Ping/Pong frames
- Disconnect on heartbeat timeout

**Inter-service RPC:**
- Gateway ↔ Service uses custom binary protocol (not Protobuf)
- Extremely optimized, zero redundant fields
- Reactive async calls based on Netty

### Mattermost: JSON over WebSocket + Event Scoping

Mattermost uses **JSON text frames** over WebSocket:

**Event format:**
```json
{
  "event": "posted",
  "data": { "channel_id": "...", "post": {...} },
  "broadcast": { "user_id": "...", "channel_id": "..." }
}
```

**Event types:** `posted`, `typing`, `user_updated`, `channel_updated`, `status_change`

**Key optimization: Event scoping (permanently enabled since v11)**
- `typing` and `reaction` events are only broadcast to clients that have the corresponding channel/thread open
- Control event distribution scope via `broadcast` field
- Reduce unnecessary traffic in large groups

**Reconnection mechanism:**
- Client auto-reconnects with exponential backoff
- Redux middleware handles WebSocket events, auto-updates store
- After reconnect, pull messages during disconnect via REST API

### Rocket.Chat: DDP over WebSocket

Rocket.Chat uses Meteor's **DDP (Distributed Data Protocol)** over WebSocket:

**DDP message types:**
```
Client -> Server:
  connect     - establish DDP session
  subscribe   - subscribe to data collection (e.g., stream-room-messages)
  method      - call server method (e.g., sendMessage)

Server -> Client:
  connected   - session established confirmation
  added       - new data
  changed     - data changed
  removed     - data deleted
  result      - method call result
  ready       - subscription data ready
```

**DDP advantages:**
- Auto incremental sync: server maintains client's subscribed data view, auto-pushes added/changed/removed on changes
- Client doesn't need to manually manage incremental logic
- Naturally integrates with MongoDB OpLog: database changes auto-trigger DDP pushes

**DDP disadvantages:**
- Protocol is not a universal standard, high learning curve
- Server needs to maintain subscription state, complex horizontal scaling
- DDPStreamer service manages all DDP connections

### Matrix: No WebSocket, Uses HTTP Long Polling

Matrix explicitly **does not use WebSocket**, instead uses `/sync` HTTP long polling:

```
GET /_matrix/client/v3/sync?since={token}&timeout=30000
```

**Reasons for choosing long polling:**
1. In federated architecture, cross-server push is complex, pull model is simpler
2. Stateless server, easy horizontal scaling
3. Better compatibility, penetrates all firewalls/proxies
4. No need to maintain persistent connections, suitable for large-scale deployment

**Trade-off:**
- Each poll has TCP/TLS handshake overhead (HTTP/2 can mitigate)
- Slightly higher latency than WebSocket

### Chat21: MQTT over WebSocket/TCP

Chat21 uses the **MQTT protocol** rather than native WebSocket:

- Clients connect to RabbitMQ via MQTT over WebSocket (browser) or MQTT over TCP (native)
- Messages routed via MQTT topics, not custom protocol
- Uses RabbitMQ JWT plugin for topic-level permission control
- MQTT QoS 1 guarantees at-least-once delivery

### Protocol Selection Comparison

| Project | Transport Protocol | Serialization | Heartbeat | Real-time Mechanism |
|---------|-------------------|--------------|-----------|-------------------|
| Turms | TCP + WebSocket | Protobuf | TCP: single byte[0], WS: Ping/Pong | Fully async push |
| Mattermost | WebSocket | JSON | App-level ping/pong | WebSocket event push |
| Rocket.Chat | WebSocket (DDP) | JSON/EJSON | DDP built-in | DDP subscription + OpLog |
| Matrix | HTTP long polling | JSON | None (timeout mechanism) | /sync incremental return |
| Chat21 | MQTT (WS/TCP) | JSON | MQTT PINGREQ | MQTT topic push |
| OpenChat | HTTP polling | Candid/JSON | None | Client polls canister |

### CBOL Project Protocol Recommendation

Based on CBOL's Java tech stack and conversation handoff scenarios:

1. **Recommended: WebSocket + Protobuf binary frames**
   - Reference Turms: browser uses WebSocket binary frames, native clients can use TCP
   - Protobuf is 30-50% smaller than JSON, faster serialization
   - Java has mature Netty + Protobuf ecosystem

2. **Heartbeat**
   - WebSocket standard Ping/Pong is sufficient, no need for app-level heartbeat
   - Timeout 90s (3 missed pings) to disconnect

3. **If extreme scalability is needed**
   - Consider Matrix-style stateless long polling
   - But CBOL is centralized deployment, WebSocket is more appropriate

4. **Avoid DDP**
   - DDP is tightly coupled with Meteor, no mature implementation in Java ecosystem
   - Custom JSON/Protobuf protocol is more controllable
