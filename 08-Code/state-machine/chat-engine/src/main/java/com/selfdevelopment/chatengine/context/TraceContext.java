package com.selfdevelopment.chatengine.context;

import lombok.Builder;
import java.util.Map;
import java.util.UUID;

@Builder
public record TraceContext(
        String traceId,
        String spanId,
        String parentSpanId,
        long startTimeMs,
        Map<String, String> tags
) {
    public static TraceContext generate() {
        return TraceContext.builder()
                .traceId(UUID.randomUUID().toString())
                .spanId(UUID.randomUUID().toString())
                .parentSpanId(null)
                .startTimeMs(System.currentTimeMillis())
                .tags(Map.of())
                .build();
    }
}