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
 * SURVEY_IN_PROGRESS is treated as an "in-progress" state: the conversation
 * has ended from a messaging perspective, but the customer is still active
 * completing a post-conversation survey. The survey flow is controlled by
 * the state machine (SURVEY_START → SURVEY_IN_PROGRESS → SURVEY_COMPLETE/SYS_SURVEY_TIMEOUT → ENDING).
 * <p>
 * ERROR is a failover state: entered when an action throws an unhandled exception
 * (SYS_ACTION_FAILED). From ERROR, the system can retry (SYS_RETRY), abort (SYS_ABORT),
 * or be recovered by an operator.
 */
public enum ConversationState {
    NEW,
    INITIATED,
    ACTIVE,
    TRANSFERRED,
    SURVEY_IN_PROGRESS,
    ENDING,
    ERROR,
    CLOSED
}