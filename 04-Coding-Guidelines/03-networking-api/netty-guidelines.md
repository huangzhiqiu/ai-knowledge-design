# Netty Guidelines

> Best practices for Netty 4.1.x network programming, based on Turms and other high-performance IM systems.

## Core Concepts

### Thread Model (Reference: Turms)

```
# ✅ Turms-style: thread count = CPU cores, no locks, only CAS
# Boss group: 1 thread (accept connections)
# Worker group: CPU cores threads (handle I/O)

EventLoopGroup bossGroup = new NioEventLoopGroup(1);
EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

try {
    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
     .channel(NioServerSocketChannel.class)
     .option(ChannelOption.SO_BACKLOG, 1024)
     .childOption(ChannelOption.SO_KEEPALIVE, true)
     .childOption(ChannelOption.TCP_NODELAY, true)
     .childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         protected void initChannel(SocketChannel ch) {
             ChannelPipeline p = ch.pipeline();
             p.addLast("idleStateHandler", new IdleStateHandler(60, 30, 0));
             p.addLast("websocketDecoder", new HttpServerCodec());
             p.addLast("websocketAggregator", new HttpObjectAggregator(65536));
             p.addLast("websocketHandler", new WebSocketServerProtocolHandler("/ws"));
             p.addLast("messageHandler", messageHandler);
         }
     });

    ChannelFuture f = b.bind(port).sync();
    f.channel().closeFuture().sync();
} finally {
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
}
```

### EventLoop & Channel Relationship

```
# One EventLoop = one thread
# One Channel = one EventLoop (for its lifetime)
# Multiple Channels share one EventLoop (load balancing)

EventLoopGroup (N threads)
├── EventLoop-0 (Thread-0) ← Channel-1, Channel-5, Channel-9...
├── EventLoop-1 (Thread-1) ← Channel-2, Channel-6, Channel-10...
├── EventLoop-2 (Thread-2) ← Channel-3, Channel-7, Channel-11...
└── EventLoop-3 (Thread-3) ← Channel-4, Channel-8, Channel-12...

# ✅ Good: all I/O for a Channel happens on its EventLoop thread
# No synchronization needed for Channel state (single-threaded per Channel)
```

## ChannelPipeline Best Practices

### Handler Ordering

```java
// ✅ Good - correct handler ordering
p.addLast("idleState", new IdleStateHandler(60, 30, 0));  // 1. Idle detection
p.addLast("httpCodec", new HttpServerCodec());                // 2. Protocol codec
p.addLast("httpAggregator", new HttpObjectAggregator(65536)); // 3. Frame aggregation
p.addLast("wsProtocol", new WebSocketServerProtocolHandler("/ws")); // 4. WS handshake
p.addLast("auth", authHandler);                                 // 5. Authentication
p.addLast("business", businessHandler);                         // 6. Business logic

// ❌ Bad - wrong ordering
p.addLast("business", businessHandler);  // business before codec!
p.addLast("httpCodec", new HttpServerCodec());
```

### Sharable Handlers

```java
// ✅ Good - @Sharable for stateless handlers
@Sharable
@Component
public class MessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    // No instance fields that change per-channel
    // All state stored in Channel attributes or external store

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String userId = ctx.channel().attr(AttributeKeys.USER_ID).get();
        // process message
    }
}

// ❌ Bad - non-sharable handler created per channel (memory overhead)
public class MessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private String userId; // per-channel state in handler = not @Sharable
    // creates new instance per connection = GC pressure
}
```

### Channel Attributes for State

```java
// ✅ Good - use Channel attributes for per-channel state
public final class AttributeKeys {
    public static final AttributeKey<String> USER_ID =
        AttributeKey.valueOf("userId");
    public static final AttributeKey<String> DEVICE_TYPE =
        AttributeKey.valueOf("deviceType");
    public static final AttributeKey<Long> LAST_HEARTBEAT =
        AttributeKey.valueOf("lastHeartbeat");
}

// Set on authentication
ctx.channel().attr(AttributeKeys.USER_ID).set(userId);
ctx.channel().attr(AttributeKeys.DEVICE_TYPE).set(deviceType);

// Read in any handler
String userId = ctx.channel().attr(AttributeKeys.USER_ID).get();
```

## Concurrency & Thread Safety

### No Locks in EventLoop (Reference: Turms)

```java
// ✅ Good - all operations on EventLoop thread, no synchronization
public class SessionRegistry {
    // ConcurrentHashMap for cross-thread access (from business threads)
    private final ConcurrentHashMap<String, Channel> sessions = new ConcurrentHashMap<>();

    public void register(String userId, Channel channel) {
        sessions.put(userId, channel);
        // Channel operations must be on its EventLoop thread
        channel.eventLoop().execute(() -> {
            channel.attr(AttributeKeys.USER_ID).set(userId);
        });
    }

    public void sendMessage(String userId, String message) {
        Channel channel = sessions.get(userId);
        if (channel != null && channel.isActive()) {
            // Write from any thread - Netty handles thread safety
            channel.writeAndFlush(new TextWebSocketFrame(message));
        }
    }
}

// ❌ Bad - synchronized blocks in handler (block EventLoop thread)
public class MessageHandler extends SimpleChannelInboundHandler<String> {
    private final Object lock = new Object();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        synchronized (lock) {  // blocks EventLoop thread!
            // process message
        }
    }
}
```

### Offload Blocking Operations

```java
// ✅ Good - offload blocking operations to separate thread pool
public class MessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final EventExecutorGroup businessGroup =
        new DefaultEventExecutorGroup(16); // separate from I/O threads

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        // I/O thread: fast, non-blocking
        String userId = ctx.channel().attr(AttributeKeys.USER_ID).get();

        // Offload DB call to business thread pool
        businessGroup.execute(() -> {
            Message saved = messageRepository.save(parse(frame.text()));
            // Write back to channel (thread-safe)
            ctx.writeAndFlush(new TextWebSocketFrame(toJson(saved)));
        });
    }
}

// Register handler with business group
p.addLast(businessGroup, "messageHandler", messageHandler);
```

## Memory Management

### Reference Counted Objects

```java
// ✅ Good - release ByteBuf after use
@Override
protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
    try {
        // process msg
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);
    } finally {
        // SimpleChannelInboundHandler auto-releases, but for other handlers:
        // ReferenceCountUtil.release(msg);
    }
}

// ✅ Good - retain if passing to async thread
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof ByteBuf buf) {
        buf.retain(); // increment ref count for async use
        businessGroup.execute(() -> {
            try {
                process(buf);
            } finally {
                buf.release(); // release in async thread
            }
        });
    }
    ctx.fireChannelRead(msg); // pass to next handler (original ref)
}

// ❌ Bad - memory leak (for got to release)
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    byte[] data = new byte[buf.readableBytes()];
    buf.readBytes(data);
    // buf not released = memory leak!
}
```

### Zero-Copy with CompositeByteBuf

```java
// ✅ Good - CompositeByteBuf for zero-copy aggregation
ByteBuf header = ctx.alloc().buffer(4).writeInt(messageId);
ByteBuf body = ctx.alloc().buffer(content.length).writeBytes(content);

CompositeByteBuf composite = ctx.alloc().compositeBuffer(2);
composite.addComponents(true, header, body);
ctx.writeAndFlush(composite);
// No memory copy! header and body share the composite

// ❌ Bad - copy into new buffer
ByteBuf combined = ctx.alloc().buffer(header.readableBytes() + body.readableBytes());
combined.writeBytes(header);
combined.writeBytes(body); // memory copy overhead
```

## WebSocket Best Practices

### Heartbeat & Idle Detection

```java
// ✅ Good - idle state handler + heartbeat
p.addLast("idleState", new IdleStateHandler(
    60,  // reader idle (no data from client)
    30,  // writer idle (no data to client)
    0    // all idle
));

public class HeartbeatHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                // No data from client for 60s → close
                ctx.close();
            } else if (event.state() == IdleState.WRITER_IDLE) {
                // No data to client for 30s → send ping
                ctx.writeAndFlush(new PingWebSocketFrame());
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
}
```

### Frame Size Limits

```java
// ✅ Good - limit frame size to prevent OOM
p.addLast("wsProtocol", new WebSocketServerProtocolHandler(
    "/ws",
    null,
    true,                    // allow extensions
    65536,                   // max frame size (64KB)
    false,                   // allowMaskMismatch
    true                     // performMasking
));

// ❌ Bad - unlimited frame size (client can send 1GB frame = OOM)
p.addLast("wsProtocol", new WebSocketServerProtocolHandler("/ws"));
```

## Graceful Shutdown

```java
// ✅ Good - graceful shutdown with preDestroy
@PreDestroy
public void shutdown() {
    log.info("Shutting down Netty server...");

    // 1. Stop accepting new connections
    serverChannel.close().syncUninterruptibly();

    // 2. Notify existing clients
    for (Channel channel : allChannels) {
        channel.writeAndFlush(new CloseWebSocketFrame(1001, "Server shutting down"));
    }

    // 3. Wait for in-flight operations
    Thread.sleep(5000);

    // 4. Shutdown event loops
    bossGroup.shutdownGracefully(2, 10, TimeUnit.SECONDS);
    workerGroup.shutdownGracefully(2, 10, TimeUnit.SECONDS);
    businessGroup.shutdownGracefully(2, 10, TimeUnit.SECONDS);

    log.info("Netty server shutdown complete");
}
```

## Anti-Patterns

| Anti-Pattern | Problem | Solution |
|-------------|---------|---------|
| Blocking I/O in EventLoop | Stalls all channels on that thread | Offload to business thread pool |
| `synchronized` in handler | Blocks EventLoop thread | Use Channel attributes / CAS / ConcurrentHashMap |
| No timeouts on connections | Zombie connections consume resources | IdleStateHandler + heartbeat |
| No frame size limit | Client can cause OOM with huge frame | Set maxFrameSize in WebSocket handler |
| Memory leak (ByteBuf not released) | OOM over time | Use SimpleChannelInboundHandler / try-finally release |
| Too many threads | Context switching overhead | Thread count = CPU cores (Turms style) |
| Per-channel handler instances | High memory usage, GC pressure | Use @Sharable handlers + Channel attributes |
| `channel.write()` without `flush()` | Data not sent, memory buildup | Use `writeAndFlush()` or periodic flush |
| Ignoring `ChannelFuture` | Silent failures | Add listeners to write futures |

## References

- Netty User Guide: https://netty.io/wiki/user-guide-for-4.x.html
- Turms (Java/Netty IM): https://github.com/turms-im/turms
- Netty Best Practices: https://github.com/netty/netty/wiki/Best-Practices
- Netty in Action (book): https://www.manning.com/books/netty-in-action
