# Networking (Netty)

> WebSocket/TCP server implementation with Netty.

## Netty Server Bootstrap

```java
@Component
public class ImServer {
    private final ImChannelInitializer channelInitializer;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    @Value("${im.netty.port:9000}")
    private int port;
    
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
        
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childHandler(channelInitializer);
        
        serverChannel = bootstrap.bind(port).sync().channel();
    }
    
    @PreDestroy
    public void stop() {
        if (serverChannel != null) serverChannel.close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
```

## Channel Pipeline

```java
@Component
@RequiredArgsConstructor
public class ImChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final AuthHandler authHandler;
    private final HeartbeatHandler heartbeatHandler;
    private final MessageCodec messageCodec;
    private final BusinessHandler businessHandler;
    
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // HTTP protocol (for WebSocket upgrade)
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 65536));
        
        // Custom handlers
        pipeline.addLast("auth", authHandler);
        pipeline.addLast("heartbeat", new IdleStateHandler(60, 0, 0));
        pipeline.addLast("heartbeatHandler", heartbeatHandler);
        pipeline.addLast("codec", messageCodec);
        pipeline.addLast("business", businessHandler);
    }
}
```

## Authentication Handler

```java
@Component
@RequiredArgsConstructor
public class AuthHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final JwtTokenProvider tokenProvider;
    private final SessionRegistry sessionRegistry;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        // First message must be auth
        JsonNode json = objectMapper.readTree(frame.text());
        if ("auth".equals(json.get("type").asText())) {
            String token = json.get("token").asText();
            if (tokenProvider.validate(token)) {
                String userId = tokenProvider.getUserId(token);
                String deviceId = json.get("deviceId").asText();
                ctx.channel().attr(Attributes.USER_ID).set(userId);
                ctx.channel().attr(Attributes.DEVICE_ID).set(deviceId);
                sessionRegistry.register(userId, deviceId, ctx.channel());
                ctx.fireChannelRead(frame); // pass to next handler
            } else {
                ctx.close();
            }
        } else {
            // Not authenticated yet
            ctx.writeAndFlush(new TextWebSocketFrame(
                "{\"type\":\"error\",\"code\":401,\"message\":\"Not authenticated\"}"));
            ctx.close();
        }
    }
}
```

## Heartbeat Handler

```java
@Component
public class HeartbeatHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // No data read for 60s -> close connection
                ctx.close();
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        JsonNode json = objectMapper.readTree(frame.text());
        if ("ping".equals(json.get("type").asText())) {
            ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"pong\"}"));
            return; // don't pass to business handler
        }
        ctx.fireChannelRead(frame);
    }
}
```

## Message Codec

```java
@Component
@RequiredArgsConstructor
public class MessageCodec extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ObjectMapper objectMapper;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        ImMessage message = objectMapper.readValue(frame.text(), ImMessage.class);
        ctx.fireChannelRead(message); // pass decoded message to business handler
    }
    
    // Outbound: ImMessage -> TextWebSocketFrame
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof ImMessage) {
            String json = objectMapper.writeValueAsString(msg);
            ctx.write(new TextWebSocketFrame(json), promise);
        } else {
            ctx.write(msg, promise);
        }
    }
}
```

## Business Handler

```java
@Component
@Sharable
@RequiredArgsConstructor
public class BusinessHandler extends SimpleChannelInboundHandler<ImMessage> {
    private final MessageDispatcher messageDispatcher;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ImMessage message) {
        String userId = ctx.channel().attr(Attributes.USER_ID).get();
        message.setSenderId(userId);
        messageDispatcher.dispatch(message);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String userId = ctx.channel().attr(Attributes.USER_ID).get();
        String deviceId = ctx.channel().attr(Attributes.DEVICE_ID).get();
        if (userId != null) {
            sessionRegistry.unregister(userId, deviceId);
        }
        ctx.fireChannelInactive();
    }
}
```

## Channel Attributes

```java
public final class Attributes {
    public static final AttributeKey<String> USER_ID = 
        AttributeKey.valueOf("userId");
    public static final AttributeKey<String> DEVICE_ID = 
        AttributeKey.valueOf("deviceId");
    public static final AttributeKey<Long> LAST_HEARTBEAT = 
        AttributeKey.valueOf("lastHeartbeat");
    
    private Attributes() {}
}
```

## Message Dispatcher

```java
@Component
@RequiredArgsConstructor
public class MessageDispatcher {
    private final MessageService messageService;
    private final ThreadPoolTaskExecutor messageExecutor;
    
    public void dispatch(ImMessage message) {
        // Route by type
        switch (message.getType()) {
            case "chat":
                messageExecutor.submit(() -> messageService.handleChatMessage(message));
                break;
            case "read_receipt":
                messageExecutor.submit(() -> messageService.handleReadReceipt(message));
                break;
            case "typing":
                messageService.handleTypingIndicator(message); // sync, lightweight
                break;
            default:
                log.warn("Unknown message type: {}", message.getType());
        }
    }
}
```

## WebSocket vs Raw TCP

| Aspect | WebSocket | Raw TCP |
|--------|-----------|---------|
| Protocol | HTTP upgrade | Binary protocol |
| Firewall friendly | Yes (port 80/443) | No (custom port) |
| Overhead | Frame headers (2-14 bytes) | Custom (minimal) |
| Browser support | Native | Need custom client |
| TLS | WSS (easy) | Custom SSL |
| Recommended | Web/hybrid clients | Mobile/native clients |

## Reference: Netty Best Practices
- Use `@Sharable` for stateless handlers
- Never block in EventLoop thread (offload to business executor)
- Use `PooledByteBufAllocator` for memory efficiency
- Set `TCP_NODELAY` for low latency
- Use `IdleStateHandler` for connection cleanup
