package com.selfdevelopment.agentconnector.enums;

/**
 * Events that drive the Interaction (channel/connection) state machine.
 * <p>
 * Based on Event-Driven Orchestration Design (v4.0) - InteractionFactEvent.
 * <p>
 * These are channel-level events (connection lifecycle, messaging, heartbeat,
 * reconnection, genesys consult, cross-channel transfer, ending) as opposed
 * to ConversationFact which are business-level events.
 */
public enum InteractionFact {

    // ===== connection lifecycle =====
    CONNECTION_SUCCESS,              // INITIATED → CONNECTED
    CONNECTION_FAIL,                 // INITIATED → CLOSED

    // ===== messaging =====
    FIRST_INBOUND_MESSAGE_RECEIVED, // CONNECTED → IN_PROGRESS
    INBOUND_MESSAGE_RECEIVED,        // IN_PROGRESS → IN_PROGRESS (internal, update lastInboundAt)
    OUTBOUND_MESSAGE_SENT,           // IN_PROGRESS → IN_PROGRESS (internal, audit)

    // ===== heartbeat & degradation =====
    HEARTBEAT_MISS,                  // CONNECTED/IN_PROGRESS → DEGRADED
    HEARTBEAT_RESTORED,              // DEGRADED → CONNECTED/IN_PROGRESS

    // ===== reconnection =====
    RECONNECT_ATTEMPT,               // DEGRADED → RECONNECTING
    RECONNECT_SUCCESS,               // RECONNECTING → CONNECTED/IN_PROGRESS
    RECONNECT_FAIL,                  // RECONNECTING → CLOSED (max retries exceeded)

    // ===== genesys consult transfer (same-channel, GENESYS ONLY) =====
    CONSULT_TRANSFER_STARTED,        // IN_PROGRESS → CONSULT_TRANSFER
    CONSULT_TRANSFER_ENDED,          // CONSULT_TRANSFER → IN_PROGRESS

    // ===== cross-channel transfer (source detach marker) =====
    TRANSFER_SUCCESS,                 // IN_PROGRESS → TRANSFERRED (source detached)
    TRANSFER_FAILED,                  // IN_PROGRESS → IN_PROGRESS (transfer rejected, stay)

    // ===== ending =====
    END_REQUESTED,                    // any non-terminal → CLOSED
    INTERACTION_CLOSED,               // terminal confirmation (audit)

    // ===== system =====
    SYSTEM_ERROR,                     // any → CLOSED (unrecoverable)

    // ===== downstream availability =====
    DOWNSTREAM_UNAVAILABLE            // CONNECTED/IN_PROGRESS → DEGRADED (temporary)
}
