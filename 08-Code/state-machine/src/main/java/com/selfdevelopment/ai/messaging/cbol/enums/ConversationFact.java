package com.selfdevelopment.ai.messaging.cbol.enums;

/**
 * Events (facts) that drive conversation state transitions.
 * <p>
 * Survey-related events: SURVEY_START enters the survey in-progress state,
 * SURVEY_COMPLETE exits it normally, SYS_SURVEY_TIMEOUT exits it on timeout.
 */
public enum ConversationFact {
    // LIFECYCLE
    CUSTOMER_CONNECT,
    AGENT_ATTACHED,
    // TRANSFER
    TRANSFER_REQUEST,
    TRANSFER_CONNECTED,
    TRANSFER_FAILED,
    TRANSFER_TIMEOUT,
    // SURVEY (survey as in-progress state, controlled by flow)
    SURVEY_START,
    SURVEY_COMPLETE,
    // ENDING
    CUSTOMER_CLOSE,
    AGENT_CLOSE,
    // SYSTEM
    SYS_CUSTOMER_IDLE,
    SYS_TRANSFER_TIMEOUT,
    SYS_ENDING_GRACE_TIMEOUT,
    SYS_SURVEY_TIMEOUT,
    // FAILOVER (action error → fail event → fail branch)
    SYS_ACTION_FAILED,
    SYS_RETRY,
    SYS_ABORT
}