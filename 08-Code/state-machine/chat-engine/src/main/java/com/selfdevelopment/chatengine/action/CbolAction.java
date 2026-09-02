package com.selfdevelopment.chatengine.action;

import com.selfdevelopment.chatengine.context.CbolStateContext;

@FunctionalInterface
public interface CbolAction {
    void execute(CbolStateContext ctx);
}