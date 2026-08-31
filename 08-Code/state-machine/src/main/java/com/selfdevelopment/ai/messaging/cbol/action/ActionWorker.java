package com.selfdevelopment.ai.messaging.cbol.action;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;
import com.selfdevelopment.ai.messaging.cbol.context.TraceMdcHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ActionWorker {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void submit(CbolAction action, CbolStateContext ctx) {
        String idempotentKey = UUID.randomUUID().toString();
        executor.submit(() -> {
            try {
                TraceMdcHelper.set(ctx.traceContext());
                log.info("ActionWorker submit, idempotentKey={}", idempotentKey);
                action.execute(ctx);
            } catch (Exception e) {
                log.error("Action execute error, idempotentKey={}", idempotentKey, e);
            } finally {
                TraceMdcHelper.clear();
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}