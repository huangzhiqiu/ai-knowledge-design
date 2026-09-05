package com.selfdevelopment.chatengine.enums;

/**
 * Events (facts) that drive conversation state transitions.
 * <p>
 * Based on the Event-Driven Orchestration Design (v4.0).
 * Normalizer normalizes external events into Facts, and the Conversation/Interaction
 * state machines only consume Facts.
 * <p>
 * Key design principles:
 * - Transfer failure/timeout does NOT rollback; Conversation returns directly to INITIATED
 * - Survey is field-based (surveyStatus) in ENDING, not a separate state
 * - Customer Idle ideal logic: all wait-capable states timeout -> ENDING with endReason=CUSTOMER_IDLE
 * - TRANSFERRED customer idle: enter ENDING but don't cancel transfer, defer CloseInteractions
 * - ENDING is irreversible, defaults to 120s forced convergence to CLOSED
 */
public enum ConversationFact {
    // ===== lifecycle =====
    SESSION_STARTED,                    // NEW → INITIATED: conversation initialization prepared
    ALL_INTERACTIONS_ENDED,             // ENDING → CLOSED: all interactions closed

    // ===== readiness & messaging =====
    INTERACTION_BECAME_ACTIVE,          // INITIATED → ACTIVE: interaction ready
    INBOUND_MESSAGE_RECEIVED,           // ACTIVE → IN_PROGRESS: customer inbound message

    // ===== ending =====
    ENDING_STARTED,                      // Various → ENDING: unified ending entry (payload: endReason)
    ENDING_ACTIONS_COMPLETED,           // ENDING → CLOSED: ending actions done
    ENDING_TIMEOUT,                      // ENDING → CLOSED: force close at ending deadline (>=120s)

    // ===== customer idle (ideal rule) =====
    CUSTOMER_IDLE_TIMEOUT,               // Various → ENDING: customer idle timeout (endReason=CUSTOMER_IDLE)

    // ===== survey (field in ENDING, not a separate state) =====
    SURVEY_SUBMITTED,                    // ENDING: survey submitted (surveyStatus=SUBMITTED)
    SURVEY_TIMEOUT,                       // ENDING: survey timeout (surveyStatus=TIMEOUT, endReason=CUSTOMER_IDLE)
    SURVEY_SKIPPED,                       // ENDING: survey skipped (surveyStatus=SKIPPED)

    // ===== transfer (cross-channel) =====
    SOURCE_INTERACTION_TRANSFERRED,      // IN_PROGRESS → TRANSFERRED: source interaction detached
    TARGET_INTERACTION_INITIATED,        // TRANSFERRED → TRANSFERRED: target interaction initiated
    TARGET_INTERACTION_CONNECTED,        // TRANSFERRED → ACTIVE: target interaction connected (no rollback)
    TARGET_INTERACTION_CONNECT_FAILED,   // TRANSFERRED → INITIATED: target connect failed (no rollback)
    TRANSFER_TIMEOUT,                     // TRANSFERRED → INITIATED: transfer timeout (>=180s, no rollback)

    // ===== genesys same-channel / consult (conversation no-op) =====
    GENESYS_CONSULT_TRANSFER_STARTED,    // GENESYS only: consult transfer started
    GENESYS_CONSULT_TRANSFER_ENDED,      // GENESYS only: consult transfer ended
    GENESYS_AGENT_TRANSFER_STARTED,      // GENESYS only: agent transfer started
    GENESYS_AGENT_TRANSFER_COMPLETED,    // GENESYS only: agent transfer completed
    GENESYS_AGENT_TRANSFER_FAILED,        // GENESYS only: agent transfer failed

    // ===== system =====
    SYSTEM_ERROR,                         // Various → ENDING: system error (endReason=SYSTEM_ERROR)

    // ===== downstream availability =====
    DOWNSTREAM_UNAVAILABLE                // INITIATED → INITIATED: downstream unavailable (stay, notify)
}
