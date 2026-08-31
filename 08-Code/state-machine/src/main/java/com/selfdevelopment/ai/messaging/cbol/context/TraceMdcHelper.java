package com.selfdevelopment.ai.messaging.cbol.context;

import org.slf4j.MDC;

public class TraceMdcHelper {
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    public static void set(TraceContext ctx) {
        if (ctx == null) return;
        MDC.put(MDC_TRACE_ID, ctx.traceId());
        MDC.put(MDC_SPAN_ID, ctx.spanId());
    }

    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }
}