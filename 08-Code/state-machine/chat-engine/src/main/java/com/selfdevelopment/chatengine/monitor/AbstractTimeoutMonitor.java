package com.selfdevelopment.chatengine.monitor;

import com.selfdevelopment.chatengine.context.CbolStateContext;
import com.selfdevelopment.chatengine.enums.ConversationFact;
import com.selfdevelopment.chatengine.enums.ConversationState;
import com.selfdevelopment.chatengine.service.ChatEngineStateMachineService;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for time-based monitors that trigger state machine events.
 * <p>
 * Subclasses define applicable states, timeout threshold, and event to fire.
 */
@RequiredArgsConstructor
public abstract class AbstractTimeoutMonitor {

    protected final ChatEngineStateMachineService chatEngineStateMachineService;

    /**
     * Returns whether this monitor applies to the given conversation state.
     */
    protected abstract boolean isApplicable(ConversationState state);

    /**
     * Returns the timeout threshold in seconds from the market config.
     */
    protected abstract long timeoutSeconds(CbolStateContext ctx);

    /**
     * Returns the event to fire when the timeout is exceeded.
     */
    protected abstract ConversationFact timeoutEvent();

    /**
     * Checks whether the timeout has been exceeded and fires the event if so.
     *
     * @param ctx         the conversation context
     * @param referenceTs the reference timestamp (e.g., last activity time)
     * @throws NullPointerException if ctx or ctx.conversation is null
     */
    public void check(CbolStateContext ctx, long referenceTs) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(ctx.conversation(), "ctx.conversation must not be null");

        if (!isApplicable(ctx.conversation().state())) {
            return;
        }

        long timeoutMs = TimeUnit.SECONDS.toMillis(timeoutSeconds(ctx));
        long elapsedMs = System.currentTimeMillis() - referenceTs;

        if (elapsedMs >= timeoutMs) {
            chatEngineStateMachineService.fire(ctx, timeoutEvent());
        }
    }
}
