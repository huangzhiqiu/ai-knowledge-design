package com.selfdevelopment.chatengine.enums;

/**
 * Conversation lifecycle states.
 * <p>
 * NEW is the initial state: the conversation record has been created (e.g., customer
 * opened the chat window), but the conversation has not started yet. No messages have
 * been exchanged, and no agent/AI has been assigned.
 * <p>
 * INITIATED: the conversation has started (customer sent first message or system
 * initialized), but is waiting for customer connection or agent assignment. Also used
 * as the fallback state after a transfer failure (re-routing).
 * <p>
 * IN_PROGRESS: the conversation is actively in progress. This state encompasses both
 * the messaging phase (chatting, receiving messages) and the survey phase. The survey
 * is NOT a separate state — it is a sub-phase within IN_PROGRESS, triggered by the
 * SURVEY_START event (internal transition, state remains IN_PROGRESS). When the survey
 * completes (SURVEY_COMPLETE) or times out (SYS_SURVEY_TIMEOUT), the conversation
 * transitions directly to ENDING.
 * <p>
 * ENDING: the conversation is in the ending grace period. All active communication has
 * ceased, but the system is waiting for any final cleanup or delayed messages before
 * closing permanently.
 * <p>
 * ERROR is a failover state: entered when an action throws an unhandled exception
 * (SYS_ACTION_FAILED). From ERROR, the system can retry (SYS_RETRY), abort (SYS_ABORT),
 * or be recovered by an operator.
 * <p>
 * CLOSED: the conversation is permanently closed. No further state transitions are
 * possible from this state.
 */
public enum ConversationState {
    NEW,
    INITIATED,
    IN_PROGRESS,
    TRANSFERRED,
    ENDING,
    ERROR,
    CLOSED
}
