# Serialization

> Message serialization formats and protocols.

## Comparison

| Format | Size | Speed | Readability | Schema Evolution | Use Case |
|--------|------|-------|-------------|-----------------|----------|
| JSON | Large | Slow | Yes | Flexible | REST API, debugging |
| Protobuf | Small | Fast | No | Backward compat | Internal service, high throughput |
| MessagePack | Medium | Fast | No | Flexible | Binary JSON alternative |
| Avro | Small | Fast | No | Schema required | Kafka, big data |
| Kryo | Small | Very fast | No | Java-specific | Java-only internal |

## Recommendation

| Layer | Format | Reason |
|-------|--------|--------|
| Client <-> Gateway | JSON (WebSocket) | Easy debugging, browser support |
| Service <-> Service | Protobuf | High performance, schema evolution |
| Message Queue (Kafka) | Protobuf / Avro | Compact, schema registry |
| Database | JSON (MySQL JSON) | Flexible, queryable |
| Cache (Redis) | JSON / Protobuf | Depends on size requirement |

## Protobuf Message Definition

```protobuf
syntax = "proto3";

package com.cbol.im.protocol;

option java_package = "com.cbol.im.protocol";
option java_outer_classname = "ImProtocol";

// Envelope for all messages
message ImMessage {
    string msg_id = 1;
    int64 seq_id = 2;
    string conversation_id = 3;
    string sender_id = 4;
    MessageType type = 5;
    bytes content = 6;  // serialized content based on type
    int64 timestamp = 7;
    MessageStatus status = 8;
    string reply_to = 9;
}

enum MessageType {
    UNKNOWN = 0;
    TEXT = 1;
    IMAGE = 2;
    FILE = 3;
    VOICE = 4;
    VIDEO = 5;
    SYSTEM = 6;
    READ_RECEIPT = 7;
    TYPING = 8;
}

enum MessageStatus {
    SENDING = 0;
    SENT = 1;
    DELIVERED = 2;
    READ = 3;
    FAILED = 4;
}

message TextContent {
    string text = 1;
    repeated string mentions = 2;
}

message ImageContent {
    string url = 1;
    int32 width = 2;
    int32 height = 3;
    string thumbnail_url = 4;
}

message FileContent {
    string url = 1;
    string filename = 2;
    int64 size = 3;
    string mime_type = 4;
}

// Sync request
message SyncRequest {
    string device_id = 1;
    repeated ConversationCursor cursors = 2;
}

message ConversationCursor {
    string conversation_id = 1;
    int64 last_delivered_seq = 2;
}

message SyncResponse {
    repeated ConversationMessages conversations = 1;
    bool has_more = 2;
}

message ConversationMessages {
    string conversation_id = 1;
    repeated ImMessage messages = 2;
    int64 new_max_seq = 3;
}
```

## JSON Message Format (Client-Facing)

```json
{
  "msgId": "uuid-or-snowflake",
  "seqId": 12345,
  "conversationId": "conv_xxx",
  "senderId": "user_xxx",
  "type": "text",
  "content": {
    "text": "Hello world",
    "mentions": ["user_1"]
  },
  "timestamp": 1718438400000,
  "status": "sent",
  "replyTo": null
}
```

## Java Serialization Utilities

### JSON (Jackson)
```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
```

### Protobuf (Spring Boot)
```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>3.25.0</version>
</dependency>
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.25.0:exe:${os.detected.classifier}</protocArtifact>
    </configuration>
    <executions>
        <execution>
            <goals><goal>compile</goal></goals>
        </execution>
    </executions>
</plugin>
```

## Schema Evolution Rules

### Protobuf
- Never reuse field numbers
- New fields must be optional (proto3 default)
- Don't change field types
- Deprecate instead of delete (use `reserved`)

### JSON
- Add new fields with defaults
- Never remove fields (mark deprecated)
- Use version field for breaking changes: `"version": 2`

## Content-Type Negotiation

For WebSocket, use subprotocol negotiation:
```
Sec-WebSocket-Protocol: im.json.v1, im.protobuf.v1
```
Server picks one and responds with selected protocol.

## Reference: Turms Protocol
Turms uses Protobuf for client-server communication with a custom transport layer. Protocol buffers defined in separate module for shared use by server and clients.
