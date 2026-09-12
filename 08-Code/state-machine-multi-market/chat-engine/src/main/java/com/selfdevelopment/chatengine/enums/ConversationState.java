package com.selfdevelopment.chatengine.enums;

/**
 * Conversation lifecycle states.
 * <p>
 * Based on the Event-Driven Orchestration Design (v4.0):
 * <p>
 * NEW: The conversation record has been created (e.g., customer opened the chat window),
 * but the conversation has not started yet. No messages have been exchanged, and no
 * agent/AI has been assigned.
 * <p>
 * INITIATED: The conversation has started (SESSION_STARTED), but is waiting for
 * interaction ready or downstream assignment. Also used as the fallback state after
 * a transfer failure (re-routing).
 * <p>
 * ACTIVE: The current bound interaction is ready (InteractionState=CONNECTED).
 * Entered when INTERACTION_BECAME_ACTIVE is fired.
 * <p>
 * IN_PROGRESS: Customer inbound message (INBOUND) has been received, business is in progress.
 * Entered when INBOUND_MESSAGE_RECEIVED is fired from ACTIVE.
 * <p>
 * TRANSFERRED: CBOL cross-channel transfer phase (in-flight, waiting for target result or timeout).
 * Entered when SOURCE_INTERACTION_TRANSFERRED is fired from IN_PROGRESS.
 * <p>
 * ENDING: Irreversible - pre-close orchestration (guarantees final CLOSED).
 * Entered via ENDING_STARTED, CUSTOMER_IDLE_TIMEOUT, or SYSTEM_ERROR from any business state.
 * <p>
 * CLOSED: Final convergence state. No further state transitions are possible from this state.
 */
public enum ConversationState {
    NEW,            // 会话已创建，等待 interaction ready（或下游分配）
    INITIATED,      // 当前绑定 interaction ready（InteractionState=CONNECTED）
    ACTIVE,         // 已收到客户入站消息（INBOUND），业务进行中
    IN_PROGRESS,    // 业务进行中
    TRANSFERRED,    // CBOL 跨渠道转接阶段（in-flight，等待 target 结果或超时）
    ENDING,         // 不可逆：关闭前收尾编排（保证最终 CLOSED）
    CLOSED          // 最终收敛态
}
