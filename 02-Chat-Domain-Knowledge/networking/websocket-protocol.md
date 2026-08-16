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
