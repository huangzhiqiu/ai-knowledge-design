# WebSocket Coding Guidelines

> WebSocket 开发规范，涵盖协议使用、安全、连接管理、消息设计、性能优化与错误处理。

---

## 1. 协议规范

### 1.1 握手规范

```http
GET /ws HTTP/1.1
Host: example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
Origin: https://example.com
```

**规范要求：**
- [Mandatory] 必须校验 `Origin` 头，防止跨站 WebSocket 劫持（CSWSH）
- [Mandatory] 必须使用 `Sec-WebSocket-Version: 13`（RFC 6455 标准版本）
- [Mandatory] 握手必须在 HTTPS/WSS 下进行（生产环境）
- [Recommended] 握手时携带认证 Token（Query 参数或 Cookie）
- [Recommended] 支持子协议协商（`Sec-WebSocket-Protocol`）

### 1.2 子协议设计

```
Sec-WebSocket-Protocol: cbol-im-v1, cbol-im-v2
```

- 子协议用于版本协商，避免硬编码消息格式
- 服务端选择支持的最高版本返回
- 不支持的版本应在握手阶段拒绝（返回 400）

### 1.3 关闭码规范

| 关闭码 | 含义 | 使用场景 |
|--------|------|---------|
| 1000 | Normal Closure | 正常断开，客户端主动退出 |
| 1001 | Going Away | 服务端关闭/重启 |
| 1002 | Protocol Error | 协议格式错误 |
| 1003 | Unsupported Data | 收到不支持的数据类型 |
| 1007 | Invalid Payload | 数据内容无效（如非 UTF-8） |
| 1008 | Policy Violation | 违反安全策略 |
| 1009 | Message Too Big | 消息超过大小限制 |
| 1010 | Mandatory Extension | 缺少必要扩展 |
| 1011 | Internal Error | 服务端内部错误 |
| 4000-4999 | Private Use | 自定义业务关闭码 |

**自定义业务关闭码示例：**
| 关闭码 | 含义 |
|--------|------|
| 4001 | Token 过期 |
| 4002 | 重复登录（被踢下线） |
| 4003 | 账号被禁用 |
| 4004 | 服务端维护中 |
| 4005 | 频率限制 |

---

## 2. 安全规范

### 2.1 认证与授权

```java
// 握手阶段认证
public class WebSocketAuthHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest req) {
            String token = extractToken(req);  // from query or header
            if (!authService.validate(token)) {
                sendHttpResponse(ctx, req, 
                    new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                ctx.close();
                return;
            }
            ctx.fireChannelRead(msg);
        }
    }
}
```

**规范要求：**
- [Mandatory] 握手阶段必须完成认证，未认证连接不得进入业务处理
- [Mandatory] Token 应设置过期时间，过期后服务端主动关闭（4001）
- [Mandatory] 敏感操作（如发送消息）需在业务层二次校验权限
- [Recommended] 使用短期 Token + Refresh 机制，避免 Token 泄露后长期有效
- [Recommended] 支持 Token 撤销（黑名单机制）

### 2.2 防攻击措施

| 攻击类型 | 防御措施 |
|---------|---------|
| **CSWSH（跨站劫持）** | 校验 Origin 头 + CSRF Token |
| **DoS（连接耗尽）** | 单 IP 连接数限制 + 全局连接数上限 |
| **消息洪水** | 单连接消息频率限制（如 100条/秒） |
| **大消息攻击** | 单条消息大小限制（如 64KB）+ 帧大小限制 |
| **Slowloris** | 握手超时（如 10秒）+ 读空闲超时 |
| **内存耗尽** | 出站消息队列大小限制 + 背压机制 |

```java
// 连接数限制示例
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {
    private static final AtomicInteger GLOBAL_COUNT = new AtomicInteger(0);
    private static final int MAX_CONNECTIONS = 100_000;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (GLOBAL_COUNT.incrementAndGet() > MAX_CONNECTIONS) {
            GLOBAL_COUNT.decrementAndGet();
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }
}
```

### 2.3 数据安全

- [Mandatory] 生产环境必须使用 WSS（WebSocket over TLS）
- [Mandatory] 消息体中的敏感字段（如手机号、邮箱）应脱敏或加密
- [Mandatory] 禁止在 WebSocket 消息中传输密码、Token 等凭证
- [Recommended] 端到端加密（E2EE）用于高安全场景（参考 Matrix Olm/Megolm）
- [Recommended] 消息签名防止篡改

---

## 3. 连接管理规范

### 3.1 连接生命周期

```
┌─────────┐   握手成功   ┌──────────┐   心跳正常   ┌─────────┐
│ CONNECT │ ──────────> │  ACTIVE  │ ──────────> │ ACTIVE  │
└─────────┘             └──────────┘              └─────────┘
     │                      │
     │ 握手失败             │ 心跳超时/异常
     ▼                      ▼
┌─────────┐             ┌──────────┐
│ CLOSED  │             │ RECONNECT│
└─────────┘             └──────────┘
                            │
                            │ 指数退避重连
                            ▼
                         ┌─────────┐
                         │ CONNECT │
                         └─────────┘
```

### 3.2 心跳机制

**规范要求：**
- [Mandatory] 必须实现心跳机制，检测死连接
- [Mandatory] 使用 WebSocket Ping/Pong 帧（opcode 0x9/0xA），而非业务消息
- [Mandatory] 心跳间隔应小于网络中间设备（NAT/负载均衡）的超时时间
- [Recommended] 服务端 60秒 未收到任何消息则发送 Ping
- [Recommended] 客户端 30秒 无活动则发送 Ping，连续 3 次未收到 Pong 则断开重连
- [Recommended] 心跳间隔可动态调整（弱网下加长，节省流量）

```java
// Netty 心跳配置
ChannelPipeline pipeline = ch.pipeline();
pipeline.addLast(new IdleStateHandler(60, 0, 0));  // 读空闲60秒
pipeline.addLast(new HeartbeatHandler());

public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.READER_IDLE) {
            ctx.writeAndFlush(new PingWebSocketFrame());
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
```

### 3.3 重连机制

**规范要求：**
- [Mandatory] 客户端必须实现自动重连
- [Mandatory] 使用指数退避（Exponential Backoff），避免重连风暴
- [Mandatory] 重连时必须恢复会话状态（同步离线消息、拉取增量）
- [Recommended] 最大重连间隔不超过 30秒，避免长时间离线
- [Recommended] 网络状态变化时（WiFi→4G）主动触发重连
- [Recommended] 重连成功后发送 `resume` 指令，携带最后收到的 seq_id

```
重连退避策略：
第1次：1s
第2次：2s
第3次：4s
第4次：8s
第5次：16s
第6次+：30s（上限）
连接成功后重置计数器
```

### 3.4 多设备连接管理

- [Mandatory] 同一账号允许多设备同时在线（手机、PC、Web）
- [Mandatory] 每台设备分配唯一 `device_id`，消息按设备维度投递
- [Recommended] 支持"互踢"策略配置（同类型设备互踢，如 PC 端只能一个在线）
- [Recommended] 在线状态变更时广播给相关用户（Presence）
- [Recommended] 单账号最大连接数限制（如 10 台设备）

---

## 4. 消息设计规范

### 4.1 消息格式

**推荐使用 JSON + 类型字段：**
```json
{
  "type": "message.send",
  "msg_id": "snowflake-123456",
  "timestamp": 1723987200000,
  "payload": {
    "conversation_id": "conv_001",
    "content": "hello",
    "content_type": "text"
  }
}
```

**规范要求：**
- [Mandatory] 每条消息必须有唯一 `msg_id`（客户端生成，用于幂等）
- [Mandatory] 必须有 `type` 字段区分消息类型
- [Mandatory] 必须有 `timestamp`（服务端时间，客户端时钟不可信）
- [Recommended] 高性能场景使用 Protobuf 替代 JSON
- [Recommended] 消息类型使用枚举，禁止自由字符串

### 4.2 消息大小限制

| 场景 | 限制 | 说明 |
|------|------|------|
| 单条文本消息 | ≤ 4KB | 普通聊天文本 |
| 单条富媒体消息 | ≤ 64KB | 含图片URL、卡片等 |
| 单条二进制消息 | ≤ 1MB | 文件分片传输 |
| 单帧大小 | ≤ 16KB | WebSocket 帧，超过需分片 |

- [Mandatory] 超过限制的消息应拒绝并返回错误（关闭码 1009）
- [Recommended] 大文件使用 HTTP 上传，WebSocket 只传 URL 和通知

### 4.3 消息分片

```
客户端发送大消息 → 分片为多个 WebSocket 帧
  - 第一帧：FIN=0, opcode=text/binary
  - 中间帧：FIN=0, opcode=continuation
  - 最后帧：FIN=1, opcode=continuation
```

- [Mandatory] 服务端必须支持分片消息的重组
- [Recommended] 单条消息总大小设置上限，防止内存攻击
- [Recommended] 分片重组超时（如 30秒），超时丢弃并关闭连接

---

## 5. 性能优化规范

### 5.1 线程模型

```java
// Netty 推荐配置
EventLoopGroup bossGroup = new NioEventLoopGroup(1);           // 接受连接
EventLoopGroup workerGroup = new NioEventLoopGroup(            // IO处理
    Runtime.getRuntime().availableProcessors() * 2
);
```

**规范要求：**
- [Mandatory] Boss 线程组只负责接受连接，1个线程足够
- [Mandatory] Worker 线程组负责 IO 读写，线程数 = CPU核心数 * 2
- [Mandatory] 业务逻辑必须异步处理，不得阻塞 Worker 线程
- [Recommended] 业务处理使用独立线程池，按会话维度串行化
- [Recommended] 单连接绑定固定 Worker 线程，避免线程切换

### 5.2 内存管理

- [Mandatory] 使用 Netty `PooledByteBufAllocator`，减少 GC
- [Mandatory] 及时释放 ByteBuf（ReferenceCounted）
- [Mandatory] 出站消息队列设置上限，防止 OOM
- [Recommended] 使用零拷贝（`DefaultFileRegion`）传输文件
- [Recommended] 大消息使用流式处理，不全部加载到内存

```java
// 出站队列背压
Channel channel = ...;
if (channel.isWritable()) {
    channel.writeAndFlush(message);
} else {
    // 队列已满，丢弃低优先级消息或等待
    pendingQueue.offer(message, 5, TimeUnit.SECONDS);
}
```

### 5.3 压缩

- [Recommended] 启用 `permessage-deflate` 扩展（RFC 7692），文本消息压缩率可达 70%+
- [Recommended] 压缩级别设为 1-3（速度优先，IM 消息短小）
- [Mandatory] 二进制消息（图片、文件）不压缩（已压缩格式）
- [Mandatory] 压缩必须在握手阶段协商，双方都支持才启用

### 5.4 广播优化

- [Mandatory] 群消息使用扇出（Fanout）模式，避免循环发送
- [Recommended] 在线用户直接推送，离线用户写入离线队列
- [Recommended] 大群（>1000人）使用读扩散而非写扩散
- [Recommended] 广播时批量 flush，减少系统调用

---

## 6. 错误处理规范

### 6.1 错误响应格式

```json
{
  "type": "error",
  "code": 2001,
  "message": "Message content too long",
  "detail": {
    "max_length": 4096,
    "actual_length": 8192
  },
  "ref_msg_id": "snowflake-123456"
}
```

**规范要求：**
- [Mandatory] 业务错误必须返回结构化错误响应，不得直接关闭连接
- [Mandatory] 错误码分段：1xxx 协议错误，2xxx 业务错误，3xxx 系统错误
- [Mandatory] 错误响应必须携带 `ref_msg_id`，关联触发错误的请求
- [Recommended] 错误消息对用户友好，不暴露内部细节（堆栈、SQL）

### 6.2 异常场景处理

| 场景 | 处理方式 |
|------|---------|
| 消息格式错误 | 返回错误响应，不关闭连接 |
| 未认证操作 | 返回 401 错误，关闭连接 |
| 频率超限 | 返回 429 错误，提示重试时间 |
| 服务端内部错误 | 返回 500 错误，记录日志，不关闭连接 |
| 消息投递失败 | 重试 3 次，仍失败则存入死信队列 |
| 连接异常断开 | 标记用户离线，保留会话，等待重连 |

### 6.3 日志规范

```java
// 连接日志
log.info("WebSocket connected: userId={}, device={}, channel={}", 
    userId, deviceId, channel.id().asShortText());

// 消息日志（不记录消息内容）
log.debug("Message received: type={}, msgId={}, size={}bytes", 
    msg.getType(), msg.getMsgId(), msg.getSize());

// 错误日志
log.error("WebSocket error: userId={}, error={}", userId, e.getMessage(), e);
```

- [Mandatory] 连接建立/断开必须记录 INFO 日志（含 userId、deviceId）
- [Mandatory] 错误必须记录 ERROR 日志（含异常堆栈）
- [Mandatory] 禁止在日志中记录消息内容（隐私合规）
- [Recommended] 消息收发记录 DEBUG 日志（仅类型、ID、大小）
- [Recommended] 全链路追踪：每条消息携带 traceId，通过 MDC 传递

---

## 7. 测试规范

### 7.1 单元测试

- [Mandatory] 消息编解码器 100% 覆盖率
- [Mandatory] 业务处理器核心逻辑 ≥ 90% 覆盖率
- [Mandatory] 状态机转换全覆盖（正常+异常路径）

### 7.2 集成测试

- [Mandatory] 连接建立/断开/重连流程测试
- [Mandatory] 消息收发端到端测试
- [Mandatory] 心跳超时测试
- [Mandatory] 认证失败/Token过期测试

### 7.3 性能测试

| 指标 | 目标 |
|------|------|
| 单节点并发连接 | ≥ 10万 |
| 单节点消息吞吐量 | ≥ 10万条/秒 |
| 消息端到端延迟 | P99 ≤ 100ms |
| 连接建立耗时 | P99 ≤ 50ms |
| 内存占用（每连接） | ≤ 10KB |

**测试工具：**
- [wscat](https://github.com/websockets/wscat) — 手动调试
- [Artillery](https://artillery.io) — 负载测试
- [k6](https://k6.io) — 性能测试（支持 WebSocket）
- [tcpkali](https://github.com/machinezone/tcpkali) — 高并发连接测试

### 7.4 安全测试

- [Mandatory] Origin 校验绕过测试
- [Mandatory] 未认证连接测试
- [Mandatory] 大消息/洪水攻击测试
- [Mandatory] 慢连接（Slowloris）测试
- [Recommended] 使用 OWASP ZAP 进行 WebSocket 安全扫描

---

## 8. Java/Netty 实现规范

### 8.1 Channel Pipeline 顺序

```java
pipeline.addLast("http-codec", new HttpServerCodec());
pipeline.addLast("http-aggregator", new HttpObjectAggregator(65536));
pipeline.addLast("auth", new WebSocketAuthHandler());
pipeline.addLast("ws-handshake", new WebSocketServerProtocolHandler("/ws", "cbol-im-v1"));
pipeline.addLast("idle", new IdleStateHandler(60, 0, 0));
pipeline.addLast("heartbeat", new HeartbeatHandler());
pipeline.addLast("codec", new MessageCodec());
pipeline.addLast("business", new BusinessHandler(businessExecutor));
```

**顺序原则：**
1. HTTP 编解码 → 聚合
2. 认证（握手前）
3. WebSocket 协议处理
4. 空闲检测 → 心跳
5. 消息编解码
6. 业务处理（异步线程池）

### 8.2 关键实现要点

```java
// 业务Handler - 必须异步，不阻塞Worker线程
public class BusinessHandler extends SimpleChannelInboundHandler<Message> {
    private final ExecutorService businessExecutor;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        businessExecutor.execute(() -> {
            try {
                Message response = messageDispatcher.dispatch(ctx, msg);
                if (response != null) {
                    ctx.writeAndFlush(response);
                }
            } catch (Exception e) {
                log.error("Business error", e);
                ctx.writeAndFlush(ErrorResponse.from(e, msg));
            }
        });
    }
}
```

- [Mandatory] `SimpleChannelInboundHandler` 自动释放 ByteBuf
- [Mandatory] 业务异常必须捕获，不得抛到 Pipeline 外层（会导致连接关闭）
- [Mandatory] `channelInactive` 中清理资源（SessionRegistry、订阅关系）
- [Recommended] 使用 `ChannelGroup` 管理所有连接，方便广播和批量关闭

---

## 9. 参考资料

- RFC 6455 — The WebSocket Protocol: https://tools.ietf.org/html/rfc6455
- RFC 7692 — Compression Extensions: https://tools.ietf.org/html/rfc7692
- Netty WebSocket Documentation: https://netty.io/wiki/websocket.html
- OWASP WebSocket Security: https://owasp.org/www-community/attacks/Web_Socket_Attacks
- HTML5 WebSocket API: https://developer.mozilla.org/en-US/docs/Web/API/WebSocket

---

*最后更新：2026-08-18*
