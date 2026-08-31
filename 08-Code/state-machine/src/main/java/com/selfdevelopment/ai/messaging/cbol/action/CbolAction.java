package com.selfdevelopment.ai.messaging.cbol.action;

import com.selfdevelopment.ai.messaging.cbol.context.CbolStateContext;

@FunctionalInterface
public interface CbolAction {
    void execute(CbolStateContext ctx);
}