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

### Turms: TCP + WebSocket 双协议 + 自定义二进制编码

Turms 同时支持 TCP 和 WebSocket，客户端通信使用 **Protobuf**，服务间使用**自定义二进制 RPC**。

**TCP 协议帧格式：**
```
┌──────────────┬──────────────────────┐
│  Length      │  Protobuf Payload     │
│  (ZigZag)    │  (序列化后的消息)      │
└──────────────┴──────────────────────┘
```
- Length 使用 **ZigZag 变长编码**，小消息只占 1-2 字节
- Payload 是 Protobuf 序列化的业务消息
- 比 WebSocket 帧头更紧凑（WebSocket 帧头 2-14 字节）

**WebSocket 协议：**
- 使用 WebSocket **二进制帧**（opcode 0x2）
- Payload 同样是 Protobuf
- 浏览器客户端使用 WebSocket，原生客户端使用 TCP

**心跳机制：**
- TCP：发送单字节 `[0]` 作为心跳（极致轻量）
- WebSocket：使用标准 Ping/Pong 帧
- 心跳超时后断开连接

**服务间 RPC：**
- Gateway ↔ Service 使用自定义二进制协议（非 Protobuf）
- 极致优化，零冗余字段
- 基于 Netty 的 Reactive 异步调用

### Mattermost: JSON over WebSocket + 事件作用域

Mattermost 使用 **JSON 文本帧** over WebSocket：

**事件格式：**
```json
{
  "event": "posted",
  "data": { "channel_id": "...", "post": {...} },
  "broadcast": { "user_id": "...", "channel_id": "..." }
}
```

**事件类型：** `posted`, `typing`, `user_updated`, `channel_updated`, `status_change`

**关键优化：事件作用域（v11 永久启用）**
- `typing` 和 `reaction` 事件只广播给**打开了对应频道/线程**的客户端
- 通过 `broadcast` 字段控制事件分发范围
- 减少大群的不必要流量

**重连机制：**
- 客户端自动重连，指数退避
- Redux middleware 处理 WebSocket 事件，自动更新 store
- 重连后通过 REST API 拉取断开期间的消息

### Rocket.Chat: DDP over WebSocket

Rocket.Chat 使用 Meteor 的 **DDP（Distributed Data Protocol）** over WebSocket：

**DDP 消息类型：**
```
客户端 → 服务端:
  connect     - 建立 DDP 会话
  subscribe   - 订阅数据集合 (如 stream-room-messages)
  method      - 调用服务端方法 (如 sendMessage)

服务端 → 客户端:
  connected   - 会话建立确认
  added       - 新增数据
  changed     - 数据变更
  removed     - 数据删除
  result      - method 调用结果
  ready       - 订阅数据就绪
```

**DDP 的优势：**
- 自动增量同步：服务端维护客户端订阅的数据视图，变更时自动推送 added/changed/removed
- 客户端不需要手动管理增量逻辑
- 与 MongoDB OpLog 天然配合：数据库变更自动触发 DDP 推送

**DDP 的劣势：**
- 协议非通用标准，学习成本高
- 服务端需维护订阅状态，水平扩展复杂
- DDPStreamer 服务负责管理所有 DDP 连接

### Matrix: 不用 WebSocket，用 HTTP 长轮询

Matrix 明确**不使用 WebSocket**，而是用 `/sync` HTTP 长轮询：

```
GET /_matrix/client/v3/sync?since={token}&timeout=30000
```

**选择长轮询的原因：**
1. 联邦架构下，跨服务器推送复杂，拉模式更简单
2. 服务端无状态，水平扩展容易
3. 兼容性好，穿透所有防火墙/代理
4. 不需要维护持久连接，适合大规模部署

**代价：**
- 每次轮询有 TCP/TLS 握手开销（HTTP/2 可缓解）
- 延迟比 WebSocket 略高

### Chat21: MQTT over WebSocket/TCP

Chat21 使用 **MQTT 协议**而非原生 WebSocket：

- 客户端通过 MQTT over WebSocket（浏览器）或 MQTT over TCP（原生）连接 RabbitMQ
- 消息通过 MQTT 主题路由，而非自定义协议
- 利用 RabbitMQ 的 JWT 插件实现主题级权限控制
- MQTT QoS 1 保证消息至少送达一次

### 协议选择对比

| 项目 | 传输协议 | 序列化 | 心跳 | 实时机制 |
|------|---------|--------|------|---------|
| Turms | TCP + WebSocket | Protobuf | TCP: 单字节[0], WS: Ping/Pong | 全异步推送 |
| Mattermost | WebSocket | JSON | 应用级 ping/pong | WebSocket 事件推送 |
| Rocket.Chat | WebSocket (DDP) | JSON/ EJSON | DDP 内置 | DDP 订阅 + OpLog |
| Matrix | HTTP 长轮询 | JSON | 无（timeout 机制） | /sync 增量返回 |
| Chat21 | MQTT (WS/TCP) | JSON | MQTT PINGREQ | MQTT 主题推送 |
| OpenChat | HTTP 轮询 | Candid/JSON | 无 | 客户端轮询 canister |

### CBOL 项目协议建议

基于 CBOL 的 Java 技术栈和接回话场景：

1. **推荐 WebSocket + Protobuf 二进制帧**
   - 参考 Turms：浏览器用 WebSocket 二进制帧，原生客户端可用 TCP
   - Protobuf 比 JSON 小 30-50%，序列化更快
   - Java 有成熟的 Netty + Protobuf 生态

2. **心跳**
   - WebSocket 标准 Ping/Pong 即可，不需要应用级心跳
   - 超时 90s（3 次 missed ping）断开

3. **如果需要极高扩展性**
   - 考虑 Matrix 风格的无状态长轮询
   - 但 CBOL 是中心化部署，WebSocket 更合适

4. **避免 DDP**
   - DDP 与 Meteor 强绑定，Java 生态无成熟实现
   - 自定义 JSON/Protobuf 协议更可控
